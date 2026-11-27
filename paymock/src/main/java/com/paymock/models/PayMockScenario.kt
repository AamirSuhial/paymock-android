package com.paymock.models

/**
 * Test outcomes simulated by the PayMock Android SDK.
 */
sealed class PayMockScenario(
    val id: String,
    val title: String,
    val description: String
) {
    data class Success(val transactionId: String? = null) : PayMockScenario(
        id = "success",
        title = "Approve (Success)",
        description = "Simulates successful customer payment authorization."
    )

    object Cancelled : PayMockScenario(
        id = "cancelled",
        title = "User Cancelled",
        description = "Simulates user tapping Cancel or back button in wallet."
    )

    object InsufficientFunds : PayMockScenario(
        id = "insufficient_funds",
        title = "Insufficient Balance",
        description = "Declines authorization due to low wallet balance."
    )

    object CardExpired : PayMockScenario(
        id = "card_expired",
        title = "Card Expired",
        description = "Declines authorization due to an expired card."
    )

    object Timeout : PayMockScenario(
        id = "timeout",
        title = "Session Timeout",
        description = "Simulates session expiry with no user interaction."
    )

    object NetworkError : PayMockScenario(
        id = "network_error",
        title = "Network Connection Error",
        description = "Simulates gateway connectivity failure."
    )

    data class PartialBalance(val availableAmount: Double) : PayMockScenario(
        id = "partial_balance",
        title = "Partial Voucher Balance",
        description = "Only covers a portion of cart total (requires secondary payment)."
    )

    data class CustomDecline(val code: String, val message: String) : PayMockScenario(
        id = "custom_decline",
        title = "Declined ($code)",
        description = message
    )

    companion object {
        fun fromString(value: String): PayMockScenario {
            return when (value.lowercase().trim()) {
                "success" -> Success()
                "cancelled", "cancel" -> Cancelled
                "insufficient_funds", "insufficient" -> InsufficientFunds
                "card_expired", "expired" -> CardExpired
                "timeout" -> Timeout
                "network_error" -> NetworkError
                else -> Success()
            }
        }
    }
}
