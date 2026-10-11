package org.thebottle.libOwl.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.thebottle.libOwl.data.CoverStore
import org.thebottle.libOwl.data.Loan
import java.time.LocalDate
import org.thebottle.libOwl.data.ReadStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookEditScreen(
    vm: BookEditViewModel,
    covers: CoverStore,
    onDone: () -> Unit,
    onBorrowerHidden: (String, Boolean) -> Unit = { _, _ -> },
    onSaved: () -> Unit = onDone
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var showViewer by remember { mutableStateOf(false) }
    val f = vm.form
    val loans by vm.loans.collectAsState()
    val borrowers by vm.borrowers.collectAsState()
    var lendDialog by remember { mutableStateOf(false) }
    var editingLoan by remember { mutableStateOf<Loan?>(null) }
    val defaultLoanDays = LocalAppSettings.current.loans.defaultLoanDays
    val currencySymbol = LocalAppSettings.current.currency.symbol

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importCover(uri)
    }
    val pickCover = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Scaffold(
        floatingActionButton = {
            if (vm.loaded) {
                FloatingActionButton(
                    onClick = { if (vm.canSave) vm.save(onSaved) },
                    containerColor = if (vm.canSave) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (vm.canSave) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                ) { Icon(Icons.Default.Save, contentDescription = "Save") }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "Add book" else "Edit book") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (!vm.isNew) IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                }
            )
        }
    ) { pad ->
        if (!vm.loaded) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()).padding(bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cover
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(
                    covers, f.coverFile,
                    Modifier.size(width = 110.dp, height = 165.dp)
                        .clickable { if (f.coverFile != null) showViewer = true else pickCover() }
                )
                if (f.coverFile == null) {
                    OutlinedButton(onClick = { pickCover() }) { Text("Add cover") }
                } else {
                    Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickCover() }, modifier = Modifier.fillMaxWidth()) { Text("Change cover") }
                        OutlinedButton(onClick = { vm.setCover(null) }, modifier = Modifier.fillMaxWidth()) { Text("Remove cover") }
                    }
                }
            }

            Field("Title *", f.title) { v -> vm.update { it.copy(title = v) } }
            Field("Author", f.author) { v -> vm.update { it.copy(author = v) } }

            val ratingText = if (f.rating > 0) " (${(f.rating / 2f).toString().removeSuffix(".0")} / 5)" else ""
            Text("Rating$ratingText", style = MaterialTheme.typography.labelLarge)
            RatingBar(f.rating, starSize = 36.dp) { r -> vm.update { it.copy(rating = r) } }

            Text("Status", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadStatus.entries.forEach { s ->
                    FilterChip(
                        selected = f.readStatus == s,
                        onClick = { vm.update { it.copy(readStatus = s) } },
                        label = { Text(s.label) }
                    )
                }
            }

            Field("ISBN", f.isbn, KeyboardType.Number) { v -> vm.update { it.copy(isbn = v) } }
            OutlinedButton(
                onClick = { vm.lookupIsbn() },
                enabled = !vm.lookingUp && f.isbn.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (vm.lookingUp) "Looking up…" else "Look up details online") }
            vm.lookupStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Field("Publisher", f.publisher) { v -> vm.update { it.copy(publisher = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Year", f.year, KeyboardType.Number, Modifier.weight(1f)) { v ->
                    vm.update { it.copy(year = v.filter(Char::isDigit).take(4)) }
                }
                Field("Pages", f.pages, KeyboardType.Number, Modifier.weight(1f)) { v ->
                    vm.update { it.copy(pages = v.filter(Char::isDigit).take(5)) }
                }
                Field("Price", f.price, KeyboardType.Decimal, Modifier.weight(1f), prefix = currencySymbol) { v ->
                    vm.update { it.copy(price = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(10)) }
                }
            }
            Field("Genre", f.genre) { v -> vm.update { it.copy(genre = v) } }

            OutlinedTextField(
                value = f.description, onValueChange = { v -> vm.update { it.copy(description = v) } },
                label = { Text("Description") }, minLines = 3, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = f.notes, onValueChange = { v -> vm.update { it.copy(notes = v) } },
                label = { Text("My notes") }, minLines = 2, modifier = Modifier.fillMaxWidth()
            )

            if (!vm.isNew) {
                LoanSection(
                    loans = loans,
                    onLend = { lendDialog = true },
                    onEdit = { editingLoan = it },
                    onReturn = { vm.markReturned(it) },
                    onDelete = { vm.deleteLoan(it) }
                )
            }

        }
    }

    if (lendDialog) {
        LendDialog(
            title = "Lend this book",
            confirmLabel = "Lend",
            initialBorrower = "",
            initialDue = if (defaultLoanDays > 0) localDateToMillis(LocalDate.now().plusDays(defaultLoanDays.toLong())) else null,
            suggestions = borrowers,
            onBorrowerHidden = onBorrowerHidden,
            onConfirm = { who, due -> vm.lend(who, due); lendDialog = false },
            onDismiss = { lendDialog = false }
        )
    }
    editingLoan?.let { loan ->
        LendDialog(
            title = "Edit loan",
            confirmLabel = "Save",
            initialBorrower = loan.borrower,
            initialDue = loan.dueDate,
            suggestions = borrowers,
            onBorrowerHidden = onBorrowerHidden,
            onConfirm = { who, due -> vm.updateLoan(loan.copy(borrower = who, dueDate = due)); editingLoan = null },
            onDismiss = { editingLoan = null }
        )
    }

    if (showViewer && f.coverFile != null) {
        CoverViewerDialog(covers, f.coverFile, onDismiss = { showViewer = false })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete book?") },
            text = { Text("\"${f.title}\" will be removed from your library.") },
            confirmButton = { TextButton(onClick = { vm.delete(onSaved) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    type: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    prefix: String = "",
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        prefix = if (prefix.isNotEmpty()) ({ Text(prefix) }) else null,
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = modifier
    )
}

/** Who has this book now, plus past loans. Changes apply immediately (they don't wait for Save). */
@Composable
private fun LoanSection(
    loans: List<Loan>,
    onLend: () -> Unit,
    onEdit: (Loan) -> Unit,
    onReturn: (Loan) -> Unit,
    onDelete: (Loan) -> Unit
) {
    val current = loans.firstOrNull { it.isActive }
    val past = loans.filter { !it.isActive }
    var showPast by remember { mutableStateOf(false) }

    Text("Loan", style = MaterialTheme.typography.labelLarge)
    if (current == null) {
        OutlinedButton(onClick = onLend, modifier = Modifier.fillMaxWidth()) { Text("Lend this book") }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("On loan to ${current.borrower}", style = MaterialTheme.typography.titleMedium)
            Text(
                "Since ${formatDate(current.dateOut)} · ${current.dueText()}",
                color = if (current.isOverdue) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onReturn(current) }) { Text("Mark returned") }
                OutlinedButton(onClick = { onEdit(current) }) { Text("Edit loan") }
            }
        }
    }
    if (past.isNotEmpty()) {
        TextButton(onClick = { showPast = !showPast }) {
            Text(if (showPast) "Hide past loans" else "Past loans (${past.size})")
        }
        if (showPast) past.forEach { l ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${l.borrower} · ${formatDate(l.dateOut)} – ${l.returnedDate?.let { formatDate(it) }.orEmpty()}",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium
                )
                IconButton(onClick = { onDelete(l) }) { Icon(Icons.Default.Delete, "Delete this record") }
            }
        }
    }
}
