package com.gigbudget.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gigbudget.app.BudgetViewModel
import com.gigbudget.app.autoimport.MoneyNotificationListener
import com.gigbudget.app.autoimport.ScoutNotifier
import com.gigbudget.app.data.Categories
import com.gigbudget.app.data.Dates
import com.gigbudget.app.data.Money
import androidx.compose.ui.text.font.FontWeight
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.gigbudget.app.data.Outcomes
import com.gigbudget.app.data.Roles
import com.gigbudget.app.data.WatchedApp
import com.gigbudget.app.ui.theme.MoneyInColor

@Composable
fun SettingsScreen(vm: BudgetViewModel, modifier: Modifier) {
    var showLog by rememberSaveable { mutableStateOf(false) }
    if (showLog) {
        Box(modifier) { NotificationLogScreen(vm, onBack = { showLog = false }) }
        return
    }
    var editingCategories by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val apps by vm.watchedApps.collectAsState()
    val log by vm.notificationLog.collectAsState()
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
                        leadingContent = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MoneyInColor) },
                    )
                } else {
                    Text(
                        "Stack It can log your income and spending for you by reading the notifications " +
                            "DoorDash, Spark and your bank/payment apps already send (\"You earned \$X\", " +
                            "\"Deposit of \$X received\", \"You spent \$X at …\"). Everything stays on your phone — " +
                            "the app has no internet access."
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
            val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            val recent = log.filter { it.postedAt >= weekAgo }
            SectionCard("Captured notifications") {
                if (log.isEmpty()) {
                    Text("Nothing yet. Money notifications from the apps below will be listed here with what they became.")
                } else {
                    Text("Last 7 days:")
                    Text(
                        "${recent.count { it.outcome == Outcomes.INCOME }} income · " +
                            "${recent.count { it.outcome == Outcomes.EXPENSE }} spending · " +
                            "${recent.count { it.outcome == Outcomes.SKIPPED }} skipped",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Check the skipped ones — if something was real money, add it with one tap.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = { showLog = true }) { Text("View captured notifications") }
            }
        }

        item {
            SectionCard("Apps to watch") {
                Text(
                    "Choose what each app's notifications count as:\n" +
                        "• DoorDash pay / Spark pay — earnings from the gig app\n" +
                        "• Money in & out — purchases are spending; deposits and money sent to you are income\n" +
                        "• Spending only — ignore money coming in\n" +
                        "Apps that send money alerts show up here automatically — switch them on if they're yours.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "No double counting: a DoorDash or Spark deposit in your bank is skipped if the DoorDash/Spark " +
                        "app already logged pay in the last 2 weeks.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(apps, key = { it.packageName }) { app ->
            WatchedAppRow(app) { vm.setAppRole(app, it) }
            HorizontalDivider()
        }

        item {
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                vm.updateSettings { it.copy(scoutAlerts = granted) }
            }
            SectionCard("Savings Scout alerts", emoji = "🔔") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "After pay comes in, get a heads-up with how much you can safely save.",
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = settings.scoutAlerts && ScoutNotifier.canPost(context),
                        onCheckedChange = { on ->
                            if (on && !ScoutNotifier.canPost(context) && Build.VERSION.SDK_INT >= 33) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                vm.updateSettings { it.copy(scoutAlerts = on) }
                            }
                        },
                    )
                }
                Text(
                    "Works with auto-import. At most one alert every 3 hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard("Categories", emoji = "✏️") {
                Text("Rename the parts of your plan, change emojis, move or hide categories, or add your own.")
                OutlinedButton(onClick = { editingCategories = true }) { Text("Edit categories") }
            }
        }

        item {
            val driveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) vm.connectAutoBackup(uri) { result ->
                    Toast.makeText(
                        context,
                        if (result.isSuccess) "Google Drive backup is on 💜" else (result.exceptionOrNull()?.message ?: "Couldn't connect"),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            SectionCard("Google Drive backup", emoji = "☁️") {
                if (settings.autoBackupUri.isEmpty()) {
                    Text(
                        "Back up to Google Drive automatically, every day. Tap Connect, choose Google Drive " +
                            "(tap ☰ in the corner if you don't see it), pick a folder and tap Save. That's it.",
                    )
                    Button(onClick = { driveLauncher.launch("StackIt-auto-backup.json") }) { Text("Connect Google Drive") }
                    Text(
                        "Stack It can only see that one file — nothing else in your Drive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("✅ Backing up to ${settings.autoBackupName}", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (settings.lastAutoBackupAt == 0L) "No backup yet."
                        else "Last backup: ${Dates.formatLong(settings.lastAutoBackupAt)} at " +
                            DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(
                                Instant.ofEpochMilli(settings.lastAutoBackupAt).atZone(ZoneId.systemDefault())
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (settings.autoBackupError.isNotEmpty()) {
                        Text(settings.autoBackupError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "Updates every day, and when you leave the app if it's been 6+ hours.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            vm.autoBackupNow { result ->
                                Toast.makeText(context, if (result.isSuccess) "Backed up to Google Drive 💜" else "Backup failed — try reconnecting", Toast.LENGTH_LONG).show()
                            }
                        }) { Text("Back up now") }
                        OutlinedButton(onClick = { restoreUri = Uri.parse(settings.autoBackupUri) }) { Text("Restore") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { driveLauncher.launch("StackIt-auto-backup.json") }) { Text("Reconnect") }
                        TextButton(onClick = { vm.disconnectAutoBackup() }) { Text("Turn off") }
                    }
                }
            }
        }

        item {
            val backupName = "StackIt-backup-${LocalDate.now()}.json"
            val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) vm.exportTo(uri) { result ->
                    Toast.makeText(context, if (result.isSuccess) "Backup saved 💜" else "Couldn't save: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
            val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) restoreUri = uri
            }
            SectionCard("Backup file", emoji = "💾") {
                Text(
                    "Save an extra copy anywhere you like, or restore one from a file — after a reinstall or on a new phone.",
                )
                Text(
                    if (settings.lastBackupAt == 0L) "No backup yet." else "Last backup: ${Dates.formatLong(settings.lastBackupAt)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Button(onClick = { saveLauncher.launch(backupName) }) { Text("Save a backup") }
                OutlinedButton(onClick = { openLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }) {
                    Text("Restore from a backup")
                }
            }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Roll leftover into next month")
                        Text("Money you didn't spend (or overspent) carries over.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.carryOver, onCheckedChange = { on -> vm.updateSettings { it.copy(carryOver = on) } })
                }
                MoneyField(bottleLimit, { bottleLimit = it }, "Weekly ${Categories.label(Categories.BOTTLE).lowercase()} limit (blank = none)")
                MoneyField(prerollLimit, { prerollLimit = it }, "Weekly ${Categories.label(Categories.PREROLL).lowercase()} limit (blank = none)")
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

    if (editingCategories) CategoryEditorDialog(vm, onDismiss = { editingCategories = false })
    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Restore this backup?") },
            text = { Text("Everything in the app now is replaced with what's in the backup file.") },
            confirmButton = {
                TextButton(onClick = {
                    restoreUri = null
                    vm.restoreFrom(uri) { result ->
                        Toast.makeText(
                            context,
                            if (result.isSuccess) "Backup restored 💜" else (result.exceptionOrNull()?.message ?: "Couldn't restore that file"),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancel") } },
        )
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
