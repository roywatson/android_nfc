package com.roywatson.cmp_nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NdefParserTest {

    @Test
    fun parsesWellKnownTextRecord() {
        val record = NdefParser.parseRecord(
            tnfCode = 0x01,
            typeBytes = byteArrayOf(0x54),
            idBytes = byteArrayOf(0x12, 0x34),
            payloadBytes = byteArrayOf(0x02, 0x65, 0x6E) + "Hello NFC".encodeToByteArray()
        )

        assertEquals(NdefTnf.WELL_KNOWN, record.tnf)
        assertEquals("Text (RTD_T)", record.typeName)
        assertEquals("1234", record.id)
        assertEquals("[en] Hello NFC", record.parsedContent)
        assertEquals("02656E48 656C6C6F 204E4643", record.payloadHex)
    }

    @Test
    fun parsesWellKnownUriRecordWithPrefix() {
        val record = NdefParser.parseRecord(
            tnfCode = 0x01,
            typeBytes = byteArrayOf(0x55),
            idBytes = byteArrayOf(),
            payloadBytes = byteArrayOf(0x04) + "roywatson.dev".encodeToByteArray()
        )

        assertEquals("URI (RTD_U)", record.typeName)
        assertEquals("https://roywatson.dev", record.parsedContent)
    }

    @Test
    fun leavesBinaryMimePayloadUnparsed() {
        val record = NdefParser.parseRecord(
            tnfCode = 0x02,
            typeBytes = "application/octet-stream".encodeToByteArray(),
            idBytes = byteArrayOf(),
            payloadBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        )

        assertEquals("MIME: application/octet-stream", record.typeName)
        assertNull(record.parsedContent)
    }
}
