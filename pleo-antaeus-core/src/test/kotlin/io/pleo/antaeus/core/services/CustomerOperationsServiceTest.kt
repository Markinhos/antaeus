package io.pleo.antaeus.core.services

import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.external.TicketingProvider
import io.pleo.antaeus.models.CustomerErrorCodes
import org.junit.jupiter.api.Test

internal class CustomerOperationsServiceTest {

    @Test
    fun `given a customer operations service when call createTicketToOperationsForCustomerNotFound expect ticket service called`() {
        val ticketingProvider = mockk<TicketingProvider>(relaxed = true)

        val customerOperationsService = CustomerOperationsService(ticketingProvider)
        customerOperationsService.createTicketToOperationsForCustomerNotFound(1)

        verify { ticketingProvider.openTicket(1, CustomerErrorCodes.CUSTOMER_NOT_FOUND) }
    }
}