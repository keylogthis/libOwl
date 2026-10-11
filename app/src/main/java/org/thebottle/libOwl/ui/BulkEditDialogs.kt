package org.thebottle.libOwl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.thebottle.libOwl.data.ReadStatus

/** What the multi-select pencil menu can change. */
enum class BulkField { STATUS, RATING, PRICE, GENRE, BORROWER }

private fun booksText(count: Int) = if (count == 1) "1 book" else "$count books"

@Composable
fun BulkStatusDialog(count: Int, onPick: (ReadStatus) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Read status for ${booksText(count)}") },
        text = {
            Column {
                ReadStatus.entries.forEach { st ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(st) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = false, onClick = null)
                        Text(st.label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun BulkRatingDialog(count: Int, onApply: (Int) -> Unit, onDismiss: () -> Unit) {
    var rating by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rating for ${booksText(count)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RatingBar(rating, starSize = 40.dp) { rating = it }
                Text(
                    if (rating > 0) "${formatStars(rating)} / 5" else "No rating (applying clears the rating)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApply(rating) }) { Text(if (rating > 0) "Apply" else "Clear rating") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun BulkPriceDialog(count: Int, currencySymbol: String, onApply: (Double?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val value = text.trim().replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Price for ${booksText(count)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(10) },
                    label = { Text("Price") },
                    prefix = if (currencySymbol.isNotEmpty()) ({ Text(currencySymbol) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { onApply(null) }) { Text("Clear price instead") }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(value) }, enabled = value != null) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BulkGenreDialog(count: Int, genres: List<String>, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Genre for ${booksText(count)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("Genre") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (genres.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        genres.take(10).forEach { g ->
                            AssistChip(onClick = { text = g }, label = { Text(g) })
                        }
                    }
                }
                TextButton(onClick = { onApply("") }) { Text("Clear genre instead") }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(text.trim()) }, enabled = text.isNotBlank()) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun BulkInfoDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}
