package com.gigbudget.app.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.ln

data class Summary(
    val incomeBySource: Map<String, Long>,
    val incomeTotal: Long,
    /** Everyday spending (needs + wants + giving) by category, biggest first. Excludes savings and debt. */
    val spendingByCategory: Map<String, Long>,
    val spendingTotal: Long,
    /** Every outflow, including savings and debt payments, per bucket. */
    val byBucket: Map<Bucket, Long>,
    val savingsTotal: Long,
    val debtTotal: Long,
    val bottleCount: Int,
    val bottleTotal: Long,
    val prerollCount: Int,
    val prerollTotal: Long,
)

data class GoalPlan(
    val remainingCents: Long,
    val daysLeft: Long?,
    /** How much to put away each week to hit the goal by its due date (null without a due date). */
    val perWeekCents: Long?,
    val perDayCents: Long?,
    /** perWeek as a share of recent average weekly income, e.g. 12 means "save 12% of what you make". */
    val percentOfIncome: Int?,
)

data class BucketPlan(val bucket: Bucket, val percent: Int, val plannedCents: Long, val usedCents: Long) {
    val leftCents get() = plannedCents - usedCents
}

data class MonthPlan(
    /** Income the plan is built on (expected, or estimated from recent pay). */
    val basisIncomeCents: Long,
    val basisIsEstimate: Boolean,
    val taxCents: Long,
    /** basisIncome minus the tax set-aside: what the percentages split. */
    val takeHomeCents: Long,
    val buckets: List<BucketPlan>,
    val percentTotal: Int,
)

enum class BillStatus { PAID, OVERDUE, DUE_SOON, LATER }

data class BillState(val bill: Bill, val dueDate: LocalDate, val status: BillStatus, val daysUntilDue: Long)

object BudgetMath {
    fun summarize(incomes: List<Income>, expenses: List<Expense>, start: Long, end: Long): Summary {
        val inc = incomes.filter { it.date in start until end }
        val exp = expenses.filter { it.date in start until end }
        val everyday = exp.filter { Bucket.of(it.category) !in setOf(Bucket.SAVINGS, Bucket.DEBT) }
        val bottles = exp.filter { it.category == Categories.BOTTLE }
        val prerolls = exp.filter { it.category == Categories.PREROLL }
        val byBucket = Bucket.entries.associateWith { b -> exp.filter { Bucket.of(it.category) == b }.sumOf { it.amountCents } }
        return Summary(
            incomeBySource = inc.groupBy { it.source }.mapValues { (_, v) -> v.sumOf { it.amountCents } },
            incomeTotal = inc.sumOf { it.amountCents },
            spendingByCategory = everyday.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amountCents } }
                .entries.sortedByDescending { it.value }.associate { it.key to it.value },
            spendingTotal = everyday.sumOf { it.amountCents },
            byBucket = byBucket,
            savingsTotal = byBucket.getValue(Bucket.SAVINGS),
            debtTotal = byBucket.getValue(Bucket.DEBT),
            bottleCount = bottles.size,
            bottleTotal = bottles.sumOf { it.amountCents },
            prerollCount = prerolls.size,
            prerollTotal = prerolls.sumOf { it.amountCents },
        )
    }

    /** Average weekly income over the last 4 weeks. */
    fun averageWeeklyIncome(incomes: List<Income>, today: LocalDate = LocalDate.now()): Long {
        val start = Dates.startOf(today.minusDays(27))
        val end = Dates.startOf(today.plusDays(1))
        return incomes.filter { it.date in start until end }.sumOf { it.amountCents } / 4
    }

    /** 4-week average scaled to a month (52 weeks / 12 months). */
    fun estimatedMonthlyIncome(incomes: List<Income>, today: LocalDate = LocalDate.now()): Long =
        averageWeeklyIncome(incomes, today) * 52 / 12

    /**
     * The 50 / 25 / 5 / 15 / 5 style plan for the month containing [today]. The plan is built on
     * the larger of what you expect to make and what you've actually made so far this month.
     */
    fun monthPlan(settings: Settings, incomes: List<Income>, expenses: List<Expense>, today: LocalDate = LocalDate.now()): MonthPlan {
        val (start, end) = Dates.range(Period.MONTH, today)
        val month = summarize(incomes, expenses, start, end)
        val expected = settings.expectedMonthlyIncomeCents.takeIf { it > 0 }
        val base = maxOf(expected ?: estimatedMonthlyIncome(incomes, today), month.incomeTotal)
        val tax = base * settings.taxPercent / 100
        val takeHome = base - tax
        val buckets = Bucket.entries.map { b ->
            val pct = settings.bucketPercents[b] ?: b.defaultPercent
            BucketPlan(b, pct, takeHome * pct / 100, month.byBucket.getValue(b))
        }
        return MonthPlan(base, expected == null, tax, takeHome, buckets, buckets.sumOf { it.percent })
    }

    fun goalPlan(goal: SavingsGoal, avgWeeklyIncomeCents: Long, today: LocalDate = LocalDate.now()): GoalPlan {
        val remaining = (goal.targetCents - goal.savedCents).coerceAtLeast(0)
        val due = goal.dueDate ?: return GoalPlan(remaining, null, null, null, null)
        val daysLeft = ChronoUnit.DAYS.between(today, Dates.toLocalDate(due))
        val perWeek: Long
        val perDay: Long
        if (daysLeft <= 0) {
            // Due today or overdue: everything that's left is needed now.
            perWeek = remaining
            perDay = remaining
        } else {
            val weeks = maxOf(1.0, daysLeft / 7.0)
            perWeek = ceil(remaining / weeks).toLong()
            perDay = ceil(remaining / daysLeft.toDouble()).toLong()
        }
        val percent = if (avgWeeklyIncomeCents > 0 && remaining > 0) {
            ceil(perWeek * 100.0 / avgWeeklyIncomeCents).toInt()
        } else null
        return GoalPlan(remaining, daysLeft, perWeek, perDay, percent)
    }

    /**
     * Months to pay off [balanceCents] at [paymentCents] a month with [apr]% interest.
     * Null when the payment doesn't cover the interest (it would never be paid off).
     */
    fun monthsToPayOff(balanceCents: Long, paymentCents: Long, apr: Double): Int? {
        if (balanceCents <= 0) return 0
        if (paymentCents <= 0) return null
        val r = apr / 100 / 12
        if (r <= 0) return ceil(balanceCents.toDouble() / paymentCents).toInt()
        val x = 1 - r * balanceCents / paymentCents
        if (x <= 0) return null
        return ceil(-ln(x) / ln(1 + r)).toInt()
    }

    fun billStates(bills: List<Bill>, expenses: List<Expense>, today: LocalDate = LocalDate.now()): List<BillState> {
        val (start, end) = Dates.range(Period.MONTH, today)
        val paidIds = expenses.filter { it.billId != null && it.date in start until end }.mapNotNull { it.billId }.toSet()
        val ym = YearMonth.from(today)
        return bills.map { bill ->
            val due = ym.atDay(bill.dueDay.coerceIn(1, ym.lengthOfMonth()))
            val days = ChronoUnit.DAYS.between(today, due)
            val status = when {
                bill.id in paidIds -> BillStatus.PAID
                days < 0 -> BillStatus.OVERDUE
                days <= 7 -> BillStatus.DUE_SOON
                else -> BillStatus.LATER
            }
            BillState(bill, due, status, days)
        }.sortedWith(compareBy({ it.status == BillStatus.PAID }, { it.dueDate }))
    }
}
