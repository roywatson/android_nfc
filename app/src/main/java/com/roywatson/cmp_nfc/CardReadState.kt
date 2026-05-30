package com.roywatson.cmp_nfc

sealed class CardReadState {
    data object Idle : CardReadState()
    data object Reading : CardReadState()
    data class Success(val cardData: CardData) : CardReadState()
    data class Error(val message: String) : CardReadState()
}
