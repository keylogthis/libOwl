package org.thebottle.libOwl.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.thebottle.libOwl.data.Book
import org.thebottle.libOwl.data.CoverStore
import org.thebottle.libOwl.data.CurrencySymbol
import org.thebottle.libOwl.data.Loan
import org.thebottle.libOwl.data.ReadStatus
import org.thebottle.libOwl.data.ReportPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    vm: BookListViewModel,
    covers: CoverStore,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onStats: () -> Unit,
    onLoans: () -> Unit,
    onScan: () -> Unit,
    onMultiScan: () -> Unit,
    onSaveReportPrefs: (ReportPrefs) -> Unit,
    onBorrowerHidden: (String, Boolean) -> Unit
) {
    val state by vm.state.collectAsState()
    val filters by vm.filters.collectAsState()
    val sort by vm.sort.collectAsState()
    val currency = LocalAppSettings.current.currency
    var showFilters by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    // Multi-select: started by long-pressing a book.
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editMenu by remember { mutableStateOf(false) }
    var bulk by remember { mutableStateOf<BulkField?>(null) }
    var bulkInfo by remember { mutableStateOf<String?>(null) }
    val borrowers by vm.borrowers.collectAsState()
    val selecting = selected.isNotEmpty()

    BackHandler(enabled = addMenu) { addMenu = false }
    BackHandler(enabled = selecting) { selected = emptySet() }
    BackHandler(enabled = editMenu) { editMenu = false }

    val s = state
    val narrowed = vm.query.isNotBlank() || filters.isActive
    val countText = when {
        s == null -> ""
        narrowed -> " (${s.books.size} of ${s.totalCount})"
        else -> " (${s.totalCount})"
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            if (selecting) TopAppBar(
                title = { Text("${selected.size} selected") },
                navigationIcon = {
                    IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, "Cancel selection") }
                },
                actions = {
                    IconButton(onClick = { s?.let { st -> selected = st.books.map { it.id }.toSet() } }) {
                        Icon(Icons.Default.SelectAll, "Select all shown")
                    }
                    IconButton(onClick = { editMenu = !editMenu }) {
                        Icon(Icons.Default.Edit, "Edit selected books")
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete selected") }
                }
            ) else TopAppBar(
                title = { Text("Library Owl$countText") },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortField.entries.forEach { field ->
                                DropdownMenuItem(
                                    text = { Text(field.label) },
                                    leadingIcon = {
                                        if (sort.field == field) Icon(Icons.Default.Check, contentDescription = "Selected")
                                    },
                                    onClick = { vm.setSortField(field); sortMenu = false }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (sort.ascending) "Ascending" else "Descending") },
                                leadingIcon = {
                                    Icon(
                                        if (sort.ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                        contentDescription = null
                                    )
                                },
                                onClick = { vm.toggleSortDirection() }
                            )
                        }
                    }
                    IconButton(onClick = { showFilters = true }) {
                        BadgedBox(badge = {
                            if (filters.activeCount > 0) Badge { Text("${filters.activeCount}") }
                        }) { Icon(Icons.Default.FilterList, contentDescription = "Filter") }
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Loans") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                onClick = { moreMenu = false; onLoans() }
                            )
                            DropdownMenuItem(
                                text = { Text("Statistics") },
                                leadingIcon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                                onClick = { moreMenu = false; onStats() }
                            )
                            DropdownMenuItem(
                                text = { Text("Print report") },
                                leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                                onClick = { moreMenu = false; showReport = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = { moreMenu = false; onSettings() }
                            )
                            DropdownMenuItem(
                                text = { Text("Help") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
                                onClick = { moreMenu = false; onHelp() }
                            )
                        }
                    }
                }
            )
        },
    ) { pad: PaddingValues ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            when {
                s == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                s.totalCount == 0 -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No books yet. Tap + to add one.")
                }
                else -> {
                    OutlinedTextField(
                        value = vm.query,
                        onValueChange = vm::onQueryChange,
                        placeholder = { Text("Search title or author") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (vm.query.isNotEmpty()) IconButton(onClick = { vm.onQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    ActiveFilterChips(filters, vm::updateFilters, vm::clearFilters)

                    if (s.books.isEmpty()) {
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("No books match your search or filters.")
                            TextButton(onClick = vm::clearAll) { Text("Clear search and filters") }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                            items(s.books, key = { it.id }) { book ->
                                BookRow(
                                    book, covers, showPrice = sort.field == SortField.PRICE, currency = currency,
                                    loan = s.loans[book.id],
                                    selecting = selecting,
                                    selected = book.id in selected,
                                    onLongClick = { selected = selected + book.id },
                                    onClick = {
                                        if (selecting) selected = if (book.id in selected) selected - book.id else selected + book.id
                                        else onOpen(book.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (selecting && editMenu) EditMenu(
        onClose = { editMenu = false },
        onPick = { field ->
            editMenu = false
            if (field == BulkField.BORROWER) {
                val chosen = s?.allBooks?.filter { it.id in selected }.orEmpty()
                if (chosen.all { it.id in (s?.loans ?: emptyMap()) }) {
                    bulkInfo = if (chosen.size == 1) "This book is already on loan." else "All of the selected books are already on loan."
                } else bulk = field
            } else bulk = field
        }
    )

    if (!selecting) AddMenu(
        expanded = addMenu,
        onToggle = { addMenu = !addMenu },
        onClose = { addMenu = false },
        onScan = onScan,
        onMultiScan = onMultiScan,
        onManual = onAdd
    )
    }

    if (s != null && selecting && bulk != null) {
        val chosen = s.allBooks.filter { it.id in selected }
        val done = { bulk = null; selected = emptySet() }
        when (bulk) {
            BulkField.STATUS -> BulkStatusDialog(
                chosen.size,
                onPick = { st -> vm.editBooks(chosen) { it.copy(readStatus = st) }; done() },
                onDismiss = { bulk = null }
            )
            BulkField.RATING -> BulkRatingDialog(
                chosen.size,
                onApply = { r -> vm.editBooks(chosen) { it.copy(rating = r) }; done() },
                onDismiss = { bulk = null }
            )
            BulkField.PRICE -> BulkPriceDialog(
                chosen.size, currency.symbol,
                onApply = { price -> vm.editBooks(chosen) { it.copy(price = price) }; done() },
                onDismiss = { bulk = null }
            )
            BulkField.GENRE -> BulkGenreDialog(
                chosen.size, s.genres,
                onApply = { g -> vm.editBooks(chosen) { it.copy(genre = g) }; done() },
                onDismiss = { bulk = null }
            )
            BulkField.BORROWER -> {
                val available = chosen.filter { it.id !in s.loans }
                val skipped = chosen.size - available.size
                val defaultDays = LocalAppSettings.current.loans.defaultLoanDays
                LendDialog(
                    title = (if (available.size == 1) "Lend 1 book" else "Lend ${available.size} books") +
                        if (skipped > 0) "\n($skipped already on loan, skipped)" else "",
                    confirmLabel = "Lend",
                    initialBorrower = "",
                    initialDue = if (defaultDays > 0) localDateToMillis(java.time.LocalDate.now().plusDays(defaultDays.toLong())) else null,
                    suggestions = borrowers,
                    onBorrowerHidden = onBorrowerHidden,
                    onConfirm = { who, due -> vm.lendBooks(available, who, due); done() },
                    onDismiss = { bulk = null }
                )
            }
            null -> Unit
        }
    }
    bulkInfo?.let { BulkInfoDialog(it) { bulkInfo = null } }

    if (confirmDelete && s != null) {
        val toDelete = s.allBooks.filter { it.id in selected }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (toDelete.size == 1) "Delete 1 book?" else "Delete ${toDelete.size} books?") },
            text = { Text("They will be removed from your library, along with their covers and loan records. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteBooks(toDelete, covers)
                    selected = emptySet()
                    confirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    if (showReport && s != null) {
        ReportSheet(
            books = s.books,
            allBooks = s.allBooks,
            narrowed = narrowed,
            covers = covers,
            prefs = LocalAppSettings.current.report,
            currency = currency,
            onSavePrefs = onSaveReportPrefs,
            onDismiss = { showReport = false }
        )
    }

    if (showFilters && s != null) {
        FilterSheet(
            filters = filters,
            genres = s.genres,
            onChange = vm::updateFilters,
            onClear = vm::clearFilters,
            onDismiss = { showFilters = false }
        )
    }
}

/** The pencil's menu: dims the screen and shows pills under the top bar, styled like the + menu. */
@Composable
private fun BoxScope.EditMenu(onClose: () -> Unit, onPick: (BulkField) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
    )
    Column(
        Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 68.dp, end = 12.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AddMenuPill("Read Status", Icons.Default.Bookmark) { onPick(BulkField.STATUS) }
        AddMenuPill("Rating", Icons.Default.Star) { onPick(BulkField.RATING) }
        AddMenuPill("Price", Icons.Default.AttachMoney) { onPick(BulkField.PRICE) }
        AddMenuPill("Genre", Icons.AutoMirrored.Filled.Label) { onPick(BulkField.GENRE) }
        AddMenuPill("Borrower", Icons.Default.Person) { onPick(BulkField.BORROWER) }
    }
}

/** The + button. When open it dims the screen and shows three equal-width pills above the button. */
@Composable
private fun BoxScope.AddMenu(
    expanded: Boolean,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    onScan: () -> Unit,
    onMultiScan: () -> Unit,
    onManual: () -> Unit
) {
    if (expanded) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
        )
    }
    Column(
        Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (expanded) {
            AddMenuPill("Scan", Icons.Default.QrCodeScanner) { onClose(); onScan() }
            AddMenuPill("Multi-Scan", Icons.Default.Layers) { onClose(); onMultiScan() }
            AddMenuPill("Manual", Icons.Default.Edit) { onClose(); onManual() }
        }
        FloatingActionButton(onClick = onToggle) {
            Icon(
                if (expanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = if (expanded) "Close add menu" else "Add book"
            )
        }
    }
}

@Composable
private fun AddMenuPill(label: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.width(150.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.weight(1f).padding(start = 10.dp))
    }
}

@Composable
private fun ActiveFilterChips(
    filters: BookFilters,
    onChange: ((BookFilters) -> BookFilters) -> Unit,
    onClear: () -> Unit
) {
    if (!filters.isActive) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(filters.statuses.toList()) { st ->
            RemovableChip(st.label) { onChange { f -> f.copy(statuses = f.statuses - st) } }
        }
        if (filters.minRating > 0) item {
            RemovableChip("★ ${formatStars(filters.minRating)}+") { onChange { f -> f.copy(minRating = 0) } }
        }
        if (filters.needsDetailsOnly) item {
            RemovableChip("Needs details") { onChange { f -> f.copy(needsDetailsOnly = false) } }
        }
        if (filters.onLoanOnly) item {
            RemovableChip("On loan") { onChange { f -> f.copy(onLoanOnly = false) } }
        }
        if (filters.cover != CoverFilter.ANY) item {
            RemovableChip(filters.cover.label) { onChange { f -> f.copy(cover = CoverFilter.ANY) } }
        }
        items(filters.genres.toList()) { g ->
            RemovableChip("Genre: $g") { onChange { f -> f.copy(genres = f.genres.toggleIgnoreCase(g)) } }
        }
        item { TextButton(onClick = onClear) { Text("Clear all") } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                Icons.Default.Close, contentDescription = "Remove filter",
                modifier = Modifier.size(InputChipDefaults.IconSize)
            )
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(
    book: Book,
    covers: CoverStore,
    showPrice: Boolean,
    currency: CurrencySymbol,
    loan: Loan?,
    selecting: Boolean,
    selected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                CoverImage(covers, book.coverFile, Modifier.size(width = 48.dp, height = 72.dp))
            }
        },
        headlineContent = { Text(book.title) },
        supportingContent = {
            Column {
                val parts = listOf(
                    book.author,
                    book.year?.toString().orEmpty(),
                    if (showPrice) book.price?.let { currency.format(it) }.orEmpty() else ""
                ).filter { it.isNotBlank() }
                if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
                if (book.rating > 0) RatingBar(book.rating, starSize = 14.dp)
                if (loan != null) Text(
                    "On loan to ${loan.borrower} · ${loan.dueText()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (loan.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        },
        trailingContent = {
            Text(if (book.needsAttention) "Needs details" else book.readStatus.label)
        },
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            }
        )
    )
    HorizontalDivider()
}
