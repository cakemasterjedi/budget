package com.gigbudget.app

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.gigbudget.app.data.AutoBackup
import com.gigbudget.app.data.Backup
import com.gigbudget.app.data.Income
import com.gigbudget.app.data.IncomeSources
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoBackupTest {
    private lateinit var app: BudgetApp
    private lateinit var file: File

    @Before fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        file = File(app.cacheDir, "StackIt-auto-backup.json").apply { delete() }
        app.db.incomeDao().deleteAll()
        app.db.incomeDao().insert(Income(source = IncomeSources.DOORDASH, amountCents = 8_888, date = 1))
        app.settings.update { it.copy(autoBackupUri = Uri.fromFile(file).toString(), autoBackupName = "StackIt-auto-backup.json", lastAutoBackupAt = 0, autoBackupError = "") }
    }

    @Test fun writesTheConnectedFile() = runBlocking {
        assertTrue(AutoBackup.run(app).isSuccess)
        val json = JSONObject(file.readText())
        assertEquals(8_888L, json.getJSONArray("income").getJSONObject(0).getLong("amountCents"))
        assertTrue(app.settings.state.value.lastAutoBackupAt > 0)
        assertEquals("", app.settings.state.value.autoBackupError)

        // And it restores.
        app.db.incomeDao().deleteAll()
        Backup.restore(app.db, app.settings, file.readText())
        assertEquals(8_888L, app.db.incomeDao().list().single().amountCents)
    }

    @Test fun aBrokenFileShowsReconnect() = runBlocking {
        app.settings.update { it.copy(autoBackupUri = Uri.fromFile(File(app.cacheDir, "missing-dir/x.json")).toString()) }
        assertFalse(AutoBackup.run(app).isSuccess)
        assertTrue(app.settings.state.value.autoBackupError.contains("Reconnect"))
    }

    @Test fun onlyRunsWhenStale() = runBlocking {
        app.settings.update { it.copy(lastAutoBackupAt = System.currentTimeMillis()) }
        AutoBackup.runIfStale(app)
        assertFalse(file.exists())
        app.settings.update { it.copy(lastAutoBackupAt = System.currentTimeMillis() - 7 * 3_600_000L) }
        AutoBackup.runIfStale(app)
        assertTrue(file.exists())
    }

    @Test fun driveConnectionStaysOnThisPhone() = runBlocking {
        val exported = JSONObject(Backup.export(app.db, app.settings)).getJSONObject("settings")
        assertFalse(exported.has("autoBackupUri"))
        // Restoring a backup from another phone keeps this phone's Drive connection.
        val before = app.settings.state.value.autoBackupUri
        Backup.restore(app.db, app.settings, Backup.export(app.db, app.settings))
        assertEquals(before, app.settings.state.value.autoBackupUri)
    }
}
