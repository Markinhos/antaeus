
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
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
import java.util.function.Supplier

internal class BillingServiceTest {

    @Test
    fun `given a list of invoices it should charge to every client`() {

        val paymentProvider = mockk<PaymentProvider>()
        val invoiceService = mockk<InvoiceService>()
        val retry = mockk<Retry>(relaxed = true)
        val firstInvoice = Invoice(1, 1, Money(BigDecimal.valueOf(100), Currency.USD), InvoiceStatus.PENDING)
        val secondInvoice = Invoice(1, 1, Money(BigDecimal.valueOf(100), Currency.USD), InvoiceStatus.PAID)
        val supplier = slot<Supplier<Boolean>>()

        every { retry.executeSupplier(capture(supplier)) } answers { supplier.captured.get() }
        every { invoiceService.fetchAll() } returns listOf(firstInvoice, secondInvoice)
        every { paymentProvider.charge(firstInvoice) } returns true

        val billingService = BillingService(retry, paymentProvider, invoiceService)

        billingService.billClients()

        verify { paymentProvider.charge(firstInvoice) }
        verify(exactly = 0) { paymentProvider.charge(secondInvoice) }

    }

    @Test
    fun `given a list of invoices when calling payments fail when currency mismatch expect to fail`() {

        val paymentProvider = mockk<PaymentProvider>()
        val invoiceService = mockk<InvoiceService>()
        val retry = mockk<Retry>()
        val firstInvoice = Invoice(1, 1, Money(BigDecimal.valueOf(100), Currency.USD), InvoiceStatus.PENDING)
        val secondInvoice = Invoice(1, 1, Money(BigDecimal.valueOf(100), Currency.USD), InvoiceStatus.PENDING)
        val supplier = slot<Supplier<Boolean>>()

        every { retry.executeSupplier(capture(supplier)) } answers { supplier.captured.get() }
        every { invoiceService.fetchAll() } returns listOf(firstInvoice, secondInvoice)
        every { paymentProvider.charge(firstInvoice) } returns true
        every { paymentProvider.charge(secondInvoice) } throws CurrencyMismatchException(secondInvoice.id, secondInvoice.customerId)

        val billingService = BillingService(retry, paymentProvider, invoiceService)

        assertThrows<CurrencyMismatchException> { billingService.billClients() }


        verify { paymentProvider.charge(firstInvoice) }
        verify { paymentProvider.charge(secondInvoice) }

    }

    @Test
    fun `given a list of invoices when calling payments fail with a network exception expect to retry`() {

        val paymentProvider = mockk<PaymentProvider>()
        val invoiceService = mockk<InvoiceService>()
        val secondInvoice = Invoice(2, 1, Money(BigDecimal.valueOf(100), Currency.USD), InvoiceStatus.PENDING)

        every { invoiceService.fetchAll() } returns listOf(secondInvoice)
        every { paymentProvider.charge(secondInvoice) } throws NetworkException() andThen true

        val config = RetryConfig.custom<Any>()
            .maxAttempts(3)
            .retryExceptions(NetworkException::class.java)
            .ignoreExceptions(CurrencyMismatchException::class.java, CustomerNotFoundException::class.java)
            .build()

        val billingService = BillingService(RetryRegistry.of(config).retry("mock"),paymentProvider, invoiceService)

        billingService.billClients()

        verify(exactly = 2) { paymentProvider.charge(secondInvoice) }

    }

}