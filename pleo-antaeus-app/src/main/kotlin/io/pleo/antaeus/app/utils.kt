
import io.pleo.antaeus.core.external.TicketingProvider
import io.pleo.antaeus.core.external.EmailProvider
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.CustomerErrorCodes
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import java.math.BigDecimal
import kotlin.random.Random

// This will create all schemas and setup initial data
internal fun setupInitialData(dal: AntaeusDal) {
    val customers = (1..100).mapNotNull {
        dal.createCustomer(
            currency = Currency.values()[Random.nextInt(0, Currency.values().size)]
        )
    }

    customers.forEach { customer ->
        (1..10).forEach {
            dal.createInvoice(
                amount = Money(
                    value = BigDecimal(Random.nextDouble(10.0, 500.0)),
                    currency = customer.currency
                ),
                customer = customer,
                status = if (it == 1) InvoiceStatus.PENDING else InvoiceStatus.PAID
            )
        }
    }
}

// This is the mocked instance of the payment provider
internal fun getPaymentProvider(): PaymentProvider {
    return object : PaymentProvider {
        override fun charge(invoice: Invoice): Boolean {
                return Random.nextBoolean()
        }
    }
}

// This is the mocked instance of the email provider
internal fun getEmailProvider(): EmailProvider {
    return object : EmailProvider {
        override fun emailCustomer(address: String, content: String) {
        }
    }
}

// This is the mocked instance of the ticketing provider
internal fun getTicketingProvider(): TicketingProvider {
    return object : TicketingProvider {
        override fun openTicket(invoiceId: Int, errorCode: CustomerErrorCodes): Int {
            return Random.nextInt()
        }
    }
}
