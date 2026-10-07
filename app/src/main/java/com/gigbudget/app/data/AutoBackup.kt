package com.gigbudget.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gigbudget.app.BudgetApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Automatic backups to one file Niome picked once (normally in Google Drive, through Android's
 * file picker). The app keeps permission to that single file and overwrites it with a fresh
 * backup every day, and when she leaves the app if the last one is more than 6 hours old.
 * Drive uploads the new version itself, even if the phone was offline at the time.
 */
object AutoBackup {
    private const val WORK_NAME = "auto-backup"
    private val STALE_MS = TimeUnit.HOURS.toMillis(6)

    /** Connects [uri] (just created in the picker) and writes the first backup to it. */
    suspend fun connect(app: BudgetApp, uri: Uri): Result<Unit> {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            app.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            return Result.failure(IllegalStateException("That location doesn't allow automatic backups. Pick Google Drive."))
        }
        app.settings.update {
            it.copy(autoBackupUri = uri.toString(), autoBackupName = displayName(app, uri) ?: "Stack It backup", autoBackupError = "")
        }
        schedule(app)
        return run(app)
    }

    fun disconnect(app: BudgetApp) {
        val uri = app.settings.state.value.autoBackupUri
        if (uri.isNotEmpty()) {
            runCatching {
                app.contentResolver.releasePersistableUriPermission(
                    Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        app.settings.update { it.copy(autoBackupUri = "", autoBackupName = "", autoBackupError = "") }
        WorkManager.getInstance(app).cancelUniqueWork(WORK_NAME)
    }

    /** Writes a backup to the connected file now. Records success or the error in settings. */
    suspend fun run(app: BudgetApp): Result<Unit> {
        val target = app.settings.state.value.autoBackupUri.takeIf { it.isNotEmpty() } ?: return Result.success(Unit)
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val json = Backup.export(app.db, app.settings)
                val out = app.contentResolver.openOutputStream(Uri.parse(target), "wt")
                    ?: error("Couldn't open the backup file")
                out.use { it.write(json.toByteArray()) }
            }
        }
        app.settings.update {
            if (result.isSuccess) it.copy(lastAutoBackupAt = System.currentTimeMillis(), autoBackupError = "")
            else it.copy(autoBackupError = "Couldn't update the backup file. Reconnect Google Drive.")
        }
        return result
    }

    /** Backs up if connected and the last automatic backup is more than 6 hours old. */
    suspend fun runIfStale(app: BudgetApp) {
        val s = app.settings.state.value
        if (s.autoBackupUri.isNotEmpty() && System.currentTimeMillis() - s.lastAutoBackupAt > STALE_MS) run(app)
    }

    /** Daily backup job; harmless to call repeatedly (an existing schedule is kept). */
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<Worker>(1, TimeUnit.DAYS).build(),
        )
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val app = applicationContext as BudgetApp
            // A failure is recorded and shown in Settings; retrying every few minutes wouldn't help.
            run(app)
            return Result.success()
        }
    }
}
