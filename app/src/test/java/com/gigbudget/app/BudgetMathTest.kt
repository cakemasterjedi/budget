package com.gigbudget.app

import com.gigbudget.app.data.Bill
import com.gigbudget.app.data.BillStatus
import com.gigbudget.app.data.Bucket
import com.gigbudget.app.data.BudgetMath
import com.gigbudget.app.data.Categories
import com.gigbudget.app.data.Dates
import com.gigbudget.app.data.Debt
import com.gigbudget.app.data.Expense
import com.gigbudget.app.data.Income
import com.gigbudget.app.data.IncomeSources
import com.gigbudget.app.data.Money
import com.gigbudget.app.data.SavingsGoal
import com.gigbudget.app.data.Settings
import com.gigbudget.app.data.SplitPreset
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

    // today = Tue Oct 6, 2026 → 26 days left in the month, counting today.
    @Test fun scoutSuggestsHalfOfWhatsSpareCappedAtFifty() {
        val incomes = listOf(Income(source = IncomeSources.DOORDASH, amountCents = 100_000, date = at(today)))
        val expenses = listOf(Expense(category = Categories.GROCERIES, amountCents = 2_800, date = at(today))) // $1/day pace
        val bills = listOf(Bill(id = 1, name = "Phone", amountCents = 6_000, dueDay = 20))
        val debts = listOf(Debt(id = 1, name = "Card", balanceCents = 50_000, minPaymentCents = 3_000))
        val scout = BudgetMath.savingsScout(Settings(taxPercent = 25), incomes, expenses, bills, debts, today)
        assertEquals(72_200L, scout.leftCents)           // 1000 − 250 tax − 28
        assertEquals(6_000L, scout.billsDueCents)
        assertEquals(3_000L, scout.debtDueCents)
        assertEquals(26, scout.daysLeft)
        assertEquals(2_600L, scout.expectedSpendingCents)
        assertEquals(5_000L, scout.amountCents)          // spare $556 → half is $278 → capped at $50
    }

    @Test fun scoutRoundsDownToWholeDollars() {
        val incomes = listOf(Income(source = IncomeSources.SPARK, amountCents = 8_000 + 1_250, date = at(today)))
        val scout = BudgetMath.savingsScout(Settings(taxPercent = 0), incomes, emptyList(), emptyList(), emptyList(), today)
        assertEquals(2_100L, scout.amountCents)          // spare $42.50 after the $50 cushion → half $21.25 → $21
    }

    @Test fun scoutSaysNotTodayWhenTight() {
        val incomes = listOf(Income(source = IncomeSources.SPARK, amountCents = 10_000, date = at(today)))
        val bills = listOf(Bill(id = 1, name = "Rent", amountCents = 90_000, dueDay = 28))
        val scout = BudgetMath.savingsScout(Settings(taxPercent = 0), incomes, emptyList(), bills, emptyList(), today)
        assertEquals(0L, scout.amountCents)
    }

    @Test fun scoutCountsDebtPaymentsAlreadyMade() {
        val debts = listOf(Debt(id = 7, name = "Card", balanceCents = 50_000, minPaymentCents = 3_000))
        val paid = listOf(Expense(category = Categories.DEBT, amountCents = 3_000, date = at(today), debtId = 7))
        val scout = BudgetMath.savingsScout(Settings(), emptyList(), paid, emptyList(), debts, today)
        assertEquals(0L, scout.debtDueCents)
    }

    @Test fun milestones() {
        assertEquals(25, BudgetMath.crossedMilestone(0, 2_500, 10_000))
        assertEquals(50, BudgetMath.crossedMilestone(2_000, 6_000, 10_000))
        assertEquals(100, BudgetMath.crossedMilestone(9_000, 12_000, 10_000))
        assertNull(BudgetMath.crossedMilestone(5_100, 6_000, 10_000))
        assertNull(BudgetMath.crossedMilestone(6_000, 2_000, 10_000))
    }

    @Test fun emergencyFundIsThreeMonthsOfNeeds() {
        val settings = Settings(taxPercent = 0, expectedMonthlyIncomeCents = 200_000)
        assertEquals(300_000L, BudgetMath.emergencyFundTarget(settings, emptyList(), emptyList(), today)) // 3 × 50% of $2000
        assertEquals(100_000L, BudgetMath.emergencyFundTarget(Settings(), emptyList(), emptyList(), today))
    }

    @Test fun splitFiveHundredLikeTheCashStuffingVideo() {
        val settings = Settings(taxPercent = 0, bucketPercents = SplitPreset.CASH_STUFFING.percents)
        val split = BudgetMath.splitPaycheck(50_000, settings, emptyList(), 0, today)
        assertEquals(27_500L, split.bucket(Bucket.NEEDS))   // 45% bills + 10% expenses
        assertEquals(5_000L, split.bucket(Bucket.DEBT))
        assertEquals(15_000L, split.bucket(Bucket.SAVINGS))
        assertEquals(2_500L, split.bucket(Bucket.WANTS))
        assertEquals(0L, split.bucket(Bucket.GIVING))
        SplitPreset.entries.forEach { assertEquals(100, it.percents.values.sum()) }
    }

    @Test fun splitTakesTaxesFirstAndKeepsEveryPenny() {
        val split = BudgetMath.splitPaycheck(50_001, Settings(taxPercent = 25), emptyList(), 0, today)
        assertEquals(12_500L, split.taxCents)
        assertEquals(50_001L, split.taxCents + split.buckets.sumOf { it.second })
    }

    @Test fun splitShareSavingsBetweenGoalsByWhatTheyNeed() {
        val settings = Settings(taxPercent = 0, bucketPercents = SplitPreset.CASH_STUFFING.percents) // 30% savings = $150
        val goals = listOf(
            SavingsGoal(id = 1, name = "Wedding", targetCents = 40_000, savedCents = 10_000), // needs $300
            SavingsGoal(id = 2, name = "Family", targetCents = 20_000, savedCents = 10_000),  // needs $100
            SavingsGoal(id = 3, name = "Done", targetCents = 5_000, savedCents = 5_000),
        )
        val shares = BudgetMath.splitPaycheck(50_000, settings, goals, 0, today).goalShares.associate { it.first.id to it.second }
        assertEquals(mapOf(1L to 11_250L, 2L to 3_750L), shares)
    }

    @Test fun splitNeverOverfillsAGoal() {
        val settings = Settings(taxPercent = 0, bucketPercents = SplitPreset.CASH_STUFFING.percents)
        val goals = listOf(SavingsGoal(id = 1, name = "Almost", targetCents = 10_000, savedCents = 9_000))
        assertEquals(1_000L, BudgetMath.splitPaycheck(50_000, settings, goals, 0, today).goalShares.single().second)
    }

    @Test fun rolloverCarriesLastMonthsLeftover() {
        val lastMonth = at(today.minusMonths(1))
        val incomes = listOf(Income(source = IncomeSources.DOORDASH, amountCents = 100_000, date = lastMonth))
        val expenses = listOf(Expense(category = Categories.GAS, amountCents = 30_000, date = lastMonth))
        val monthStart = Dates.range(com.gigbudget.app.data.Period.MONTH, today).first
        assertEquals(60_000L, BudgetMath.rollover(Settings(taxPercent = 10), incomes, expenses, monthStart))
        assertEquals(0L, BudgetMath.rollover(Settings(taxPercent = 10, carryOver = false), incomes, expenses, monthStart))
    }

    @Test fun categoryBudgetsTrackThisMonthOnly() {
        val settings = Settings(categoryBudgets = mapOf(Categories.GROCERIES to 40_000, Categories.FOOD to 10_000))
        val expenses = listOf(
            Expense(category = Categories.GROCERIES, amountCents = 12_000, date = at(today)),
            Expense(category = Categories.FOOD, amountCents = 13_000, date = at(today)),
            Expense(category = Categories.GROCERIES, amountCents = 99_000, date = at(today.minusMonths(1))),
        )
        val budgets = BudgetMath.categoryBudgets(settings, expenses, today)
        assertEquals(listOf(Categories.GROCERIES, Categories.FOOD), budgets.map { it.category })
        assertEquals(28_000L, budgets[0].leftCents)
        assertEquals(-3_000L, budgets[1].leftCents)
    }

    @Test fun splitUsesTheRoundingPennies() {
        val goals = listOf(
            SavingsGoal(id = 1, name = "A", targetCents = 150_000, savedCents = 30_000, dueDate = Dates.startOf(today.plusDays(120))),
            SavingsGoal(id = 2, name = "B", targetCents = 300_000, savedCents = 50_000, dueDate = Dates.startOf(today.plusDays(300))),
        )
        val split = BudgetMath.splitPaycheck(50_000, Settings(taxPercent = 20), goals, 0, today)
        assertEquals(split.bucket(Bucket.SAVINGS), split.goalShares.sumOf { it.second })
    }

    @Test fun newUserAverageCountsOnlyTheirFirstDays() {
        // First pay 2 days ago: 3 days of history, but at least a full week is counted.
        val firstWeek = listOf(
            Income(source = IncomeSources.DOORDASH, amountCents = 40_000, date = at(today.minusDays(2))),
            Income(source = IncomeSources.DOORDASH, amountCents = 20_000, date = at(today)),
        )
        assertEquals(3, BudgetMath.incomeHistoryDays(firstWeek, today))
        assertEquals(60_000L, BudgetMath.averageWeeklyIncome(firstWeek, today))      // not $150
        assertEquals(260_000L, BudgetMath.estimatedMonthlyIncome(firstWeek, today))  // $600 × 52 / 12

        // Two weeks in (15 days): $1,500 over 15 days = $700 a week.
        val twoWeeks = listOf(Income(source = IncomeSources.SPARK, amountCents = 150_000, date = at(today.minusDays(14))))
        assertEquals(70_000L, BudgetMath.averageWeeklyIncome(twoWeeks, today))
    }

    @Test fun afterFourWeeksTheAverageUsesTheLastFourWeeks() {
        val incomes = listOf(
            Income(source = IncomeSources.SPARK, amountCents = 999_999, date = at(today.minusDays(40))), // too old to count
            Income(source = IncomeSources.SPARK, amountCents = 200_000, date = at(today.minusDays(10))),
        )
        assertEquals(28, BudgetMath.incomeHistoryDays(incomes, today))
        assertEquals(50_000L, BudgetMath.averageWeeklyIncome(incomes, today))
        assertEquals(0L, BudgetMath.averageWeeklyIncome(emptyList(), today))
    }
}
