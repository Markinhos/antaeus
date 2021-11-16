package io.pleo.antaeus.core.services

import io.github.resilience4j.retry.Retry
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Billing service class provides two public methods: billClients() and retryFailedInvoices(). The former is used
 * for billing pending invoices every first day of the month. That latter is used to retry every day failed invoices that
 * are considered to be retryable.
 */
class BillingService(
    private val retry: Retry,
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService,
    private val emailService: EmailService,
    private val customerOperationsService: CustomerOperationsService
) {

    /**
     * Bills pending invoices
     */
    fun billClients() {
        invoiceService.fetchPendingInvoices().forEach {
            billInvoice(it)
        }
    }

    /**
     * Bills retryable invoices
     */
    fun retryFailedInvoices() {
        invoiceService.fetchRetryableInvoices().forEach {
            billInvoice(it)
        }
    }

    private fun billInvoice(invoice: Invoice) {
        try {
            retry.executeSupplier {
                paymentProvider.charge(invoice)
            }
            invoiceService.update(invoice.id, invoice.amount, invoice.customerId, InvoiceStatus.PAID)
            logger.info { "Customer charged with $invoice" }
        } catch (currencyMismatchException: CurrencyMismatchException) {
            logger.warn { "Failed to charge invoice $invoice due to currency mismatch" }
            emailService.emailCurrencyMismatch(invoice.customerId, invoice)
            invoiceService.update(invoice.id, invoice.amount, invoice.customerId, InvoiceStatus.RETRYABLE_FAILED)
        } catch (customerNotFoundException: CustomerNotFoundException) {
            logger.warn { "Failed to charge invoice $invoice due to customer not found" }
            customerOperationsService.createTicketToOperationsForCustomerNotFound(invoice.id)
            invoiceService.update(invoice.id, invoice.amount, invoice.customerId, InvoiceStatus.FAILED)
        } catch (throwable: Throwable) {
            logger.error { "Failed to charge invoice $invoice due to $throwable" }
            invoiceService.update(invoice.id, invoice.amount, invoice.customerId, InvoiceStatus.RETRYABLE_FAILED)
        }
    }
}
