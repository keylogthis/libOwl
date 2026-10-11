package org.thebottle.libOwl.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

/** Currency symbol shown with prices. The enum name is what gets stored in settings. */
enum class CurrencySymbol(val symbol: String) {
    NONE(""),
    DOLLAR("$"),
    EURO("€"),
    POUND("£"),
    YEN("¥"),
    RUPEE("₹"),
    WON("₩");

    val summary: String get() = if (symbol.isEmpty()) "No symbol" else symbol

    fun format(amount: Double): String = symbol + String.format(Locale.US, "%.2f", amount)

    companion object {
        /** Also maps the per-currency names stored by earlier test builds (USD, CAD, ...). */
        fun fromName(name: String?): CurrencySymbol = when (name) {
            "NONE" -> NONE
            "EURO", "EUR" -> EURO
            "POUND", "GBP" -> POUND
            "YEN", "JPY", "CNY" -> YEN
            "RUPEE", "INR" -> RUPEE
            "WON", "KRW" -> WON
            else -> DOLLAR // DOLLAR, USD, CAD, AUD, NZD, MXN, anything unknown
        }
    }
}

/** Loan preferences. Reminders are off until the user turns them on (which also asks for notification permission). */
data class LoanPrefs(
    val remindersEnabled: Boolean = false,
    /** Notify this many days before the due date (0 = on the day). Overdue loans are always included. */
    val remindDaysBefore: Int = 1,
    /** Due date suggested when lending: this many days from today (0 = no due date). */
    val defaultLoanDays: Int = 0
)

/** Accent colour choices. Colours are ARGB values so this file needs no UI imports; the theme turns them into a colour scheme. */
enum class AccentColor(
    val label: String,
    val swatch: Long,
    val lightPrimary: Long,
    val lightContainer: Long,
    val onLightContainer: Long,
    val darkPrimary: Long,
    val darkContainer: Long,
    val onDarkContainer: Long
) {
    PURPLE("Purple", 0xFF6650A4, 0xFF6650A4, 0xFFEADDFF, 0xFF21005D, 0xFFD0BCFF, 0xFF4F378B, 0xFFEADDFF),
    BLUE("Blue", 0xFF1565C0, 0xFF1565C0, 0xFFD6E4FF, 0xFF001B3E, 0xFFA9C7FF, 0xFF0F4A8A, 0xFFD6E4FF),
    TEAL("Teal", 0xFF00695C, 0xFF00695C, 0xFFB2F0E4, 0xFF00201B, 0xFF71D8C7, 0xFF005046, 0xFFB2F0E4),
    GREEN("Green", 0xFF2E7D32, 0xFF2E7D32, 0xFFB8F0B5, 0xFF002106, 0xFF9DD49A, 0xFF1B5E20, 0xFFB8F0B5),
    ORANGE("Orange", 0xFFC2570A, 0xFFC2570A, 0xFFFFDCC5, 0xFF2F1500, 0xFFFFB787, 0xFF8A3A00, 0xFFFFDCC5),
    RED("Red", 0xFFC62828, 0xFFC62828, 0xFFFFDAD6, 0xFF410002, 0xFFFFB4AB, 0xFF93000A, 0xFFFFDAD6),
    PINK("Pink", 0xFFAD1457, 0xFFAD1457, 0xFFFFD9E3, 0xFF3E001D, 0xFFFFB0C9, 0xFF8E0F46, 0xFFFFD9E3),
    BROWN("Brown", 0xFF6D4C41, 0xFF6D4C41, 0xFFEFDCD3, 0xFF2A160E, 0xFFD7C0B5, 0xFF553A30, 0xFFEFDCD3);

    companion object {
        fun fromName(name: String?): AccentColor = entries.firstOrNull { it.name == name } ?: GREEN
    }
}

