package com.roywatson.cmp_nfc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstract base class with platform-agnostic EMV card reading logic.
 *
 * EMV read flow:
 *   1. SELECT PPSE  — list payment applications on the card
 *   2. SELECT AID   — select the first application (Visa, Mastercard, etc.)
 *   3. GET PROCESSING OPTIONS — initialise a transaction; response contains the AFL
 *   4. READ RECORD  — read each record from the AFL
 *
 * All APDU commands follow ISO 7816-4. Responses end with SW1 SW2; 0x90 0x00 = success.
 * Subclasses implement [transceive] for the platform-specific APDU transport.
 */
abstract class EmvCardReader : NfcCardReader {

    protected val _state = MutableStateFlow<CardReadState>(CardReadState.Idle)
    override val state: StateFlow<CardReadState> = _state

    protected abstract suspend fun transceive(command: ByteArray): ByteArray

    protected suspend fun readEmvCard(): CardData? {
        val ppseResponse = transceive(selectPpse())
        if (!isSuccess(ppseResponse)) return null

        val aid = EmvParser.findFirstAid(ppseResponse) ?: return null
        val cardTypeFromAid = EmvParser.cardTypeFromAid(aid)
        val appResponse = transceive(selectApplication(aid))
        if (!isSuccess(appResponse)) return null

        val gpoResponse = transceive(getProcessingOptions())
        val afl = EmvParser.parseAfl(gpoResponse)

        var pan: String? = null
        var expiry: String? = null
        var name: String? = null

        val sources: Iterable<Pair<Int, Int>> = if (afl.isNotEmpty()) {
            afl.flatMap { e -> (e.startRecord..e.endRecord).map { e.sfi to it } }
        } else {
            (1..4).flatMap { sfi -> (1..8).map { sfi to it } }
        }

        for ((sfi, record) in sources) {
            try {
                val resp = transceive(readRecord(record, sfi))
                if (!isSuccess(resp)) continue
                val parsed = EmvParser.parseRecord(resp)
                if (pan == null)    pan    = parsed.pan
                if (expiry == null) expiry = parsed.expiryDate
                if (name == null)   name   = parsed.cardholderName
            } catch (_: Exception) {}

            if (pan != null && expiry != null) break
        }

        return pan?.let {
            /*
             * This flow demonstrates how to extract full PAN values and the EMV
             * metadata needed for payment processing, but full PAN values are used
             * only transiently and are never retained in app state.
             */
            CardData.fromPan(
                pan = it,
                expiryDate = expiry ?: "",
                cardholderName = name,
                cardType = cardTypeFromAid ?: EmvParser.cardTypeFromPan(it)
            )
        }
    }

    private fun isSuccess(response: ByteArray): Boolean =
        response.size >= 2 &&
                response[response.size - 2] == 0x90.toByte() &&
                response[response.size - 1] == 0x00.toByte()

    private fun selectPpse(): ByteArray = byteArrayOf(
        0x00, 0xA4.toByte(), 0x04, 0x00, 0x0E,
        0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31,
        0x00
    )

    private fun selectApplication(aid: ByteArray): ByteArray =
        byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) +
                aid + byteArrayOf(0x00)

    // Empty PDOL (83 00) works for most cards in a read-only scenario
    private fun getProcessingOptions(): ByteArray =
        byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, 0x02, 0x83.toByte(), 0x00, 0x00)

    private fun readRecord(record: Int, sfi: Int): ByteArray =
        byteArrayOf(0x00, 0xB2.toByte(), record.toByte(), ((sfi shl 3) or 4).toByte(), 0x00)
}
