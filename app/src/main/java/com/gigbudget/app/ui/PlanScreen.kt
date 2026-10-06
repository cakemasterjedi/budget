package com.gigbudget.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gigbudget.app.BudgetViewModel
import com.gigbudget.app.data.Bill
import com.gigbudget.app.data.BillState
import com.gigbudget.app.data.BillStatus
import com.gigbudget.app.data.Bucket
import com.gigbudget.app.data.BudgetMath
import com.gigbudget.app.data.Categories
import com.gigbudget.app.data.Dates
import com.gigbudget.app.data.Money
import com.gigbudget.app.data.Period
import com.gigbudget.app.ui.theme.IncomeGreen
import com.gigbudget.app.ui.theme.bucketColor

@Composable
fun PlanScreen(vm: BudgetViewModel, modifier: Modifier) {
    val incomes by vm.incomes.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val bills by vm.bills.collectAsState()
    val settings by vm.settings.collectAsState()
    var editingIncome by remember { mutableStateOf(false) }
    var adjusting by rememberSaveable { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<Bill?>(null) }
    var payingBill by remember { mutableStateOf<Bill?>(null) }

    val plan = BudgetMath.monthPlan(settings, incomes, expenses)
    val (start, end) = Dates.range(Period.MONTH)
    val month = BudgetMath.summarize(incomes, expenses, start, end)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. The split, as a pie.
        SectionCard("Your monthly plan", emoji = "🎯", action = {
            TextButton(onClick = { adjusting = !adjusting }) { Text(if (adjusting) "Done" else "Adjust") }
        }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                DonutChart(
                    slices = plan.buckets.map { Slice(it.bucket.label, it.percent.toLong(), bucketColor(it.bucket)) },
                    centerTop = Money.format(plan.takeHomeCents),
                    centerBottom = "to plan / month",
                )
            }
            plan.buckets.forEach { b ->
                LegendRow(bucketColor(b.bucket), "${b.bucket.emoji} ${b.bucket.label}", b.plannedCents, b.percent.toLong())
            }
            Text(
                (if (plan.basisIsEstimate) "Based on about ${Money.format(plan.basisIncomeCents)}/month (from your last 4 weeks of pay)"
                else "Based on ${Money.format(plan.basisIncomeCents)}/month") +
                    ", minus ${settings.taxPercent}% for taxes (${Money.format(plan.taxCents)}).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { editingIncome = true }) { Text("Set what I expect to make") }

            if (adjusting) {
                HorizontalDivider()
                plan.buckets.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(bucketColor(b.bucket))
                        Text("  ${b.bucket.label}", modifier = Modifier.weight(1f))
                        Text("${b.percent}%", fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = b.percent.toFloat(),
                        onValueChange = { v ->
                            vm.updateSettings { s -> s.copy(bucketPercents = s.bucketPercents + (b.bucket to v.toInt())) }
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = bucketColor(b.bucket), activeTrackColor = bucketColor(b.bucket)),
                    )
                }
                val total = plan.percentTotal
                Text(
                    if (total == 100) "Adds up to 100% ✓" else "Adds up to $total% — make it 100% so every dollar has a job.",
                    color = if (total == 100) IncomeGreen else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                )
                TextButton(onClick = {
                    vm.updateSettings { it.copy(bucketPercents = Bucket.entries.associateWith { b -> b.defaultPercent }) }
                }) { Text("Reset to 50 / 25 / 5 / 15 / 5") }
            }
        }

        // 2. How each bucket is doing this month.
        SectionCard("This month so far", emoji = "📊") {
            plan.buckets.forEach { b ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${b.bucket.emoji} ${b.bucket.label}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${Money.format(b.usedCents)} of ${Money.format(b.plannedCents)}")
                    }
                    BudgetBar(b.usedCents, b.plannedCents, bucketColor(b.bucket))
                    val left = b.leftCents
                    val goodWhenFull = b.bucket == Bucket.SAVINGS || b.bucket == Bucket.DEBT
                    Text(
                        when {
                            goodWhenFull && left <= 0 -> "Goal met for this month 🎉"
                            goodWhenFull -> "${Money.format(left)} more to hit your ${b.percent}%"
                            left >= 0 -> "${Money.format(left)} left to spend"
                            else -> "${Money.format(-left)} over plan"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!goodWhenFull && left < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "Bills & needs: rent, bills, phone, groceries, gas, car, health. Wants: eating out, fun, shopping, " +
                    "subscriptions, bottles, prerolls. Savings come from the Goals tab; debt payments from the debt tracker.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 3. Budget summary, like the paper template.
        SectionCard("Budget summary", emoji = "🧮") {
            val tax = month.incomeTotal * settings.taxPercent / 100
            val remaining = month.incomeTotal - tax - month.savingsTotal - month.spendingTotal - month.debtTotal
            AmountRow("Total income", month.incomeTotal)
            AmountRow("− Set aside for taxes", tax)
            AmountRow("− Total savings", month.savingsTotal)
            AmountRow("− Total expenses", month.spendingTotal)
            AmountRow("− Total debt payments", month.debtTotal)
            HorizontalDivider()
            AmountRow("= Remaining balance", remaining, color = if (remaining >= 0) IncomeGreen else MaterialTheme.colorScheme.error, bold = true)
        }

        // 4. Monthly bills.
        SectionCard("Monthly bills", emoji = "📅", action = {
            TextButton(onClick = { editingBill = Bill(name = "", amountCents = 0, dueDay = 1) }) { Text("+ Add") }
        }) {
            val states = BudgetMath.billStates(bills, expenses)
            if (states.isEmpty()) Text("Add rent, phone, car insurance… and see what's due and what's paid.")
            states.forEach { state ->
                BillRow(state, onPay = { payingBill = state.bill }, onClick = { editingBill = state.bill })
            }
            if (states.isNotEmpty()) {
                val unpaid = states.filter { it.status != BillStatus.PAID }.sumOf { it.bill.amountCents }
                AmountRow("Still to pay this month", unpaid, bold = true)
            }
        }

        // 5. Notes.
        SectionCard("Notes & reminders", emoji = "📝") {
            var notes by remember { mutableStateOf(settings.notes) }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it; vm.updateSettings { s -> s.copy(notes = it) } },
                placeholder = { Text("e.g. Car registration due in March, save tips for Christmas…") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (editingIncome) {
        ExpectedIncomeDialog(
            currentCents = settings.expectedMonthlyIncomeCents,
            estimateCents = BudgetMath.estimatedMonthlyIncome(incomes),
            onDismiss = { editingIncome = false },
            onSave = { cents -> vm.updateSettings { it.copy(expectedMonthlyIncomeCents = cents) }; editingIncome = false },
        )
    }
    editingBill?.let { bill ->
        BillDialog(
            initial = bill,
            onDismiss = { editingBill = null },
            onSave = { vm.saveBill(it); editingBill = null },
            onDelete = if (bill.id != 0L) ({ vm.deleteBill(bill); editingBill = null }) else null,
        )
    }
    payingBill?.let { bill ->
        PayDialog("Pay ${bill.name}", bill.amountCents, onDismiss = { payingBill = null }) { vm.payBill(bill, it); payingBill = null }
    }
}

@Composable
internal fun BillRow(state: BillState, onPay: () -> Unit, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(state.bill.name, fontWeight = FontWeight.SemiBold)
            Text(
                "${Money.format(state.bill.amountCents)} · due ${Dates.formatShort(Dates.startOf(state.dueDate))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (state.status) {
            BillStatus.PAID -> AssistChip(onClick = {}, label = { Text("Paid ✓") },
                colors = AssistChipDefaults.assistChipColors(labelColor = IncomeGreen))
            else -> {
                Text(
                    when (state.status) {
                        BillStatus.OVERDUE -> "Late"
                        BillStatus.DUE_SOON -> if (state.daysUntilDue == 0L) "Today" else "${state.daysUntilDue}d"
                        else -> ""
                    },
                    color = if (state.status == BillStatus.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 8.dp),
                )
                FilledTonalButton(onClick = onPay) { Text("Pay") }
            }
        }
    }
}

/** Confirms an amount (pre-filled) for a bill or debt payment. */
@Composable
internal fun PayDialog(title: String, suggestedCents: Long, onDismiss: () -> Unit, onPay: (Long) -> Unit) {
    var amount by remember { mutableStateOf(Money.toInput(suggestedCents)) }
    val cents = Money.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { MoneyField(amount, { amount = it }, "Amount paid") },
        confirmButton = { TextButton(enabled = cents != null && cents > 0, onClick = { onPay(cents ?: 0) }) { Text("Mark paid") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ExpectedIncomeDialog(currentCents: Long, estimateCents: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var amount by remember { mutableStateOf(Money.toInput(currentCents)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly income") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("About how much do you make in a month? Leave blank to use your recent pay (≈ ${Money.format(estimateCents)}).")
                MoneyField(amount, { amount = it }, "Expected per month")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(Money.parse(amount) ?: 0) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BillDialog(initial: Bill, onDismiss: () -> Unit, onSave: (Bill) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial.name) }
    var amount by remember { mutableStateOf(Money.toInput(initial.amountCents)) }
    var day by remember { mutableStateOf(initial.dueDay.toString()) }
    var category by remember { mutableStateOf(initial.category) }
    val cents = Money.parse(amount)
    val dueDay = day.toIntOrNull()?.takeIf { it in 1..31 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New monthly bill" else "Edit bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Rent, Phone)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                MoneyField(amount, { amount = it }, "Amount")
                OutlinedTextField(
                    value = day,
                    onValueChange = { new -> if (new.length <= 2 && new.all(Char::isDigit)) day = new },
                    label = { Text("Due day of the month (1–31)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(220.dp),
                )
                ChoiceChips(Bucket.categoriesIn(Bucket.NEEDS) + Categories.SUBSCRIPTIONS + Categories.DEBT, category, { category = it })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && cents != null && cents > 0 && dueDay != null, onClick = {
                onSave(initial.copy(name = name.trim(), amountCents = cents ?: 0, dueDay = dueDay ?: 1, category = category))
            }) { Text("Save") }
        },
        dismissButton = {
            if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
