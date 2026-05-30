package com.roywatson.cmp_nfc

sealed class NfcReadState {
    data object Idle : NfcReadState()
    data object Scanning : NfcReadState()
    data class Success(val tag: NfcTagData) : NfcReadState()
    data class Error(val message: String) : NfcReadState()
}
