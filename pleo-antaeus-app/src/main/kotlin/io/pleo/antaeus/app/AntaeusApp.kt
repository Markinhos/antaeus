/*
    Defines the main() entry point of the app.
    Configures the database and sets up the REST web service.
 */

@file:JvmName("AntaeusApp")

package io.pleo.antaeus.app

import getEmailProvider
import getPaymentProvider
import getTicketingProvider
import io.pleo.antaeus.core.resilience.getRetryRegistry
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.CustomerOperationsService
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.EmailService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.data.CustomerTable
import io.pleo.antaeus.data.InvoiceTable
import io.pleo.antaeus.rest.AntaeusRest
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import setupInitialData
import java.io.File
import java.sql.Connection
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


fun main() {
    // The tables to create in the database.
    val tables = arrayOf(InvoiceTable, CustomerTable)

    val dbFile: File = File.createTempFile("antaeus-db", ".sqlite")
    // Connect to the database and create the needed tables. Drop any existing data.
    val db = Database
        .connect(url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC",
            user = "root",
            password = "")
        .also {
            TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
            transaction(it) {
                addLogger(StdOutSqlLogger)
                // Drop all existing tables to ensure a clean slate on each run
                SchemaUtils.drop(*tables)
                // Create all tables
                SchemaUtils.create(*tables)
            }
        }

    // Set up data access layer.
    val dal = AntaeusDal(db = db)

    // Insert example data in the database.
    setupInitialData(dal = dal)

    // Get third parties
    val paymentProvider = getPaymentProvider()
    val emailProvider = getEmailProvider()
    val ticketingProvider = getTicketingProvider()


    // Create core services
    val invoiceService = InvoiceService(dal = dal)
    val customerService = CustomerService(dal = dal)
    val emailService = EmailService(emailProvider = emailProvider, customerService = customerService)
    val customerOperationsService = CustomerOperationsService(ticketingProvider = ticketingProvider)

    // Get retry registry
    val registry = getRetryRegistry()

    // This is _your_ billing service to be included where you see fit
    val billingService = BillingService(
        retry = registry.retry("payment-retry"),
        paymentProvider = paymentProvider,
        invoiceService = invoiceService,
        emailService = emailService,
        customerOperationsService = customerOperationsService
    )

    // Schedule billing
    val scheduler = Executors.newScheduledThreadPool(1)
    scheduler.scheduleAtFixedRate({
        val cal = Calendar.getInstance()
        val dayOfMonth = cal[Calendar.DAY_OF_MONTH]
        if (dayOfMonth == 1) {
            // If is the first day of the month schedule pending payments
            billingService.billClients()
        } else {
            // Otherwise, retry failed invoices
            billingService.retryFailedInvoices()
        }
    }, 0, 1, TimeUnit.DAYS)

    // Create REST web service
    AntaeusRest(
        invoiceService = invoiceService,
        customerService = customerService,
        billingService = billingService
    ).run()
}
