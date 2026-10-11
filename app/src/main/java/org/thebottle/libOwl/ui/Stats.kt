package org.thebottle.libOwl.ui

import org.thebottle.libOwl.data.Book
import org.thebottle.libOwl.data.ReadStatus

data class LibraryStats(
    val total: Int,
    val totalPages: Long,
    val booksWithPages: Int,
    /** Pages in Finished and Read Again books (only books with a page count). */
    val readPages: Long,
    /** How many books those pages come from. */
    val readBooks: Int,
    /** Title and page count of the shortest / longest book that has a page count. */
    val shortest: Pair<String, Int>?,
    val longest: Pair<String, Int>?,
    val totalValue: Double,
    val booksWithPrice: Int,
    /** Average of rated books on the 0-5 scale, or null if nothing is rated. */
    val avgRating: Double?,
    val ratedCount: Int,
    val byStatus: List<Pair<ReadStatus, Int>>,
    val topAuthors: List<Pair<String, Int>>,
    val topGenres: List<Pair<String, Int>>,
    val topPublishers: List<Pair<String, Int>>,
    val byDecade: List<Pair<String, Int>>,
    val noCover: Int,
    val needsDetails: Int,
    val noIsbn: Int,
    val noDescription: Int
)

fun computeStats(books: List<Book>): LibraryStats {
    val withPages = books.mapNotNull { it.pages?.takeIf { p -> p > 0 } }
    val withPrice = books.mapNotNull { it.price }
    val rated = books.filter { it.rating > 0 }

    val paged = books.filter { (it.pages ?: 0) > 0 }
    val byLength = compareBy<Book>({ it.pages }, { it.title.lowercase() })
    val readList = paged.filter { it.readStatus == ReadStatus.FINISHED || it.readStatus == ReadStatus.READ_AGAIN }
    val readPages = readList.sumOf { (it.pages ?: 0).toLong() }

    val decades = books.mapNotNull { it.year }
        .groupingBy { it / 10 * 10 }
        .eachCount()
        .toSortedMap()
        .map { (decade, n) -> "${decade}s" to n }

    return LibraryStats(
        total = books.size,
        totalPages = withPages.sumOf { it.toLong() },
        booksWithPages = withPages.size,
        readPages = readPages,
        readBooks = readList.size,
        shortest = paged.minWithOrNull(byLength)?.let { it.title to (it.pages ?: 0) },
        longest = paged.maxWithOrNull(byLength)?.let { it.title to (it.pages ?: 0) },
        totalValue = withPrice.sum(),
        booksWithPrice = withPrice.size,
        avgRating = if (rated.isEmpty()) null else rated.sumOf { it.rating } / 2.0 / rated.size,
        ratedCount = rated.size,
        byStatus = ReadStatus.entries.map { s -> s to books.count { it.readStatus == s } },
        topAuthors = top(books) { it.author },
        topGenres = top(books) { it.genre },
        topPublishers = top(books) { it.publisher },
        byDecade = decades,
        noCover = books.count { it.coverFile == null },
        needsDetails = books.count { it.needsAttention },
        noIsbn = books.count { it.isbn.isBlank() },
        noDescription = books.count { it.description.isBlank() }
    )
}

/** Most common non-blank values (case-insensitive), keeping the first spelling seen. */
private fun top(books: List<Book>, n: Int = 5, selector: (Book) -> String): List<Pair<String, Int>> {
    val counts = LinkedHashMap<String, Pair<String, Int>>()
    for (b in books) {
        val v = selector(b).trim()
        if (v.isEmpty()) continue
        val key = v.lowercase()
        val cur = counts[key]
        counts[key] = if (cur == null) v to 1 else cur.first to cur.second + 1
    }
    return counts.values
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
        .take(n)
}
