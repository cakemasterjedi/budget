package com.gigbudget.app

import android.app.Application
import com.gigbudget.app.data.AppDatabase
import com.gigbudget.app.data.AutoBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.gigbudget.app.data.SettingsStore

class BudgetApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.build(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        // Load settings right away so custom categories are in effect everywhere, including the
        // notification listener, before any screen asks.
        settings
        if (settings.state.value.autoBackupUri.isNotEmpty()) AutoBackup.schedule(this)
    }

    /** For work that should finish even after the screen that started it closes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
