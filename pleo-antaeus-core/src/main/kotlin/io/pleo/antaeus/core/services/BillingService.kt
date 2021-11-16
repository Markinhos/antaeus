package io.pleo.antaeus.core.services

import io.github.resilience4j.retry.Retry
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus


class BillingService(
    private val retry: Retry,
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService,
    private val emailService: EmailService,
    private val customerOperationsService: CustomerOperationsService
) {

    fun billClients() {
        invoiceService.fetchAll().forEach {
            if (it.status == InvoiceStatus.PENDING) {
                billInvoice(it)
            }
        }
    }

    fun retryFailedInvoices() {
        invoiceService.fetchAll().forEach {
            if (it.status == InvoiceStatus.RETRYABLE_FAILED) {
                billInvoice(it)
            }
        }
    }

    private fun billInvoice(it: Invoice) {
        try {
            retry.executeSupplier {
                paymentProvider.charge(it)
            }
            invoiceService.update(it.id, it.amount, it.customerId, InvoiceStatus.PAID)
        } catch (currencyMismatchException: CurrencyMismatchException) {
            emailService.emailCurrencyMismatch(it.customerId, it)
            invoiceService.update(it.id, it.amount, it.customerId, InvoiceStatus.RETRYABLE_FAILED)
        } catch (customerNotFoundException: CustomerNotFoundException) {
            customerOperationsService.createTicketToOperationsForCustomerNotFound(it.id)
            invoiceService.update(it.id, it.amount, it.customerId, InvoiceStatus.FAILED)
        }
    }
}
