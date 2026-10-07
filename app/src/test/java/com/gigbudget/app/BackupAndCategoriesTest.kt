package com.gigbudget.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gigbudget.app.data.AppDatabase
import com.gigbudget.app.data.Backup
import com.gigbudget.app.data.Bill
import com.gigbudget.app.data.Bucket
import com.gigbudget.app.data.BudgetMath
import com.gigbudget.app.data.Categories
import com.gigbudget.app.data.CategoryRules
import com.gigbudget.app.data.Debt
import com.gigbudget.app.data.Expense
import com.gigbudget.app.data.GoalCategory
import com.gigbudget.app.data.Income
import com.gigbudget.app.data.IncomeSources
import com.gigbudget.app.data.Roles
import com.gigbudget.app.data.SavingsGoal
import com.gigbudget.app.data.SettingsStore
import com.gigbudget.app.data.WatchedApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupAndCategoriesTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore

    private val nails = "Nails"
    private val rules = CategoryRules(
        bucketNames = mapOf(Bucket.NEEDS to "Must-pay", Bucket.WANTS to "Treat yourself"),
        bucketEmojis = mapOf(Bucket.WANTS to "💅"),
        categoryBuckets = mapOf(nails to Bucket.WANTS, Categories.BOTTLE to Bucket.NEEDS),
        categoryLabels = mapOf(Categories.FOOD to "Eating out"),
        categoryEmojis = mapOf(nails to "💅"),
        customCategories = listOf(nails),
        hidden = setOf(Categories.HEALTH),
    )

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsStore(context)
    }

    @After fun tearDown() {
        db.close()
        CategoryRules.current = CategoryRules()
    }

    private fun seed() = runBlocking {
        db.incomeDao().insert(Income(source = IncomeSources.SPARK, amountCents = 12_345, date = 1_000, note = "Sunday", auto = true, dedupeKey = "k1", sourcePackage = "spark"))
        db.goalDao().insert(SavingsGoal(id = 7, name = "Wedding", targetCents = 100_000, savedCents = 20_000, dueDate = 5_000, category = GoalCategory.HOLIDAY.name))
        db.debtDao().insert(Debt(id = 3, name = "Card", balanceCents = 50_000, minPaymentCents = 2_500, apr = 24.99))
        db.billDao().insert(Bill(id = 4, name = "Phone", amountCents = 6_500, dueDay = 20, category = Categories.PHONE))
        db.expenseDao().insert(Expense(category = nails, amountCents = 4_000, date = 2_000, note = "Gel"))
        db.expenseDao().insert(Expense(category = Categories.SAVINGS, amountCents = 5_000, date = 2_100, goalId = 7))
        db.expenseDao().insert(Expense(category = Categories.DEBT, amountCents = 2_500, date = 2_200, debtId = 3))
        db.expenseDao().insert(Expense(category = Categories.PHONE, amountCents = 6_500, date = 2_300, billId = 4))
        db.watchedAppDao().insertIgnore(WatchedApp("com.onedebit.chime", "Chime", Roles.BANK, 9, "You spent $5"))
        settings.update {
            it.copy(taxPercent = 22, categoryRules = rules, categoryBudgets = mapOf(nails to 6_000, Categories.GAS to 20_000),
                notes = "Tires in March", expectedMonthlyIncomeCents = 300_000)
        }
    }

    private fun snapshot() = runBlocking {
        listOf(db.incomeDao().list(), db.expenseDao().list().sortedBy { it.id }, db.goalDao().list(), db.debtDao().list(),
            db.billDao().list(), db.watchedAppDao().list())
    }

    @Test fun backupRestoresEverything() = runBlocking {
        seed()
        val before = snapshot()
        val beforeSettings = settings.state.value
        val file = Backup.export(db, settings)

        // Wipe everything, as if the app were reinstalled.
        listOf(db.incomeDao()::deleteAll, db.expenseDao()::deleteAll, db.goalDao()::deleteAll, db.debtDao()::deleteAll,
            db.billDao()::deleteAll, db.watchedAppDao()::deleteAll).forEach { it() }
        settings.importJson(org.json.JSONObject())
        assertEquals(0, db.incomeDao().list().size)
        assertEquals(CategoryRules(), settings.state.value.categoryRules)

        Backup.restore(db, settings, file)
        assertEquals(before, snapshot())
        assertEquals(beforeSettings, settings.state.value)
        assertEquals("Treat yourself", Bucket.WANTS.label)
    }

    @Test fun badFileLeavesDataAlone() = runBlocking {
        seed()
        val before = snapshot()
        for (bad in listOf("not json", "{}", """{"format":1,"settings":{},"income":[{"oops":1}]}""")) {
            try {
                Backup.restore(db, settings, bad)
                fail("expected a bad-file error for $bad")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.isNotBlank())
            }
        }
        assertEquals(before, snapshot())
        assertEquals(22, settings.state.value.taxPercent)
    }

    @Test fun customCategoriesSurviveARestart() {
        seed()
        CategoryRules.current = CategoryRules()
        val reopened = SettingsStore(context) // what happens when the app starts after an update
        assertEquals(rules, reopened.state.value.categoryRules)
        assertEquals(rules, CategoryRules.current)
        assertEquals(mapOf(nails to 6_000L, Categories.GAS to 20_000L), reopened.state.value.categoryBudgets)
        assertEquals("Tires in March", reopened.state.value.notes)
    }

    @Test fun rulesChangeNamesAndGroups() {
        CategoryRules.current = rules
        assertEquals(listOf(nails), Categories.all - Categories.builtIn.toSet())
        assertEquals("💅 Nails", Categories.display(nails))
        assertEquals("Eating out", Categories.label(Categories.FOOD))
        assertEquals("Must-pay", Bucket.NEEDS.label)
        assertEquals(Bucket.NEEDS, Bucket.of(Categories.BOTTLE))
        assertTrue(Categories.HEALTH !in Categories.pickable)
        // Savings and Debt can't be moved, whatever the rules say.
        CategoryRules.current = rules.copy(categoryBuckets = mapOf(Categories.SAVINGS to Bucket.WANTS))
        assertEquals(Bucket.SAVINGS, Bucket.of(Categories.SAVINGS))
        assertEquals(rules, CategoryRules.fromJson(rules.toJson()))
    }

    @Test fun customCategorySpendingCountsInItsBucket() {
        CategoryRules.current = rules
        val s = BudgetMath.summarize(emptyList(), listOf(
            Expense(category = nails, amountCents = 4_000, date = 10),
            Expense(category = Categories.BOTTLE, amountCents = 2_000, date = 10),
        ), 0, 100)
        assertEquals(4_000L, s.byBucket[Bucket.WANTS])
        assertEquals(2_000L, s.byBucket[Bucket.NEEDS])
    }

    @Test fun deletingACustomCategoryMovesItsSpendingToOther() = runBlocking {
        seed()
        db.expenseDao().recategorize(nails, Categories.OTHER)
        assertEquals(listOf(Categories.OTHER), db.expenseDao().list().filter { it.note == "Gel" }.map { it.category })
    }
}
