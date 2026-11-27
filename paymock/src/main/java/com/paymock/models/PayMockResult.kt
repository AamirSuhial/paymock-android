package com.paymock.models

import java.util.UUID

/**
 * Result returned to the merchant Android application after mock authorization.
 */
sealed interface PayMockResult {

    data class Success(
        val transactionId: String,
        val token: String,
        val brand: PaymentBrand,
        val amount: Double,
        val currency: String,
        val rawParameters: Map<String, String>
    ) : PayMockResult

    object Cancelled : PayMockResult

    data class Declined(val reason: DeclineReason) : PayMockResult

    object Timeout : PayMockResult

    object NetworkError : PayMockResult
}

sealed interface DeclineReason {
    object InsufficientFunds : DeclineReason
    object CardExpired : DeclineReason
    data class PartialBalance(val coveredAmount: Double, val remainingAmount: Double) : DeclineReason
    data class Custom(val code: String, val message: String) : DeclineReason

    fun localizedMessage(): String {
        return when (this) {
            is InsufficientFunds -> "The bank reported insufficient funds."
            is CardExpired -> "The payment card has expired."
            is PartialBalance -> "Voucher covers $coveredAmount. Remaining $remainingAmount requires secondary payment."
            is Custom -> "[$code] $message"
        }
    }
}

/**
 * Input context passed to initiate a mock payment session.
 */
data class PayMockContext(
    val brand: PaymentBrand,
    val amount: Double,
    val currency: String = brand.defaultCurrency,
    val orderId: String = "ORD-${(10000..99999).random()}",
    val returnUrl: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
