package org.thebottle.libOwl.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.thebottle.libOwl.data.Book
import org.thebottle.libOwl.data.CoverStore
import org.thebottle.libOwl.data.CurrencySymbol
import org.thebottle.libOwl.data.ReportColumn
import org.thebottle.libOwl.data.ReportLayout
import org.thebottle.libOwl.data.ReportPrefs
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import kotlin.math.max

private const val CSS = """
@page { margin: 14mm; }
body { font-family: sans-serif; font-size: 10pt; color: #111; }
h1 { font-size: 17pt; margin: 0 0 2pt 0; }
.sub { color: #555; font-size: 9pt; margin-bottom: 12pt; }
table { width: 100%; border-collapse: collapse; }
thead { display: table-header-group; }
th { text-align: left; font-size: 9pt; border-bottom: 1.5pt solid #333; padding: 3pt 5pt; }
td { border-bottom: 0.5pt solid #ccc; padding: 4pt 5pt; vertical-align: top; }
tr { page-break-inside: avoid; }
.num { text-align: right; white-space: nowrap; }
.long { font-size: 8.5pt; width: 32%; }
.cv { width: 34pt; }
img.c { width: 30pt; height: auto; }
.book { display: flex; gap: 12pt; padding: 8pt 0; border-bottom: 0.5pt solid #ccc; page-break-inside: avoid; }
.cvbox { flex: none; width: 56pt; }
.cvbox img { width: 56pt; height: auto; }
.info { flex: 1; }
.t { font-size: 12pt; font-weight: bold; margin-bottom: 3pt; }
.f { margin: 1pt 0; }
.l { color: #555; }
.p { margin-top: 4pt; font-size: 9pt; }
"""

private fun esc(s: String): String = s
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    .replace("\r", "").replace("\n", "<br>")

private fun stars(rating: Int): String =
    if (rating <= 0) "" else "★".repeat(rating / 2) + if (rating % 2 == 1) "½" else ""

/** A small base64 copy of a cover so the report page doesn't need file access. */
private fun thumbnail(covers: CoverStore, name: String): String? {
    return try {
        val file = covers.file(name)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 240) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 75, out)
        bmp.recycle()
        "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}

suspend fun buildReportHtml(
    books: List<Book>,
    prefs: ReportPrefs,
    currency: CurrencySymbol,
    covers: CoverStore
): String = withContext(Dispatchers.IO) {
    val cols = ReportColumn.entries.filter { it in prefs.columns }
    val numbers = NumberFormat.getIntegerInstance()
    val thumbs: Map<Long, String?> =
        if (ReportColumn.COVER in cols) books.associate { it.id to it.coverFile?.let { f -> thumbnail(covers, f) } }
        else emptyMap()

    fun value(b: Book, c: ReportColumn): String = when (c) {
        ReportColumn.COVER -> thumbs[b.id]?.let { "<img class=\"c\" src=\"$it\">" }.orEmpty()
        ReportColumn.AUTHOR -> esc(b.author)
        ReportColumn.ISBN -> esc(b.isbn)
        ReportColumn.PUBLISHER -> esc(b.publisher)
        ReportColumn.YEAR -> b.year?.toString().orEmpty()
        ReportColumn.PAGES -> b.pages?.toString().orEmpty()
        ReportColumn.PRICE -> b.price?.let { currency.format(it) }.orEmpty()
        ReportColumn.GENRE -> esc(b.genre)
        ReportColumn.STATUS -> esc(b.readStatus.label)
        ReportColumn.RATING -> stars(b.rating)
        ReportColumn.DESCRIPTION -> esc(b.description)
        ReportColumn.NOTES -> esc(b.notes)
    }

    val summary = buildList {
        add("${numbers.format(books.size)} ${if (books.size == 1) "book" else "books"}")
        if (ReportColumn.PAGES in cols) add("${numbers.format(books.sumOf { (it.pages ?: 0).toLong() })} pages")
        if (ReportColumn.PRICE in cols) {
            val priced = books.mapNotNull { it.price }
            if (priced.isNotEmpty()) add("total value ${currency.format(priced.sum())}")
        }
    }.joinToString(" · ")
    val date = DateFormat.getDateInstance(DateFormat.LONG).format(Date())

    val html = StringBuilder()
    html.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>").append(CSS).append("</style></head><body>")
    html.append("<h1>").append(esc(prefs.title)).append("</h1>")
    html.append("<div class=\"sub\">").append(esc(date)).append(" · ").append(esc(summary)).append("</div>")

    when (prefs.layout) {
        ReportLayout.TABLE -> {
            html.append("<table><thead><tr>")
            if (ReportColumn.COVER in cols) html.append("<th class=\"cv\"></th>")
            html.append("<th>Title</th>")
            for (c in cols.filter { it != ReportColumn.COVER }) {
                val cls = when (c) {
                    ReportColumn.YEAR, ReportColumn.PAGES, ReportColumn.PRICE -> " class=\"num\""
                    else -> ""
                }
                html.append("<th$cls>").append(esc(c.label)).append("</th>")
            }
            html.append("</tr></thead><tbody>")
            for (b in books) {
                html.append("<tr>")
                if (ReportColumn.COVER in cols) html.append("<td class=\"cv\">").append(value(b, ReportColumn.COVER)).append("</td>")
                html.append("<td><b>").append(esc(b.title)).append("</b></td>")
                for (c in cols.filter { it != ReportColumn.COVER }) {
                    val cls = when (c) {
                        ReportColumn.YEAR, ReportColumn.PAGES, ReportColumn.PRICE -> " class=\"num\""
                        ReportColumn.DESCRIPTION, ReportColumn.NOTES -> " class=\"long\""
                        else -> ""
                    }
                    html.append("<td$cls>").append(value(b, c)).append("</td>")
                }
                html.append("</tr>")
            }
            html.append("</tbody></table>")
        }
        ReportLayout.DETAILED -> {
            for (b in books) {
                html.append("<div class=\"book\">")
                if (ReportColumn.COVER in cols) {
                    html.append("<div class=\"cvbox\">").append(thumbs[b.id]?.let { "<img src=\"$it\">" }.orEmpty()).append("</div>")
                }
                html.append("<div class=\"info\"><div class=\"t\">").append(esc(b.title)).append("</div>")
                for (c in cols) {
                    if (c == ReportColumn.COVER) continue
                    val v = value(b, c)
                    if (v.isEmpty()) continue
                    if (c == ReportColumn.DESCRIPTION || c == ReportColumn.NOTES) {
                        html.append("<div class=\"p\"><span class=\"l\">").append(esc(c.label)).append("</span><br>").append(v).append("</div>")
                    } else {
                        html.append("<div class=\"f\"><span class=\"l\">").append(esc(c.label)).append(":</span> ").append(v).append("</div>")
                    }
                }
                html.append("</div></div>")
            }
        }
    }
    html.append("</body></html>")
    html.toString()
}
