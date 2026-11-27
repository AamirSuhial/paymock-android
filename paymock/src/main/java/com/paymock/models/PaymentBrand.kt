package com.paymock.models

import androidx.compose.ui.graphics.Color

/**
 * Supported payment brands and local wallets.
 */
enum class PaymentBrand(
    val id: String,
    val displayName: String,
    val region: String,
    val defaultCurrency: String,
    val defaultScheme: String,
    val brandColor: Color
) {
    VIPPS(
        id = "vipps",
        displayName = "Vipps MobilePay",
        region = "Nordics (NO / DK / FI)",
        defaultCurrency = "NOK",
        defaultScheme = "vippsMT://",
        brandColor = Color(0xFFFF5B29)
    ),
    PAYPAY(
        id = "paypay",
        displayName = "PayPay",
        region = "Japan",
        defaultCurrency = "JPY",
        defaultScheme = "paypay://",
        brandColor = Color(0xFFE0242E)
    ),
    EDENRED(
        id = "edenred",
        displayName = "Edenred Ticket Restaurant",
        region = "Global (Meal Voucher)",
        defaultCurrency = "EUR",
        defaultScheme = "edenred://",
        brandColor = Color(0xFFE83333)
    ),
    IDEAL(
        id = "ideal",
        displayName = "iDEAL / Wero",
        region = "Netherlands & EU",
        defaultCurrency = "EUR",
        defaultScheme = "ideal://",
        brandColor = Color(0xFFCC0D73)
    ),
    TWINT(
        id = "twint",
        displayName = "TWINT",
        region = "Switzerland",
        defaultCurrency = "CHF",
        defaultScheme = "twint://",
        brandColor = Color(0xFF00A661)
    ),
    BLIK(
        id = "blik",
        displayName = "BLIK",
        region = "Poland",
        defaultCurrency = "PLN",
        defaultScheme = "blik://",
        brandColor = Color(0xFFE85421)
    ),
    KLARNA(
        id = "klarna",
        displayName = "Klarna",
        region = "Global (BNPL)",
        defaultCurrency = "EUR",
        defaultScheme = "klarna://",
        brandColor = Color(0xFFFFB3C7)
    ),
    SWISH(
        id = "swish",
        displayName = "Swish",
        region = "Sweden",
        defaultCurrency = "SEK",
        defaultScheme = "swish://",
        brandColor = Color(0xFF248AD6)
    ),
    PIX(
        id = "pix",
        displayName = "Pix",
        region = "Brazil",
        defaultCurrency = "BRL",
        defaultScheme = "pix://",
        brandColor = Color(0xFF32B09F)
    ),
    GRABPAY(
        id = "grabpay",
        displayName = "GrabPay",
        region = "Southeast Asia",
        defaultCurrency = "SGD",
        defaultScheme = "grabpay://",
        brandColor = Color(0xFF00B14F)
    );

    companion object {
        fun fromId(id: String): PaymentBrand? {
            return entries.find { it.id.equals(id, ignoreCase = true) }
        }
    }
}
