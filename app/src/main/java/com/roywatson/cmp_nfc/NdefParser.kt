package com.roywatson.cmp_nfc

object NdefParser {

    private val RTD_TEXT = byteArrayOf(0x54)        // "T"
    private val RTD_URI = byteArrayOf(0x55)          // "U"
    private val RTD_SMART_POSTER = byteArrayOf(0x53, 0x70) // "Sp"
    private val RTD_AAR = "android.com:pkg".encodeToByteArray()

    private val URI_PREFIXES = listOf(
        "", "http://www.", "https://www.", "http://", "https://",
        "tel:", "mailto:", "ftp://anonymous:anonymous@", "ftp://ftp.",
        "ftps://", "sftp://", "smb://", "nfs://", "ftp://", "dav://",
        "news:", "telnet://", "imap:", "rtsp://", "urn:", "pop:", "sip:",
        "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://",
        "tcpobex://", "irdaobex://", "file://", "urn:epc:id:",
        "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:"
    )

    fun parseRecord(
        tnfCode: Byte,
        typeBytes: ByteArray,
        idBytes: ByteArray,
        payloadBytes: ByteArray
    ): NdefRecordData {
        val tnf = NdefTnf.from(tnfCode)
        val typeName = resolveTypeName(tnf, typeBytes)
        val parsedContent = parsePayload(tnf, typeBytes, payloadBytes)

        return NdefRecordData(
            tnf = tnf,
            typeName = typeName,
            id = idBytes.toHexString(),
            payloadHex = payloadBytes.toGroupedHex(),
            parsedContent = parsedContent
        )
    }

    private fun resolveTypeName(tnf: NdefTnf, typeBytes: ByteArray): String = when (tnf) {
        NdefTnf.EMPTY -> "Empty"
        NdefTnf.WELL_KNOWN -> when {
            typeBytes.contentEquals(RTD_TEXT) -> "Text (RTD_T)"
            typeBytes.contentEquals(RTD_URI) -> "URI (RTD_U)"
            typeBytes.contentEquals(RTD_SMART_POSTER) -> "Smart Poster (RTD_Sp)"
            else -> "Well Known: \"${typeBytes.decodeToString()}\""
        }
        NdefTnf.MIME_MEDIA -> "MIME: ${typeBytes.decodeToString()}"
        NdefTnf.ABSOLUTE_URI -> "Absolute URI: ${typeBytes.decodeToString()}"
        NdefTnf.EXTERNAL_TYPE -> when {
            typeBytes.contentEquals(RTD_AAR) -> "Android App Record"
            else -> "External: ${typeBytes.decodeToString()}"
        }
        NdefTnf.UNKNOWN -> "Unknown"
        NdefTnf.UNCHANGED -> "Unchanged (chunked)"
        NdefTnf.RESERVED -> "Reserved"
    }

    private fun parsePayload(tnf: NdefTnf, typeBytes: ByteArray, payload: ByteArray): String? {
        if (payload.isEmpty()) return "(empty payload)"
        return when (tnf) {
            NdefTnf.WELL_KNOWN -> when {
                typeBytes.contentEquals(RTD_TEXT) -> parseTextPayload(payload)
                typeBytes.contentEquals(RTD_URI) -> parseUriPayload(payload)
                typeBytes.contentEquals(RTD_SMART_POSTER) -> "Smart Poster — see sub-records"
                else -> null
            }
            NdefTnf.MIME_MEDIA -> {
                val mime = typeBytes.decodeToString()
                if (mime.startsWith("text/")) tryDecodeUtf8(payload) else null
            }
            NdefTnf.ABSOLUTE_URI -> tryDecodeUtf8(payload)
            NdefTnf.EXTERNAL_TYPE -> when {
                typeBytes.contentEquals(RTD_AAR) -> payload.decodeToString()
                else -> tryDecodeUtf8(payload)
            }
            else -> null
        }
    }

    private fun parseTextPayload(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val statusByte = payload[0].toInt() and 0xFF
        val langLen = statusByte and 0x3F
        val isUtf16 = (statusByte and 0x80) != 0
        if (payload.size < 1 + langLen) return null
        val lang = payload.copyOfRange(1, 1 + langLen).decodeToString()
        val textBytes = payload.copyOfRange(1 + langLen, payload.size)
        val text = if (isUtf16) decodeUtf16(textBytes) else textBytes.decodeToString()
        return if (lang.isNotEmpty()) "[$lang] $text" else text
    }

    private fun parseUriPayload(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val prefixIdx = payload[0].toInt() and 0xFF
        val prefix = if (prefixIdx < URI_PREFIXES.size) URI_PREFIXES[prefixIdx] else ""
        val rest = payload.copyOfRange(1, payload.size).decodeToString()
        return "$prefix$rest"
    }

    private fun tryDecodeUtf8(bytes: ByteArray): String? = try {
        bytes.decodeToString()
    } catch (_: Exception) { null }

    private fun decodeUtf16(bytes: ByteArray): String {
        if (bytes.size < 2) return ""
        val bigEndian: Boolean
        val start: Int
        when {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> { bigEndian = false; start = 2 }
            bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> { bigEndian = true;  start = 2 }
            else -> { bigEndian = true; start = 0 }
        }
        val data = if (start == 0) bytes else bytes.copyOfRange(start, bytes.size)
        val chars = CharArray(data.size / 2) { i ->
            val a = data[i * 2].toInt() and 0xFF
            val b = data[i * 2 + 1].toInt() and 0xFF
            if (bigEndian) Char((a shl 8) or b) else Char((b shl 8) or a)
        }
        return chars.concatToString()
    }

    fun ByteArray.toHexString(): String =
        joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }

    fun ByteArray.toGroupedHex(groupSize: Int = 4): String {
        val hex = toHexString()
        return hex.chunked(groupSize * 2).joinToString(" ")
    }

    fun ByteArray.toColonHex(): String =
        joinToString(":") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }
}
