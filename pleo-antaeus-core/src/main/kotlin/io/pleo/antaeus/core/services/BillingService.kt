package io.pleo.antaeus.core.services

import io.github.resilience4j.retry.Retry
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.InvoiceStatus


class BillingService(
    private val retry: Retry,
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService
) {

    fun billClients() {
        invoiceService.fetchAll().forEach {
            if (it.status == InvoiceStatus.PENDING) {
                retry.executeSupplier {
                    paymentProvider.charge(it)
                }
            }
        }
    }
}
