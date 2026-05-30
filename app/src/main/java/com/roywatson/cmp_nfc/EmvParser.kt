package com.roywatson.cmp_nfc

/**
 * Parses EMV BER-TLV data structures from NFC APDU responses.
 *
 * Key EMV tags:
 *   0x4F   – Application Identifier (AID)
 *   0x57   – Track 2 Equivalent Data (PAN + expiry)
 *   0x5A   – Primary Account Number (PAN)
 *   0x5F20 – Cardholder Name
 *   0x5F24 – Application Expiry Date (YYMMDD)
 *   0x80   – Response Message Template Format 1 (GET PROCESSING OPTIONS)
 *   0x94   – Application File Locator (AFL)
 */
object EmvParser {

    data class ParsedRecord(
        val pan: String? = null,
        val expiryDate: String? = null,
        val cardholderName: String? = null
    )

    data class AflEntry(val sfi: Int, val startRecord: Int, val endRecord: Int)

    fun findFirstAid(ppseResponse: ByteArray): ByteArray? {
        val body = ppseResponse.dropLast(2).toByteArray()
        return findTag(parseTlv(body), 0x4F)?.value
    }

    fun cardTypeFromAid(aid: ByteArray): String? {
        val aidHex = aid.toHexString().uppercase()
        return when {
            aidHex.startsWith("A000000003") -> "Visa"
            aidHex.startsWith("A000000004") -> "Mastercard"
            aidHex.startsWith("A000000025") -> "American Express"
            aidHex.startsWith("A000000065") -> "JCB"
            aidHex.startsWith("A000000152") -> "Discover"
            aidHex.startsWith("A000000324") -> "Discover"
            aidHex.startsWith("A000000333") -> "UnionPay"
            aidHex.startsWith("A000000277") -> "Interac"
            else -> null
        }
    }

    fun cardTypeFromPan(pan: String): String = when {
        pan.startsWith("4") -> "Visa"
        pan.length >= 2 && pan.take(2).toIntOrNull() in 51..55 -> "Mastercard"
        pan.length >= 4 && pan.take(4).toIntOrNull() in 2221..2720 -> "Mastercard"
        pan.startsWith("34") || pan.startsWith("37") -> "American Express"
        pan.startsWith("6011") || pan.startsWith("65") -> "Discover"
        pan.length >= 3 && pan.take(3).toIntOrNull() in 644..649 -> "Discover"
        pan.length >= 6 && pan.take(6).toIntOrNull() in 622126..622925 -> "Discover"
        pan.length >= 4 && pan.take(4).toIntOrNull() in 3528..3589 -> "JCB"
        pan.startsWith("62") -> "UnionPay"
        else -> "Unknown"
    }

    fun parseAfl(gpoResponse: ByteArray): List<AflEntry> {
        if (gpoResponse.size < 4) return emptyList()
        val body = gpoResponse.dropLast(2).toByteArray()

        val aflBytes: ByteArray = when {
            body[0] == 0x80.toByte() -> {
                if (body.size > 4) body.copyOfRange(4, body.size) else byteArrayOf()
            }
            body[0] == 0x77.toByte() -> {
                findTag(parseTlv(body), 0x94)?.value ?: byteArrayOf()
            }
            else -> byteArrayOf()
        }

        val entries = mutableListOf<AflEntry>()
        var i = 0
        while (i + 3 < aflBytes.size) {
            val sfi      = (aflBytes[i].toInt() and 0xFF) ushr 3
            val startRec = aflBytes[i + 1].toInt() and 0xFF
            val endRec   = aflBytes[i + 2].toInt() and 0xFF
            if (sfi > 0 && startRec > 0 && endRec >= startRec) {
                entries.add(AflEntry(sfi, startRec, endRec))
            }
            i += 4
        }
        return entries
    }

    fun parseRecord(recordResponse: ByteArray): ParsedRecord {
        if (recordResponse.size < 2) return ParsedRecord()
        val body = recordResponse.dropLast(2).toByteArray()
        val nodes = parseTlv(body)

        var pan: String? = null
        var expiry: String? = null
        var name: String? = null

        findTag(nodes, 0x5A)?.value?.let { bytes ->
            pan = bytes.toHexString().uppercase().trimEnd('F')
        }

        if (pan == null) {
            findTag(nodes, 0x57)?.value?.let { bytes ->
                val hex = bytes.toHexString().uppercase()
                val sep = hex.indexOf('D')
                if (sep >= 0) {
                    pan = hex.substring(0, sep).trimEnd('F')
                    if (hex.length >= sep + 5) {
                        val yymm = hex.substring(sep + 1, sep + 5)
                        expiry = "${yymm.substring(2, 4)}/${yymm.substring(0, 2)}"
                    }
                }
            }
        }

        if (expiry == null) {
            findTag(nodes, 0x5F24)?.value?.let { bytes ->
                if (bytes.size >= 2) {
                    val yy = (bytes[0].toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()
                    val mm = (bytes[1].toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()
                    expiry = "$mm/$yy"
                }
            }
        }

        findTag(nodes, 0x5F20)?.value?.let { bytes ->
            name = bytes.decodeToString().trim().ifBlank { null }
        }

        return ParsedRecord(pan, expiry, name)
    }

    data class TlvNode(val tag: Int, val value: ByteArray, val children: List<TlvNode>)

    fun parseTlv(data: ByteArray): List<TlvNode> {
        val nodes = mutableListOf<TlvNode>()
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            if (b0 == 0x00 || b0 == 0xFF) { i++; continue }

            var tag = b0
            i++
            if ((b0 and 0x1F) == 0x1F) {
                while (i < data.size) {
                    val next = data[i].toInt() and 0xFF
                    tag = (tag shl 8) or next
                    i++
                    if ((next and 0x80) == 0) break
                }
            }
            if (i >= data.size) break

            var len = data[i].toInt() and 0xFF
            i++
            if (len == 0x81) {
                if (i >= data.size) break
                len = data[i].toInt() and 0xFF; i++
            } else if (len == 0x82) {
                if (i + 1 >= data.size) break
                len = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                i += 2
            }

            if (i + len > data.size) break
            val value = data.copyOfRange(i, i + len)
            i += len

            val constructed = (b0 and 0x20) != 0
            val children = if (constructed) parseTlv(value) else emptyList()
            nodes.add(TlvNode(tag, value, children))
        }
        return nodes
    }

    fun findTag(nodes: List<TlvNode>, tag: Int): TlvNode? {
        for (node in nodes) {
            if (node.tag == tag) return node
            findTag(node.children, tag)?.let { return it }
        }
        return null
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
