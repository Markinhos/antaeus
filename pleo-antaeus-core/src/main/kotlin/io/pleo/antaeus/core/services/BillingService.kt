package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.InvoiceStatus

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService
) {
    fun billClients() {
        invoiceService.fetchAll().forEach {
            if (it.status == InvoiceStatus.PENDING) {
                paymentProvider.charge(it)
            }
        }
    }
}
