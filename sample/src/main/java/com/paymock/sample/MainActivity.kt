package com.paymock.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paymock.core.PayMock
import com.paymock.models.PayMockContext
import com.paymock.models.PayMockResult
import com.paymock.models.PaymentBrand
import com.paymock.ui.PayMockHostOverlay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PayMock.initialize()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CheckoutScreen(
                        onPayClicked = { brand, amount, currency ->
                            lifecycleScopeLaunch {
                                val result = PayMock.present(
                                    appContext = this@MainActivity,
                                    context = PayMockContext(
                                        brand = brand,
                                        amount = amount,
                                        currency = currency,
                                        returnUrl = "myapp://checkout/callback"
                                    )
                                )
                                // Result handled by UI or deep-link
                            }
                        }
                    )

                    // The PayMock popover overlay listens to PayMock.present()
                    PayMockHostOverlay()
                }
            }
        }
    }

    private fun lifecycleScopeLaunch(block: suspend () -> Unit) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            block()
        }
    }
}

@Composable
fun CheckoutScreen(
    onPayClicked: (PaymentBrand, Double, String) -> Unit
) {
    var selectedBrand by remember { mutableStateOf(PaymentBrand.VIPPS) }
    var resultText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Checkout Demo",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Cart Summary
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Salmon Poke Bowl + Drink", style = MaterialTheme.typography.titleMedium)
                Text(text = "Order #ORD-9821", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Total: 249.00 NOK",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "Select Payment Method:", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        listOf(PaymentBrand.VIPPS, PaymentBrand.PAYPAY, PaymentBrand.EDENRED, PaymentBrand.IDEAL, PaymentBrand.KLARNA).forEach { brand ->
            Button(
                onClick = { selectedBrand = brand },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedBrand == brand) brand.brandColor else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selectedBrand == brand) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(brand.displayName)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                onPayClicked(selectedBrand, 249.00, selectedBrand.defaultCurrency)
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Pay with ${selectedBrand.displayName}")
        }
    }
}
