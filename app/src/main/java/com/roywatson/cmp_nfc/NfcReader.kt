package com.roywatson.cmp_nfc

import kotlinx.coroutines.flow.StateFlow

interface NfcReader {
    val state: StateFlow<NfcReadState>
    fun startScanning()
    fun stopScanning()
}
