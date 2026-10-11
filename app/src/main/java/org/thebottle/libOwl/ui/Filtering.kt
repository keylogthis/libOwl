package org.thebottle.libOwl.ui

import org.thebottle.libOwl.data.Book
import org.thebottle.libOwl.data.ReadStatus

enum class SortField(val label: String, val defaultAscending: Boolean) {
    TITLE("Title", true),
    AUTHOR("Author", true),
    RATING("Rating", false),
    YEAR("Year", true),
    PRICE("Price", true),
    DATE_ADDED("Date added", false)
}

data class SortSpec(val field: SortField = SortField.TITLE, val ascending: Boolean = true)

enum class CoverFilter(val label: String) { ANY("Any"), WITH("Has cover"), WITHOUT("No cover") }

data class BookFilters(
    val statuses: Set<ReadStatus> = emptySet(),
    /** Minimum rating in half-star units (0 = any). */
    val minRating: Int = 0,
    val genres: Set<String> = emptySet(),
    val cover: CoverFilter = CoverFilter.ANY,
    val needsDetailsOnly: Boolean = false,
    /** Only books currently on loan. Needs loan data, so it is applied by the list view model, not [matches]. */
    val onLoanOnly: Boolean = false
) {
    val activeCount: Int
        get() = listOf(
            statuses.isNotEmpty(), minRating > 0, genres.isNotEmpty(), cover != CoverFilter.ANY, needsDetailsOnly, onLoanOnly
        ).count { it }

    val isActive: Boolean get() = activeCount > 0

    fun matches(b: Book): Boolean =
        (statuses.isEmpty() || b.readStatus in statuses) &&
            (minRating == 0 || b.rating >= minRating) &&
            (!needsDetailsOnly || b.needsAttention) &&
            (genres.isEmpty() || genres.any { it.equals(b.genre.trim(), ignoreCase = true) }) &&
            when (cover) {
                CoverFilter.ANY -> true
                CoverFilter.WITH -> b.coverFile != null
                CoverFilter.WITHOUT -> b.coverFile == null
            }
}

/** Every word typed must appear somewhere in the title or author (case-insensitive). */
fun List<Book>.searchFilterSort(query: String, filters: BookFilters, sort: SortSpec): List<Book> {
    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return filter { b ->
        val hay = (b.title + " " + b.author).lowercase()
        tokens.all { hay.contains(it) } && filters.matches(b)
    }.sortedWith(sort.comparator())
}

/** Distinct, non-blank values (case-insensitive), sorted alphabetically. */
fun List<Book>.distinctValues(selector: (Book) -> String): List<String> =
    map { selector(it).trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }

fun formatStars(halfStars: Int): String = (halfStars / 2f).toString().removeSuffix(".0")

fun Set<String>.containsIgnoreCase(value: String) = any { it.equals(value, ignoreCase = true) }
fun Set<String>.toggleIgnoreCase(value: String): Set<String> =
    if (containsIgnoreCase(value)) filterNot { it.equals(value, ignoreCase = true) }.toSet() else this + value

/** Missing values (no price, no year, unrated, blank author) always sort last, in either direction. */
private fun <T : Comparable<T>> keyed(ascending: Boolean, key: (Book) -> T?): Comparator<Book> =
    Comparator { a, b ->
        val ka = key(a)
        val kb = key(b)
        when {
            ka == null && kb == null -> 0
            ka == null -> 1
            kb == null -> -1
            ascending -> ka.compareTo(kb)
            else -> kb.compareTo(ka)
        }
    }

private fun SortSpec.comparator(): Comparator<Book> {
    val primary: Comparator<Book> = when (field) {
        SortField.TITLE -> keyed(ascending) { it.title.lowercase() }
        SortField.AUTHOR -> keyed(ascending) { it.author.trim().lowercase().ifEmpty { null } }
        SortField.RATING -> keyed(ascending) { it.rating.takeIf { r -> r > 0 } }
        SortField.YEAR -> keyed(ascending) { it.year }
        SortField.PRICE -> keyed(ascending) { it.price }
        SortField.DATE_ADDED -> keyed(ascending) { it.dateAdded }
    }
    return primary.then(compareBy { it.title.lowercase() })
}
