package com.roywatson.cmp_nfc

import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.*
import androidx.activity.ComponentActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AndroidNfcReader(private val activity: ComponentActivity) : NfcReader {

    private val _state = MutableStateFlow<NfcReadState>(NfcReadState.Idle)
    override val state: StateFlow<NfcReadState> = _state

    private val adapter: NfcAdapter? =
        activity.getSystemService(NfcManager::class.java).defaultAdapter
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var readingJob: Job? = null

    override fun startScanning() {
        when {
            adapter == null -> {
                _state.value = NfcReadState.Error("NFC is not available on this device.")
                return
            }
            !adapter.isEnabled -> {
                _state.value = NfcReadState.Error("NFC is disabled. Enable it in Settings.")
                return
            }
        }
        _state.value = NfcReadState.Scanning

        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V

	        adapter!!.enableReaderMode(activity, { tag ->
	            readingJob = scope.launch(Dispatchers.IO) {
	                try {
	                    val tagData = readTag(tag)
	                    _state.value = NfcReadState.Success(tagData)
	                } catch (e: Exception) {
	                    _state.value = NfcReadState.Error(e.message ?: "Unexpected error during NFC read.")
	                } finally {
	                    adapter.disableReaderMode(activity)
	                    readingJob = null
	                }
	            }
	        }, flags, null)
	    }

    override fun stopScanning() {
        readingJob?.cancel()
        readingJob = null
        adapter?.disableReaderMode(activity)
        if (_state.value is NfcReadState.Scanning) {
            _state.value = NfcReadState.Idle
        }
    }

    private fun readTag(tag: Tag): NfcTagData {
        val uidHex = tag.id?.toColonHex() ?: "Unknown"
        val techList = tag.techList.toList()
        val technologies = mutableListOf<String>()
        val protocolDetails = mutableMapOf<String, String>()
        var ndefMessage: NdefMessageData? = null
        var isWritable = false
        var ndefCapacity: Int? = null
        val rawPages = mutableListOf<RawPageData>()

        if (techList.contains(NfcA::class.java.name)) {
            readSafely(NfcA.get(tag)) { nfcA ->
                nfcA.connect()
                technologies.add("NFC-A (ISO 14443-3A)")
                protocolDetails["ATQA"] = nfcA.atqa.toHexString()
                protocolDetails["SAK"] = "0x${nfcA.sak.toInt().and(0xFF).toString(16).uppercase().padStart(2, '0')}"
                protocolDetails["Max Transceive"] = "${nfcA.maxTransceiveLength} bytes"
            }
        }

        if (techList.contains(NfcB::class.java.name)) {
            readSafely(NfcB.get(tag)) { nfcB ->
                nfcB.connect()
                technologies.add("NFC-B (ISO 14443-3B)")
                nfcB.applicationData?.let { protocolDetails["Application Data"] = it.toHexString() }
                nfcB.protocolInfo?.let { protocolDetails["Protocol Info"] = it.toHexString() }
                protocolDetails["Max Transceive"] = "${nfcB.maxTransceiveLength} bytes"
            }
        }

        if (techList.contains(NfcF::class.java.name)) {
            readSafely(NfcF.get(tag)) { nfcF ->
                nfcF.connect()
                technologies.add("NFC-F / FeliCa (ISO 18092)")
                nfcF.manufacturer?.let { protocolDetails["Manufacturer"] = it.toHexString() }
                nfcF.systemCode?.let { protocolDetails["System Code"] = it.toHexString() }
                protocolDetails["Max Transceive"] = "${nfcF.maxTransceiveLength} bytes"
            }
        }

        if (techList.contains(NfcV::class.java.name)) {
            readSafely(NfcV.get(tag)) { nfcV ->
                nfcV.connect()
                technologies.add("NFC-V (ISO 15693)")
                protocolDetails["DSF ID"] = "0x${nfcV.dsfId.toString(16).uppercase().padStart(2, '0')}"
                protocolDetails["Response Flags"] = "0x${nfcV.responseFlags.toString(16).uppercase().padStart(2, '0')}"
                protocolDetails["Max Transceive"] = "${nfcV.maxTransceiveLength} bytes"
            }
        }

        if (techList.contains(IsoDep::class.java.name)) {
            readSafely(IsoDep.get(tag)) { isoDep ->
                isoDep.connect()
                technologies.add("ISO-DEP (ISO 14443-4)")
                isoDep.hiLayerResponse?.let {
                    protocolDetails["HI Layer Response (ATTRIB)"] = it.toHexString()
                }
                isoDep.historicalBytes?.let {
                    protocolDetails["Historical Bytes (ATS)"] = it.toHexString()
                }
                protocolDetails["Extended APDU"] = isoDep.isExtendedLengthApduSupported.toString()
                protocolDetails["Max Transceive"] = "${isoDep.maxTransceiveLength} bytes"
            }
        }

        if (techList.contains(MifareClassic::class.java.name)) {
            readSafely(MifareClassic.get(tag)) { mifare ->
                mifare.connect()
                technologies.add("MIFARE Classic")
                protocolDetails["MIFARE Type"] = when (mifare.type) {
                    MifareClassic.TYPE_CLASSIC -> "Classic"
                    MifareClassic.TYPE_PLUS    -> "Plus"
                    MifareClassic.TYPE_PRO     -> "Pro"
                    else                        -> "Unknown"
                }
                protocolDetails["Sectors"] = mifare.sectorCount.toString()
                protocolDetails["Blocks"]  = mifare.blockCount.toString()
                protocolDetails["Size"]    = "${mifare.size} bytes"
            }
        }

        if (techList.contains(MifareUltralight::class.java.name)) {
            readSafely(MifareUltralight.get(tag)) { ul ->
                ul.connect()
                technologies.add("MIFARE Ultralight")
                val ulTypeName = when (ul.type) {
                    MifareUltralight.TYPE_ULTRALIGHT   -> "Ultralight"
                    MifareUltralight.TYPE_ULTRALIGHT_C -> "Ultralight C"
                    else                                -> "Unknown"
                }
                protocolDetails["UL Type"] = ulTypeName
                val pageCount = if (ul.type == MifareUltralight.TYPE_ULTRALIGHT_C) 48 else 16
                for (startPage in 0 until pageCount step 4) {
                    try {
                        val data = ul.readPages(startPage)
                        repeat(minOf(4, pageCount - startPage)) { i ->
                            val pageData = data.copyOfRange(i * 4, minOf((i + 1) * 4, data.size))
                            rawPages.add(RawPageData(startPage + i, pageData.toHexString()))
                        }
                    } catch (_: Exception) { break }
                }
            }
        }

        if (techList.contains(Ndef::class.java.name)) {
            readSafely(Ndef.get(tag)) { ndef ->
                ndef.connect()
                technologies.add("NDEF")
                isWritable = ndef.isWritable
                ndefCapacity = ndef.maxSize
                protocolDetails["NDEF Type"]     = ndef.type ?: "Unknown"
                protocolDetails["NDEF Capacity"] = "${ndef.maxSize} bytes"
                protocolDetails["NDEF Writable"] = ndef.isWritable.toString()
                protocolDetails["Can Lock"]      = ndef.canMakeReadOnly().toString()
	                ndef.ndefMessage?.let { raw ->
	                    ndefMessage = NdefMessageData(
	                        records = raw.records.map { r ->
	                            NdefParser.parseRecord(r.tnf.toByte(), r.type, r.id, r.payload)
	                        },
	                        byteLength = raw.toByteArray().size,
	                        capacityBytes = ndef.maxSize
	                    )
	                }
            }
        } else if (techList.contains(NdefFormatable::class.java.name)) {
            technologies.add("NDEF Formatable")
            isWritable = true
            protocolDetails["NDEF Status"] = "Can be formatted as NDEF"
        }

        return NfcTagData(
            uid = uidHex,
            tagDescription = buildDescription(techList, protocolDetails),
            technologies = technologies,
            protocolDetails = protocolDetails,
            ndefMessage = ndefMessage,
            isNdefWritable = isWritable,
            ndefCapacityBytes = ndefCapacity,
            rawPages = rawPages.takeIf { it.isNotEmpty() }
        )
    }

    private fun buildDescription(techList: List<String>, details: Map<String, String>): String {
        val hasMifareUL = techList.contains(MifareUltralight::class.java.name)
        val hasMifareC  = techList.contains(MifareClassic::class.java.name)
        val hasNdef     = techList.contains(Ndef::class.java.name)
        val hasIsoDep   = techList.contains(IsoDep::class.java.name)
        val hasNfcF     = techList.contains(NfcF::class.java.name)
        val hasNfcV     = techList.contains(NfcV::class.java.name)

        return when {
            hasNdef && hasMifareUL -> "NDEF / MIFARE Ultralight (${details["UL Type"] ?: ""})"
            hasNdef                -> "NDEF (${details["NDEF Type"] ?: ""})"
            hasMifareC             -> "MIFARE Classic (${details["MIFARE Type"] ?: ""})"
            hasIsoDep              -> "ISO 14443-4 Smart Card"
            hasNfcF                -> "FeliCa (NFC-F)"
            hasNfcV                -> "ISO 15693 (NFC-V)"
            else                   -> "NFC Tag"
        }
    }
}

// Connects, runs block, then closes — swallows exceptions so one bad tech doesn't abort the rest.
private fun <T : AutoCloseable> readSafely(tech: T?, block: (T) -> Unit) {
    tech ?: return
    try {
        block(tech)
    } catch (_: Exception) {
    } finally {
        try { tech.close() } catch (_: Exception) {}
    }
}

private fun ByteArray.toHexString(): String =
    joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }

private fun ByteArray.toColonHex(): String =
    joinToString(":") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }
