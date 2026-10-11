package org.thebottle.libOwl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.thebottle.libOwl.data.Book
import org.thebottle.libOwl.data.BookRepository
import org.thebottle.libOwl.data.CoverStore
import org.thebottle.libOwl.data.Loan

data class LoanRow(val loan: Loan, val book: Book)

data class LoansState(val active: List<LoanRow>, val history: List<LoanRow>)

class LoansViewModel(private val repo: BookRepository) : ViewModel() {
    val state: StateFlow<LoansState?> = combine(repo.books, repo.allLoans) { books, loans ->
        val byId = books.associateBy { it.id }
        val rows = loans.mapNotNull { l -> byId[l.bookId]?.let { LoanRow(l, it) } }
        val (active, past) = rows.partition { it.loan.isActive }
        LoansState(
            // Overdue and soonest-due first; loans without a due date last.
            active = active.sortedWith(compareBy({ it.loan.dueDate == null }, { it.loan.dueDate ?: 0L })),
            history = past.sortedByDescending { it.loan.returnedDate ?: 0L }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun markReturned(loan: Loan) { viewModelScope.launch { repo.markReturned(loan) } }
    fun deleteLoan(loan: Loan) { viewModelScope.launch { repo.deleteLoan(loan) } }
    fun clearHistory() { viewModelScope.launch { repo.clearLoanHistory() } }

    companion object {
        fun factory(repo: BookRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { LoansViewModel(repo) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(vm: LoansViewModel, covers: CoverStore, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val state by vm.state.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<LoanRow?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Loans") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { pad ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        androidx.compose.foundation.layout.Column(Modifier.padding(pad)) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showHistory, onClick = { showHistory = false },
                    label = { Text("On loan (${s.active.size})") }
                )
                FilterChip(
                    selected = showHistory, onClick = { showHistory = true },
                    label = { Text("History (${s.history.size})") }
                )
            }
            if (showHistory && s.history.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text("Clear all history")
                }
            }
            val rows = if (showHistory) s.history else s.active
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(if (showHistory) "No returned loans yet." else "Nothing is on loan. Open a book to lend it.")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rows, key = { it.loan.id }) { row ->
                        val overdue = row.loan.isOverdue
                        ListItem(
                            leadingContent = { CoverImage(covers, row.book.coverFile, Modifier.size(width = 48.dp, height = 72.dp)) },
                            headlineContent = { Text(row.book.title) },
                            supportingContent = {
                                androidx.compose.foundation.layout.Column {
                                    Text(row.loan.borrower)
                                    Text(
                                        "Out ${formatDate(row.loan.dateOut)} · ${row.loan.dueText()}",
                                        color = if (overdue) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailingContent = {
                                if (!showHistory) OutlinedButton(onClick = { vm.markReturned(row.loan) }) { Text("Returned") }
                                else IconButton(onClick = { deleting = row }) {
                                    Icon(Icons.Default.Delete, "Remove from history")
                                }
                            },
                            colors = ListItemDefaults.colors(),
                            modifier = Modifier.clickable { onOpen(row.book.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    deleting?.let { row ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove from history?") },
            text = { Text("The record of \"${row.book.title}\" lent to ${row.loan.borrower} will be deleted.") },
            confirmButton = { TextButton(onClick = { vm.deleteLoan(row.loan); deleting = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("All records of returned loans will be deleted. Books currently on loan are not affected.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}
