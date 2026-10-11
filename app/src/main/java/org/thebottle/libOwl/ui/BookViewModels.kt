package org.thebottle.libOwl.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import org.thebottle.libOwl.data.Book
import org.thebottle.libOwl.data.BookInfo
import org.thebottle.libOwl.data.BookLookup
import org.thebottle.libOwl.data.BookRepository
import org.thebottle.libOwl.data.Isbn
import org.thebottle.libOwl.data.Loan
import org.thebottle.libOwl.data.LookupResult
import org.thebottle.libOwl.data.UNKNOWN_TITLE_PREFIX
import org.thebottle.libOwl.data.CoverStore
import org.thebottle.libOwl.data.ReadStatus
import java.util.Locale

data class BookListState(
    /** Books after search, filters and sorting. */
    val books: List<Book>,
    /** Every book in the library, sorted like the list, ignoring search and filters (used by reports). */
    val allBooks: List<Book>,
    val totalCount: Int,
    val genres: List<String>,
    /** Current loan for each loaned book, by book id. */
    val loans: Map<Long, Loan> = emptyMap()
)

class BookListViewModel(private val repo: BookRepository) : ViewModel() {
    var query by mutableStateOf("")
        private set
    private val _filters = MutableStateFlow(BookFilters())
    private val _sort = MutableStateFlow(SortSpec())
    val filters: StateFlow<BookFilters> = _filters.asStateFlow()
    val sort: StateFlow<SortSpec> = _sort.asStateFlow()

