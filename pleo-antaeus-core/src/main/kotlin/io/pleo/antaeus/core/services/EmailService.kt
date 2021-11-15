package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.EmailProvider
import io.pleo.antaeus.models.Invoice

class EmailService(private val customerService: CustomerService, private val emailProvider: EmailProvider) {

    companion object {
        const val CURRENCY_MISMATCH_TEMPLATE =  """
            Dear %s,
            
            Pleo is sorry to communicate that the invoice id %d could not be processed
            because it does not match your currency (Invoice currency: %s - Customer currency %s). 
            Please, fix your currency and they payment will be retried on the
            next day.
            
            Sincerely
        """
    }
    fun emailCurrencyMismatch(id: Int, invoice: Invoice) {
        val customer = customerService.fetch(id)
        emailProvider.emailCustomer(customer.email,
            CURRENCY_MISMATCH_TEMPLATE.format(
                customer.name,
                invoice.id,
                invoice.amount.currency,
                customer.currency.toString()
            )
        )
    }

}