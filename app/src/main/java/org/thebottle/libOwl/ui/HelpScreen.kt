package org.thebottle.libOwl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** One help topic. In the bullets, text between double asterisks is shown in bold. */
private class HelpTopic(val title: String, val bullets: List<String>)

private val topics = listOf(
    HelpTopic(
        "Adding books",
        listOf(
            "**Scan:** Point the camera at a book's barcode, check the details that come up, then save.",
            "**Multi-Scan:** Scan many books in a row. After each scan you'll see what was found. Tap **Add** to keep it or **Discard** to skip it, then scan the next one.",
            "**Manual:** Type in the details yourself.",
            "If a scanned book has no details online, it's saved as \"Unknown title\" and marked **Needs details**. Open the filter and choose \"Needs details (from scans)\" to find those books and finish them.",
            "On any book, **Look up details online** fills in only the empty fields. It never overwrites what you've typed."
        )
    ),
    HelpTopic(
        "Selecting several books",
        listOf(
            "**Press and hold** a book to start selecting. Tap other books to add or remove them.",
            "The **pencil** opens a menu to change the **Read Status**, **Rating**, **Price**, **Genre** or **Borrower** of all selected books at once. **Borrower** lends the selected books to someone; books that are already on loan are skipped.",
            "**Select all** picks every book currently shown. Search or filter first to narrow it down.",
            "The **trash can** deletes the selected books, after asking you to confirm. The deleted books' covers and loan records go with them.",
            "Tap **✕** or press Back to cancel."
        )
    ),
    HelpTopic(
        "Lending books",
        listOf(
            "Open a book and scroll to the **Loan** section. Tap **Lend this book**, enter who has it, and optionally set a due date.",
            "Names you've lent to before appear as chips. Tap one to select it. Or **press and hold** a name to remove it from the suggestions. Past loans to that person are kept, and the name comes back if you lend to them again.",
            "**⋮ menu → Loans** shows everything currently on loan, with overdue books first. The **History** tab lists returned loans. Use the trash icon to remove one, or **Clear all history** to remove them all.",
            "Turn on **Due date reminders** in Settings for a daily notification about loans that are due soon or overdue."
        )
    ),
    HelpTopic(
        "Ratings and covers",
        listOf(
            "**Clearing:** After setting a rating, quickly tap the same rating again to clear it. ",
            "**Covers:** Tap the empty cover box to add one from your filesystem. Tap an existing cover to view it full size."
        )
    ),
    HelpTopic(
        "Searching, sorting, and filtering",
        listOf(
            "Search matches titles and authors.",
            "The **sort** icon changes what the list is ordered by and whether it runs ascending or descending.",
            "The **filter** icon narrows the list by read status, minimum rating, genre, cover, needs details, and whether a book is on loan. Your active filters show as chips under the search box. Tap a chip to remove it, or **Clear all**."
        )
    ),
    HelpTopic(
        "Reports and printing",
        listOf(
            "**⋮ menu → Print report** lets you set a title, choose a **Table** or **Detailed** layout, and pick which properties to include. Cover images are optional.",
            "If a search or filter is active, you can print just those books or the whole library.",
            "In the print screen, choose **Save as PDF** to keep a copy, or pick a printer. Your choices are remembered for next time."
        )
    ),
    HelpTopic(
        "Backup and restore",
        listOf(
            "In **Settings → Backup location**, pick a folder. **Back up now** saves your books, covers, loans and settings into one file there.",
            "**Restore from backup** replaces everything on this phone with the contents of a backup file, then restarts the app. This can't be undone.",
            "Keep a copy of your backups somewhere other than this phone, such as cloud storage or your computer."
        )
    )
)

private fun boldMarkup(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    // Only one topic is open at a time; -1 means all closed. The first one starts open.
    var open by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(topics) { index, topic ->
                val expanded = open == index
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.clickable { open = if (expanded) -1 else index }.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                topic.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (expanded) "Collapse" else "Expand"
                            )
                        }
                        if (expanded) {
                            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                topic.bullets.forEach { b ->
                                    Row {
                                        Text("•", modifier = Modifier.padding(end = 8.dp))
                                        Text(boldMarkup(b), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
