package com.gigbudget.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.gigbudget.app.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun migrate1To2KeepsDataAndSwitchesBanksToMoneyInAndOut() {
        helper.createDatabase("test.db", 1).apply {
            execSQL("INSERT INTO income (source, amountCents, date, note, auto) VALUES ('DoorDash', 4500, 1, '', 0)")
            execSQL("INSERT INTO watched_apps VALUES ('com.onedebit.chime', 'Chime', 'SPENDING', 0, '')")
            execSQL("INSERT INTO watched_apps VALUES ('com.payfare.doordash', 'DasherDirect', 'IGNORE', 0, '')")
            execSQL("INSERT INTO watched_apps VALUES ('com.example.other', 'Other', 'IGNORE', 5, 'x')")
            close()
        }
        val db = helper.runMigrationsAndValidate("test.db", 2, true, AppDatabase.MIGRATION_1_2)

        db.query("SELECT amountCents, sourcePackage FROM income").use {
            it.moveToFirst()
            assertEquals(4500L, it.getLong(0))
            assertEquals("", it.getString(1))
        }
        fun role(pkg: String) = db.query("SELECT role FROM watched_apps WHERE packageName = ?", arrayOf(pkg)).use {
            it.moveToFirst(); it.getString(0)
        }
        assertEquals("BANK", role("com.onedebit.chime"))
        assertEquals("BANK", role("com.payfare.doordash"))
        assertEquals("IGNORE", role("com.example.other"))
    }

    @Test fun migrate2To3AddsDebtsAndBills() {
        helper.createDatabase("test3.db", 2).apply {
            execSQL("INSERT INTO expenses (category, amountCents, date, note, auto, sourceApp) VALUES ('Gas', 1200, 1, '', 0, '')")
            close()
        }
        val db = helper.runMigrationsAndValidate("test3.db", 3, true, AppDatabase.MIGRATION_2_3)
        db.query("SELECT amountCents, goalId, debtId, billId FROM expenses").use {
            it.moveToFirst()
            assertEquals(1200L, it.getLong(0))
            assertEquals(true, it.isNull(1) && it.isNull(2) && it.isNull(3))
        }
        db.execSQL("INSERT INTO debts (name, balanceCents, minPaymentCents, apr) VALUES ('Card', 50000, 2500, 24.9)")
        db.execSQL("INSERT INTO bills (name, amountCents, dueDay, category) VALUES ('Rent', 90000, 1, 'Rent')")
    }

    @Test fun migrate3To4AddsGoalCategory() {
        helper.createDatabase("test4.db", 3).apply {
            execSQL("INSERT INTO goals (name, targetCents, savedCents) VALUES ('Tires', 60000, 1000)")
            close()
        }
        val db = helper.runMigrationsAndValidate("test4.db", 4, true, AppDatabase.MIGRATION_3_4)
        db.query("SELECT name, category FROM goals").use {
            it.moveToFirst()
            assertEquals("Tires", it.getString(0))
            assertEquals("OTHER", it.getString(1))
        }
    }

    /** Someone on the very first version updating straight to today's app keeps everything. */
    @Test fun upgradeFromFirstVersionKeepsData() {
        helper.createDatabase("first.db", 1).apply {
            execSQL("INSERT INTO income (source, amountCents, date, note, auto) VALUES ('DoorDash', 4500, 1, 'first dash', 0)")
            execSQL("INSERT INTO expenses (category, amountCents, date, note, auto, sourceApp) VALUES ('Preroll', 1500, 2, '', 0, '')")
            execSQL("INSERT INTO goals (name, targetCents, savedCents) VALUES ('Tires', 60000, 1000)")
            close()
        }
        val db = helper.runMigrationsAndValidate(
            "first.db", 4, true, AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4,
        )
        db.query("SELECT note FROM income").use { it.moveToFirst(); assertEquals("first dash", it.getString(0)) }
        db.query("SELECT category, amountCents FROM expenses").use { it.moveToFirst(); assertEquals("Preroll", it.getString(0)); assertEquals(1500L, it.getLong(1)) }
        db.query("SELECT name, savedCents FROM goals").use { it.moveToFirst(); assertEquals("Tires", it.getString(0)); assertEquals(1000L, it.getLong(1)) }
    }
}
