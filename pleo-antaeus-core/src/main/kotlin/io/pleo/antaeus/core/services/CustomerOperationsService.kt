package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.TicketingProvider
import io.pleo.antaeus.models.CustomerErrorCodes

class CustomerOperationsService(private val ticketingProvider: TicketingProvider) {

    fun createTicketToOperationsForCustomerNotFound(invoiceId: Int) {
        ticketingProvider.openTicket(invoiceId, CustomerErrorCodes.CUSTOMER_NOT_FOUND)
    }
}