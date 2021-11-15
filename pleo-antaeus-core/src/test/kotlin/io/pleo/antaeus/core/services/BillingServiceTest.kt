
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

internal class BillingServiceTest {

    @Test
    fun `given a list of invoices it should charge to every client`() {

        val paymentProvider = mockk<PaymentProvider>()
        val invoiceService = mockk<InvoiceService>()
        val firstInvoice = Invoice(1, 1, Money(BigDecimal.valueOf(100), Currency.USD), InvoiceStatus.PENDING)
        val secondInvoice = Invoice(1, 1, Money(BigDecimal.valueOf(100), Currency.USD), InvoiceStatus.PAID)

        every { invoiceService.fetchAll() } returns listOf(firstInvoice, secondInvoice)
        every { paymentProvider.charge(firstInvoice) } returns true

        val billingService = BillingService(paymentProvider, invoiceService)

        billingService.billClients()

        verify { paymentProvider.charge(firstInvoice) }
        verify(exactly = 0) { paymentProvider.charge(secondInvoice) }

    }

    @Test
    fun `given a list of invoices when calling payments fail when currency mismatch expect to fail`() {

        val paymentProvider = mockk<PaymentProvider>()
        val invoiceService = mockk<InvoiceService>()
        val firstInvoice = Invoice(1, 1, Money(BigDecimal.valueOf(100), Currency.USD), InvoiceStatus.PENDING)
        val secondInvoice = Invoice(1, 1, Money(BigDecimal.valueOf(100), Currency.USD), InvoiceStatus.PENDING)

        every { invoiceService.fetchAll() } returns listOf(firstInvoice, secondInvoice)
        every { paymentProvider.charge(firstInvoice) } returns true
        every { paymentProvider.charge(secondInvoice) } throws CurrencyMismatchException(secondInvoice.id, secondInvoice.customerId)

        val billingService = BillingService(paymentProvider, invoiceService)

        assertThrows<CurrencyMismatchException> { billingService.billClients() }


        verify { paymentProvider.charge(firstInvoice) }
        verify { paymentProvider.charge(secondInvoice) }

    }

}