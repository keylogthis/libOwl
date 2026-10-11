package org.thebottle.libOwl.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneOffset

/** Asks who has the book and when it is due. Used for new loans and for editing an existing one. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun LendDialog(
    title: String,
    confirmLabel: String,
    initialBorrower: String,
    /** Start-of-day millis, or null for no due date. */
    initialDue: Long?,
    suggestions: List<String>,
    /** Hide (true) or show again (false) a name in the suggestions. */
    onBorrowerHidden: (String, Boolean) -> Unit,
    onConfirm: (borrower: String, dueDate: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var borrower by remember { mutableStateOf(initialBorrower) }
    var due by remember { mutableStateOf(initialDue) }
    var pickingDate by remember { mutableStateOf(false) }
    var forgetName by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    val hidden = LocalAppSettings.current.hiddenBorrowers

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = borrower, onValueChange = { borrower = it },
                    label = { Text("Borrower") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val shown = suggestions.filter { it.isNotBlank() && !it.equals(borrower.trim(), true) }
                    .filter { it.trim().lowercase() !in hidden }
                    .filter { borrower.isBlank() || it.contains(borrower.trim(), ignoreCase = true) }
                    .take(6)
                if (shown.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        shown.forEach { name ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.combinedClickable(
                                    onClick = { borrower = name },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        forgetName = name
                                    }
                                )
                            ) {
                                Text(
                                    name, style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "Press and hold a name to remove it from this list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("Due date", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickingDate = true }) {
                        Text(due?.let { formatDate(it) } ?: "No due date – tap to set")
                    }
                    if (due != null) TextButton(onClick = { due = null }) { Text("Clear") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Lending to someone again brings their name back into the suggestions.
                    if (borrower.trim().lowercase() in hidden) onBorrowerHidden(borrower.trim(), false)
                    onConfirm(borrower.trim(), due)
                },
                enabled = borrower.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    forgetName?.let { name ->
        AlertDialog(
            onDismissRequest = { forgetName = null },
            title = { Text("Remove \"$name\"?") },
            text = { Text("The name won't be suggested any more. Past loans to them are kept, and they come back if you lend to them again.") },
            confirmButton = {
                TextButton(onClick = { onBorrowerHidden(name, true); forgetName = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { forgetName = null }) { Text("Cancel") } }
        )
    }

    if (pickingDate) {
        // The picker works in UTC midnight; convert to and from the local calendar date.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (due?.let { millisToLocalDate(it) } ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val date = java.time.Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        due = localDateToMillis(date)
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}
