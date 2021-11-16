package io.pleo.antaeus.core.external

import io.pleo.antaeus.models.CustomerErrorCodes

interface TicketingProvider {
    fun openTicket(invoiceId: Int, errorCode: CustomerErrorCodes): Int
}