    val state: StateFlow<BookListState?> =
        combine(repo.books, snapshotFlow { query }, _filters, _sort, repo.activeLoans) { all, q, f, s, loans ->
            val loanMap = loans.associateBy { it.bookId }
            BookListState(
                books = all.searchFilterSort(q, f, s).let { list ->
                    if (f.onLoanOnly) list.filter { it.id in loanMap } else list
                },
                loans = loanMap,
                allBooks = all.searchFilterSort("", BookFilters(), s),
                totalCount = all.size,
                genres = all.distinctValues { it.genre }
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Applies the same change to several books (multi-select editing). */
    fun editBooks(books: List<Book>, transform: (Book) -> Book) {
        viewModelScope.launch { repo.updateAll(books, transform) }
    }

    /** Lends each of the given books to [borrower]. */
    fun lendBooks(books: List<Book>, borrower: String, dueDate: Long?) {
        viewModelScope.launch { books.forEach { repo.lend(it.id, borrower, dueDate) } }
    }

    /** Names of past borrowers, newest first (for suggestions). */
    val borrowers: StateFlow<List<String>> =
        repo.borrowers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Deletes the given books and their stored cover images (their loans go with them). */
    fun deleteBooks(books: List<Book>, covers: CoverStore) {
        viewModelScope.launch {
            repo.deleteAll(books)
            books.forEach { b -> b.coverFile?.let { covers.delete(it) } }
        }
    }

    fun onQueryChange(q: String) { query = q }
    fun updateFilters(transform: (BookFilters) -> BookFilters) = _filters.update(transform)
    fun clearFilters() { _filters.value = BookFilters() }
    fun clearAll() { query = ""; clearFilters() }

    fun setSortField(field: SortField) =
        _sort.update { if (it.field == field) it else SortSpec(field, field.defaultAscending) }

    fun toggleSortDirection() = _sort.update { it.copy(ascending = !it.ascending) }

    companion object {
        fun factory(repo: BookRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { BookListViewModel(repo) } }
    }
}

data class BookForm(
    val isbn: String = "",
    val title: String = "",
    val author: String = "",
    val publisher: String = "",
    val year: String = "",
    val pages: String = "",
    val price: String = "",
    val genre: String = "",
    val description: String = "",
    val notes: String = "",
    val rating: Int = 0,
    val readStatus: ReadStatus = ReadStatus.UNREAD,
    val coverFile: String? = null
)

class BookEditViewModel(
    private val repo: BookRepository,
    private val covers: CoverStore,
    private val lookup: BookLookup,
    private val bookId: Long,
    initialIsbn: String = "",
    autoLookup: Boolean = false
) : ViewModel() {
    var form by mutableStateOf(BookForm(isbn = initialIsbn))
        private set
    var loaded by mutableStateOf(bookId == 0L)
        private set
    var lookingUp by mutableStateOf(false)
        private set
    var lookupStatus by mutableStateOf<String?>(null)
        private set
    private var existing: Book? = null
    /** The cover file currently stored in the database for this book (if any). */
    private var persistedCover: String? = null

    /** Loans of this book, newest first (empty for a book that isn't saved yet). */
    val loans: StateFlow<List<Loan>> =
        (if (bookId == 0L) kotlinx.coroutines.flow.flowOf(emptyList<Loan>()) else repo.loansFor(bookId))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val borrowers: StateFlow<List<String>> =
        repo.borrowers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun lend(borrower: String, dueDate: Long?) { viewModelScope.launch { repo.lend(bookId, borrower, dueDate) } }
    fun updateLoan(loan: Loan) { viewModelScope.launch { repo.updateLoan(loan) } }
    fun markReturned(loan: Loan) { viewModelScope.launch { repo.markReturned(loan) } }
    fun deleteLoan(loan: Loan) { viewModelScope.launch { repo.deleteLoan(loan) } }

    val isNew get() = bookId == 0L
    val canSave get() = form.title.isNotBlank()

    init {
        if (bookId != 0L) {
            viewModelScope.launch {
                repo.get(bookId)?.let { b ->
                    existing = b
                    persistedCover = b.coverFile
                    form = BookForm(
                        isbn = b.isbn, title = b.title, author = b.author, publisher = b.publisher,
                        year = b.year?.toString() ?: "", pages = b.pages?.toString() ?: "",
                        price = b.price?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                        genre = b.genre, description = b.description, notes = b.notes,
                        rating = b.rating, readStatus = b.readStatus, coverFile = b.coverFile
                    )
                }
                loaded = true
            }
        } else if (autoLookup && initialIsbn.isNotBlank()) {
            lookupIsbn()
        }
    }

    fun update(transform: (BookForm) -> BookForm) { form = transform(form) }

    fun setCover(name: String?) {
        // Discard a previously picked (but never saved) cover being replaced.
        val prev = form.coverFile
        if (prev != null && prev != persistedCover) covers.delete(prev)
        form = form.copy(coverFile = name)
    }

    fun importCover(uri: Uri) {
        viewModelScope.launch { covers.import(uri)?.let { setCover(it) } }
    }

    /** Looks the ISBN up online and fills in any fields that are still empty. */
    fun lookupIsbn() {
        if (lookingUp) return
        val isbn = Isbn.normalize(form.isbn)
        if (isbn == null) {
            lookupStatus = "Enter a valid ISBN first."
            return
        }
        viewModelScope.launch {
            lookingUp = true
            lookupStatus = null
            lookupStatus = when (val r = lookup.lookup(isbn)) {
                is LookupResult.Found -> {
                    applyInfo(r.info)
                    "Details filled in. Check them, then save."
                }
                LookupResult.NotFound -> "No details found for this ISBN."
                is LookupResult.Error -> "Couldn't reach the lookup service (${r.detail})."
            }
            lookingUp = false
        }
    }

    private suspend fun applyInfo(info: BookInfo) {
        val f = form
        fun fill(current: String, new: String?): String =
            if (current.isBlank() && !new.isNullOrBlank()) new else current

        val titleIsPlaceholder = f.title.isBlank() || f.title.startsWith(UNKNOWN_TITLE_PREFIX)
        form = f.copy(
            isbn = info.isbn,
            title = if (titleIsPlaceholder) info.title else f.title,
            author = fill(f.author, info.author),
            publisher = fill(f.publisher, info.publisher),
            year = fill(f.year, info.year?.toString()),
            pages = fill(f.pages, info.pages?.toString()),
            genre = fill(f.genre, info.genre),
            description = fill(f.description, info.description)
        )
        if (form.coverFile == null && info.coverUrls.isNotEmpty()) {
            covers.importUrl(info.coverUrls)?.let { name ->
                if (form.coverFile == null) setCover(name) else covers.delete(name)
            }
        }
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val f = form
        val base = existing ?: Book(title = f.title.trim())
        repo.save(
            base.copy(
                isbn = Isbn.normalize(f.isbn) ?: f.isbn.trim(), title = f.title.trim(), author = f.author.trim(),
                publisher = f.publisher.trim(), year = f.year.trim().toIntOrNull(),
                pages = f.pages.trim().toIntOrNull(),
                price = f.price.trim().replace(',', '.').toDoubleOrNull(),
                genre = f.genre.trim(), description = f.description.trim(), notes = f.notes.trim(),
                rating = f.rating, readStatus = f.readStatus, coverFile = f.coverFile,
                needsAttention = false
            )
        )
        persistedCover?.let { if (it != f.coverFile) covers.delete(it) }
        persistedCover = f.coverFile
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        existing?.let { repo.delete(it) }
        persistedCover?.let { covers.delete(it) }
        persistedCover = null
        form = form.copy(coverFile = null)
        onDone()
    }

    override fun onCleared() {
        // Left the screen without saving: remove any newly picked cover that was never stored.
        form.coverFile?.let { if (it != persistedCover) covers.delete(it) }
    }

    companion object {
        fun factory(
            repo: BookRepository,
            covers: CoverStore,
            lookup: BookLookup,
            id: Long,
            isbn: String = "",
            autoLookup: Boolean = false
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { BookEditViewModel(repo, covers, lookup, id, isbn, autoLookup) } }
    }
}
