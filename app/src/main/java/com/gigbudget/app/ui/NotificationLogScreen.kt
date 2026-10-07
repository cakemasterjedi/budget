package com.gigbudget.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.gigbudget.app.BudgetViewModel
import com.gigbudget.app.autoimport.NotificationParser
import com.gigbudget.app.data.Categories
import com.gigbudget.app.data.Dates
import com.gigbudget.app.data.Expense
import com.gigbudget.app.data.Income
import com.gigbudget.app.data.IncomeSources
import com.gigbudget.app.data.Money
import com.gigbudget.app.data.NotificationLog
import com.gigbudget.app.data.Outcomes
import com.gigbudget.app.ui.theme.MoneyInColor
import com.gigbudget.app.ui.theme.MoneyOutColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

/** Every money notification from a watched app: what it became, or why it was skipped. */
@Composable
fun NotificationLogScreen(vm: BudgetViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val entries by vm.notificationLog.collectAsState()
    var skippedOnly by rememberSaveable { mutableStateOf(false) }
    var addingIncome by remember { mutableStateOf<NotificationLog?>(null) }
    var addingExpense by remember { mutableStateOf<NotificationLog?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val shown = if (skippedOnly) entries.filter { it.outcome == Outcomes.SKIPPED } else entries

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Captured notifications", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { confirmClear = true }, enabled = entries.isNotEmpty()) { Text("Clear") }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !skippedOnly, onClick = { skippedOnly = false }, label = { Text("All (${entries.size})") })
            FilterChip(
                selected = skippedOnly,
                onClick = { skippedOnly = true },
                label = { Text("Skipped (${entries.count { it.outcome == Outcomes.SKIPPED }})") },
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        if (entries.isEmpty()) "Nothing captured yet. Money notifications from the apps you watch will show up here."
                        else "Nothing skipped."
                    )
                }
            }
            items(shown, key = { it.id }) { entry ->
                LogCard(entry, onAddIncome = { addingIncome = entry }, onAddExpense = { addingExpense = entry })
            }
        }
    }

    addingIncome?.let { entry ->
        val source = NotificationParser.gigSource("${entry.title} ${entry.text}".lowercase()) ?: IncomeSources.OTHER
        IncomeDialog(
            initial = Income(source = source, amountCents = entry.amountCents ?: 0, date = entry.postedAt, note = entry.appLabel),
            onDismiss = { addingIncome = null },
            onSave = {
                vm.saveIncome(it)
                vm.markLogHandled(entry, Outcomes.INCOME, "Added by you: ${it.source} income +${Money.format(it.amountCents)}")
                addingIncome = null
            },
            onDelete = null,
        )
    }
    addingExpense?.let { entry ->
        val category = NotificationParser.guessCategory("${entry.title} ${entry.text}")
        ExpenseDialog(
            initial = Expense(category = category, amountCents = entry.amountCents ?: 0, date = entry.postedAt, note = entry.title, sourceApp = entry.appLabel),
            onDismiss = { addingExpense = null },
            onSave = {
                vm.saveExpense(it)
                vm.markLogHandled(entry, Outcomes.EXPENSE, "Added by you: ${Categories.label(it.category)} −${Money.format(it.amountCents)}")
                addingExpense = null
            },
            onDelete = null,
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the log?") },
            text = { Text("This only clears this list. Income and spending that were already added stay.") },
            confirmButton = { TextButton(onClick = { vm.clearNotificationLog(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LogCard(entry: NotificationLog, onAddIncome: () -> Unit, onAddExpense: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.appLabel, fontWeight = FontWeight.SemiBold)
                Text(
                    "${Dates.formatShort(entry.postedAt)} ${timeFormat.format(Instant.ofEpochMilli(entry.postedAt).atZone(ZoneId.systemDefault()))}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (entry.title.isNotBlank()) Text(entry.title, style = MaterialTheme.typography.bodyMedium)
            if (entry.text.isNotBlank()) Text(entry.text, style = MaterialTheme.typography.bodySmall, maxLines = 4)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            val color = when (entry.outcome) {
                Outcomes.INCOME -> MoneyInColor
                Outcomes.EXPENSE -> MoneyOutColor
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                if (entry.outcome == Outcomes.SKIPPED) "Skipped: ${entry.detail}" else entry.detail,
                color = color,
                style = MaterialTheme.typography.labelLarge,
            )
            if (entry.outcome == Outcomes.SKIPPED && entry.amountCents != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAddIncome) { Text("Add as income") }
                    OutlinedButton(onClick = onAddExpense) { Text("Add as spending") }
                }
            }
        }
    }
}
