package com.roywatson.cmp_nfc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    private lateinit var nfcReader: AndroidNfcReader
    private lateinit var nfcWriter: AndroidNfcWriter
    private lateinit var cardReader: AndroidNfcCardReader

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        nfcReader = AndroidNfcReader(this)
        nfcWriter = AndroidNfcWriter(this)
        cardReader = AndroidNfcCardReader(this)
        setContent {
            App(nfcReader = nfcReader, nfcWriter = nfcWriter, cardReader = cardReader)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcReader.stopScanning()
        nfcWriter.cancelWrite()
        cardReader.stopReading()
    }
}
