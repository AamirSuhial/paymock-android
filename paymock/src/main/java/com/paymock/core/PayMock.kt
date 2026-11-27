package com.paymock.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.paymock.models.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Main singleton entry point for PayMock on Android.
 */
object PayMock {

    private const val TAG = "PayMock"

    data class Configuration(
        val simulateLatency: Boolean = true,
        val latencyMillis: Long = 500L,
        val autoInterceptOutboundUris: Boolean = true,
        val autoDispatchReturnIntent: Boolean = true,
        val webhookEndpointUrl: String? = null,
        val webhookDelayMillis: Long = 300L
    )

    private var configuration = Configuration()
    private val presetScenarios = mutableMapOf<PaymentBrand, PayMockScenario>()
    private val webhookScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeSession = MutableStateFlow<PayMockSession?>(null)
    val activeSession: StateFlow<PayMockSession?> = _activeSession.asStateFlow()

    data class PayMockSession(
        val context: PayMockContext,
        val deferred: CompletableDeferred<PayMockResult>
    )

    fun initialize(config: Configuration = Configuration(), launchIntent: Intent? = null) {
        this.configuration = config
        loadLaunchOverrides(launchIntent)
    }

    /**
     * Parses Maestro / ADB launch intent extras for headless CI overrides.
     * Example: `PAYMOCK_FORCE_VIPPS = "cancelled"`, `PAYMOCK_LATENCY = "0"`
     */
    fun loadLaunchOverrides(intent: Intent?) {
        val extras = intent?.extras ?: return
        if (extras.containsKey("PAYMOCK_LATENCY")) {
            val latency = extras.getString("PAYMOCK_LATENCY")?.toLongOrNull()
                ?: extras.getLong("PAYMOCK_LATENCY", configuration.latencyMillis)
            configuration = configuration.copy(
                latencyMillis = latency,
                simulateLatency = latency > 0L
            )
        }

        for (brand in PaymentBrand.entries) {
            val key = "PAYMOCK_FORCE_${brand.id.uppercase()}"
            val value = extras.getString(key)
            if (!value.isNullOrBlank()) {
                presetScenarios[brand] = PayMockScenario.fromString(value)
            }
        }
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
     * Attempts to intercept an outbound wallet URI (e.g. `vippsMT://pay?amount=249&orderId=123&returnUrl=myapp://cb`).
     * Returns null if the URI does not match a known brand.
     */
    suspend fun handleOutboundUri(appContext: Context? = null, uri: Uri): PayMockResult? {
        if (!configuration.autoInterceptOutboundUris) return null
        val scheme = uri.scheme?.lowercase() ?: return null

        val brand = PaymentBrand.entries.find {
            val brandScheme = it.defaultScheme.removeSuffix("://").lowercase()
            brandScheme == scheme || it.id.lowercase() == scheme
        } ?: return null

        val amount = uri.getQueryParameter("amount")?.toDoubleOrNull() ?: 0.0
        val currency = uri.getQueryParameter("currency") ?: brand.defaultCurrency
        val orderId = uri.getQueryParameter("orderId")
            ?: uri.getQueryParameter("order_id")
            ?: "ORD-${(10000..99999).random()}"
        val returnUrl = uri.getQueryParameter("returnUrl")
            ?: uri.getQueryParameter("return_url")
            ?: uri.getQueryParameter("redirect_uri")

        val context = PayMockContext(
            brand = brand,
            amount = amount,
            currency = currency,
            orderId = orderId,
            returnUrl = returnUrl
        )
        return present(appContext = appContext, context = context)
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
            finalizeResolution(appContext, context, result)
            return result
        }

        // 2. Guard against concurrent overlapping sessions
        if (_activeSession.value != null) {
            Log.w(TAG, "PayMock session already active; ignoring overlapping present() call.")
            return PayMockResult.Cancelled
        }

        // 3. Open interactive Compose UI session
        val deferred = CompletableDeferred<PayMockResult>()
        _activeSession.value = PayMockSession(context, deferred)

        val result = deferred.await()
        _activeSession.value = null

        // 4. Dispatch return intent & optional webhook
        finalizeResolution(appContext, context, result)

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

    private fun finalizeResolution(appContext: Context?, context: PayMockContext, result: PayMockResult) {
        if (configuration.autoDispatchReturnIntent && appContext != null && context.returnUrl != null) {
            dispatchReturnIntent(appContext, context.returnUrl, result)
        }
        val webhookUrl = configuration.webhookEndpointUrl
        if (!webhookUrl.isNullOrBlank()) {
            dispatchWebhook(webhookUrl, context, result)
        }
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
            Log.w(TAG, "No activity found to handle returnUrl: $returnUrl", e)
        }
    }

    private fun dispatchWebhook(webhookUrl: String, context: PayMockContext, result: PayMockResult) {
        val delayMs = configuration.webhookDelayMillis
        val (status, txId) = when (result) {
            is PayMockResult.Success -> "AUTHORIZED" to result.transactionId
            is PayMockResult.Cancelled -> "CANCELLED" to ""
            is PayMockResult.Declined -> "DECLINED" to ""
            is PayMockResult.Timeout -> "EXPIRED" to ""
            is PayMockResult.NetworkError -> "FAILED" to ""
        }

        val payload = JSONObject().apply {
            put("event", "payment.${status.lowercase()}")
            put("brand", context.brand.id)
            put("orderId", context.orderId)
            put("amount", context.amount)
            put("currency", context.currency)
            put("status", status)
            put("transactionId", txId)
        }.toString()

        webhookScope.launch {
            if (delayMs > 0) delay(delayMs)
            try {
                val conn = (URL(webhookUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("User-Agent", "PayMock-Android/1.0")
                    doOutput = true
                }
                conn.outputStream.use { it.write(payload.toByteArray()) }
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to dispatch mock webhook to $webhookUrl", e)
            }
        }
    }
}
