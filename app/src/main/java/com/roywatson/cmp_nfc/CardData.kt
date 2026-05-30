package com.roywatson.cmp_nfc

data class CardData(
    val maskedPan: String,
    val last4: String,
    val expiryDate: String,
    val cardholderName: String? = null,
    val cardType: String = "Unknown"
) {
    companion object {
        fun fromPan(
            pan: String,
            expiryDate: String,
            cardholderName: String? = null,
            cardType: String = "Unknown"
        ): CardData = CardData(
            maskedPan = maskPan(pan),
            last4 = pan.takeLast(minOf(4, pan.length)),
            expiryDate = expiryDate,
            cardholderName = cardholderName,
            cardType = cardType
        )

        private fun maskPan(pan: String): String {
            if (pan.length < 4) return pan
            val last4 = pan.takeLast(4)
            val prefixLen = pan.length - 4
            val remainder = prefixLen % 4
            val fullGroups = prefixLen / 4
            val parts = buildList {
                if (remainder > 0) add("*".repeat(remainder))
                repeat(fullGroups) { add("****") }
                add(last4)
            }
            return parts.joinToString(" ")
        }
    }
}
