package com.roywatson.cmp_nfc

import org.junit.Assert.assertEquals
import org.junit.Test

class CardDataTest {

    @Test
    fun masksCardNumberInReadableGroups() {
        val card = CardData.fromPan(
            pan = "4111111111111111",
            expiryDate = "12/25",
            cardholderName = "CARDHOLDER TEST",
            cardType = "Visa"
        )

        assertEquals("**** **** **** 1111", card.maskedPan)
        assertEquals("1111", card.last4)
    }

    @Test
    fun keepsShortCardNumberVisible() {
        val card = CardData.fromPan(pan = "123", expiryDate = "")

        assertEquals("123", card.maskedPan)
        assertEquals("123", card.last4)
    }
}
