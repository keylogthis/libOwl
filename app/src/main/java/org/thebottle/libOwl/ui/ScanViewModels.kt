package org.thebottle.libOwl.ui

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.thebottle.libOwl.data.Book
import org.thebottle.libOwl.data.BookLookup
import org.thebottle.libOwl.data.BookRepository
import org.thebottle.libOwl.data.CoverStore
import org.thebottle.libOwl.data.Isbn
import org.thebottle.libOwl.data.LookupResult
import org.thebottle.libOwl.data.placeholderBook
import org.thebottle.libOwl.data.toBook

sealed interface ConfirmState {
    data object Scanning : ConfirmState
    data class Looking(val isbn: String) : ConfirmState
    data class Result(val isbn: String, val outcome: LookupResult, val existing: Book?) : ConfirmState
}

/**
 * Shared by "Scan" and "Multi-Scan". One barcode at a time: scan, look up, then the user decides
 * (save / add / discard). Nothing is ever saved without the user choosing to.
 */
class ConfirmScanViewModel(
    private val repo: BookRepository,
    private val covers: CoverStore,
    private val lookup: BookLookup
) : ViewModel() {
    var state by mutableStateOf<ConfirmState>(ConfirmState.Scanning)
        private set
    var saving by mutableStateOf(false)
        private set

    /** Books added during this scanning session (used by Multi-Scan's counter). */
    var addedCount by mutableStateOf(0)
        private set
    /** Of those, how many were added with only an ISBN. */
    var needsDetailsCount by mutableStateOf(0)
        private set
    /** Bumped each time a barcode is accepted (for the beep). */
    var scanCount by mutableStateOf(0)
        private set

    private var job: Job? = null
    private var lastIsbn: String? = null
    private var ignoreUntil = 0L

    /** Keeps the same barcode from re-triggering while it is still in front of the camera. */
    private fun cooldown(isbn: String) {
        lastIsbn = isbn
        ignoreUntil = SystemClock.elapsedRealtime() + 2500
    }

    fun onBarcode(raw: String) {
        if (state !is ConfirmState.Scanning) return
        val isbn = Isbn.normalize(raw) ?: return
        val now = SystemClock.elapsedRealtime()
        if (isbn == lastIsbn && now < ignoreUntil) {
            ignoreUntil = now + 1500
            return
        }
        state = ConfirmState.Looking(isbn)
        scanCount++
        job = viewModelScope.launch {
            val existing = repo.findByIsbn(isbn)
            val outcome = lookup.lookup(isbn)
            state = ConfirmState.Result(isbn, outcome, existing)
        }
    }

    private fun currentIsbn(): String? = when (val s = state) {
        is ConfirmState.Looking -> s.isbn
        is ConfirmState.Result -> s.isbn
        ConfirmState.Scanning -> null
    }

    /** Cancel / discard: back to scanning without saving. */
    fun dismiss() {
        job?.cancel()
        currentIsbn()?.let { cooldown(it) }
        state = ConfirmState.Scanning
    }

    /** Saves the looked-up book (with its cover), then calls [onSaved]. */
    fun save(onSaved: () -> Unit) {
        val r = state as? ConfirmState.Result ?: return
        val found = r.outcome as? LookupResult.Found ?: return
        if (saving) return
        viewModelScope.launch {
            saving = true
            try {
                val cover = covers.importUrl(found.info.coverUrls)
                repo.save(found.info.toBook(cover))
                addedCount++
            } finally {
                saving = false
            }
            cooldown(r.isbn)
            state = ConfirmState.Scanning
            onSaved()
        }
    }

    /** For books the lookup couldn't find: keep just the ISBN (flagged "needs details"), then call [onSaved]. */
    fun saveIsbnOnly(onSaved: () -> Unit) {
        val r = state as? ConfirmState.Result ?: return
        if (saving) return
        viewModelScope.launch {
            saving = true
            try {
                repo.save(placeholderBook(r.isbn))
                addedCount++
                needsDetailsCount++
            } finally {
                saving = false
            }
            cooldown(r.isbn)
            state = ConfirmState.Scanning
            onSaved()
        }
    }

    companion object {
        fun factory(repo: BookRepository, covers: CoverStore, lookup: BookLookup): ViewModelProvider.Factory =
            viewModelFactory { initializer { ConfirmScanViewModel(repo, covers, lookup) } }
    }
}
