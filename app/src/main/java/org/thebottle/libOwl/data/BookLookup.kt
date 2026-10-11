package org.thebottle.libOwl.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Title prefix used for books saved from a scan when no details could be found. */
const val UNKNOWN_TITLE_PREFIX = "Unknown title"

data class BookInfo(
    val isbn: String,
    val title: String,
    val author: String?,
    val publisher: String?,
    val year: Int?,
    val pages: Int?,
    val genre: String?,
    val description: String?,
    /** Candidate cover image URLs, best first. */
    val coverUrls: List<String>
)

sealed interface LookupResult {
    data class Found(val info: BookInfo) : LookupResult
    data object NotFound : LookupResult
    /** [detail] says which service failed and why, e.g. "Open Library: HTTP 403". */
    data class Error(val detail: String) : LookupResult
}

fun BookInfo.toBook(coverFile: String?) = Book(
    isbn = isbn, title = title, author = author.orEmpty(), publisher = publisher.orEmpty(),
    year = year, pages = pages, genre = genre.orEmpty(), description = description.orEmpty(),
    coverFile = coverFile
)

fun placeholderBook(isbn: String) = Book(
    isbn = isbn, title = "$UNKNOWN_TITLE_PREFIX (ISBN $isbn)", needsAttention = true
)

/** Looks a book up by ISBN using Open Library and Google Books (in parallel), merging what they return. */
class BookLookup {
    private val cache = ConcurrentHashMap<String, BookInfo>()

    suspend fun lookup(isbn: String): LookupResult = coroutineScope {
        cache[isbn]?.let { return@coroutineScope LookupResult.Found(it) }

        val ol = async(Dispatchers.IO) { runCatching { openLibrary(isbn) } }
        val gb = async(Dispatchers.IO) { runCatching { googleBooks(isbn) } }
        val results = listOf(ol.await(), gb.await())
        val hits = results.mapNotNull { it.getOrNull() }

        when {
            hits.isNotEmpty() -> {
                var info = merge(isbn, hits)
                // Neither main lookup had a description: try Open Library's edition and work records.
                if (info.description == null) {
                    val extra = withContext(Dispatchers.IO) { runCatching { openLibraryDescription(isbn) }.getOrNull() }
                    if (extra != null) info = info.copy(description = extra)
                }
                cache[isbn] = info
                LookupResult.Found(info)
            }
            results.all { it.isSuccess } -> LookupResult.NotFound
            else -> LookupResult.Error(
                listOf("Open Library" to results[0], "Google Books" to results[1])
                    .mapNotNull { (name, r) -> r.exceptionOrNull()?.let { "$name: ${describe(it)}" } }
                    .joinToString("; ")
            )
        }
    }

    private fun describe(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "no internet connection"
        is java.net.SocketTimeoutException -> "timed out"
        is org.json.JSONException -> "unexpected reply"
        else -> e.message ?: e.javaClass.simpleName
    }

    private class Partial(
        val title: String,
        val author: String?,
        val publisher: String?,
        val year: Int?,
        val pages: Int?,
        val genre: String?,
        val description: String?,
        val coverUrl: String?
    )

    private fun merge(isbn: String, parts: List<Partial>): BookInfo {
        fun <T> pick(sel: (Partial) -> T?): T? = parts.firstNotNullOfOrNull(sel)
        val covers = parts.mapNotNull { it.coverUrl } + "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg?default=false"
        return BookInfo(
            isbn = isbn,
            title = parts.first().title,
            author = pick { it.author },
            publisher = pick { it.publisher },
            year = pick { it.year },
            pages = pick { it.pages },
            genre = pick { it.genre },
            description = pick { it.description },
            coverUrls = covers.distinct()
        )
    }

    private fun openLibrary(isbn: String): Partial? {
        val body = httpGet("https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&format=json&jscmd=data") ?: return null
        val obj = JSONObject(body).optJSONObject("ISBN:$isbn") ?: return null
        val title = obj.optString("title").trim().ifEmpty { return null }

        val authors = obj.optJSONArray("authors")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name")?.trim()?.ifEmpty { null } }
        }?.joinToString(", ")?.ifEmpty { null }

        val publisher = obj.optJSONArray("publishers")?.optJSONObject(0)?.optString("name")?.trim()?.ifEmpty { null }
        val cover = obj.optJSONObject("cover")?.let { c ->
            c.optString("large").ifEmpty { c.optString("medium") }.ifEmpty { null }
        }
        return Partial(
            title = title, author = authors, publisher = publisher,
            year = parseYear(obj.optString("publish_date")),
            pages = obj.optInt("number_of_pages", 0).takeIf { it > 0 },
            genre = null, description = null, coverUrl = cover
        )
    }

    private fun googleBooks(isbn: String): Partial? {
        val body = httpGet("https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn&maxResults=1") ?: return null
        val items = JSONObject(body).optJSONArray("items") ?: return null
        val v = items.optJSONObject(0)?.optJSONObject("volumeInfo") ?: return null
        val title = v.optString("title").trim().ifEmpty { return null }

        val authors = v.optJSONArray("authors")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        }?.joinToString(", ")?.ifEmpty { null }

        val thumb = v.optJSONObject("imageLinks")?.let { l ->
            l.optString("thumbnail").ifEmpty { l.optString("smallThumbnail") }.ifEmpty { null }
        }?.replace("http://", "https://")?.replace("&edge=curl", "")

        return Partial(
            title = title, author = authors,
            publisher = v.optString("publisher").trim().ifEmpty { null },
            year = parseYear(v.optString("publishedDate")),
            pages = v.optInt("pageCount", 0).takeIf { it > 0 },
            genre = v.optJSONArray("categories")?.optString(0)?.substringBefore(" / ")?.trim()?.ifEmpty { null },
            description = v.optString("description").replace(Regex("<[^>]*>"), "").trim().ifEmpty { null },
            coverUrl = thumb
        )
    }

    /**
     * Open Library keeps descriptions on the edition record, or more often on the "work" it belongs to.
     * Both can store the text as a plain string or as {"type": ..., "value": ...}.
     */
    private fun openLibraryDescription(isbn: String): String? {
        val edition = httpGet("https://openlibrary.org/isbn/$isbn.json")?.let { JSONObject(it) } ?: return null
        textOf(edition.opt("description"))?.let { return it }
        val workKey = edition.optJSONArray("works")?.optJSONObject(0)?.optString("key")?.ifEmpty { null } ?: return null
        val work = httpGet("https://openlibrary.org$workKey.json")?.let { JSONObject(it) } ?: return null
        return textOf(work.opt("description"))
    }

    private fun textOf(value: Any?): String? {
        val raw = when (value) {
            is String -> value
            is JSONObject -> value.optString("value")
            else -> return null
        }
        return raw
            .substringBefore("----------") // Open Library appends "see also" sections after a rule
            .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "\$1") // markdown links -> link text
            .replace(Regex("""\(\[source]\[\d+]\)"""), "")
            .replace(Regex("""(?m)^\[\d+]:.*$"""), "")
            .trim()
            .ifEmpty { null }
    }

    private fun parseYear(text: String): Int? =
        Regex("\\b(1[0-9]{3}|20[0-9]{2})\\b").find(text)?.value?.toIntOrNull()

    /** Returns the body, null on 404, and throws on any other failure (so callers can tell "not found" from "offline"). */
    private fun httpGet(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "libOwl/0.9.1 (Android personal book catalog app)")
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            if (code == 404) return null
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
