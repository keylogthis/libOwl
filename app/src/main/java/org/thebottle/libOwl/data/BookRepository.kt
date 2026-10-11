package org.thebottle.libOwl.data

import kotlinx.coroutines.flow.Flow

class BookRepository(private val db: AppDatabase) {
    val books: Flow<List<Book>> = db.bookDao().observeAll()
    suspend fun get(id: Long): Book? = db.bookDao().get(id)
    suspend fun findByIsbn(isbn: String): Book? = db.bookDao().findByIsbn(isbn)
    suspend fun save(book: Book) { db.bookDao().upsert(book) }
    suspend fun delete(book: Book) = db.bookDao().delete(book)
    suspend fun updateAll(books: List<Book>, transform: (Book) -> Book) =
        books.forEach { db.bookDao().upsert(transform(it)) }
    suspend fun setReadStatus(books: List<Book>, status: ReadStatus) =
        books.forEach { db.bookDao().upsert(it.copy(readStatus = status)) }
    suspend fun deleteAll(books: List<Book>) = books.forEach { db.bookDao().delete(it) }

    // ---- loans
    val activeLoans: Flow<List<Loan>> = db.loanDao().observeActive()
    val allLoans: Flow<List<Loan>> = db.loanDao().observeAll()
    val borrowers: Flow<List<String>> = db.loanDao().observeBorrowers()
    fun loansFor(bookId: Long): Flow<List<Loan>> = db.loanDao().observeForBook(bookId)

    suspend fun lend(bookId: Long, borrower: String, dueDate: Long?) {
        db.loanDao().insert(Loan(bookId = bookId, borrower = borrower.trim(), dueDate = dueDate))
    }

    suspend fun updateLoan(loan: Loan) = db.loanDao().update(loan)

    suspend fun markReturned(loan: Loan) =
        db.loanDao().update(loan.copy(returnedDate = System.currentTimeMillis()))

    suspend fun clearLoanHistory() = db.loanDao().deleteReturned()

    suspend fun deleteLoan(loan: Loan) = db.loanDao().delete(loan)
}
