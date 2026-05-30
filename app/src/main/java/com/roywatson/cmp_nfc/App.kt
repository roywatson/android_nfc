package com.roywatson.cmp_nfc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun App(nfcReader: NfcReader, nfcWriter: NfcWriter, cardReader: NfcCardReader) {
    MaterialTheme {
        var selectedTab by remember { mutableStateOf(0) }
        fun selectTab(tab: Int) {
            if (selectedTab == tab) return
            nfcReader.stopScanning()
            nfcWriter.cancelWrite()
            cardReader.stopReading()
            selectedTab = tab
        }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectTab(0) },
                        label = { Text("Scan") },
                        icon = {}
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectTab(1) },
                        label = { Text("Write") },
                        icon = {}
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectTab(2) },
                        label = { Text("Credit Card") },
                        icon = {}
                    )
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (selectedTab) {
                    0 -> ScanScreen(nfcReader)
                    1 -> WriteScreen(nfcWriter)
                    2 -> CardScreen(cardReader)
                }
            }
        }
    }
}

// ── Scan Screen ───────────────────────────────────────────────────────────────

@Composable
private fun ScanScreen(reader: NfcReader) {
    val state by reader.state.collectAsState()

    when (val s = state) {
        is NfcReadState.Idle    -> ScanIdleContent(onStart = reader::startScanning)
        is NfcReadState.Scanning -> ScanningContent(onCancel = reader::stopScanning)
        is NfcReadState.Error   -> ScanErrorContent(s.message, onRetry = reader::startScanning)
        is NfcReadState.Success -> TagDetailContent(s.tag, onScanAgain = reader::startScanning)
    }
}

@Composable
private fun ScanIdleContent(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("NFC Tag Scanner", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            "Reads all NFC formats — NDEF, ISO 7816, MIFARE, FeliCa, ISO 15693",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onStart) { Text("Start Scanning") }
    }
}

@Composable
private fun ScanningContent(onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(24.dp))
        Text("Hold tag near device…", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun ScanErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Error", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Try Again") }
    }
}

@Composable
private fun TagDetailContent(tag: NfcTagData, onScanAgain: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TagHeaderCard(tag) }
        item { TechnologiesCard(tag.technologies) }
        if (tag.protocolDetails.isNotEmpty()) {
            item { ExpandableCard("Protocol Details") { ProtocolDetailsContent(tag.protocolDetails) } }
        }
        if (tag.ndefMessage != null) {
            item { NdefCard(tag.ndefMessage) }
        }
        if (!tag.rawPages.isNullOrEmpty()) {
            item { ExpandableCard("Raw Memory Pages") { RawPagesContent(tag.rawPages) } }
        }
        item {
            Button(
                onClick = onScanAgain,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Scan Another Tag") }
        }
    }
}

@Composable
private fun TagHeaderCard(tag: NfcTagData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tag Detected", style = MaterialTheme.typography.labelSmall)
            Text(tag.tagDescription, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            DataRow("UID", tag.uid, monospace = true)
            tag.ndefCapacityBytes?.let { DataRow("NDEF Capacity", "$it bytes") }
            DataRow("Writable", if (tag.isNdefWritable) "Yes" else "No")
        }
    }
}

