package com.gigbudget.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gigbudget.app.BudgetViewModel
import com.gigbudget.app.data.BudgetMath
import com.gigbudget.app.data.GoalCategory
import com.gigbudget.app.data.Money
import com.gigbudget.app.data.SavingsGoal
import com.gigbudget.app.ui.theme.MoneyInColor

/**
 * Savings Scout: looks at what's left this month, bills and debt payments still due and your
 * everyday spending pace, and suggests a safe $5–$50 to move into a goal.
 */
@Composable
fun SavingsScoutCard(vm: BudgetViewModel, onNeedGoal: () -> Unit) {
    val incomes by vm.incomes.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val bills by vm.bills.collectAsState()
    val debts by vm.debts.collectAsState()
    val goals by vm.goals.collectAsState()
    val settings by vm.settings.collectAsState()
    var showMath by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val scout = BudgetMath.savingsScout(settings, incomes, expenses, bills, debts)

    SectionCard("Savings Scout", emoji = "🔍", action = {
        TextButton(onClick = { showMath = !showMath }) { Text(if (showMath) "Hide" else "How?") }
    }) {
        if (scout.amountCents > 0) {
            Text("You can safely save", style = MaterialTheme.typography.bodyMedium)
            Text(
                "${Money.format(scout.amountCents)} today",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MoneyInColor,
            )
            FilledTonalButton(onClick = { if (goals.isEmpty()) onNeedGoal() else saving = true }) {
                Text(if (goals.isEmpty()) "Make a goal to save it in" else "Save ${Money.format(scout.amountCents)}")
            }
        } else {
            Text("Not today 💜", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Your money's spoken for until more pay comes in. Scout checks again after every payout" +
                    (if (settings.scoutAlerts) " and will let you know." else "."),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (showMath) {
            HorizontalDivider()
            AmountRow("Left this month", scout.leftCents)
            AmountRow("− Bills still due", scout.billsDueCents)
            AmountRow("− Minimum debt payments due", scout.debtDueCents)
            AmountRow("− Everyday spending, ${scout.daysLeft} days", scout.expectedSpendingCents)
            AmountRow("− Safety cushion", scout.safetyCents)
            val spare = scout.leftCents - scout.billsDueCents - scout.debtDueCents - scout.expectedSpendingCents - scout.safetyCents
            AmountRow("= Spare", spare, bold = true)
            Text(
                "Scout suggests half of what's spare, between \$5 and \$50, so you're never left short. " +
                    "Add your monthly bills on the Plan tab to make it smarter.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (saving) {
        SaveToGoalDialog(
            amountCents = scout.amountCents,
            goals = goals,
            onDismiss = { saving = false },
            onSave = { goal, cents -> vm.adjustGoal(goal, cents); saving = false },
        )
    }
}

@Composable
private fun SaveToGoalDialog(amountCents: Long, goals: List<SavingsGoal>, onDismiss: () -> Unit, onSave: (SavingsGoal, Long) -> Unit) {
    // Emergency fund first, then the goal due soonest.
    val ordered = goals.sortedWith(compareBy({ it.category != GoalCategory.EMERGENCY.name }, { it.dueDate ?: Long.MAX_VALUE }))
    var selectedId by remember { mutableLongStateOf(ordered.firstOrNull { it.savedCents < it.targetCents }?.id ?: ordered.first().id) }
    var amount by remember { mutableStateOf(Money.toInput(amountCents)) }
    val cents = Money.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save it") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(amount, { amount = it }, "Amount")
                Text("Put it toward:", style = MaterialTheme.typography.labelLarge)
                ordered.forEach { goal ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = goal.id == selectedId, onClick = { selectedId = goal.id }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = goal.id == selectedId, onClick = { selectedId = goal.id })
                        Text("${GoalCategory.of(goal.category).emoji} ${goal.name}", modifier = Modifier.weight(1f))
                        Text("${Money.format(goal.savedCents)} / ${Money.format(goal.targetCents)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    "Move the money to your savings account in your bank app too — Stack It keeps score, your bank holds the cash.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null && cents > 0, onClick = {
                onSave(goals.first { it.id == selectedId }, cents ?: 0)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
fun MilestoneDialog(celebration: BudgetViewModel.Celebration, onDismiss: () -> Unit) {
    val message = when (celebration.percent) {
        100 -> "You did it! ${celebration.goalName} is fully funded."
        75 -> "75% of the way to ${celebration.goalName}. The finish line is right there!"
        50 -> "Halfway to ${celebration.goalName}! Keep it going."
        else -> "25% saved for ${celebration.goalName}. Great start!"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (celebration.percent == 100) "🎉 Goal reached!" else "${celebration.emoji} ${celebration.percent}% there!",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = { Text(message, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Yay! 💜") } },
    )
}
