
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.CustomerOperationsService
import io.pleo.antaeus.core.services.EmailService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.function.Supplier

internal class BillingServiceTest {

    private val paymentProvider = mockk<PaymentProvider>()
    private val invoiceService = mockk<InvoiceService>()
    private val emailService = mockk<EmailService>()
    private val retry = mockk<Retry>()
    private val customerOperationsService = mockk<CustomerOperationsService>()

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        val supplier = slot<Supplier<Boolean>>()
        every { retry.executeSupplier(capture(supplier)) } answers { supplier.captured.get() }
    }

    @Test
    fun `given a list of invoices it should charge every invoice pending`() {
        val firstInvoice = Invoice(1, 1, Money(BigDecimal.valueOf(100), Currency.USD), InvoiceStatus.PENDING)
        val secondInvoice = Invoice(1, 1, Money(BigDecimal.valueOf(100), Currency.USD), InvoiceStatus.PENDING)

        every { invoiceService.fetchPendingInvoices() } returns listOf(firstInvoice, secondInvoice)
        every { paymentProvider.charge(firstInvoice) } returns true
        every { invoiceService.update(firstInvoice.id, firstInvoice.amount, firstInvoice.customerId, InvoiceStatus.PAID) } returns mockk()

        val billingService = BillingService(retry, paymentProvider, invoiceService, emailService, customerOperationsService)
        billingService.billClients()

        verify { paymentProvider.charge(firstInvoice) }
        verify { invoiceService.update(firstInvoice.id, firstInvoice.amount, firstInvoice.customerId, InvoiceStatus.PAID) }
        verify { paymentProvider.charge(secondInvoice) }
        verify { invoiceService.update(secondInvoice.id, secondInvoice.amount, secondInvoice.customerId, InvoiceStatus.PAID) }

    }

    @Test
    fun `given a list of invoices failed it should charge every invoice failed`() {
        val firstInvoice = Invoice(1, 1, Money(BigDecimal.valueOf(100), Currency.USD), InvoiceStatus.RETRYABLE_FAILED)

        every { invoiceService.fetchRetryableInvoices() } returns listOf(firstInvoice)
        every { paymentProvider.charge(firstInvoice) } returns true
        every { invoiceService.update(firstInvoice.id, firstInvoice.amount, firstInvoice.customerId, InvoiceStatus.PAID) } returns mockk()

        val billingService = BillingService(retry, paymentProvider, invoiceService, emailService, customerOperationsService)
        billingService.retryFailedInvoices()

        verify { paymentProvider.charge(firstInvoice) }
        verify { invoiceService.update(firstInvoice.id, firstInvoice.amount, firstInvoice.customerId, InvoiceStatus.PAID) }
    }

    @Test
    fun `given a list of invoices when calling payments fail with currency mismatch expect to update invoice and call email service`() {

        val firstInvoice = Invoice(1, 1, Money(BigDecimal.valueOf(100), Currency.USD), InvoiceStatus.PENDING)
        val money = Money(BigDecimal.valueOf(100), Currency.USD)

        every { paymentProvider.charge(firstInvoice) } throws CurrencyMismatchException(firstInvoice.id, firstInvoice.customerId)
        every { invoiceService.update(firstInvoice.id, money, firstInvoice.id, InvoiceStatus.RETRYABLE_FAILED) } returns mockk()
        every { emailService.emailCurrencyMismatch(firstInvoice.customerId, firstInvoice) } just Runs
        every { invoiceService.fetchPendingInvoices() } returns listOf(firstInvoice)

        val billingService = BillingService(retry, paymentProvider, invoiceService, emailService, customerOperationsService)

        billingService.billClients()

        verify { paymentProvider.charge(firstInvoice) }
        verify { emailService.emailCurrencyMismatch(firstInvoice.customerId, firstInvoice) }
        verify { invoiceService.update(firstInvoice.id, firstInvoice.amount, firstInvoice.customerId, InvoiceStatus.RETRYABLE_FAILED) }
    }

    @Test
    fun `given a list of invoices when calling payments fail with CustomerNotFoundException expect to update invoice and call customer operations service`() {

        val firstInvoice = Invoice(1, 1, Money(BigDecimal.valueOf(100), Currency.USD), InvoiceStatus.PENDING)
        val money = Money(BigDecimal.valueOf(100), Currency.USD)

        every { paymentProvider.charge(firstInvoice) } throws CustomerNotFoundException(firstInvoice.customerId)
        every { invoiceService.update(firstInvoice.id, money, firstInvoice.id, InvoiceStatus.FAILED) } returns mockk()
        every { customerOperationsService.createTicketToOperationsForCustomerNotFound(firstInvoice.id) } just Runs
        every { invoiceService.fetchPendingInvoices() } returns listOf(firstInvoice)

        val billingService = BillingService(retry, paymentProvider, invoiceService, emailService, customerOperationsService)

        billingService.billClients()

        verify { paymentProvider.charge(firstInvoice) }
        verify { customerOperationsService.createTicketToOperationsForCustomerNotFound(firstInvoice.id) }
        verify { invoiceService.update(firstInvoice.id, firstInvoice.amount, firstInvoice.customerId, InvoiceStatus.FAILED) }
    }

    @Test
    fun `given a list of invoices when calling payments fail with a network exception expect to retry`() {

        val money = Money(BigDecimal.valueOf(100), Currency.USD)
        val invoice = Invoice(2, 1, money, InvoiceStatus.PENDING)

        every { invoiceService.fetchPendingInvoices() } returns listOf(invoice)
        every { paymentProvider.charge(invoice) } throws NetworkException() andThen true
        every { invoiceService.update(2, money, 1, InvoiceStatus.PAID) } returns invoice

        val config = RetryConfig.custom<Any>()
            .maxAttempts(3)
            .retryExceptions(NetworkException::class.java)
            .ignoreExceptions(CurrencyMismatchException::class.java, CustomerNotFoundException::class.java)
            .build()

        val billingService = BillingService(RetryRegistry.of(config).retry("mock"),paymentProvider, invoiceService, emailService, customerOperationsService)

        billingService.billClients()

        verify(exactly = 2) { paymentProvider.charge(invoice) }
        verify { invoiceService.update(invoice.id, invoice.amount, invoice.customerId, InvoiceStatus.PAID) }

    }

    @Test
    fun `given a list of invoices when calling payments fail more than three times with a network exception expect to fail and update invoice`() {

        val money = Money(BigDecimal.valueOf(100), Currency.USD)
        val invoice = Invoice(2, 1, money, InvoiceStatus.PENDING)

        every { invoiceService.fetchPendingInvoices() } returns listOf(invoice)
        every { paymentProvider.charge(invoice) } throws NetworkException()
        every { invoiceService.update(2, money, 1, InvoiceStatus.RETRYABLE_FAILED) } returns invoice

        val config = RetryConfig.custom<Any>()
            .maxAttempts(3)
            .retryExceptions(NetworkException::class.java)
            .ignoreExceptions(CurrencyMismatchException::class.java, CustomerNotFoundException::class.java)
            .build()

        val billingService = BillingService(RetryRegistry.of(config).retry("mock"),paymentProvider, invoiceService, emailService, customerOperationsService)

        billingService.billClients()

        verify(exactly = 3) { paymentProvider.charge(invoice) }
        verify { invoiceService.update(invoice.id, invoice.amount, invoice.customerId, InvoiceStatus.RETRYABLE_FAILED) }

    }

}