data class AppSettings(
    val currency: CurrencySymbol = CurrencySymbol.DOLLAR,
    val accent: AccentColor = AccentColor.GREEN,
    /** Tree URI of the folder chosen for backups (null = not set). */
    val backupFolder: String? = null,
    val backupFolderName: String? = null,
    /** Epoch millis of the last successful backup (null = never). */
    val lastBackup: Long? = null,
    val report: ReportPrefs = ReportPrefs(),
    val loans: LoanPrefs = LoanPrefs(),
    /** Borrower names (lowercase) hidden from the suggestion chips; their loan records are kept. */
    val hiddenBorrowers: Set<String> = emptySet()
)

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val currencyKey = stringPreferencesKey("currency")
    private val backupFolderKey = stringPreferencesKey("backup_folder")
    private val backupFolderNameKey = stringPreferencesKey("backup_folder_name")
    private val lastBackupKey = longPreferencesKey("last_backup")
    private val reportColumnsKey = stringSetPreferencesKey("report_columns")
    private val reportLayoutKey = stringPreferencesKey("report_layout")
    private val reportTitleKey = stringPreferencesKey("report_title")
    private val loanRemindersKey = booleanPreferencesKey("loan_reminders")
    private val loanRemindDaysKey = intPreferencesKey("loan_remind_days")
    private val accentKey = stringPreferencesKey("accent_color")
    private val hiddenBorrowersKey = stringSetPreferencesKey("hidden_borrowers")
    private val loanDefaultDaysKey = intPreferencesKey("loan_default_days")

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(
            currency = CurrencySymbol.fromName(prefs[currencyKey]),
            accent = AccentColor.fromName(prefs[accentKey]),
            backupFolder = prefs[backupFolderKey],
            backupFolderName = prefs[backupFolderNameKey],
            lastBackup = prefs[lastBackupKey],
            report = ReportPrefs(
                columns = prefs[reportColumnsKey]
                    ?.mapNotNull { n -> ReportColumn.entries.firstOrNull { it.name == n } }
                    ?.toSet() ?: ReportPrefs.DEFAULT_COLUMNS,
                layout = ReportLayout.entries.firstOrNull { it.name == prefs[reportLayoutKey] } ?: ReportLayout.TABLE,
                title = prefs[reportTitleKey] ?: ReportPrefs().title
            ),
            loans = LoanPrefs(
                remindersEnabled = prefs[loanRemindersKey] ?: false,
                remindDaysBefore = prefs[loanRemindDaysKey] ?: 1,
                defaultLoanDays = prefs[loanDefaultDaysKey] ?: 0
            ),
            hiddenBorrowers = prefs[hiddenBorrowersKey] ?: emptySet()
        )
    }

    suspend fun setCurrency(option: CurrencySymbol) {
        context.settingsStore.edit { it[currencyKey] = option.name }
    }

    suspend fun setBackupFolder(uri: String, name: String?) {
        context.settingsStore.edit {
            it[backupFolderKey] = uri
            if (name != null) it[backupFolderNameKey] = name else it.remove(backupFolderNameKey)
        }
    }

    suspend fun setLastBackup(millis: Long) {
        context.settingsStore.edit { it[lastBackupKey] = millis }
    }

    suspend fun setReportPrefs(report: ReportPrefs) {
        context.settingsStore.edit {
            it[reportColumnsKey] = report.columns.map { c -> c.name }.toSet()
            it[reportLayoutKey] = report.layout.name
            it[reportTitleKey] = report.title
        }
    }

    suspend fun setLoanPrefs(loans: LoanPrefs) {
        context.settingsStore.edit {
            it[loanRemindersKey] = loans.remindersEnabled
            it[loanRemindDaysKey] = loans.remindDaysBefore
            it[loanDefaultDaysKey] = loans.defaultLoanDays
        }
    }

    suspend fun setBorrowerHidden(name: String, hidden: Boolean) {
        val key = name.trim().lowercase()
        context.settingsStore.edit {
            val current = it[hiddenBorrowersKey] ?: emptySet()
            it[hiddenBorrowersKey] = if (hidden) current + key else current - key
        }
    }

    suspend fun setAccent(accent: AccentColor) {
        context.settingsStore.edit { it[accentKey] = accent.name }
    }
}
