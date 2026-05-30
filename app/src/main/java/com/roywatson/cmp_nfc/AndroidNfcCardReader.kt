package com.roywatson.cmp_nfc

import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.tech.IsoDep
import androidx.activity.ComponentActivity
import kotlinx.coroutines.*

/**
 * Android implementation of [NfcCardReader] using [NfcAdapter.enableReaderMode].
 *
 * Handles the 0x61 "more data available" APDU continuation that Android's IsoDep
 * does not handle automatically. EMV protocol logic lives in [EmvCardReader].
 */
class AndroidNfcCardReader(private val activity: ComponentActivity) : EmvCardReader() {

    private val adapter: NfcAdapter? =
        activity.getSystemService(NfcManager::class.java).defaultAdapter
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var readingJob: Job? = null
    private var activeIsoDep: IsoDep? = null

    override fun startReading() {
        when {
            adapter == null -> {
                _state.value = CardReadState.Error("NFC is not available on this device.")
                return
            }
            !adapter.isEnabled -> {
                _state.value = CardReadState.Error("NFC is disabled. Enable it in Settings and try again.")
                return
            }
        }

        _state.value = CardReadState.Reading

        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

        adapter.enableReaderMode(activity, { tag ->
            readingJob = scope.launch(Dispatchers.IO) {
                try {
                    val isoDep = IsoDep.get(tag) ?: run {
                        _state.value = CardReadState.Error("Card does not support ISO-DEP. Tap a contactless payment card.")
                        return@launch
                    }

                    isoDep.connect()
                    isoDep.timeout = 5_000
                    activeIsoDep = isoDep

                    try {
                        val cardData = readEmvCard()
                        _state.value = if (cardData != null) {
                            CardReadState.Success(cardData)
                        } else {
                            CardReadState.Error("Could not extract card data. Try again or use a different card.")
                        }
	                    } finally {
	                        activeIsoDep = null
	                        isoDep.close()
	                    }
	                } catch (e: Exception) {
	                    _state.value = CardReadState.Error(e.message ?: "Unexpected error during NFC read.")
	                } finally {
	                    adapter.disableReaderMode(activity)
	                    readingJob = null
	                }
	            }
	        }, flags, null)
	    }

    override fun stopReading() {
        readingJob?.cancel()
        readingJob = null
        adapter?.disableReaderMode(activity)
        if (_state.value is CardReadState.Reading) {
            _state.value = CardReadState.Idle
        }
    }

    /**
     * Sends an APDU and handles SW1=0x61 "more data available" chaining.
     * IsoDep does not handle this automatically; we follow up with GET RESPONSE.
     */
    override suspend fun transceive(command: ByteArray): ByteArray {
        val isoDep = activeIsoDep ?: throw IllegalStateException("No active IsoDep")
        var response = isoDep.transceive(command)
        while (response.size >= 2 && response[response.size - 2] == 0x61.toByte()) {
            val remaining = response[response.size - 1].toInt() and 0xFF
            val more = isoDep.transceive(
                byteArrayOf(0x00, 0xC0.toByte(), 0x00, 0x00, remaining.toByte())
            )
            response = response.copyOfRange(0, response.size - 2) + more
        }
        return response
    }
}
