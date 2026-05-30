package com.roywatson.cmp_nfc

sealed class NfcWriteState {
    data object Idle : NfcWriteState()
    data object WaitingForTag : NfcWriteState()
    data object Success : NfcWriteState()
    data class Error(val message: String) : NfcWriteState()
}
