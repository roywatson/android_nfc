package com.roywatson.cmp_nfc

import kotlinx.coroutines.flow.StateFlow

interface NfcWriter {
    val state: StateFlow<NfcWriteState>
    fun writeText(text: String)
    fun writeUri(uri: String)
    fun cancelWrite()
}
