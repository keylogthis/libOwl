package org.thebottle.libOwl.data

import android.content.Context
import android.content.Intent
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backs up and restores the whole library as one zip:
 *   manifest.json   what made it (app + database version)
 *   libowl.db       the database
 *   settings.json   app settings (not the backup folder, which is specific to this phone)
 *   covers folder   cover images
 */
class BackupManager(
    context: Context,
    private val database: AppDatabase,
    private val covers: CoverStore,
    private val settings: SettingsRepository
) {
    private val appContext = context.applicationContext
    private val dbFile: File get() = appContext.getDatabasePath(DB_NAME)

    sealed interface Outcome {
        data class Ok(val message: String) : Outcome
        /** [restart] is true when the database was already closed, so the app must restart to recover. */
        data class Failed(val message: String, val restart: Boolean = false) : Outcome
    }

    private class RestoreProblem(message: String) : Exception(message)

    // ------------------------------------------------------------------ folder

    /** Remembers the chosen folder (and keeps access to it across restarts). */
    suspend fun setFolder(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            settings.setBackupFolder(uri.toString(), DocumentFile.fromTreeUri(appContext, uri)?.name)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------ backup

    suspend fun backupNow(): Outcome = withContext(Dispatchers.IO) {
        val folderUri = settings.settings.first().backupFolder
            ?: return@withContext Outcome.Failed("Choose a backup location first.")
        val folder = DocumentFile.fromTreeUri(appContext, Uri.parse(folderUri))
        if (folder == null || !folder.canWrite()) {
            return@withContext Outcome.Failed("Can't write to the backup location. Choose it again.")
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val target = folder.createFile("application/zip", "libOwl-backup-$stamp.zip")
            ?: return@withContext Outcome.Failed("Couldn't create the backup file.")
        try {
            writeBackup(target.uri)
            settings.setLastBackup(System.currentTimeMillis())
            Outcome.Ok("Backup saved: ${target.name ?: "libOwl-backup-$stamp.zip"}")
        } catch (e: Exception) {
            runCatching { target.delete() }
            Outcome.Failed("Backup failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun writeBackup(uri: Uri) {
        // Fold the write-ahead log into the main file so the copied database is complete.
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val dbVersion = database.openHelper.readableDatabase.version
        val appVersion = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        }.getOrNull().orEmpty()

        val manifest = JSONObject()
            .put("app", APP_ID)
            .put("format", 1)
            .put("dbVersion", dbVersion)
            .put("appVersion", appVersion)
            .put("created", System.currentTimeMillis())
            .toString()

        val currency = settings.settings.first().currency.name
        val report = settings.settings.first().report
        val loanPrefs = settings.settings.first().loans
        val settingsJson = JSONObject()
            .put("currency", currency)
            .put("accent", settings.settings.first().accent.name)
            .put("reportTitle", report.title)
            .put("reportLayout", report.layout.name)
            .put("reportColumns", JSONArray(report.columns.map { it.name }))
            .put("loanReminders", loanPrefs.remindersEnabled)
            .put("loanRemindDays", loanPrefs.remindDaysBefore)
            .put("loanDefaultDays", loanPrefs.defaultLoanDays)
            .toString()

        val out = appContext.contentResolver.openOutputStream(uri, "w")
            ?: throw IOException("Can't open the backup file")
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST)); zip.write(manifest.toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry(DB_NAME)); dbFile.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            zip.putNextEntry(ZipEntry(SETTINGS)); zip.write(settingsJson.toByteArray()); zip.closeEntry()
            covers.dir.listFiles()?.filter { it.isFile }?.forEach { f ->
                zip.putNextEntry(ZipEntry("$COVERS_DIR/${f.name}"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    // ----------------------------------------------------------------- restore

    suspend fun restore(uri: Uri): Outcome = withContext(Dispatchers.IO) {
        val work = File(appContext.cacheDir, "restore-${System.currentTimeMillis()}").apply { mkdirs() }
        var dbClosed = false
        try {
            val extracted = extract(uri, work)
            val currentVersion = database.openHelper.readableDatabase.version
            checkDatabase(extracted.db, currentVersion)

            // Everything checked out: from here on the database is replaced.
            database.close()
            dbClosed = true
            swapIn(extracted)

            extracted.settingsJson?.let { json ->
                val j = runCatching { JSONObject(json) }.getOrNull()
                if (j != null) {
                    j.optString("currency").takeIf { it.isNotEmpty() }
                        ?.let { settings.setCurrency(CurrencySymbol.fromName(it)) }
                    if (j.has("loanReminders")) {
                        settings.setLoanPrefs(
                            LoanPrefs(
                                remindersEnabled = j.optBoolean("loanReminders", false),
                                remindDaysBefore = j.optInt("loanRemindDays", 1),
                                defaultLoanDays = j.optInt("loanDefaultDays", 0)
                            )
                        )
                    }
                    j.optString("accent").takeIf { it.isNotEmpty() }
                        ?.let { settings.setAccent(AccentColor.fromName(it)) }
                    val arr = j.optJSONArray("reportColumns")
                    if (arr != null) {
                        val cols = (0 until arr.length())
                            .mapNotNull { i -> ReportColumn.entries.firstOrNull { it.name == arr.optString(i) } }
                            .toSet()
                        val layout = ReportLayout.entries.firstOrNull { it.name == j.optString("reportLayout") }
                            ?: ReportLayout.TABLE
                        val title = j.optString("reportTitle").ifEmpty { ReportPrefs().title }
                        settings.setReportPrefs(ReportPrefs(cols, layout, title))
                    }
                }
            }
            Outcome.Ok("Restored")
        } catch (e: RestoreProblem) {
            Outcome.Failed(e.message ?: "Couldn't restore that file.", restart = dbClosed)
        } catch (e: Exception) {
            Outcome.Failed("Restore failed: ${e.message ?: e.javaClass.simpleName}", restart = dbClosed)
        } finally {
            work.deleteRecursively()
        }
    }

    private class Extracted(val db: File, val coversDir: File, val settingsJson: String?)

    private fun extract(uri: Uri, work: File): Extracted {
        val dbOut = File(work, DB_NAME)
        val coversOut = File(work, COVERS_DIR).apply { mkdirs() }
        var manifest: String? = null
        var settingsJson: String? = null
        val input = appContext.contentResolver.openInputStream(uri) ?: throw RestoreProblem("Can't open that file.")

        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        entry.isDirectory -> {}
                        name == MANIFEST -> manifest = zip.readBytes().toString(Charsets.UTF_8)
                        name == SETTINGS -> settingsJson = zip.readBytes().toString(Charsets.UTF_8)
                        name == DB_NAME -> dbOut.outputStream().use { zip.copyTo(it) }
                        name.startsWith("$COVERS_DIR/") -> {
                            val file = name.removePrefix("$COVERS_DIR/")
                            // Only plain file names: never trust paths inside a zip.
                            if (SAFE_NAME.matches(file)) File(coversOut, file).outputStream().use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: java.util.zip.ZipException) {
            throw RestoreProblem("That file isn't a valid backup.")
        }

        val m = try { JSONObject(manifest ?: throw RestoreProblem("That file isn't a libOwl backup.")) }
        catch (e: org.json.JSONException) { throw RestoreProblem("That file isn't a libOwl backup.") }
        if (m.optString("app") != APP_ID) throw RestoreProblem("That file isn't a libOwl backup.")
        if (!dbOut.exists() || dbOut.length() == 0L) throw RestoreProblem("This backup doesn't contain a library.")
        return Extracted(dbOut, coversOut, settingsJson)
    }

    private fun checkDatabase(file: File, maxVersion: Int) {
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw RestoreProblem("The backup's database can't be opened.")
        }
        try {
            if (DatabaseUtils.stringForQuery(db, "PRAGMA integrity_check", null) != "ok") {
                throw RestoreProblem("The backup's database is damaged.")
            }
            val hasBooks = DatabaseUtils.longForQuery(
                db, "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='books'", null
            ) > 0
            if (!hasBooks) throw RestoreProblem("This backup doesn't contain a library.")
            if (db.version > maxVersion) {
                throw RestoreProblem("This backup is from a newer version of libOwl. Update the app first.")
            }
            if (db.version < 1) throw RestoreProblem("This backup doesn't contain a library.")
        } finally {
            db.close()
        }
    }

    /** Replaces the database and covers, keeping the old ones until the swap succeeds so it can roll back. */
    private fun swapIn(extracted: Extracted) {
        val dbf = dbFile
        val oldDb = File(dbf.path + ".pre-restore")
        val coversDir = covers.dir
        val oldCovers = File(coversDir.parentFile, "covers.pre-restore")

        oldDb.delete()
        oldCovers.deleteRecursively()
        val hadDb = dbf.exists()
        if (hadDb) dbf.copyTo(oldDb, overwrite = true)

        try {
            File(dbf.path + "-wal").delete()
            File(dbf.path + "-shm").delete()
            extracted.db.copyTo(dbf, overwrite = true)

            if (coversDir.exists() && !coversDir.renameTo(oldCovers)) throw IOException("Couldn't replace the covers")
            coversDir.mkdirs()
            extracted.coversDir.listFiles()?.forEach { it.copyTo(File(coversDir, it.name), overwrite = true) }

            oldDb.delete()
            oldCovers.deleteRecursively()
        } catch (e: Exception) {
            if (hadDb) runCatching { oldDb.copyTo(dbf, overwrite = true) }
            coversDir.deleteRecursively()
            if (oldCovers.exists()) oldCovers.renameTo(coversDir) else coversDir.mkdirs()
            throw RestoreProblem("Restore failed and your previous library was kept.")
        }
    }

    /** Relaunches the app so it opens the restored database from scratch. */
    fun restartApp() {
        val intent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        appContext.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    private companion object {
        const val APP_ID = "libOwl"
        const val DB_NAME = "libowl.db"
        const val MANIFEST = "manifest.json"
        const val SETTINGS = "settings.json"
        const val COVERS_DIR = "covers"
        val SAFE_NAME = Regex("^[A-Za-z0-9._-]+$")
    }
}
