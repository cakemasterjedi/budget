package com.gigbudget.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.gigbudget.app.BudgetViewModel
import com.gigbudget.app.data.Bucket
import com.gigbudget.app.data.BudgetMath
import com.gigbudget.app.data.Debt
import com.gigbudget.app.data.Period
import com.gigbudget.app.data.Dates
import com.gigbudget.app.data.GoalPlan
import com.gigbudget.app.data.Money
import com.gigbudget.app.data.SavingsGoal
import com.gigbudget.app.ui.theme.IncomeGreen

@Composable
fun GoalsScreen(vm: BudgetViewModel, modifier: Modifier) {
    val goals by vm.goals.collectAsState()
    val incomes by vm.incomes.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val debts by vm.debts.collectAsState()
    val settings by vm.settings.collectAsState()
    var editing by remember { mutableStateOf<SavingsGoal?>(null) }
    var editingDebt by remember { mutableStateOf<Debt?>(null) }
    var payingDebt by remember { mutableStateOf<Debt?>(null) }
    var adjusting by remember { mutableStateOf<Pair<SavingsGoal, Boolean>?>(null) }

    val avgWeekly = BudgetMath.averageWeeklyIncome(incomes)
    val plans = goals.associate { it.id to BudgetMath.goalPlan(it, avgWeekly) }
    val weeklyNeed = plans.values.sumOf { it.perWeekCents ?: 0 }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard("To stay on track", emoji = "🐷") {
                    Text("${Money.format(weeklyNeed)} / week", style = MaterialTheme.typography.headlineMedium)
                    when {
                        goals.isEmpty() -> Text("Add something you're saving for — a car repair, rent, a trip, a new phone.")
                        avgWeekly > 0 && weeklyNeed > 0 ->
                            Text("That's about ${weeklyNeed * 100 / avgWeekly}% of your average week (${Money.format(avgWeekly)}). Try moving that much out after each payout.")
                        weeklyNeed > 0 -> Text("Log some income to see what share of your pay that is.")
                        else -> Text("Give a goal a due date to get a weekly target.")
                    }
                }
            }
            if (goals.isEmpty()) item { EmptyState("No savings goals yet.") }
            items(goals, key = { it.id }) { goal ->
                GoalCard(
                    goal = goal,
                    plan = plans.getValue(goal.id),
                    onAdd = { adjusting = goal to true },
                    onTakeOut = { adjusting = goal to false },
                    onEdit = { editing = goal },
                )
            }

            item {
                val (start, end) = Dates.range(Period.MONTH)
                val debtPlan = BudgetMath.monthPlan(settings, incomes, expenses).buckets.first { it.bucket == Bucket.DEBT }
                val minTotal = debts.filter { it.balanceCents > 0 }.sumOf { it.minPaymentCents }
                SectionCard("Debt tracker", emoji = "💳", action = {
                    TextButton(onClick = { editingDebt = Debt(name = "", balanceCents = 0) }) { Text("+ Add") }
                }) {
                    if (debts.isEmpty()) {
                        Text("Add loans and credit cards to track balances, payments and when you'll be debt-free.")
                    } else {
                        AmountRow("Total owed", debts.sumOf { it.balanceCents }, bold = true)
                        AmountRow("Minimum payments / month", minTotal)
                        AmountRow("Paid this month", expenses.filter { it.debtId != null && it.date in start until end }.sumOf { it.amountCents })
                    }
                    AmountRow("Your ${debtPlan.percent}% debt budget / month", debtPlan.plannedCents)
                    if (minTotal > debtPlan.plannedCents && debtPlan.plannedCents > 0) {
                        Text(
                            "Your minimums are more than your ${debtPlan.percent}% — raise the debt slice on the Plan tab so bills don't go late.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("Debt is normal, but staying in debt is optional 💜", style = MaterialTheme.typography.bodySmall)
                }
            }
            items(debts, key = { "debt-${it.id}" }) { debt ->
                val (start, end) = Dates.range(Period.MONTH)
                val paid = expenses.filter { it.debtId == debt.id && it.date in start until end }.sumOf { it.amountCents }
                DebtCard(debt, paid, onPay = { payingDebt = debt }, onEdit = { editingDebt = debt })
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = SavingsGoal(name = "", targetCents = 0) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("New goal") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { goal ->
        GoalDialog(
            initial = goal,
            onDismiss = { editing = null },
            onSave = { vm.saveGoal(it); editing = null },
            onDelete = if (goal.id != 0L) ({ vm.deleteGoal(goal); editing = null }) else null,
        )
    }
    editingDebt?.let { debt ->
        DebtDialog(
            initial = debt,
            onDismiss = { editingDebt = null },
            onSave = { vm.saveDebt(it); editingDebt = null },
            onDelete = if (debt.id != 0L) ({ vm.deleteDebt(debt); editingDebt = null }) else null,
        )
    }
    payingDebt?.let { debt ->
        PayDialog("Payment to ${debt.name}", debt.minPaymentCents.coerceAtMost(debt.balanceCents), onDismiss = { payingDebt = null }) {
            vm.payDebt(debt, it); payingDebt = null
        }
    }
    adjusting?.let { (goal, adding) ->
        AdjustDialog(
            goal = goal,
            adding = adding,
            suggestedCents = if (adding) plans[goal.id]?.perWeekCents ?: 0 else 0,
            onDismiss = { adjusting = null },
            onConfirm = { cents -> vm.adjustGoal(goal, if (adding) cents else -cents); adjusting = null },
        )
    }
}

@Composable
private fun GoalCard(goal: SavingsGoal, plan: GoalPlan, onAdd: () -> Unit, onTakeOut: () -> Unit, onEdit: () -> Unit) {
    SectionCard(goal.name, emoji = "🎯") {
        Text("${Money.format(goal.savedCents)} of ${Money.format(goal.targetCents)}")
        ProgressLine(goal.savedCents, goal.targetCents)
        val due = goal.dueDate
        when {
            plan.remainingCents == 0L -> Text("Goal reached 🎉", color = IncomeGreen)
            due == null -> Text("${Money.format(plan.remainingCents)} to go. Set a due date to get a weekly target.")
            (plan.daysLeft ?: 0) <= 0 -> Text(
                "Due ${Dates.formatLong(due)} — ${Money.format(plan.remainingCents)} still needed.",
                color = MaterialTheme.colorScheme.error,
            )
            else -> {
                Text("${Money.format(plan.remainingCents)} to go by ${Dates.formatLong(due)} (${plan.daysLeft} days)")
                Text(
                    "Save ${Money.format(plan.perWeekCents ?: 0)}/week · ${Money.format(plan.perDayCents ?: 0)}/day" +
                        (plan.percentOfIncome?.let { " · ~$it% of your pay" } ?: ""),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onAdd) { Text("Add money") }
            OutlinedButton(onClick = onTakeOut, enabled = goal.savedCents > 0) { Text("Take out") }
            TextButton(onClick = onEdit) { Text("Edit") }
        }
    }
}

@Composable
private fun GoalDialog(initial: SavingsGoal, onDismiss: () -> Unit, onSave: (SavingsGoal) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial.name) }
    var target by remember { mutableStateOf(Money.toInput(initial.targetCents)) }
    var saved by remember { mutableStateOf(Money.toInput(initial.savedCents)) }
    var due by remember { mutableStateOf(initial.dueDate) }
    val targetCents = Money.parse(target)
    val savedCents = if (saved.isBlank()) 0L else Money.parse(saved)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New savings goal" else "Edit goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("What for? (e.g. Tires, Rent)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                MoneyField(target, { target = it }, "Goal amount")
                MoneyField(saved, { saved = it }, "Already saved")
                DateButton("Need it by", due, { due = it }, allowClear = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && targetCents != null && targetCents > 0 && savedCents != null,
                onClick = {
                    onSave(initial.copy(name = name.trim(), targetCents = targetCents ?: 0, savedCents = savedCents ?: 0, dueDate = due))
                },
            ) { Text("Save") }
        },
        dismissButton = {
            if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AdjustDialog(goal: SavingsGoal, adding: Boolean, suggestedCents: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var amount by remember { mutableStateOf(Money.toInput(suggestedCents)) }
    val cents = Money.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (adding) "Add to ${goal.name}" else "Take out of ${goal.name}") },
        text = { MoneyField(amount, { amount = it }, "Amount") },
        confirmButton = {
            TextButton(enabled = cents != null && cents > 0, onClick = { onConfirm(cents ?: 0) }) { Text(if (adding) "Add" else "Take out") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DebtCard(debt: Debt, paidThisMonth: Long, onPay: () -> Unit, onEdit: () -> Unit) {
    SectionCard(debt.name, emoji = "💳") {
        if (debt.balanceCents == 0L) {
            Text("Paid off! 🎉", color = IncomeGreen, fontWeight = FontWeight.SemiBold)
        } else {
            Text(Money.format(debt.balanceCents), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val details = buildList {
                if (debt.minPaymentCents > 0) add("Min ${Money.format(debt.minPaymentCents)}/mo")
                if (debt.apr > 0) add("${debt.apr}% APR")
                add("Paid ${Money.format(paidThisMonth)} this month")
            }
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            if (debt.minPaymentCents > 0) {
                val months = BudgetMath.monthsToPayOff(debt.balanceCents, debt.minPaymentCents, debt.apr)
                Text(
                    if (months == null) "The minimum doesn't cover the interest — pay more to make progress."
                    else "Debt-free in ~$months months (" +
                        LocalDate.now().plusMonths(months.toLong()).format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)) +
                        ") paying the minimum.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (months == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onPay, enabled = debt.balanceCents > 0) { Text("Make a payment") }
            TextButton(onClick = onEdit) { Text("Edit") }
        }
    }
}

@Composable
private fun DebtDialog(initial: Debt, onDismiss: () -> Unit, onSave: (Debt) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial.name) }
    var balance by remember { mutableStateOf(Money.toInput(initial.balanceCents)) }
    var minPayment by remember { mutableStateOf(Money.toInput(initial.minPaymentCents)) }
    var apr by remember { mutableStateOf(if (initial.apr > 0) initial.apr.toString() else "") }
    val balanceCents = Money.parse(balance)
    val minCents = if (minPayment.isBlank()) 0L else Money.parse(minPayment)
    val aprValue = if (apr.isBlank()) 0.0 else apr.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add a debt" else "Edit debt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Car loan, Capital One)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                MoneyField(balance, { balance = it }, "Balance owed")
                MoneyField(minPayment, { minPayment = it }, "Minimum payment / month")
                OutlinedTextField(
                    value = apr,
                    onValueChange = { new -> if (new.all { it.isDigit() || it == '.' }) apr = new },
                    label = { Text("Interest rate % (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && balanceCents != null && minCents != null && aprValue != null,
                onClick = {
                    onSave(initial.copy(name = name.trim(), balanceCents = balanceCents ?: 0, minPaymentCents = minCents ?: 0, apr = aprValue ?: 0.0))
                },
            ) { Text("Save") }
        },
        dismissButton = {
            if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
