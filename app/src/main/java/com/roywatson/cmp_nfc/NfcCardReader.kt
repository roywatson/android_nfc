package com.roywatson.cmp_nfc

import kotlinx.coroutines.flow.StateFlow

interface NfcCardReader {
    val state: StateFlow<CardReadState>
    fun startReading()
    fun stopReading()
}
