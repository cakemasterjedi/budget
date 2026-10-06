package com.gigbudget.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gigbudget.app.BudgetViewModel
import com.gigbudget.app.data.BillStatus
import com.gigbudget.app.data.Bucket
import com.gigbudget.app.data.BudgetMath
import com.gigbudget.app.data.GoalCategory
import com.gigbudget.app.data.Money
import com.gigbudget.app.ui.theme.PinkPurpleGradient
import com.gigbudget.app.ui.theme.bucketColor

/**
 * "Where does this paycheck go?" — splits one payout envelope-style using the plan's percentages,
 * and can move the savings envelope into goals in one tap.
 */
@Composable
fun PaycheckSplitDialog(vm: BudgetViewModel, initialCents: Long, onDismiss: () -> Unit) {
    val incomes by vm.incomes.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val goals by vm.goals.collectAsState()
    val bills by vm.bills.collectAsState()
    val debts by vm.debts.collectAsState()
    val settings by vm.settings.collectAsState()
    var amount by remember { mutableStateOf(Money.toInput(initialCents)) }
    var savedToGoals by remember { mutableStateOf(false) }
    val cents = Money.parse(amount) ?: 0
    val split = BudgetMath.splitPaycheck(cents, settings, goals, BudgetMath.averageWeeklyIncome(incomes))

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✂️ Split a paycheck", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                MoneyField(amount, { amount = it; savedToGoals = false }, "Paycheck amount")

                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(PinkPurpleGradient).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Every dollar gets a job", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
                    Text(Money.format(cents), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "🧾 ${Money.format(split.taxCents)} off the top for taxes (${settings.taxPercent}%), then your plan splits the rest.",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                val billsDue = BudgetMath.billStates(bills, expenses).filter { it.status != BillStatus.PAID }
                split.buckets.forEach { (bucket, value) ->
                    val pct = settings.bucketPercents[bucket] ?: bucket.defaultPercent
                    if (pct == 0 && value == 0L) return@forEach
                    Envelope(bucket, pct, value) {
                        when (bucket) {
                            Bucket.NEEDS -> if (billsDue.isNotEmpty()) {
                                Text("Coming up: " + billsDue.take(3).joinToString { "${it.bill.name} ${Money.format(it.bill.amountCents)}" },
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Bucket.DEBT -> debts.filter { it.balanceCents > 0 && it.minPaymentCents > 0 }.takeIf { it.isNotEmpty() }?.let { d ->
                                Text("Minimums: " + d.joinToString { "${it.name} ${Money.format(it.minPaymentCents)}" }, style = MaterialTheme.typography.bodySmall)
                            }
                            Bucket.WANTS -> Text("Fun money — bottles & prerolls come out of here 🍾🌿", style = MaterialTheme.typography.bodySmall)
                            Bucket.SAVINGS -> {
                                if (split.goalShares.isEmpty()) {
                                    Text("Add savings goals to split this into envelopes.", style = MaterialTheme.typography.bodySmall)
                                }
                                split.goalShares.forEach { (goal, share) ->
                                    Row(Modifier.fillMaxWidth()) {
                                        Text("${GoalCategory.of(goal.category).emoji} ${goal.name}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Text(Money.format(share), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                val unassigned = value - split.goalShares.sumOf { it.second }
                                if (split.goalShares.isNotEmpty() && unassigned > 0) {
                                    Text("${Money.format(unassigned)} extra — keep it in savings or start a new goal.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Bucket.GIVING -> {}
                        }
                    }
                }

                if (split.goalShares.isNotEmpty()) {
                    Button(
                        onClick = {
                            split.goalShares.forEach { (goal, share) -> vm.adjustGoal(goal, share) }
                            savedToGoals = true
                        },
                        enabled = !savedToGoals && cents > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (savedToGoals) "Saved to your goals ✓" else "Put ${Money.format(split.goalShares.sumOf { it.second })} into my goals")
                    }
                    Text(
                        "Then move the same amount to savings in your bank app (or into your cash envelopes).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Change the percentages on the Plan tab → Adjust.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** An envelope card: colored stripe in the bucket's color, name, percent and dollar amount. */
@Composable
private fun Envelope(bucket: Bucket, percent: Int, cents: Long, details: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(bucketColor(bucket)))
            Column(Modifier.padding(14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${bucket.emoji} ${bucket.label}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("$percent%", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                }
                Text(Money.format(cents), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                details()
            }
        }
    }
}
