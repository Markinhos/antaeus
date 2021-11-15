package io.pleo.antaeus.core.resilience

import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException


fun getRetryRegistry(): RetryRegistry {
    val config = RetryConfig.custom<Any>()
        .maxAttempts(3)
        .retryExceptions(NetworkException::class.java)
        .ignoreExceptions(CurrencyMismatchException::class.java, CustomerNotFoundException::class.java)
        .build()

    return RetryRegistry.of(config)
}

