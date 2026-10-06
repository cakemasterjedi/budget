package com.gigbudget.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

data class Summary(
    val incomeBySource: Map<String, Long>,
    val incomeTotal: Long,
    val spendingByCategory: Map<String, Long>,
    val spendingTotal: Long,
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

object BudgetMath {
    fun summarize(incomes: List<Income>, expenses: List<Expense>, start: Long, end: Long): Summary {
        val inc = incomes.filter { it.date in start until end }
        val exp = expenses.filter { it.date in start until end }
        val bottles = exp.filter { it.category == Categories.BOTTLE }
        val prerolls = exp.filter { it.category == Categories.PREROLL }
        return Summary(
            incomeBySource = inc.groupBy { it.source }.mapValues { (_, v) -> v.sumOf { it.amountCents } },
            incomeTotal = inc.sumOf { it.amountCents },
            spendingByCategory = exp.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amountCents } }
                .entries.sortedByDescending { it.value }.associate { it.key to it.value },
            spendingTotal = exp.sumOf { it.amountCents },
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
}
