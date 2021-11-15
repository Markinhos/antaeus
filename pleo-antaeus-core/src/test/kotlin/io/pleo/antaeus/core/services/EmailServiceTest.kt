package io.pleo.antaeus.core.services

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.pleo.antaeus.core.external.EmailProvider
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class EmailServiceTest {
    @Test
    fun `given an email service when emailCurrencyMismatch is called expected to call email provider with the correct id and template`() {
        val customerService = mockk<CustomerService>()
        val emailProvider = mockk<EmailProvider>()
        val customer = mockk<Customer>()
        val invoice = mockk<Invoice>()
        val customerId = 1
        val email = "test@pleo.io"
        val name = "MrCustomer"
        val customerCurrency = Currency.DKK
        val invoiceCurrency = Currency.USD
        val invoiceId = 1

        val template = """
            Dear $name,
            
            Pleo is sorry to communicate that the invoice id $invoiceId could not be processed
            because it does not match your currency (Invoice currency: $invoiceCurrency - Customer currency $customerCurrency). 
            Please, fix your currency and they payment will be retried on the
            next day.
            
            Sincerely
        """

        every { customer.email } returns email
        every { customer.name } returns name
        every { customer.currency } returns customerCurrency
        every { invoice.amount } returns Money(BigDecimal.valueOf(1), invoiceCurrency)
        every { invoice.id } returns invoiceId
        every { customerService.fetch(customerId) } returns customer
        every { emailProvider.emailCustomer(email, template) } just Runs

        val emailService = EmailService(customerService, emailProvider)
        emailService.emailCurrencyMismatch(customerId, invoice)
    }
}