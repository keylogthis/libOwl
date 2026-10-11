package org.thebottle.libOwl

import android.app.Application
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.thebottle.libOwl.data.AppDatabase
import org.thebottle.libOwl.data.BackupManager
import org.thebottle.libOwl.data.BookLookup
import org.thebottle.libOwl.data.BookRepository
import org.thebottle.libOwl.data.CoverStore
import org.thebottle.libOwl.data.LoanReminders
import org.thebottle.libOwl.data.SettingsRepository

class BookApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: BookRepository by lazy { BookRepository(database) }
    val covers: CoverStore by lazy { CoverStore(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val lookup: BookLookup by lazy { BookLookup() }
    val backup: BackupManager by lazy { BackupManager(this, database, covers, settings) }

    override fun onCreate() {
        super.onCreate()
        // Make sure the daily reminder check matches the saved setting (also after a restore).
        MainScope().launch {
            LoanReminders.sync(this@BookApp, settings.settings.first().loans.remindersEnabled)
        }
    }
}
