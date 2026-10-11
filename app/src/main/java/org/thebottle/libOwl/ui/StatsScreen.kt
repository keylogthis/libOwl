package org.thebottle.libOwl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.thebottle.libOwl.data.BookRepository
import java.text.NumberFormat
import java.util.Locale

class StatsViewModel(repo: BookRepository) : ViewModel() {
    val stats: StateFlow<LibraryStats?> =
        repo.books
            .map { computeStats(it) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    companion object {
        fun factory(repo: BookRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { StatsViewModel(repo) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: StatsViewModel, onBack: () -> Unit) {
    val stats by vm.stats.collectAsState()
    val currency = LocalAppSettings.current.currency
    val numbers = NumberFormat.getIntegerInstance()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { pad ->
        val s = stats
        when {
            s == null -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { CircularProgressIndicator() }
            s.total == 0 -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                Text("Add some books to see statistics.")
            }
            else -> Column(
                Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (s.booksWithPages > 0) ReadProgress(s, numbers)

                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Total Books", numbers.format(s.total), null, Modifier.weight(1f).fillMaxHeight())
                    StatCard(
                        "Pages", numbers.format(s.totalPages),
                        if (s.booksWithPages > 0) "avg ${numbers.format(s.totalPages / s.booksWithPages)} per book" else null,
                        Modifier.weight(1f).fillMaxHeight()
                    )
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        "Average rating",
                        s.avgRating?.let { String.format(Locale.US, "%.1f / 5", it) } ?: "None yet",
                        "${s.ratedCount} rated",
                        Modifier.weight(1f).fillMaxHeight()
                    )
                    StatCard(
                        "Collection value",
                        if (s.booksWithPrice > 0) currency.format(s.totalValue) else "None yet",
                        "${s.booksWithPrice} with a price",
                        Modifier.weight(1f).fillMaxHeight()
                    )
                }

                if (s.booksWithPages > 0) {
                    val shortest = s.shortest
                    val longest = s.longest
                    if (shortest != null && longest != null) {
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                "Shortest book", shortest.first, "${numbers.format(shortest.second)} pages",
                                Modifier.weight(1f).fillMaxHeight(),
                                valueStyle = MaterialTheme.typography.titleMedium
                            )
                            StatCard(
                                "Longest book", longest.first, "${numbers.format(longest.second)} pages",
                                Modifier.weight(1f).fillMaxHeight(),
                                valueStyle = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                Section("Reading status") {
                    val max = s.byStatus.maxOf { it.second }
                    s.byStatus.forEach { (status, n) -> BarRow(status.label, n, max) }
                }

                if (s.topAuthors.isNotEmpty()) Section("Top authors") {
                    val max = s.topAuthors.first().second
                    s.topAuthors.forEach { (name, n) -> BarRow(name, n, max) }
                }
                if (s.topGenres.isNotEmpty()) Section("Top genres") {
                    val max = s.topGenres.first().second
                    s.topGenres.forEach { (name, n) -> BarRow(name, n, max) }
                }
                if (s.topPublishers.isNotEmpty()) Section("Top publishers") {
                    val max = s.topPublishers.first().second
                    s.topPublishers.forEach { (name, n) -> BarRow(name, n, max) }
                }
                if (s.byDecade.isNotEmpty()) Section("Published by decade") {
                    val max = s.byDecade.maxOf { it.second }
                    s.byDecade.forEach { (decade, n) -> BarRow(decade, n, max) }
                }

                Section("Missing information") {
                    BarRow("No cover", s.noCover, s.total)
                    BarRow("No description", s.noDescription, s.total)
                    BarRow("No ISBN", s.noIsbn, s.total)
                    BarRow("Needs details", s.needsDetails, s.total)
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    sub: String?,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.headlineSmall
) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = valueStyle, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Share of the library's pages that are in Finished or Read Again books. */
@Composable
private fun ReadProgress(s: LibraryStats, numbers: NumberFormat) {
    val fraction = if (s.totalPages > 0) (s.readPages.toFloat() / s.totalPages).coerceIn(0f, 1f) else 0f
    val missing = s.total - s.booksWithPages
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Read so far", style = MaterialTheme.typography.titleMedium)
            Text(String.format(Locale.US, "%.1f%%", fraction * 100), style = MaterialTheme.typography.titleMedium)
        }
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(10.dp))
        Text(
            "${numbers.format(s.readPages)} pages in " +
                "${numbers.format(s.readBooks)} ${if (s.readBooks == 1) "book" else "books"} marked read so far.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (missing > 0) {
            Text(
                "$missing ${if (missing == 1) "book has" else "books have"} no page count and ${if (missing == 1) "isn't" else "aren't"} included.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

/** One labelled horizontal bar; the longest bar in a section is the one with the largest count. */
@Composable
private fun BarRow(label: String, count: Int, max: Int) {
    val fraction = if (max <= 0) 0f else (count.toFloat() / max).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            modifier = Modifier.width(120.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
        Box(
            Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (count > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(maxOf(fraction, 0.02f))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Text(
            count.toString(),
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
