package org.thebottle.libOwl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.thebottle.libOwl.data.ReadStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    filters: BookFilters,
    genres: List<String>,
    onChange: ((BookFilters) -> BookFilters) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showGenres by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onClear, enabled = filters.isActive) { Text("Clear all") }
            }

            Text("Status", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadStatus.entries.forEach { st ->
                    FilterChip(
                        selected = st in filters.statuses,
                        onClick = {
                            onChange { f ->
                                f.copy(statuses = if (st in f.statuses) f.statuses - st else f.statuses + st)
                            }
                        },
                        label = { Text(st.label) }
                    )
                }
            }

            val minText = if (filters.minRating > 0) " (${formatStars(filters.minRating)} stars and up)" else " (any)"
            Text("Minimum rating$minText", style = MaterialTheme.typography.labelLarge)
            RatingBar(filters.minRating, starSize = 36.dp) { r -> onChange { it.copy(minRating = r) } }

            Text("Cover", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoverFilter.entries.forEach { c ->
                    FilterChip(
                        selected = filters.cover == c,
                        onClick = { onChange { it.copy(cover = c) } },
                        label = { Text(c.label) }
                    )
                }
            }

            FilterChip(
                selected = filters.needsDetailsOnly,
                onClick = { onChange { it.copy(needsDetailsOnly = !it.needsDetailsOnly) } },
                label = { Text("Needs details (from scans)") }
            )
            FilterChip(
                selected = filters.onLoanOnly,
                onClick = { onChange { it.copy(onLoanOnly = !it.onLoanOnly) } },
                label = { Text("Currently on loan") }
            )

            PickerRow("Genre", filters.genres) { showGenres = true }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }

    if (showGenres) {
        MultiSelectDialog(
            title = "Genre", options = genres, selected = filters.genres,
            onToggle = { v -> onChange { it.copy(genres = it.genres.toggleIgnoreCase(v)) } },
            onClear = { onChange { it.copy(genres = emptySet()) } },
            onDismiss = { showGenres = false }
        )
    }
}

@Composable
private fun PickerRow(label: String, selected: Set<String>, onClick: () -> Unit) {
    val summary = when (selected.size) {
        0 -> "Any"
        1 -> selected.first()
        else -> "${selected.size} selected"
    }
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(summary) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun MultiSelectDialog(
    title: String,
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val shown = options.filter { search.isBlank() || it.contains(search.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (options.isEmpty()) {
                    Text("Nothing to choose from yet. Fill in this field on some books first.")
                } else {
                    if (options.size > 8) {
                        OutlinedTextField(
                            value = search, onValueChange = { search = it },
                            placeholder = { Text("Search") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(shown, key = { it }) { opt ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onToggle(opt) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = selected.containsIgnoreCase(opt), onCheckedChange = null)
                                Spacer(Modifier.width(12.dp))
                                Text(opt)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onClear, enabled = selected.isNotEmpty()) { Text("Clear") } }
    )
}
