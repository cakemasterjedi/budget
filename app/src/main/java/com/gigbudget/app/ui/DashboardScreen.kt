package com.gigbudget.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gigbudget.app.BudgetViewModel
import com.gigbudget.app.autoimport.MoneyNotificationListener
import com.gigbudget.app.data.BudgetMath
import com.gigbudget.app.data.Categories
import com.gigbudget.app.data.Dates
import com.gigbudget.app.data.IncomeSources
import com.gigbudget.app.data.Money
import com.gigbudget.app.data.Period
import com.gigbudget.app.ui.theme.IncomeGreen
import com.gigbudget.app.ui.theme.SpendRed

@Composable
fun DashboardScreen(vm: BudgetViewModel, modifier: Modifier, onOpenSettings: () -> Unit) {
    val incomes by vm.incomes.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val goals by vm.goals.collectAsState()
    val settings by vm.settings.collectAsState()
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }

    val context = LocalContext.current
    var autoImportOn by remember { mutableStateOf(MoneyNotificationListener.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        autoImportOn = MoneyNotificationListener.isEnabled(context)
        onPauseOrDispose { }
    }

    val (start, end) = Dates.range(period)
    val summary = BudgetMath.summarize(incomes, expenses, start, end)
    val taxCents = summary.incomeTotal * settings.taxPercent / 100
    val leftOver = summary.incomeTotal - summary.spendingTotal - taxCents
    val avgWeekly = BudgetMath.averageWeeklyIncome(incomes)
    val goalPlans = goals.map { BudgetMath.goalPlan(it, avgWeekly) }
    val weeklyGoalNeed = goalPlans.sumOf { it.perWeekCents ?: 0 }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!autoImportOn) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Auto-import is off", style = MaterialTheme.typography.titleMedium)
                    Text("Let Gig Budget read DoorDash, Spark and bank notifications to log pay and spending for you.")
                    Button(onClick = onOpenSettings) { Text("Set it up") }
                }
            }
        }

        ChoiceChips(Period.entries.map { it.name }, period.name, { period = Period.valueOf(it) }) { Period.valueOf(it).label }

        SectionCard("Income") {
            Text(Money.format(summary.incomeTotal), style = MaterialTheme.typography.headlineMedium, color = IncomeGreen)
            IncomeSources.all.forEach { source ->
                val cents = summary.incomeBySource[source] ?: 0
                if (cents > 0 || source != IncomeSources.OTHER) AmountRow(source, cents)
            }
        }

        SectionCard("Spending") {
            Text(Money.format(summary.spendingTotal), style = MaterialTheme.typography.headlineMedium, color = SpendRed)
            if (summary.spendingByCategory.isEmpty()) Text("Nothing spent yet.")
            summary.spendingByCategory.entries.take(5).forEach { (category, cents) -> AmountRow(category, cents) }
        }

        SectionCard("Bottles & prerolls") {
            val weekRange = Dates.range(Period.WEEK)
            val week = BudgetMath.summarize(incomes, expenses, weekRange.first, weekRange.second)
            AmountRow("🍾 Bottles (${summary.bottleCount})", summary.bottleTotal)
            if (settings.bottleWeeklyLimitCents > 0) {
                ProgressLine(week.bottleTotal, settings.bottleWeeklyLimitCents, overIsBad = true)
                LimitText(week.bottleTotal, settings.bottleWeeklyLimitCents)
            }
            AmountRow("🌿 Prerolls (${summary.prerollCount})", summary.prerollTotal)
            if (settings.prerollWeeklyLimitCents > 0) {
                ProgressLine(week.prerollTotal, settings.prerollWeeklyLimitCents, overIsBad = true)
                LimitText(week.prerollTotal, settings.prerollWeeklyLimitCents)
            }
            if (summary.incomeTotal > 0) {
                val pct = (summary.bottleTotal + summary.prerollTotal) * 100 / summary.incomeTotal
                Text("That's $pct% of your income ${period.label.lowercase()}.", style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionCard("What's left") {
            AmountRow("Income", summary.incomeTotal)
            AmountRow("Spending", -summary.spendingTotal)
            AmountRow("Set aside for taxes (${settings.taxPercent}%)", -taxCents)
            HorizontalDivider()
            AmountRow("Left over", leftOver, color = if (leftOver >= 0) IncomeGreen else SpendRed, bold = true)
            Text(
                "Gig pay has no taxes taken out, so park the tax amount somewhere you won't spend it.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard("Savings goals") {
            if (goals.isEmpty()) {
                Text("Add a goal on the Savings tab to see how much to put away each week.")
            } else {
                Row(Modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Save each week:")
                    Text(Money.format(weeklyGoalNeed), style = MaterialTheme.typography.titleMedium)
                }
                if (avgWeekly > 0 && weeklyGoalNeed > 0) {
                    Text("≈ ${weeklyGoalNeed * 100 / avgWeekly}% of your average week (${Money.format(avgWeekly)}).")
                }
                goals.zip(goalPlans).take(4).forEach { (goal, plan) ->
                    AmountRow("${goal.name} — left", plan.remainingCents)
                }
            }
        }

        if (expenses.none { it.category == Categories.BOTTLE || it.category == Categories.PREROLL }) {
            Text(
                "Tip: use the Bottle and Preroll buttons on the Spending tab for one-tap logging.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LimitText(spent: Long, limit: Long) {
    val left = limit - spent
    Text(
        if (left >= 0) "${Money.format(left)} left of ${Money.format(limit)} weekly limit"
        else "${Money.format(-left)} over your ${Money.format(limit)} weekly limit",
        style = MaterialTheme.typography.bodySmall,
        color = if (left >= 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
    )
}
