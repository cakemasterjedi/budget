package com.gigbudget.app.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gigbudget.app.BudgetViewModel
import com.gigbudget.app.autoimport.MoneyNotificationListener
import com.gigbudget.app.data.Money
import com.gigbudget.app.data.Roles
import com.gigbudget.app.data.WatchedApp
import com.gigbudget.app.ui.theme.IncomeGreen

@Composable
fun SettingsScreen(vm: BudgetViewModel, modifier: Modifier) {
    val apps by vm.watchedApps.collectAsState()
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(MoneyNotificationListener.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        enabled = MoneyNotificationListener.isEnabled(context)
        onPauseOrDispose { }
    }

    var tax by remember(settings.taxPercent) { mutableStateOf(settings.taxPercent.toString()) }
    var bottleLimit by remember(settings.bottleWeeklyLimitCents) { mutableStateOf(Money.toInput(settings.bottleWeeklyLimitCents)) }
    var prerollLimit by remember(settings.prerollWeeklyLimitCents) { mutableStateOf(Money.toInput(settings.prerollWeeklyLimitCents)) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard("Automatic import") {
                if (enabled) {
                    ListItem(
                        headlineContent = { Text("On — watching notifications") },
                        leadingContent = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = IncomeGreen) },
                    )
                } else {
                    Text(
                        "Gig Budget can log your pay and spending for you by reading the notifications " +
                            "DoorDash, Spark and your bank/payment apps already send (\"You earned \$X\", " +
                            "\"You spent \$X at …\"). Everything stays on your phone — the app has no internet access."
                    )
                    Text(
                        "Make sure those apps have notifications turned on, then allow access:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val openSettings = {
                    context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                if (enabled) OutlinedButton(onClick = openSettings) { Text("Notification access settings") }
                else Button(onClick = openSettings) { Text("Allow notification access") }
            }
        }

        item {
            SectionCard("Apps to watch") {
                Text(
                    "Choose what each app's notifications count as. Apps that send money alerts show up here " +
                        "automatically — switch them on if they're yours. Only turn on one app per payout " +
                        "(e.g. Dasher app OR DasherDirect) so pay isn't counted twice.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(apps, key = { it.packageName }) { app ->
            WatchedAppRow(app) { vm.setAppRole(app, it) }
            HorizontalDivider()
        }

        item {
            SectionCard("Budget settings") {
                OutlinedTextField(
                    value = tax,
                    onValueChange = { new -> if (new.length <= 2 && new.all(Char::isDigit)) tax = new },
                    label = { Text("Set aside for taxes (%)") },
                    supportingText = { Text("Self-employment tax alone is ~15%. 25–30% is a safe range for most gig workers.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                MoneyField(bottleLimit, { bottleLimit = it }, "Weekly bottle limit (blank = none)")
                MoneyField(prerollLimit, { prerollLimit = it }, "Weekly preroll limit (blank = none)")
                Button(onClick = {
                    vm.updateSettings {
                        it.copy(
                            taxPercent = tax.toIntOrNull() ?: it.taxPercent,
                            bottleWeeklyLimitCents = Money.parse(bottleLimit) ?: 0,
                            prerollWeeklyLimitCents = Money.parse(prerollLimit) ?: 0,
                        )
                    }
                }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun WatchedAppRow(app: WatchedApp, onRoleChange: (String) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(app.label) },
        supportingContent = {
            Column {
                if (app.lastSample.isNotBlank()) {
                    Text("Last: ${app.lastSample}", maxLines = 2, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        trailingContent = {
            Box {
                TextButton(onClick = { menuOpen = true }) {
                    Text(Roles.label(app.role))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    Roles.all.forEach { role ->
                        DropdownMenuItem(text = { Text(Roles.label(role)) }, onClick = { onRoleChange(role); menuOpen = false })
                    }
                }
            }
        },
    )
}
