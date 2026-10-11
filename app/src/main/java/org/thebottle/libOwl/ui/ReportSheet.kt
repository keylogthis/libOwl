package org.thebottle.libOwl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.thebottle.libOwl.data.Book
import org.thebottle.libOwl.data.CoverStore
import org.thebottle.libOwl.data.CurrencySymbol
import org.thebottle.libOwl.data.ReportColumn
import org.thebottle.libOwl.data.ReportLayout
import org.thebottle.libOwl.data.ReportPrefs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportSheet(
    /** The books currently shown on the list (after search and filters). */
    books: List<Book>,
    allBooks: List<Book>,
    narrowed: Boolean,
    covers: CoverStore,
    prefs: ReportPrefs,
    currency: CurrencySymbol,
    onSavePrefs: (ReportPrefs) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(prefs.title) }
    var layout by remember { mutableStateOf(prefs.layout) }
    var columns by remember { mutableStateOf(prefs.columns) }
    var useCurrent by remember { mutableStateOf(narrowed) }
    var building by remember { mutableStateOf(false) }

    val chosen = if (narrowed && useCurrent) books else allBooks

    ModalBottomSheet(onDismissRequest = { if (!building) onDismiss() }, sheetState = sheetState) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Print report", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Report title") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Layout", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportLayout.entries.forEach { l ->
                    FilterChip(selected = layout == l, onClick = { layout = l }, label = { Text(l.label) })
                }
            }

            if (narrowed) {
                Text("Books to include", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = useCurrent, onClick = { useCurrent = true },
                        label = { Text("Current list (${books.size})") }
                    )
                    FilterChip(
                        selected = !useCurrent, onClick = { useCurrent = false },
                        label = { Text("All books (${allBooks.size})") }
                    )
                }
            }

            Text("Properties", style = MaterialTheme.typography.labelLarge)
            Text(
                "The title is always included. Books keep the order you've sorted the list in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportColumn.entries.forEach { c ->
                    FilterChip(
                        selected = c in columns,
                        onClick = { columns = if (c in columns) columns - c else columns + c },
                        label = { Text(c.label) }
                    )
                }
            }

            Button(
                onClick = {
                    building = true
                    val chosenPrefs = ReportPrefs(columns, layout, title.trim().ifEmpty { ReportPrefs().title })
                    onSavePrefs(chosenPrefs)
                    scope.launch {
                        val html = buildReportHtml(chosen, chosenPrefs, currency, covers)
                        ReportPrinter.print(context, html, chosenPrefs.title)
                        building = false
                        onDismiss()
                    }
                },
                enabled = !building && chosen.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (building) "Preparing…" else "Print or save as PDF") }
        }
    }
}
