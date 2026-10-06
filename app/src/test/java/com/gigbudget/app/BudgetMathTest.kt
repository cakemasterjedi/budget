package com.gigbudget.app

import com.gigbudget.app.data.BudgetMath
import com.gigbudget.app.data.Dates
import com.gigbudget.app.data.Money
import com.gigbudget.app.data.SavingsGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BudgetMathTest {
    private val today = LocalDate.of(2026, 10, 6)

    @Test fun weeklyTargetForDueGoal() {
        val goal = SavingsGoal(name = "Tires", targetCents = 60000, savedCents = 18000, dueDate = Dates.startOf(today.plusDays(42)))
        val plan = BudgetMath.goalPlan(goal, avgWeeklyIncomeCents = 70000, today = today)
        assertEquals(42000L, plan.remainingCents)
        assertEquals(7000L, plan.perWeekCents) // $420 over 6 weeks
        assertEquals(1000L, plan.perDayCents)
        assertEquals(10, plan.percentOfIncome)
    }

    @Test fun overdueGoalNeedsEverythingNow() {
        val goal = SavingsGoal(name = "Rent", targetCents = 100000, savedCents = 40000, dueDate = Dates.startOf(today.minusDays(1)))
        assertEquals(60000L, BudgetMath.goalPlan(goal, 0, today).perWeekCents)
    }

    @Test fun noDueDate() {
        val plan = BudgetMath.goalPlan(SavingsGoal(name = "Trip", targetCents = 5000), 0, today)
        assertEquals(5000L, plan.remainingCents)
        assertNull(plan.perWeekCents)
    }

    @Test fun moneyParsing() {
        assertEquals(1250L, Money.parse("12.5"))
        assertEquals(123456L, Money.parse("$1,234.56"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
    }
}
