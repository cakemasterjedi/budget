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

/** One paycheck, split envelope-style: taxes off the top, then the plan's buckets, then savings by goal. */
data class PaycheckSplit(
    val amountCents: Long,
    val taxCents: Long,
    val buckets: List<Pair<Bucket, Long>>,
    /** The savings envelope divided between goals that still need money. */
    val goalShares: List<Pair<SavingsGoal, Long>>,
) {
    fun bucket(b: Bucket) = buckets.first { it.first == b }.second
}

data class CategoryBudget(val category: String, val budgetCents: Long, val spentCents: Long) {
    val leftCents get() = budgetCents - spentCents
}

/** How the Savings Scout got to its number, so the app can show its work. */
data class ScoutResult(
    /** What's safe to move to savings right now: 0, or $5–$50 in whole dollars. */
    val amountCents: Long,
    /** This month's income minus taxes, spending, savings and debt payments so far. */
    val leftCents: Long,
    val billsDueCents: Long,
    val debtDueCents: Long,
    /** Everyday spending expected for the rest of the month, at your recent daily pace. */
    val expectedSpendingCents: Long,
    val daysLeft: Int,
    val safetyCents: Long,
)

object BudgetMath {
    const val SCOUT_MIN_CENTS = 500L
    const val SCOUT_MAX_CENTS = 5_000L
    const val SCOUT_SAFETY_CENTS = 5_000L

    /**
     * Savings Scout: finds a small amount you can save today without coming up short this month.
     * Takes what's left, holds back unpaid bills, remaining minimum debt payments, everyday spending
     * for the days left at your recent pace and a $50 cushion, then suggests half of what's spare
     * (between $5 and $50, or nothing).
     */
    fun savingsScout(
        settings: Settings,
        incomes: List<Income>,
        expenses: List<Expense>,
        bills: List<Bill>,
        debts: List<Debt>,
        today: LocalDate = LocalDate.now(),
    ): ScoutResult {
        val (start, end) = Dates.range(Period.MONTH, today)
        val month = summarize(incomes, expenses, start, end)
        val left = month.incomeTotal - month.incomeTotal * settings.taxPercent / 100 -
            month.spendingTotal - month.savingsTotal - month.debtTotal
        val billsDue = billStates(bills, expenses, today).filter { it.status != BillStatus.PAID }.sumOf { it.bill.amountCents }
        val debtDue = debts.filter { it.balanceCents > 0 }.sumOf { debt ->
            val paid = expenses.filter { it.debtId == debt.id && it.date in start until end }.sumOf { it.amountCents }
            (minOf(debt.minPaymentCents, debt.balanceCents) - paid).coerceAtLeast(0)
        }
        // Recent everyday pace, leaving out bill payments (they're counted above).
        val recentStart = Dates.startOf(today.minusDays(27))
        val recentEnd = Dates.startOf(today.plusDays(1))
        val daily = expenses.filter {
            it.date in recentStart until recentEnd && it.billId == null &&
                Bucket.of(it.category) !in setOf(Bucket.SAVINGS, Bucket.DEBT)
        }.sumOf { it.amountCents } / 28
        val daysLeft = today.lengthOfMonth() - today.dayOfMonth + 1
        val expected = daily * daysLeft
        val spare = left - billsDue - debtDue - expected - SCOUT_SAFETY_CENTS
        val amount = (spare / 2).coerceAtMost(SCOUT_MAX_CENTS) / 100 * 100
        return ScoutResult(
            amountCents = if (amount >= SCOUT_MIN_CENTS) amount else 0,
            leftCents = left,
            billsDueCents = billsDue,
            debtDueCents = debtDue,
            expectedSpendingCents = expected,
            daysLeft = daysLeft,
            safetyCents = SCOUT_SAFETY_CENTS,
        )
    }

    /**
     * Money carried into the month starting at [monthStart]: everything earned before it, minus the
     * tax set-aside and everything spent, saved or paid on debt before it.
     */
    fun rollover(settings: Settings, incomes: List<Income>, expenses: List<Expense>, monthStart: Long): Long {
        if (!settings.carryOver) return 0
        val before = summarize(incomes, expenses, Long.MIN_VALUE, monthStart)
        return before.incomeTotal - before.incomeTotal * settings.taxPercent / 100 -
            before.spendingTotal - before.savingsTotal - before.debtTotal
    }

