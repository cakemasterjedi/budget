package com.gigbudget.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gigbudget.app.BudgetViewModel
import com.gigbudget.app.autoimport.MoneyNotificationListener
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
import com.gigbudget.app.data.Period
import com.gigbudget.app.ui.theme.PinkPurpleGradient
import com.gigbudget.app.ui.theme.bucketColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val tips = listOf(
    "Track your money every day — even a \$3 snack adds up.",
    "Pay yourself first: move your savings out right after a payout.",
    "Gig pay has no taxes taken out. Park your tax money where you won't touch it.",
    "Live below your means: plan on a slow week, celebrate a busy one.",
    "Check your plan every Sunday and adjust for next week.",
    "Paying more than the minimum on debt saves you interest every month.",
    "Set a weekly limit for bottles & prerolls so treats stay fun.",
)

@Composable
fun DashboardScreen(
    vm: BudgetViewModel,
    modifier: Modifier,
    onOpenSettings: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenGoals: () -> Unit,
) {
    val incomes by vm.incomes.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val goals by vm.goals.collectAsState()
    val bills by vm.bills.collectAsState()
    val settings by vm.settings.collectAsState()
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }
    var addingIncome by remember { mutableStateOf<Income?>(null) }
    var addingExpense by remember { mutableStateOf<Expense?>(null) }
    var payingBill by remember { mutableStateOf<Bill?>(null) }
    var splitting by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var autoImportOn by remember { mutableStateOf(MoneyNotificationListener.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        autoImportOn = MoneyNotificationListener.isEnabled(context)
        onPauseOrDispose { }
    }

    val (start, end) = Dates.range(period)
    val summary = BudgetMath.summarize(incomes, expenses, start, end)
    val taxCents = summary.incomeTotal * settings.taxPercent / 100
    // Month view carries last month's leftover in; the week view is just this week.
    val rollover = if (period == Period.MONTH) BudgetMath.rollover(settings, incomes, expenses, start) else 0L
    val leftOver = rollover + summary.incomeTotal - taxCents - summary.spendingTotal - summary.savingsTotal - summary.debtTotal
    val avgWeekly = BudgetMath.averageWeeklyIncome(incomes)
    val weeklyGoalNeed = goals.sumOf { BudgetMath.goalPlan(it, avgWeekly).perWeekCents ?: 0 }
    val now = System.currentTimeMillis()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Greeting()

        // Hero: what's left, on the pink-purple gradient.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(PinkPurpleGradient).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val title = if (period == Period.MONTH) {
                LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM", Locale.US)) + " · left over"
            } else "This week · left over"
            Text(title, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
            Text(Money.format(leftOver), color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                StatPill("Earned", Money.format(summary.incomeTotal), Modifier.weight(1f))
                StatPill("Spent", Money.format(summary.spendingTotal), Modifier.weight(1f))
                StatPill("Saved", Money.format(summary.savingsTotal), Modifier.weight(1f))
            }
            Text(
                (if (rollover != 0L) "Includes ${Money.format(rollover)} rolled over from last month. " else "") +
                    "After ${settings.taxPercent}% for taxes (${Money.format(taxCents)}) and ${Money.format(summary.debtTotal)} of debt payments.",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { splitting = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF7B1FA2)),
            ) { Text("✂️ Split a paycheck") }
        }

        // One-tap logging.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuickButton("💵", "Pay", Modifier.weight(1f)) {
                addingIncome = Income(source = IncomeSources.DOORDASH, amountCents = 0, date = now)
            }
            QuickButton("🧾", "Spend", Modifier.weight(1f)) {
                addingExpense = Expense(category = Categories.OTHER, amountCents = 0, date = now)
            }
            QuickButton(Categories.emoji(Categories.BOTTLE), Categories.label(Categories.BOTTLE), Modifier.weight(1f)) {
                addingExpense = Expense(category = Categories.BOTTLE, amountCents = settings.lastBottlePriceCents, date = now)
            }
            QuickButton(Categories.emoji(Categories.PREROLL), Categories.label(Categories.PREROLL), Modifier.weight(1f)) {
                addingExpense = Expense(category = Categories.PREROLL, amountCents = settings.lastPrerollPriceCents, date = now)
            }
        }

        SavingsScoutCard(vm, onNeedGoal = onOpenGoals)

        val lastBackup = maxOf(settings.lastBackupAt, settings.lastAutoBackupAt)
        val needsBackup = lastBackup == 0L || now - lastBackup > 30L * 24 * 60 * 60 * 1000
        if (needsBackup && (incomes.size + expenses.size) >= 5) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("💾 Keep your data safe", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (lastBackup == 0L) "Turn on Google Drive backup in Settings so you never lose your budget, even on a new phone."
                        else "It's been a while since your last backup. Save a fresh one?",
                    )
                    Button(onClick = onOpenSettings) { Text("Back up now") }
                }
            }
        }

        if (!autoImportOn) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✨ Auto-import is off", style = MaterialTheme.typography.titleMedium)
                    Text("Let Stack It read DoorDash, Spark and bank notifications to log pay and spending for you.")
                    Button(onClick = onOpenSettings) { Text("Set it up") }
                }
            }
        }

        ChoiceChips(Period.entries.map { it.name }, period.name, { period = Period.valueOf(it) }) { Period.valueOf(it).label }

        // Pie: where the money went, by bucket, with each bucket's categories listed under it.
        SectionCard("Where your money went", emoji = "🥧") {
            val outflow = summary.byBucket.values.sum()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                DonutChart(
                    slices = Bucket.entries.map { Slice(it.label, summary.byBucket.getValue(it), bucketColor(it)) },
                    centerTop = Money.format(outflow),
                    centerBottom = "out ${period.label.lowercase()}",
                )
            }
            if (outflow == 0L) {
                Text("Nothing spent yet ${period.label.lowercase()}. Log spending and it'll show up here by category.")
            }
            Bucket.entries.forEach { bucket ->
                val total = summary.byBucket.getValue(bucket)
                if (total > 0) {
                    LegendRow(bucketColor(bucket), "${bucket.emoji} ${bucket.label}", total, total * 100 / outflow)
                    expenses.asSequence()
                        .filter { it.date in start until end && Bucket.of(it.category) == bucket }
                        .groupBy { it.category }
                        .mapValues { (_, v) -> v.sumOf { it.amountCents } }
                        .entries.sortedByDescending { it.value }
                        .forEach { (category, cents) ->
                            Row(Modifier.fillMaxWidth().padding(start = 22.dp)) {
                                Text(Categories.display(category), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(Money.format(cents), style = MaterialTheme.typography.bodySmall)
                                Text("${cents * 100 / outflow}%", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            }
                        }
                }
            }
            TextButton(onClick = onOpenPlan) { Text("See your 50% bills / 5% debt plan →") }
        }

        val upcoming = BudgetMath.billStates(bills, expenses).filter { it.status == BillStatus.OVERDUE || it.status == BillStatus.DUE_SOON }
        if (upcoming.isNotEmpty()) {
            SectionCard("Bills coming up", emoji = "📅") {
                upcoming.forEach { state ->
                    BillRow(state, onPay = { payingBill = state.bill })
                }
            }
        }

        SectionCard("Income", emoji = "💵") {
            if (period == Period.MONTH) {
                val expected = settings.expectedMonthlyIncomeCents.takeIf { it > 0 } ?: BudgetMath.estimatedMonthlyIncome(incomes)
                if (expected > 0) {
                    Text(
                        "Earned ${Money.format(summary.incomeTotal)} of ${Money.format(expected)} expected" +
                            " (${summary.incomeTotal * 100 / expected}%)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ProgressLine(summary.incomeTotal, expected)
                }
            }
            IncomeSources.all.forEach { source ->
                val cents = summary.incomeBySource[source] ?: 0
                if (cents > 0 || source != IncomeSources.OTHER) AmountRow(source, cents)
            }
            HorizontalDivider()
            AmountRow("Total", summary.incomeTotal, bold = true)
        }

        SectionCard("${Categories.label(Categories.BOTTLE)} & ${Categories.label(Categories.PREROLL)}", emoji = Categories.emoji(Categories.BOTTLE)) {
            val weekRange = Dates.range(Period.WEEK)
            val week = BudgetMath.summarize(incomes, expenses, weekRange.first, weekRange.second)
            AmountRow("${Categories.display(Categories.BOTTLE)} (${summary.bottleCount})", summary.bottleTotal)
            if (settings.bottleWeeklyLimitCents > 0) {
                ProgressLine(week.bottleTotal, settings.bottleWeeklyLimitCents, overIsBad = true)
                LimitText(week.bottleTotal, settings.bottleWeeklyLimitCents)
            }
            AmountRow("${Categories.display(Categories.PREROLL)} (${summary.prerollCount})", summary.prerollTotal)
            if (settings.prerollWeeklyLimitCents > 0) {
                ProgressLine(week.prerollTotal, settings.prerollWeeklyLimitCents, overIsBad = true)
                LimitText(week.prerollTotal, settings.prerollWeeklyLimitCents)
            }
            if (summary.incomeTotal > 0) {
                val pct = (summary.bottleTotal + summary.prerollTotal) * 100 / summary.incomeTotal
                Text("That's $pct% of your income ${period.label.lowercase()}.", style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionCard("Savings goals", emoji = "🐷") {
            if (goals.isEmpty()) {
                Text("Add a goal on the Goals tab to see how much to put away each week.")
            } else {
                AmountRow("Save each week", weeklyGoalNeed, bold = true)
                if (avgWeekly > 0 && weeklyGoalNeed > 0) {
                    Text("≈ ${weeklyGoalNeed * 100 / avgWeekly}% of your average week (${Money.format(avgWeekly)}).",
                        style = MaterialTheme.typography.bodySmall)
                }
                goals.take(4).forEach { goal ->
                    Text("${goal.name}: ${Money.format(goal.savedCents)} of ${Money.format(goal.targetCents)}")
                    ProgressLine(goal.savedCents, goal.targetCents)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("💡")
                Spacer(Modifier.width(12.dp))
                Text(tips[LocalDate.now().dayOfYear % tips.size], color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Text(
            "You've got this, $NAME! 💜",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }

    addingIncome?.let { income ->
        IncomeDialog(income, onDismiss = { addingIncome = null }, onSave = { vm.saveIncome(it); addingIncome = null }, onDelete = null)
    }
    addingExpense?.let { expense ->
        ExpenseDialog(expense, onDismiss = { addingExpense = null }, onSave = { vm.saveExpense(it); addingExpense = null }, onDelete = null)
    }
    if (splitting) {
        PaycheckSplitDialog(vm, incomes.firstOrNull()?.amountCents ?: 0, onDismiss = { splitting = false })
    }
    payingBill?.let { bill ->
        PayDialog("Pay ${bill.name}", bill.amountCents, onDismiss = { payingBill = null }) { vm.payBill(bill, it); payingBill = null }
    }
}

private const val NAME = "Niome"

private val hype = listOf(
    "Let's stack it 💸",
    "Every dash counts 🚗",
    "Your future self says thank you 💜",
    "Secure the bag, then save the bag 👜",
    "Small stacks turn into big stacks 📈",
    "You're doing amazing, sweetie ✨",
    "Bills paid, goals funded, vibes immaculate 💅",
)

/** "Good morning, Niome 💜" plus a little daily hype line. */
@Composable
private fun Greeting() {
    val hour = java.time.LocalTime.now().hour
    val hello = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hey night owl"
    }
    Column {
        Text(
            "$hello, $NAME 💜",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            hype[LocalDate.now().dayOfYear % hype.size],
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun QuickButton(emoji: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelMedium)
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
