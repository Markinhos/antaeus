package io.pleo.antaeus.core.external

interface EmailProvider {
    fun emailCustomer(address: String, content: String)
}