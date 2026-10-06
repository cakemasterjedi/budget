package com.gigbudget.app

import android.app.Application
import com.gigbudget.app.data.AppDatabase
import com.gigbudget.app.data.SettingsStore

class BudgetApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.build(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
}