@Composable
private fun TechnologiesCard(technologies: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Technologies", style = MaterialTheme.typography.titleSmall)
            HorizontalDivider()
            technologies.forEach { tech ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(tech, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ExpandableCard(title: String, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.labelMedium)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(Modifier.padding(bottom = 8.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun ProtocolDetailsContent(details: Map<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        details.forEach { (key, value) ->
            DataRow(key, value, monospace = value.any { it in "0123456789ABCDEFabcdef:" })
        }
    }
}

@Composable
private fun NdefCard(ndef: NdefMessageData) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("NDEF Message", style = MaterialTheme.typography.titleSmall)
                Text("${ndef.records.size} record${if (ndef.records.size != 1) "s" else ""} · ${ndef.byteLength}B",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ndef.capacityBytes?.let { capacity ->
                DataRow("Message Size", "${ndef.byteLength} bytes")
                DataRow("Tag Capacity", "$capacity bytes")
            }
            HorizontalDivider()
            ndef.records.forEachIndexed { index, record ->
                NdefRecordItem(index, record)
                if (index < ndef.records.lastIndex) HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun NdefRecordItem(index: Int, record: NdefRecordData) {
    var showRaw by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Record ${index + 1}: ${record.typeName}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
        Text("TNF: ${record.tnf.label}", style = MaterialTheme.typography.labelSmall)

        if (record.parsedContent != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    record.parsedContent,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (record.id.isNotEmpty() && record.id != "0000") {
            Text("ID: ${record.id}", style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace)
        }

        TextButton(
            onClick = { showRaw = !showRaw },
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(if (showRaw) "Hide raw payload" else "Show raw payload",
                style = MaterialTheme.typography.labelSmall)
        }

        AnimatedVisibility(showRaw) {
            Text(
                record.payloadHex.ifEmpty { "(empty)" },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Visible
            )
        }
    }
}

@Composable
private fun RawPagesContent(pages: List<RawPageData>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Page", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
            Text("Data (hex)", style = MaterialTheme.typography.labelSmall)
        }
        HorizontalDivider(Modifier.padding(vertical = 2.dp))
        pages.forEach { page ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    page.address.toString().padStart(3),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(40.dp)
                )
                Text(
                    page.dataHex,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun DataRow(label: String, value: String, monospace: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(0.62f),
            overflow = TextOverflow.Visible
        )
    }
}

// ── Write Screen ──────────────────────────────────────────────────────────────

@Composable
private fun WriteScreen(writer: NfcWriter) {
    val state by writer.state.collectAsState()
    var writeType by remember { mutableStateOf(WriteType.TEXT) }
    var content by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Write NFC Tag", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Write an NDEF message to any compatible NFC tag.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        // Type selector
        Text("Record type", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WriteType.entries.forEach { type ->
                FilterChip(
                    selected = writeType == type,
                    onClick = { writeType = type; content = "" },
                    label = { Text(type.label) }
                )
            }
        }

        // Content input
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text(writeType.hint) },
            placeholder = { Text(writeType.placeholder) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = writeType == WriteType.URI,
            maxLines = if (writeType == WriteType.TEXT) 5 else 1,
            enabled = state is NfcWriteState.Idle || state is NfcWriteState.Error || state is NfcWriteState.Success
        )

        // State display
        when (val s = state) {
            is NfcWriteState.Idle -> {
                Button(
                    onClick = {
                        if (content.isNotBlank()) {
                            when (writeType) {
                                WriteType.TEXT -> writer.writeText(content.trim())
                                WriteType.URI  -> writer.writeUri(content.trim())
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = content.isNotBlank()
                ) { Text("Write to Tag") }
            }
            is NfcWriteState.WaitingForTag -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Text("Hold tag near device…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedButton(onClick = writer::cancelWrite, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
            is NfcWriteState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Text(
                        "Written successfully!",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Button(
                    onClick = { writer.cancelWrite() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Write Another") }
            }
            is NfcWriteState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = {
                        if (content.isNotBlank()) {
                            when (writeType) {
                                WriteType.TEXT -> writer.writeText(content.trim())
                                WriteType.URI  -> writer.writeUri(content.trim())
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = content.isNotBlank()
                ) { Text("Try Again") }
            }
        }
    }
}

private enum class WriteType(val label: String, val hint: String, val placeholder: String) {
    TEXT("Text", "Text content", "Enter text to write…"),
    URI("URI", "URI / URL", "https://example.com")
}

// ── Card Screen ───────────────────────────────────────────────────────────────

@Composable
private fun CardScreen(reader: NfcCardReader) {
    val state by reader.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Credit Card Reader", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Reads contactless EMV payment cards (Visa, Mastercard, etc.)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        when (val s = state) {
            is CardReadState.Idle    -> CardIdleContent(onStart = reader::startReading)
            is CardReadState.Reading -> CardReadingContent(onCancel = reader::stopReading)
            is CardReadState.Success -> CardSuccessContent(s.cardData) { reader.startReading() }
            is CardReadState.Error   -> CardErrorContent(s.message) { reader.startReading() }
        }
    }
}

@Composable
private fun CardIdleContent(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Tap your contactless payment card to read its details.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onStart) { Text("Start Reading") }
    }
}

@Composable
private fun CardReadingContent(onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator()
        Text("Hold your card near the device…", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun CardSuccessContent(card: CardData, onReadAgain: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Card Number", style = MaterialTheme.typography.labelSmall)
            Text(
                text = card.maskedPan,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column {
                    Text("Expires", style = MaterialTheme.typography.labelSmall)
                    Text(card.expiryDate.ifBlank { "Unknown" }, style = MaterialTheme.typography.bodyLarge)
                }
                Column {
                    Text("Type", style = MaterialTheme.typography.labelSmall)
                    Text(card.cardType, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Column {
                Text("Cardholder", style = MaterialTheme.typography.labelSmall)
                Text(
                    card.cardholderName ?: "Not provided by card",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (card.cardholderName == null) {
                Text(
                    "Many contactless cards do not expose the cardholder name.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                )
            }
        }
    }
    Button(onClick = onReadAgain) { Text("Read Another Card") }
}

@Composable
private fun CardErrorContent(message: String, onRetry: () -> Unit) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium
    )
    Button(onClick = onRetry) { Text("Try Again") }
}
