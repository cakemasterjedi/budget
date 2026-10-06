package com.gigbudget.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where money came from. */
object IncomeSources {
    const val DOORDASH = "DoorDash"
    const val SPARK = "Spark"
    const val OTHER = "Other"
    val all = listOf(DOORDASH, SPARK, OTHER)
}

/** Spending categories. Bottle and Preroll get their own quick-add buttons and tracking. */
object Categories {
    const val BOTTLE = "Bottle"
    const val PREROLL = "Preroll"
    const val GAS = "Gas"
    const val FOOD = "Food"
    const val GROCERIES = "Groceries"
    const val CAR = "Car"
    const val RENT = "Rent"
    const val BILLS = "Bills"
    const val PHONE = "Phone"
    const val FUN = "Fun"
    const val OTHER = "Other"
    val all = listOf(BOTTLE, PREROLL, GAS, FOOD, GROCERIES, CAR, RENT, BILLS, PHONE, FUN, OTHER)
}

/** What the auto-importer does with a given app's notifications. */
object Roles {
    const val DOORDASH = "DOORDASH"
    const val SPARK = "SPARK"
    const val SPENDING = "SPENDING"
    const val IGNORE = "IGNORE"
    val all = listOf(DOORDASH, SPARK, SPENDING, IGNORE)

    fun label(role: String) = when (role) {
        DOORDASH -> "DoorDash pay"
        SPARK -> "Spark pay"
        SPENDING -> "Spending"
        else -> "Off"
    }
}

@Entity(tableName = "income", indices = [Index(value = ["dedupeKey"], unique = true)])
data class Income(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val amountCents: Long,
    val date: Long,
    val note: String = "",
    val auto: Boolean = false,
    val dedupeKey: String? = null,
)

@Entity(tableName = "expenses", indices = [Index(value = ["dedupeKey"], unique = true)])
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val amountCents: Long,
    val date: Long,
    val note: String = "",
    val auto: Boolean = false,
    val sourceApp: String = "",
    val dedupeKey: String? = null,
)

@Entity(tableName = "goals")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetCents: Long,
    val savedCents: Long = 0,
    val dueDate: Long? = null,
)

@Entity(tableName = "watched_apps")
data class WatchedApp(
    @PrimaryKey val packageName: String,
    val label: String,
    val role: String,
    val lastSeen: Long = 0,
    val lastSample: String = "",
)
