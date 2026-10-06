package com.gigbudget.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.unit.dp
import com.gigbudget.app.BudgetViewModel
import com.gigbudget.app.data.BudgetMath
import com.gigbudget.app.data.Dates
import com.gigbudget.app.data.Income
import com.gigbudget.app.data.IncomeSources
import com.gigbudget.app.data.Money
import com.gigbudget.app.data.Period
import com.gigbudget.app.ui.theme.IncomeGreen

@Composable
fun IncomeScreen(vm: BudgetViewModel, modifier: Modifier) {
    val incomes by vm.incomes.collectAsState()
    val expenses by vm.expenses.collectAsState()
    var editing by remember { mutableStateOf<Income?>(null) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)) {
            item {
                val (start, end) = Dates.range(Period.WEEK)
                val week = BudgetMath.summarize(incomes, expenses, start, end)
                SectionCard("This week") {
                    IncomeSources.all.forEach { AmountRow(it, week.incomeBySource[it] ?: 0) }
                    HorizontalDivider()
                    AmountRow("Total", week.incomeTotal, color = IncomeGreen, bold = true)
                    AmountRow("4-week average", BudgetMath.averageWeeklyIncome(incomes))
                }
            }
            if (incomes.isEmpty()) item { EmptyState("No income yet. Tap “Add pay” after a shift.") }
            items(incomes, key = { it.id }) { income ->
                ListItem(
                    headlineContent = { Text(Money.format(income.amountCents), color = IncomeGreen) },
                    overlineContent = { Text(income.source) },
                    supportingContent = {
                        Text(listOf(Dates.formatShort(income.date), income.note).filter { it.isNotBlank() }.joinToString(" · "))
                    },
                    trailingContent = { if (income.auto) AutoTag() },
                    modifier = Modifier.clickable { editing = income },
                )
                HorizontalDivider()
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = Income(source = IncomeSources.DOORDASH, amountCents = 0, date = System.currentTimeMillis()) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add pay") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { income ->
        IncomeDialog(
            initial = income,
            onDismiss = { editing = null },
            onSave = { vm.saveIncome(it); editing = null },
            onDelete = if (income.id != 0L) ({ vm.deleteIncome(income); editing = null }) else null,
        )
    }
}

@Composable
internal fun IncomeDialog(initial: Income, onDismiss: () -> Unit, onSave: (Income) -> Unit, onDelete: (() -> Unit)?) {
    var source by remember { mutableStateOf(initial.source) }
    var amount by remember { mutableStateOf(Money.toInput(initial.amountCents)) }
    var date by remember { mutableStateOf(initial.date) }
    var note by remember { mutableStateOf(initial.note) }
    val cents = Money.parse(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add pay" else "Edit pay") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceChips(IncomeSources.all, source, { source = it })
                MoneyField(amount, { amount = it }, "Amount (incl. tips)")
                DateButton("Date", date, { if (it != null) date = it })
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null && cents > 0, onClick = {
                onSave(initial.copy(source = source, amountCents = cents ?: 0, date = date, note = note.trim()))
            }) { Text("Save") }
        },
        dismissButton = {
            if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
