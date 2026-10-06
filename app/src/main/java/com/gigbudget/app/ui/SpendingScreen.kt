package com.gigbudget.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import com.gigbudget.app.data.Categories
import com.gigbudget.app.data.Dates
import com.gigbudget.app.data.Expense
import com.gigbudget.app.data.Money
import com.gigbudget.app.data.Period
import com.gigbudget.app.ui.theme.SpendRed

@Composable
fun SpendingScreen(vm: BudgetViewModel, modifier: Modifier) {
    val incomes by vm.incomes.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val settings by vm.settings.collectAsState()
    var editing by remember { mutableStateOf<Expense?>(null) }

    fun newExpense(category: String, priceCents: Long = 0) =
        Expense(category = category, amountCents = priceCents, date = System.currentTimeMillis())

    Box(modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)) {
            item {
                val (start, end) = Dates.range(Period.WEEK)
                val week = BudgetMath.summarize(incomes, expenses, start, end)
                SectionCard("Quick add") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = { editing = newExpense(Categories.BOTTLE, settings.lastBottlePriceCents) },
                            modifier = Modifier.weight(1f),
                        ) { Text("🍾 Bottle") }
                        FilledTonalButton(
                            onClick = { editing = newExpense(Categories.PREROLL, settings.lastPrerollPriceCents) },
                            modifier = Modifier.weight(1f),
                        ) { Text("🌿 Preroll") }
                    }
                    Text(
                        "This week: ${week.bottleCount} bottles (${Money.format(week.bottleTotal)}), " +
                            "${week.prerollCount} prerolls (${Money.format(week.prerollTotal)})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    AmountRow("All spending this week", week.spendingTotal, color = SpendRed, bold = true)
                }
            }
            if (expenses.isEmpty()) item { EmptyState("No spending logged yet.") }
            items(expenses, key = { it.id }) { expense ->
                ListItem(
                    headlineContent = { Text(Money.format(expense.amountCents), color = SpendRed) },
                    overlineContent = { Text(expense.category) },
                    supportingContent = {
                        Text(
                            listOf(Dates.formatShort(expense.date), expense.note, expense.sourceApp)
                                .filter { it.isNotBlank() }.joinToString(" · ")
                        )
                    },
                    trailingContent = { if (expense.auto) AutoTag() },
                    modifier = Modifier.clickable { editing = expense },
                )
                HorizontalDivider()
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = newExpense(Categories.OTHER) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add spending") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { expense ->
        ExpenseDialog(
            initial = expense,
            onDismiss = { editing = null },
            onSave = { vm.saveExpense(it); editing = null },
            onDelete = if (expense.id != 0L) ({ vm.deleteExpense(expense); editing = null }) else null,
        )
    }
}

@Composable
internal fun ExpenseDialog(initial: Expense, onDismiss: () -> Unit, onSave: (Expense) -> Unit, onDelete: (() -> Unit)?) {
    var category by remember { mutableStateOf(initial.category) }
    var amount by remember { mutableStateOf(Money.toInput(initial.amountCents)) }
    var date by remember { mutableStateOf(initial.date) }
    var note by remember { mutableStateOf(initial.note) }
    val cents = Money.parse(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add spending" else "Edit spending") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MoneyField(amount, { amount = it }, "Amount")
                ChoiceChips(Categories.all, category, { category = it })
                DateButton("Date", date, { if (it != null) date = it })
                OutlinedTextField(note, { note = it }, label = { Text("Where / note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (initial.auto) {
                    Text("Imported automatically from ${initial.sourceApp}. Fix the category if it guessed wrong.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null && cents > 0, onClick = {
                onSave(initial.copy(category = category, amountCents = cents ?: 0, date = date, note = note.trim()))
            }) { Text("Save") }
        },
        dismissButton = {
            if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
