package com.gigbudget.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gigbudget.app.autoimport.KnownApps

@Database(
    entities = [Income::class, Expense::class, SavingsGoal::class, WatchedApp::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun incomeDao(): IncomeDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun goalDao(): GoalDao
    abstract fun watchedAppDao(): WatchedAppDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "gigbudget.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        KnownApps.defaults.forEach { app ->
                            db.execSQL(
                                "INSERT OR IGNORE INTO watched_apps (packageName, label, role, lastSeen, lastSample) VALUES (?, ?, ?, 0, '')",
                                arrayOf(app.packageName, app.label, app.role),
                            )
                        }
                    }
                })
                .build()
    }
}
