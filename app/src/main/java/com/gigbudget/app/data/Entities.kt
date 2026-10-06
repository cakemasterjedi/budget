package com.gigbudget.app.data

import androidx.room.ColumnInfo
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
    const val HEALTH = "Health"
    const val FUN = "Fun"
    const val SHOPPING = "Shopping"
    const val SUBSCRIPTIONS = "Subscriptions"
    const val GIVING = "Giving"
    const val DEBT = "Debt"
    /** Money moved into a savings goal (created from the Goals tab). */
    const val SAVINGS = "Savings"
    const val OTHER = "Other"
    val all = listOf(BOTTLE, PREROLL, GAS, FOOD, GROCERIES, CAR, RENT, BILLS, PHONE, HEALTH, FUN, SHOPPING,
        SUBSCRIPTIONS, GIVING, DEBT, SAVINGS, OTHER)
    /** Categories offered when adding spending by hand (savings go through the Goals tab). */
    val pickable = all - SAVINGS
}

/**
 * The five parts of the monthly plan. Every spending category belongs to exactly one.
 * Order is fixed: it is the order of the pie slices and their colors.
 */
enum class Bucket(val label: String, val emoji: String, val defaultPercent: Int) {
    NEEDS("Bills & needs", "🏠", 50),
    WANTS("Wants", "🛍️", 25),
    GIVING("Giving", "💝", 5),
    SAVINGS("Savings", "🐷", 15),
    DEBT("Debt", "💳", 5);

    companion object {
        fun of(category: String): Bucket = when (category) {
            Categories.RENT, Categories.BILLS, Categories.PHONE, Categories.GROCERIES, Categories.GAS,
            Categories.CAR, Categories.HEALTH -> NEEDS
            Categories.GIVING -> GIVING
            Categories.SAVINGS -> SAVINGS
            Categories.DEBT -> DEBT
            else -> WANTS
        }

        fun categoriesIn(bucket: Bucket) = Categories.all.filter { of(it) == bucket }
    }
}

/** What the auto-importer does with a given app's notifications. */
object Roles {
    const val DOORDASH = "DOORDASH"
    const val SPARK = "SPARK"
    /** Banks & payment apps: purchases become spending, deposits / money received become income. */
    const val BANK = "BANK"
    const val SPENDING = "SPENDING"
    const val IGNORE = "IGNORE"
    val all = listOf(DOORDASH, SPARK, BANK, SPENDING, IGNORE)

    fun label(role: String) = when (role) {
        DOORDASH -> "DoorDash pay"
        SPARK -> "Spark pay"
        BANK -> "Money in & out"
        SPENDING -> "Spending only"
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
    /** Package of the app whose notification created this entry (auto-imported only). */
    @ColumnInfo(defaultValue = "") val sourcePackage: String = "",
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
    /** Set when this is money moved into a savings goal. */
    val goalId: Long? = null,
    /** Set when this is a payment toward a tracked debt. */
    val debtId: Long? = null,
    /** Set when this pays a monthly bill. */
    val billId: Long? = null,
)

/** What a savings goal is for. Picks the goal's emoji. */
enum class GoalCategory(val label: String, val emoji: String) {
    EMERGENCY("Emergency fund", "☂️"),
    CAR("Car", "🚗"),
    HOME("Home & rent", "🏠"),
    TRIP("Trip", "✈️"),
    PHONE("Phone & tech", "📱"),
    HOLIDAY("Holidays & gifts", "🎁"),
    SCHOOL("School", "🎓"),
    TAXES("Taxes", "🧾"),
    OTHER("Other", "🎯");

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

@Entity(tableName = "goals")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetCents: Long,
    val savedCents: Long = 0,
    val dueDate: Long? = null,
    /** [GoalCategory] name. */
    @ColumnInfo(defaultValue = "OTHER") val category: String = GoalCategory.OTHER.name,
)

@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val balanceCents: Long,
    val minPaymentCents: Long = 0,
    /** Yearly interest rate in percent, e.g. 24.99. 0 if unknown. */
    val apr: Double = 0.0,
)

/** A bill that comes due every month on [dueDay] (1–31; clamped to the month's last day). */
@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountCents: Long,
    val dueDay: Int,
    val category: String = Categories.BILLS,
)

@Entity(tableName = "watched_apps")
data class WatchedApp(
    @PrimaryKey val packageName: String,
    val label: String,
    val role: String,
    val lastSeen: Long = 0,
    val lastSample: String = "",
)

/** What happened to a captured money notification. */
object Outcomes {
    const val INCOME = "INCOME"
    const val EXPENSE = "EXPENSE"
    const val SKIPPED = "SKIPPED"
}

/** Every money notification from a watched app, so you can see what was imported and catch misses. */
@Entity(tableName = "notification_log", indices = [Index(value = ["dedupeKey"], unique = true)])
data class NotificationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val amountCents: Long?,
    val outcome: String,
    val detail: String,
    val dedupeKey: String,
)
