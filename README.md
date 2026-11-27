# PayMock for Android (Kotlin)

> **In-App Mock Testing & Authorization Proxy for 100+ Global Payment Methods and Local Wallets on Android.**

PayMock for Android mirrors the iOS SDK 1-to-1. It eliminates external app switches (Vipps, PayPay, Edenred, iDEAL, Klarna) and presents a native **Jetpack Compose ModalBottomSheet** right inside your app, or dispatches deep-link `Intent.ACTION_VIEW` returns for full-loop Maestro automation.

---

## 🚀 Features

- **Jetpack Compose BottomSheet:** Beautiful, native Material 3 popover sheet matching official brand styling.
- **Intent Deep-Link Dispatcher:** Automatically dispatches `Intent(ACTION_VIEW, Uri.parse(returnUrl))` upon completion to test `onNewIntent()` handling.
- **Headless Mode for CI:** Supports `PayMock.setPresetScenario()` or Maestro launch arguments.
- **Maestro Ready:** Pre-configured with `Modifier.testTag("paymock_btn_success")`, `Modifier.testTag("paymock_btn_cancelled")`.
- **Coroutines & Suspend API:** Direct `suspend fun present(...)` integration.

---

## 🛠 Usage

### 1. Coroutine Async Presentation

```kotlin
lifecycleScope.launch {
    val result = PayMock.present(
        appContext = this@MainActivity,
        context = PayMockContext(
            brand = PaymentBrand.VIPPS,
            amount = 249.00,
            currency = "NOK",
            returnUrl = "myapp://checkout/callback"
        )
    )

    when (result) {
        is PayMockResult.Success -> {
            println("Payment approved! Token: ${result.token}")
        }
        is PayMockResult.Cancelled -> {
            println("Customer cancelled.")
        }
        is PayMockResult.Declined -> {
            println("Declined: ${result.reason.localizedMessage()}")
        }
        is PayMockResult.Timeout -> {
            println("Session timed out.")
        }
        is PayMockResult.NetworkError -> {
            println("Network error.")
        }
    }
}
```

### 2. Attach Compose Overlay in Activity

```kotlin
setContent {
    MaterialTheme {
        MyCheckoutScreen()

        // Listens to PayMock.present() anywhere in the app
        PayMockHostOverlay()
    }
}
```

### 3. Maestro E2E Automation on Android

```yaml
appId: com.example.fooddelivery
---
- launchApp
- tapOn: "Pay with Vipps MobilePay"

# 1. Assert PayMock sheet is visible
- assertVisible:
    id: "paymock_sheet"

# 2. Tap Cancel
- tapOn:
    id: "paymock_btn_cancelled"

# 3. Assert deep-link returned to checkout
- assertVisible: "Payment was cancelled"
```