    /**
     * Splits one payout the way the plan says: taxes first, then each bucket's percent of what's left.
     * The savings share is divided between unfinished goals by how much each needs per week
     * (or by what's left to save when no goal has a due date), never giving a goal more than it needs.
     */
    fun splitPaycheck(
        amountCents: Long,
        settings: Settings,
        goals: List<SavingsGoal>,
        avgWeeklyIncomeCents: Long,
        today: LocalDate = LocalDate.now(),
    ): PaycheckSplit {
        val tax = amountCents * settings.taxPercent / 100
        val rest = amountCents - tax
        fun pct(b: Bucket) = settings.bucketPercents[b] ?: b.defaultPercent
        val raw = Bucket.entries.map { b -> b to rest * pct(b) / 100 }
        // Rounding pennies go to bills & needs when the plan adds up to 100%.
        val pennies = if (Bucket.entries.sumOf { pct(it) } == 100) rest - raw.sumOf { it.second } else 0
        val buckets = raw.map { (b, v) -> b to if (b == Bucket.NEEDS) v + pennies else v }
        val savings = buckets.first { it.first == Bucket.SAVINGS }.second

        val open = goals.filter { it.savedCents < it.targetCents }
        val weekly = open.map { goalPlan(it, avgWeeklyIncomeCents, today).perWeekCents ?: 0 }
        val weights = if (weekly.sum() > 0) weekly else open.map { it.targetCents - it.savedCents }
        val totalWeight = weights.sum()
        val shares = if (totalWeight <= 0) mutableListOf() else open.zip(weights).map { (goal, w) ->
            goal to minOf(savings * w / totalWeight, goal.targetCents - goal.savedCents)
        }.toMutableList()
        // Hand out the rounding pennies so the whole savings share is used (up to what goals still need).
        var spareCents = savings - shares.sumOf { it.second }
        for (i in shares.indices) {
            if (spareCents <= 0 || spareCents >= 100) break
            val (goal, share) = shares[i]
            val extra = minOf(spareCents, goal.targetCents - goal.savedCents - share)
            shares[i] = goal to share + extra
            spareCents -= extra
        }
        return PaycheckSplit(amountCents, tax, buckets, shares.filter { it.second > 0 })
    }

    /** Category budgets for the month containing [today], biggest budget first. */
    fun categoryBudgets(settings: Settings, expenses: List<Expense>, today: LocalDate = LocalDate.now()): List<CategoryBudget> {
        val (start, end) = Dates.range(Period.MONTH, today)
        return settings.categoryBudgets.entries.sortedByDescending { it.value }.map { (category, budget) ->
            CategoryBudget(category, budget, expenses.filter { it.category == category && it.date in start until end }.sumOf { it.amountCents })
        }
    }

    /** The 25 / 50 / 75 / 100% milestone a goal just passed, if any (the highest one). */
    fun crossedMilestone(oldSavedCents: Long, newSavedCents: Long, targetCents: Long): Int? {
        if (targetCents <= 0 || newSavedCents <= oldSavedCents) return null
        return listOf(100, 75, 50, 25).firstOrNull { pct ->
            val mark = targetCents * pct / 100
            oldSavedCents < mark && newSavedCents >= mark
        }
    }

    /** Suggested emergency fund: 3 months of planned bills & needs, or a $1,000 starter. */
    fun emergencyFundTarget(settings: Settings, incomes: List<Income>, expenses: List<Expense>, today: LocalDate = LocalDate.now()): Long {
        val needs = monthPlan(settings, incomes, expenses, today).buckets.first { it.bucket == Bucket.NEEDS }.plannedCents
        return if (needs > 0) (needs * 3 + 9_999) / 10_000 * 10_000 else 100_000
    }

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

    /**
     * Average weekly income. Uses the last 4 weeks once there are 4 weeks of pay history; before
     * that, averages over the days since the first logged pay (counting at least one full week),
     * so a new user's estimate isn't dragged down by weeks before they started.
     */
    fun averageWeeklyIncome(incomes: List<Income>, today: LocalDate = LocalDate.now()): Long {
        val days = incomeHistoryDays(incomes, today)
        if (days == 0) return 0
        val start = Dates.startOf(today.minusDays(27))
        val end = Dates.startOf(today.plusDays(1))
        val total = incomes.filter { it.date in start until end }.sumOf { it.amountCents }
        return total * 7 / maxOf(days, 7)
    }

    /** Days of pay history the average covers: from the first logged pay to today, capped at 28 (0 = no pay yet). */
    fun incomeHistoryDays(incomes: List<Income>, today: LocalDate = LocalDate.now()): Int {
        val first = incomes.minOfOrNull { it.date } ?: return 0
        val days = ChronoUnit.DAYS.between(Dates.toLocalDate(first), today) + 1
        return days.coerceIn(1, 28).toInt()
    }

    /** Average week scaled to a month (52 weeks / 12 months). */
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
