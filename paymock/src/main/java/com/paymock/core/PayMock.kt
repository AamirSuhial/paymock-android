package com.paymock.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.paymock.models.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Main singleton entry point for PayMock on Android.
 */
object PayMock {

    data class Configuration(
        val simulateLatency: Boolean = true,
        val latencyMillis: Long = 500L,
        val autoDispatchReturnIntent: Boolean = true
    )

    private var configuration = Configuration()
    private val presetScenarios = mutableMapOf<PaymentBrand, PayMockScenario>()

    private val _activeSession = MutableStateFlow<PayMockSession?>(null)
    val activeSession: StateFlow<PayMockSession?> = _activeSession.asStateFlow()

    data class PayMockSession(
        val context: PayMockContext,
        val deferred: CompletableDeferred<PayMockResult>
    )

    fun initialize(config: Configuration = Configuration()) {
        this.configuration = config
    }

    /**
     * Pre-set a scenario for headless CI or automated tests (Maestro / Espresso).
     */
    fun setPresetScenario(brand: PaymentBrand, scenario: PayMockScenario?) {
        if (scenario != null) {
            presetScenarios[brand] = scenario
        } else {
            presetScenarios.remove(brand)
        }
    }

    fun clearPresetScenarios() {
        presetScenarios.clear()
    }

    /**
     * Coroutine-based presentation method. Suspends until the user or test selects a scenario.
     */
    suspend fun present(appContext: Context? = null, context: PayMockContext): PayMockResult {
        // 1. Check for CI Headless override
        val preset = presetScenarios[context.brand]
        if (preset != null) {
            if (configuration.simulateLatency) {
                delay(configuration.latencyMillis)
            }
            val result = resolveResult(context, preset)
            if (configuration.autoDispatchReturnIntent && appContext != null && context.returnUrl != null) {
                dispatchReturnIntent(appContext, context.returnUrl, result)
            }
            return result
        }

        // 2. Open interactive Compose UI session
        val deferred = CompletableDeferred<PayMockResult>()
        _activeSession.value = PayMockSession(context, deferred)

        val result = deferred.await()
        _activeSession.value = null

        // 3. Dispatch return intent if configured
        if (configuration.autoDispatchReturnIntent && appContext != null && context.returnUrl != null) {
            dispatchReturnIntent(appContext, context.returnUrl, result)
        }

        return result
    }

    /**
     * Resolves the active UI session with the chosen scenario.
     */
    fun resolveActiveSession(scenario: PayMockScenario) {
        val session = _activeSession.value ?: return
        val result = resolveResult(session.context, scenario)
        session.deferred.complete(result)
    }

    /**
     * Cancels the active session (e.g. bottom sheet dismissed).
     */
    fun dismissActiveSession() {
        val session = _activeSession.value ?: return
        session.deferred.complete(PayMockResult.Cancelled)
        _activeSession.value = null
    }

    fun resolveResult(context: PayMockContext, scenario: PayMockScenario): PayMockResult {
        return when (scenario) {
            is PayMockScenario.Success -> {
                val txId = scenario.transactionId ?: "TX_${context.brand.id.uppercase()}_${(100000..999999).random()}"
                val token = "tok_${context.brand.id}_${UUID.randomUUID().toString().take(8)}"
                val rawParams = mapOf(
                    "status" to "SUCCESS",
                    "orderId" to context.orderId,
                    "transactionId" to txId,
                    "token" to token,
                    "amount" to context.amount.toString(),
                    "currency" to context.currency
                )
                PayMockResult.Success(
                    transactionId = txId,
                    token = token,
                    brand = context.brand,
                    amount = context.amount,
                    currency = context.currency,
                    rawParameters = rawParams
                )
            }
            is PayMockScenario.Cancelled -> PayMockResult.Cancelled
            is PayMockScenario.InsufficientFunds -> PayMockResult.Declined(DeclineReason.InsufficientFunds)
            is PayMockScenario.CardExpired -> PayMockResult.Declined(DeclineReason.CardExpired)
            is PayMockScenario.Timeout -> PayMockResult.Timeout
            is PayMockScenario.NetworkError -> PayMockResult.NetworkError
            is PayMockScenario.PartialBalance -> {
                val remaining = maxOf(0.0, context.amount - scenario.availableAmount)
                PayMockResult.Declined(DeclineReason.PartialBalance(scenario.availableAmount, remaining))
            }
            is PayMockScenario.CustomDecline -> PayMockResult.Declined(
                DeclineReason.Custom(scenario.code, scenario.message)
            )
        }
    }

    /**
     * Fires the real Android Intent(ACTION_VIEW) return redirect so the host app's
     * onNewIntent() or DeepLink Activity catches it identically to real Vipps/PayPay.
     */
    private fun dispatchReturnIntent(context: Context, returnUrl: String, result: PayMockResult) {
        val uriBuilder = Uri.parse(returnUrl).buildUpon()

        when (result) {
            is PayMockResult.Success -> {
                uriBuilder.appendQueryParameter("status", "SUCCESS")
                uriBuilder.appendQueryParameter("transactionId", result.transactionId)
                uriBuilder.appendQueryParameter("token", result.token)
            }
            is PayMockResult.Cancelled -> {
                uriBuilder.appendQueryParameter("status", "CANCELLED")
                uriBuilder.appendQueryParameter("error", "USER_ABORTED")
            }
            is PayMockResult.Declined -> {
                uriBuilder.appendQueryParameter("status", "DECLINED")
                uriBuilder.appendQueryParameter("error", result.reason.localizedMessage())
            }
            is PayMockResult.Timeout -> {
                uriBuilder.appendQueryParameter("status", "TIMEOUT")
            }
            is PayMockResult.NetworkError -> {
                uriBuilder.appendQueryParameter("status", "NETWORK_ERROR")
            }
        }

        val intent = Intent(Intent.ACTION_VIEW, uriBuilder.build()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Log or fallback if scheme not registered
        }
    }
}
