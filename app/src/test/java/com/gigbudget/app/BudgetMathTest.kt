package com.gigbudget.app

import com.gigbudget.app.data.Bill
import com.gigbudget.app.data.BillStatus
import com.gigbudget.app.data.Bucket
import com.gigbudget.app.data.BudgetMath
import com.gigbudget.app.data.Categories
import com.gigbudget.app.data.Dates
import com.gigbudget.app.data.Expense
import com.gigbudget.app.data.Income
import com.gigbudget.app.data.IncomeSources
import com.gigbudget.app.data.Money
import com.gigbudget.app.data.SavingsGoal
import com.gigbudget.app.data.Settings
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

    private fun at(date: LocalDate) = Dates.startOf(date) + 12 * 3600_000L

    @Test fun planSplitsTakeHomeFiftyPercentBillsFivePercentDebt() {
        val settings = Settings(taxPercent = 20, expectedMonthlyIncomeCents = 300_000)
        val expenses = listOf(
            Expense(category = Categories.RENT, amountCents = 90_000, date = at(today)),
            Expense(category = Categories.PREROLL, amountCents = 2_000, date = at(today)),
            Expense(category = Categories.DEBT, amountCents = 10_000, date = at(today)),
            Expense(category = Categories.RENT, amountCents = 99_999, date = at(today.minusMonths(1))),
        )
        val plan = BudgetMath.monthPlan(settings, emptyList(), expenses, today)
        assertEquals(240_000L, plan.takeHomeCents) // $3000 minus 20% tax
        val needs = plan.buckets.first { it.bucket == Bucket.NEEDS }
        assertEquals(50, needs.percent)
        assertEquals(120_000L, needs.plannedCents)
        assertEquals(90_000L, needs.usedCents)
        val debt = plan.buckets.first { it.bucket == Bucket.DEBT }
        assertEquals(12_000L, debt.plannedCents)
        assertEquals(10_000L, debt.usedCents)
        assertEquals(2_000L, plan.buckets.first { it.bucket == Bucket.WANTS }.usedCents)
        assertEquals(100, plan.percentTotal)
    }

    @Test fun planUsesActualIncomeWhenItBeatsTheEstimate() {
        val incomes = listOf(Income(source = IncomeSources.SPARK, amountCents = 500_000, date = at(today)))
        val plan = BudgetMath.monthPlan(Settings(taxPercent = 0, expectedMonthlyIncomeCents = 100_000), incomes, emptyList(), today)
        assertEquals(500_000L, plan.basisIncomeCents)
    }

    @Test fun savingsAndDebtAreNotEverydaySpending() {
        val s = BudgetMath.summarize(
            emptyList(),
            listOf(
                Expense(category = Categories.SAVINGS, amountCents = 5_000, date = 10),
                Expense(category = Categories.DEBT, amountCents = 3_000, date = 10),
                Expense(category = Categories.GAS, amountCents = 2_000, date = 10),
            ),
            0, 100,
        )
        assertEquals(2_000L, s.spendingTotal)
        assertEquals(5_000L, s.savingsTotal)
        assertEquals(3_000L, s.debtTotal)
    }

    @Test fun billStates() {
        val rent = Bill(id = 1, name = "Rent", amountCents = 90_000, dueDay = 1)
        val phone = Bill(id = 2, name = "Phone", amountCents = 6_000, dueDay = 10)
        val car = Bill(id = 3, name = "Car", amountCents = 30_000, dueDay = 31)
        val insurance = Bill(id = 4, name = "Insurance", amountCents = 12_000, dueDay = 3)
        val paid = listOf(Expense(category = Categories.RENT, amountCents = 90_000, date = at(today.withDayOfMonth(1)), billId = 1))
        val states = BudgetMath.billStates(listOf(rent, phone, car, insurance), paid, today).associateBy { it.bill.id }
        assertEquals(BillStatus.PAID, states.getValue(1).status)
        assertEquals(BillStatus.DUE_SOON, states.getValue(2).status) // Oct 10, 4 days away
        assertEquals(BillStatus.LATER, states.getValue(3).status)
        assertEquals(BillStatus.OVERDUE, states.getValue(4).status)
        // Day 31 clamps to the month's last day.
        assertEquals(LocalDate.of(2026, 2, 28), BudgetMath.billStates(listOf(car), emptyList(), LocalDate.of(2026, 2, 3)).single().dueDate)
    }

    @Test fun monthsToPayOff() {
        assertEquals(10, BudgetMath.monthsToPayOff(100_000, 10_000, 0.0))
        assertEquals(12, BudgetMath.monthsToPayOff(100_000, 10_000, 24.0)) // 11.3 months of payments with interest
        assertNull(BudgetMath.monthsToPayOff(100_000, 1_000, 24.0)) // $10 doesn't cover $20/mo interest
        assertEquals(0, BudgetMath.monthsToPayOff(0, 1_000, 10.0))
    }

    @Test fun everyCategoryHasABucket() {
        assertEquals(Bucket.NEEDS, Bucket.of(Categories.RENT))
        assertEquals(Bucket.WANTS, Bucket.of(Categories.BOTTLE))
        assertEquals(Bucket.WANTS, Bucket.of(Categories.PREROLL))
        assertEquals(Bucket.DEBT, Bucket.of(Categories.DEBT))
        assertEquals(100, Bucket.entries.sumOf { it.defaultPercent })
    }
}
