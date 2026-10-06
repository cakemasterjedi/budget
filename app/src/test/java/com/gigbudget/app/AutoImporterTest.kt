package com.gigbudget.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gigbudget.app.autoimport.AutoImporter
import com.gigbudget.app.data.AppDatabase
import com.gigbudget.app.data.IncomeSources
import com.gigbudget.app.data.Outcomes
import com.gigbudget.app.data.Roles
import com.gigbudget.app.data.WatchedApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoImporterTest {
    private lateinit var db: AppDatabase
    private lateinit var importer: AutoImporter
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_790_000_000_000L

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        importer = AutoImporter(db)
        db.watchedAppDao().insertIgnore(WatchedApp("dasher", "DoorDash Dasher", Roles.DOORDASH))
        db.watchedAppDao().insertIgnore(WatchedApp("bank", "Chime", Roles.BANK))
    }

    @After fun tearDown() = db.close()

    @Test fun bankTracksIncomeAndSpending() = runBlocking {
        importer.handle("bank", "Chime", "Deposit", "Direct deposit of $300.00 from DOORDASH INC", now)
        importer.handle("bank", "Chime", "Purchase", "You spent $12.00 at Shell", now + 1)
        val income = db.incomeDao().all().first()
        assertEquals(1, income.size)
        assertEquals(IncomeSources.DOORDASH, income[0].source)
        assertEquals(30000L, income[0].amountCents)
        assertEquals(1200L, db.expenseDao().all().first().single().amountCents)
        assertEquals(listOf(Outcomes.EXPENSE, Outcomes.INCOME), db.notificationLogDao().recent().first().map { it.outcome })
    }

    @Test fun bankDepositSkippedWhenGigAppAlreadyLoggedPay() = runBlocking {
        importer.handle("dasher", "DoorDash Dasher", "Dash complete", "You earned $80.00", now - 3 * day)
        importer.handle("bank", "Chime", "Deposit", "Direct deposit of $80.00 from DOORDASH INC", now)
        assertEquals(1, db.incomeDao().all().first().size)
        val log = db.notificationLogDao().recent().first()
        assertEquals(Outcomes.SKIPPED, log.first().outcome)
        assertEquals("Already counted from your DoorDash pay app", log.first().detail)
    }

    @Test fun bankDepositCountedWhenGigAppQuiet() = runBlocking {
        importer.handle("dasher", "DoorDash Dasher", "Dash complete", "You earned $80.00", now - 30 * day)
        importer.handle("bank", "Chime", "Deposit", "Direct deposit of $80.00 from DOORDASH INC", now)
        assertEquals(2, db.incomeDao().all().first().size)
    }

    @Test fun repostedNotificationCountedOnce() = runBlocking {
        repeat(3) { importer.handle("bank", "Chime", "Purchase", "You spent $5.00 at Wawa", now) }
        assertEquals(1, db.expenseDao().all().first().size)
        assertEquals(1, db.notificationLogDao().recent().first().size)
    }

    @Test fun skippedNotificationsAreLogged() = runBlocking {
        importer.handle("dasher", "DoorDash Dasher", "New order", "$9.25 est. pay", now)
        val entry = db.notificationLogDao().recent().first().single()
        assertEquals(Outcomes.SKIPPED, entry.outcome)
        assertEquals(925L, entry.amountCents)
        assertEquals(0, db.incomeDao().all().first().size)
    }
}
