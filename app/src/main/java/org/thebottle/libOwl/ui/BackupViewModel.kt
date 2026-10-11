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
import kotlinx.coroutines.launch
import org.thebottle.libOwl.data.BackupManager
import org.thebottle.libOwl.data.BackupManager.Outcome

class BackupViewModel(private val manager: BackupManager) : ViewModel() {
    /** Non-null while a backup or restore is running (shown as a progress dialog). */
    var busy by mutableStateOf<String?>(null)
        private set
    /** One-shot message for the snackbar. */
    var message by mutableStateOf<String?>(null)
        private set

    fun messageShown() { message = null }

    fun chooseFolder(uri: Uri) {
        viewModelScope.launch {
            if (!manager.setFolder(uri)) message = "Couldn't use that folder. Pick another."
        }
    }

    fun backupNow() {
        if (busy != null) return
        viewModelScope.launch {
            busy = "Backing up…"
            val result = manager.backupNow()
            busy = null
            message = when (result) {
                is Outcome.Ok -> result.message
                is Outcome.Failed -> result.message
            }
        }
    }

    fun restore(uri: Uri) {
        if (busy != null) return
        viewModelScope.launch {
            busy = "Restoring…"
            when (val result = manager.restore(uri)) {
                is Outcome.Ok -> manager.restartApp()
                is Outcome.Failed -> {
                    if (result.restart) manager.restartApp()
                    busy = null
                    message = result.message
                }
            }
        }
    }

    companion object {
        fun factory(manager: BackupManager): ViewModelProvider.Factory =
            viewModelFactory { initializer { BackupViewModel(manager) } }
    }
}
