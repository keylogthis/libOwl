package org.thebottle.libOwl.data

/** Optional properties a report can show. The title is always included. */
enum class ReportColumn(val label: String) {
    COVER("Cover"),
    AUTHOR("Author"),
    ISBN("ISBN"),
    PUBLISHER("Publisher"),
    YEAR("Year"),
    PAGES("Pages"),
    PRICE("Price"),
    GENRE("Genre"),
    STATUS("Status"),
    RATING("Rating"),
    DESCRIPTION("Description"),
    NOTES("My notes")
}

enum class ReportLayout(val label: String) {
    /** One row per book, one column per property. */
    TABLE("Table"),
    /** One block per book with labelled lines. */
    DETAILED("Detailed")
}

data class ReportPrefs(
    val columns: Set<ReportColumn> = DEFAULT_COLUMNS,
    val layout: ReportLayout = ReportLayout.TABLE,
    val title: String = "My Library"
) {
    companion object {
        val DEFAULT_COLUMNS = setOf(
            ReportColumn.AUTHOR, ReportColumn.YEAR, ReportColumn.PAGES, ReportColumn.STATUS, ReportColumn.RATING
        )
    }
}
