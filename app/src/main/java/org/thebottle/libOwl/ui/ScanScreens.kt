package org.thebottle.libOwl.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.thebottle.libOwl.data.BookInfo
import org.thebottle.libOwl.data.LookupResult

// ================================ Shared pieces ================================

@Composable
private fun BookSummary(info: BookInfo, isbn: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val cover = info.coverUrls.firstOrNull()
        Box(
            Modifier.size(width = 64.dp, height = 96.dp).clip(RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) AsyncImage(
                model = cover, contentDescription = "Cover",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(info.title, style = MaterialTheme.typography.titleMedium)
            info.author?.let { Text(it) }
            val meta = listOfNotNull(info.publisher, info.year?.toString()).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall)
            Text("ISBN $isbn", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NotFoundSummary(outcome: LookupResult, isbn: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (outcome is LookupResult.Error) "Couldn't reach the lookup service." else "No details found online.",
            style = MaterialTheme.typography.titleMedium
        )
        if (outcome is LookupResult.Error && outcome.detail.isNotBlank()) {
            Text(outcome.detail, style = MaterialTheme.typography.bodySmall)
        }
        Text("ISBN $isbn")
    }
}

@Composable
private fun LookingCard(isbn: String, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text("Looking up $isbn…", Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerScaffold(
    title: String,
    onBack: () -> Unit,
    vm: ConfirmScanViewModel,
    overlay: @Composable (Modifier) -> Unit
) {
    var torch by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { torch = !torch }) {
                        Icon(if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff, "Flashlight")
                    }
                }
            )
        }
    ) { pad ->
        CameraPermissionGate(Modifier.fillMaxSize().padding(pad), onBack = onBack) {
            Box(Modifier.fillMaxSize()) {
                BarcodeCameraView(Modifier.fillMaxSize(), torchOn = torch, onBarcode = vm::onBarcode)
                overlay(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp))
            }
        }
    }
}

// ============================== Scan (confirmation) ==============================

/** Scan one book, review it, then Save (returns to the main list), Edit first, or Cancel. */
@Composable
fun ConfirmScanScreen(
    vm: ConfirmScanViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onEditFirst: (isbn: String, lookup: Boolean) -> Unit
) {
    ScannerScaffold("Scan", onBack, vm) { mod ->
        Column(mod) {
            when (val s = vm.state) {
                ConfirmState.Scanning -> Card(Modifier.fillMaxWidth()) {
                    Text("Point the camera at a book's barcode.", Modifier.padding(16.dp))
                }
                is ConfirmState.Looking -> LookingCard(s.isbn, vm::dismiss)
                is ConfirmState.Result -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (val o = s.outcome) {
                            is LookupResult.Found -> {
                                BookSummary(o.info, s.isbn)
                                if (s.existing != null) {
                                    Text("Already in your library: ${s.existing.title}", color = MaterialTheme.colorScheme.error)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { vm.save(onSaved) }, enabled = !vm.saving,
                                        modifier = Modifier.weight(1f)
                                    ) { Text(if (vm.saving) "Saving…" else if (s.existing != null) "Save anyway" else "Save") }
                                    OutlinedButton(onClick = { onEditFirst(s.isbn, true) }, modifier = Modifier.weight(1f)) {
                                        Text("Edit first")
                                    }
                                }
                            }
                            else -> {
                                NotFoundSummary(o, s.isbn)
                                if (s.existing != null) {
                                    Text("Already in your library: ${s.existing.title}", color = MaterialTheme.colorScheme.error)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onEditFirst(s.isbn, false) }, modifier = Modifier.weight(1f)) {
                                        Text("Enter details")
                                    }
                                    OutlinedButton(
                                        onClick = { vm.saveIsbnOnly(onSaved) }, enabled = !vm.saving,
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Save ISBN only") }
                                }
                            }
                        }
                        TextButton(onClick = vm::dismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

// ================================== Multi-Scan ==================================

/** Scan many books in a row: each one shows a dialog with Add or Discard, then scanning continues. */
@Composable
fun MultiScanScreen(vm: ConfirmScanViewModel, onBack: () -> Unit, onReviewNeedsDetails: () -> Unit) {
    val tone = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }
    DisposableEffect(Unit) { onDispose { tone.release() } }
    LaunchedEffect(vm.scanCount) {
        if (vm.scanCount > 0) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    ScannerScaffold("Multi-Scan", onBack, vm) { mod ->
        Column(mod) {
            when (val s = vm.state) {
                is ConfirmState.Looking -> LookingCard(s.isbn, vm::dismiss)
                else -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${vm.addedCount} added", style = MaterialTheme.typography.headlineSmall)
                        if (vm.needsDetailsCount > 0) {
                            Text("${vm.needsDetailsCount} added without details", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("Point the camera at the next barcode.", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Done") }
                            if (vm.needsDetailsCount > 0) {
                                OutlinedButton(onClick = onReviewNeedsDetails, modifier = Modifier.weight(1f)) {
                                    Text("Review missing")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val s = vm.state
    if (s is ConfirmState.Result) {
        val found = s.outcome as? LookupResult.Found
        AlertDialog(
            onDismissRequest = {}, // must choose Add or Discard
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (found != null) BookSummary(found.info, s.isbn) else NotFoundSummary(s.outcome, s.isbn)
                    if (s.existing != null) {
                        Text("Already in your library: ${s.existing.title}", color = MaterialTheme.colorScheme.error)
                    }
                    if (found == null) {
                        Text("Add saves just the ISBN so you can fill in the rest later.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !vm.saving,
                    onClick = { if (found != null) vm.save {} else vm.saveIsbnOnly {} }
                ) { Text(if (vm.saving) "Adding…" else "Add") }
            },
            dismissButton = { TextButton(onClick = vm::dismiss, enabled = !vm.saving) { Text("Discard") } }
        )
    }
}
