package com.roywatson.cmp_nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import androidx.activity.ComponentActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AndroidNfcWriter(private val activity: ComponentActivity) : NfcWriter {

    private val _state = MutableStateFlow<NfcWriteState>(NfcWriteState.Idle)
    override val state: StateFlow<NfcWriteState> = _state

    private val adapter: NfcAdapter? =
        activity.getSystemService(NfcManager::class.java).defaultAdapter
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var pendingMessage: NdefMessage? = null

    override fun writeText(text: String) {
        pendingMessage = NdefMessage(arrayOf(NdefRecord.createTextRecord("en", text)))
        startWriteSession()
    }

    override fun writeUri(uri: String) {
        pendingMessage = NdefMessage(arrayOf(NdefRecord.createUri(uri)))
        startWriteSession()
    }

    override fun cancelWrite() {
        pendingMessage = null
        adapter?.disableReaderMode(activity)
        _state.value = NfcWriteState.Idle
    }

    private fun startWriteSession() {
        if (adapter == null || !adapter.isEnabled) {
            _state.value = NfcWriteState.Error("NFC is not available or disabled.")
            return
        }
        _state.value = NfcWriteState.WaitingForTag

        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F

        adapter.enableReaderMode(activity, { tag ->
            scope.launch(Dispatchers.IO) {
                writeToTag(tag)
            }
        }, flags, null)
    }

    private fun writeToTag(tag: Tag) {
        val message = pendingMessage ?: return

        Ndef.get(tag)?.let { ndef ->
            try {
                ndef.connect()
                when {
                    !ndef.isWritable ->
                        _state.value = NfcWriteState.Error("Tag is read-only.")
                    ndef.maxSize < message.toByteArray().size ->
                        _state.value = NfcWriteState.Error(
                            "Tag too small: ${message.toByteArray().size}B needed, ${ndef.maxSize}B available."
                        )
                    else -> {
                        ndef.writeNdefMessage(message)
                        pendingMessage = null
                        _state.value = NfcWriteState.Success
                    }
                }
            } finally {
                try { ndef.close() } catch (_: Exception) {}
            }
            return
        }

        NdefFormatable.get(tag)?.let { formatable ->
            try {
                formatable.connect()
                formatable.format(message)
                pendingMessage = null
                _state.value = NfcWriteState.Success
            } catch (e: Exception) {
                _state.value = NfcWriteState.Error("Format failed: ${e.message}")
            } finally {
                try { formatable.close() } catch (_: Exception) {}
            }
            return
        }

        _state.value = NfcWriteState.Error("Tag does not support NDEF writing.")
    }
}
