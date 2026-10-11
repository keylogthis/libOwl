package org.thebottle.libOwl.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): Book?

    @Query("SELECT * FROM books WHERE isbn = :isbn LIMIT 1")
    suspend fun findByIsbn(isbn: String): Book?

    @Upsert
    suspend fun upsert(book: Book): Long

    @Delete
    suspend fun delete(book: Book)
}

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans WHERE returnedDate IS NULL ORDER BY dueDate")
    fun observeActive(): Flow<List<Loan>>

    @Query("SELECT * FROM loans ORDER BY dateOut DESC")
    fun observeAll(): Flow<List<Loan>>

    @Query("SELECT * FROM loans WHERE bookId = :bookId ORDER BY dateOut DESC")
    fun observeForBook(bookId: Long): Flow<List<Loan>>

    @Query("SELECT DISTINCT borrower FROM loans ORDER BY dateOut DESC")
    fun observeBorrowers(): Flow<List<String>>

    @Query("SELECT * FROM loans WHERE returnedDate IS NULL")
    suspend fun activeList(): List<Loan>

    @Insert
    suspend fun insert(loan: Loan): Long

    @Query("DELETE FROM loans WHERE returnedDate IS NOT NULL")
    suspend fun deleteReturned()

    @Update
    suspend fun update(loan: Loan)

    @Delete
    suspend fun delete(loan: Loan)
}
