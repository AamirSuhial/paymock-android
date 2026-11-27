package com.paymock

import com.paymock.core.PayMock
import com.paymock.models.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PayMockTest {

    @Before
    fun setup() {
        PayMock.clearPresetScenarios()
    }

    @Test
    fun testHeadlessSuccessScenario() = runBlocking {
        PayMock.setPresetScenario(PaymentBrand.VIPPS, PayMockScenario.Success("TX_VIPPS_TEST_99"))

        val result = PayMock.present(
            context = PayMockContext(
                brand = PaymentBrand.VIPPS,
                amount = 249.0,
                currency = "NOK"
            )
        )

        assertTrue(result is PayMockResult.Success)
        val success = result as PayMockResult.Success
        assertEquals("TX_VIPPS_TEST_99", success.transactionId)
        assertEquals(PaymentBrand.VIPPS, success.brand)
        assertEquals(249.0, success.amount, 0.001)
    }

    @Test
    fun testHeadlessCancellationScenario() = runBlocking {
        PayMock.setPresetScenario(PaymentBrand.PAYPAY, PayMockScenario.Cancelled)

        val result = PayMock.present(
            context = PayMockContext(
                brand = PaymentBrand.PAYPAY,
                amount = 1500.0,
                currency = "JPY"
            )
        )

        assertTrue(result is PayMockResult.Cancelled)
    }

    @Test
    fun testEdenredPartialBalanceDecline() = runBlocking {
        PayMock.setPresetScenario(PaymentBrand.EDENRED, PayMockScenario.PartialBalance(15.0))

        val result = PayMock.present(
            context = PayMockContext(
                brand = PaymentBrand.EDENRED,
                amount = 30.0,
                currency = "EUR"
            )
        )

        assertTrue(result is PayMockResult.Declined)
        val declined = result as PayMockResult.Declined
        assertTrue(declined.reason is DeclineReason.PartialBalance)
        val partial = declined.reason as DeclineReason.PartialBalance
        assertEquals(15.0, partial.coveredAmount, 0.001)
        assertEquals(15.0, partial.remainingAmount, 0.001)
    }
}
