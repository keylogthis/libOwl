package org.thebottle.libOwl.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ReadStatus(val label: String) {
    UNREAD("Unread"), READING("Reading"), FINISHED("Finished"), READ_AGAIN("Read Again")
}

@Entity(tableName = "books", indices = [Index("isbn"), Index("title"), Index("author")])
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isbn: String = "",
    val title: String,
    val author: String = "",
    val publisher: String = "",
    val year: Int? = null,
    val genre: String = "",
    val notes: String = "",
    val readStatus: ReadStatus = ReadStatus.UNREAD,
    val dateAdded: Long = System.currentTimeMillis(),
    val pages: Int? = null,
    val price: Double? = null,
    /** 0 = not rated; stored in half-star units 1..10 (e.g. 7 = 3.5 stars) */
    @ColumnInfo(defaultValue = "0") val rating: Int = 0,
    @ColumnInfo(defaultValue = "''") val description: String = "",
    /** File name inside the app's covers directory (not a full path). */
    val coverFile: String? = null,
    /** True for books saved from a scan without details; cleared when saved from the edit screen. */
    @ColumnInfo(defaultValue = "0") val needsAttention: Boolean = false
)

/** Loan tracking (used in step 5). */
@Entity(
    tableName = "loans",
    foreignKeys = [ForeignKey(Book::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId")]
)
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val borrower: String,
    val dateOut: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val returnedDate: Long? = null
)
