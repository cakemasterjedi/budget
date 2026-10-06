package com.gigbudget.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gigbudget.app.autoimport.KnownApps

@Database(
    entities = [Income::class, Expense::class, SavingsGoal::class, WatchedApp::class, NotificationLog::class, Debt::class, Bill::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun incomeDao(): IncomeDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun goalDao(): GoalDao
    abstract fun watchedAppDao(): WatchedAppDao
    abstract fun notificationLogDao(): NotificationLogDao
    abstract fun debtDao(): DebtDao
    abstract fun billDao(): BillDao

    companion object {
        /** v2: notification log, income.sourcePackage, and banks switch from "Spending only" to "Money in & out". */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE income ADD COLUMN sourcePackage TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS notification_log (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "packageName TEXT NOT NULL, appLabel TEXT NOT NULL, title TEXT NOT NULL, text TEXT NOT NULL, " +
                        "postedAt INTEGER NOT NULL, amountCents INTEGER, outcome TEXT NOT NULL, detail TEXT NOT NULL, " +
                        "dedupeKey TEXT NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notification_log_dedupeKey ON notification_log (dedupeKey)")
                db.execSQL("UPDATE watched_apps SET role = 'BANK' WHERE role = 'SPENDING'")
                KnownApps.defaults.filter { it.role == Roles.BANK }.forEach { app ->
                    db.execSQL(
                        "UPDATE watched_apps SET role = 'BANK' WHERE packageName = ? AND role = 'IGNORE' AND lastSeen = 0",
                        arrayOf(app.packageName),
                    )
                }
            }
        }

        /** v3: debt tracker, monthly bills, and links from expenses to goals / debts / bills. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN goalId INTEGER")
                db.execSQL("ALTER TABLE expenses ADD COLUMN debtId INTEGER")
                db.execSQL("ALTER TABLE expenses ADD COLUMN billId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS debts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                        "balanceCents INTEGER NOT NULL, minPaymentCents INTEGER NOT NULL, apr REAL NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bills (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                        "amountCents INTEGER NOT NULL, dueDay INTEGER NOT NULL, category TEXT NOT NULL)"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "gigbudget.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
