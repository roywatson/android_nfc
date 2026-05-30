package com.roywatson.cmp_nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EmvParserTest {

    @Test
    fun findsFirstAidInsideNestedPpseResponse() {
        val aid = hex("A0000000031010")
        val ppseResponse = tlv(0x6F, tlv(0xA5, tlv(0x61, tlv(0x4F, aid)))) + statusOk()

        assertArrayEquals(aid, EmvParser.findFirstAid(ppseResponse))
        assertEquals("Visa", EmvParser.cardTypeFromAid(aid))
    }

    @Test
    fun parsesAflFromTemplate77GpoResponse() {
        val gpoResponse = tlv(0x77, tlv(0x94, byteArrayOf(0x10, 0x01, 0x03, 0x00))) + statusOk()

        assertEquals(listOf(EmvParser.AflEntry(sfi = 2, startRecord = 1, endRecord = 3)), EmvParser.parseAfl(gpoResponse))
    }

    @Test
    fun parsesPanExpiryAndCardholderNameFromRecord() {
        val recordResponse = tlv(
            0x70,
            tlv(0x5A, hex("4111111111111111")) +
                    tlv(0x5F24, hex("251231")) +
                    tlv(0x5F20, "CARDHOLDER TEST".encodeToByteArray())
        ) + statusOk()

        val parsed = EmvParser.parseRecord(recordResponse)

        assertEquals("4111111111111111", parsed.pan)
        assertEquals("12/25", parsed.expiryDate)
        assertEquals("CARDHOLDER TEST", parsed.cardholderName)
        assertEquals("Visa", EmvParser.cardTypeFromPan(parsed.pan!!))
    }

    private fun tlv(tag: Int, value: ByteArray): ByteArray {
        val tagBytes = when {
            tag > 0xFFFF -> byteArrayOf((tag ushr 16).toByte(), (tag ushr 8).toByte(), tag.toByte())
            tag > 0xFF -> byteArrayOf((tag ushr 8).toByte(), tag.toByte())
            else -> byteArrayOf(tag.toByte())
        }
        return tagBytes + value.size.toByte() + value
    }

    private fun statusOk(): ByteArray = byteArrayOf(0x90.toByte(), 0x00)

    private fun hex(value: String): ByteArray {
        require(value.length % 2 == 0)
        return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
