package com.roywatson.cmp_nfc

enum class NdefTnf(val code: Byte, val label: String) {
    EMPTY(0x00, "Empty"),
    WELL_KNOWN(0x01, "Well Known"),
    MIME_MEDIA(0x02, "MIME Media"),
    ABSOLUTE_URI(0x03, "Absolute URI"),
    EXTERNAL_TYPE(0x04, "External Type"),
    UNKNOWN(0x05, "Unknown"),
    UNCHANGED(0x06, "Unchanged"),
    RESERVED(0x07, "Reserved");

    companion object {
        fun from(code: Byte): NdefTnf = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

data class NdefRecordData(
    val tnf: NdefTnf,
    val typeName: String,
    val id: String,
    val payloadHex: String,
    val parsedContent: String?
)

data class NdefMessageData(
    val records: List<NdefRecordData>,
    val byteLength: Int,
    val capacityBytes: Int? = null
)

data class RawPageData(
    val address: Int,
    val dataHex: String
)

data class NfcTagData(
    val uid: String,
    val tagDescription: String,
    val technologies: List<String>,
    val protocolDetails: Map<String, String>,
    val ndefMessage: NdefMessageData?,
    val isNdefWritable: Boolean,
    val ndefCapacityBytes: Int?,
    val rawPages: List<RawPageData>? = null
)
