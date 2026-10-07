package com.gigbudget.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gigbudget.app.BudgetViewModel
import com.gigbudget.app.data.Bucket
import com.gigbudget.app.data.Categories
import com.gigbudget.app.data.CategoryRules
import com.gigbudget.app.ui.theme.bucketColor

private val emojiChoices = listOf(
    "🏠", "💡", "📱", "🛒", "⛽", "🚗", "💊", "🍔", "🍾", "🌿", "🎉", "🛍️", "📺", "💅", "💇‍♀️", "👗",
    "👶", "🐶", "🎮", "✈️", "🎓", "💝", "🙏", "🐷", "💳", "📈", "🧾", "☕", "🍕", "💄", "🏋️", "🏷️",
)

/** Lets Niome rename the plan's five parts and add, rename, move or hide spending categories. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorDialog(vm: BudgetViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val rules = settings.categoryRules
    var editingBucket by remember { mutableStateOf<Bucket?>(null) }
    // Pair(category key or null for a new one, bucket to start in)
    var editingCategory by remember { mutableStateOf<Pair<String?, Bucket>?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✏️ Your categories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                Text(
                    "Rename the parts of your plan, change emojis, move a category to a different part, hide ones you " +
                        "don't use, or add your own. Your spending history stays put — only names and groups change.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Bucket.entries.forEach { bucket ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        val stripe = bucketColor(bucket)
                        Row(Modifier.drawBehind { drawRect(stripe, size = Size(8.dp.toPx(), size.height)) }) {
                            Column(Modifier.padding(start = 22.dp, top = 14.dp, end = 14.dp, bottom = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${bucket.emoji} ${bucket.label}", style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { editingBucket = bucket }) { Text("Rename") }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Bucket.categoriesIn(bucket).forEach { key ->
                                        val hidden = key in rules.hidden
                                        AssistChip(
                                            onClick = { editingCategory = key to bucket },
                                            label = { Text(Categories.display(key) + if (hidden) " (hidden)" else "") },
                                        )
                                    }
                                }
                                if (bucket != Bucket.SAVINGS && bucket != Bucket.DEBT) {
                                    TextButton(onClick = { editingCategory = null to bucket }) { Text("+ Add a category") }
                                }
                            }
                        }
                    }
                }

                OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset names and groups to default") }
                Text(
                    "Percentages for each part are on the Plan tab → Adjust.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    editingBucket?.let { bucket ->
        BucketDialog(
            bucket = bucket,
            onDismiss = { editingBucket = null },
            onSave = { name, emoji ->
                vm.updateCategoryRules { r ->
                    r.copy(
                        bucketNames = if (name == bucket.defaultLabel) r.bucketNames - bucket else r.bucketNames + (bucket to name),
                        bucketEmojis = if (emoji == bucket.defaultEmoji) r.bucketEmojis - bucket else r.bucketEmojis + (bucket to emoji),
                    )
                }
                editingBucket = null
            },
        )
    }
    editingCategory?.let { (key, bucket) ->
        CategoryDialog(
            key = key,
            startBucket = bucket,
            rules = rules,
            onDismiss = { editingCategory = null },
            onSave = { newRules -> vm.updateCategoryRules { newRules }; editingCategory = null },
            onDelete = if (key != null && key in rules.customCategories) ({ vm.deleteCustomCategory(key); editingCategory = null }) else null,
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset categories?") },
            text = {
                Text("Bucket names, emojis and groups go back to the defaults. Categories you added stay, " +
                    "and none of your spending is changed.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateCategoryRules { r ->
                        CategoryRules(
                            customCategories = r.customCategories,
                            categoryBuckets = r.categoryBuckets.filterKeys { it in r.customCategories },
                            categoryLabels = r.categoryLabels.filterKeys { it in r.customCategories },
                            categoryEmojis = r.categoryEmojis.filterKeys { it in r.customCategories },
                        )
                    }
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmojiPicker(selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        emojiChoices.forEach { e ->
            FilterChip(selected = e == selected, onClick = { onPick(e) }, label = { Text(e) })
        }
    }
}

@Composable
private fun BucketDialog(bucket: Bucket, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(bucket.label) }
    var emoji by remember { mutableStateOf(bucket.emoji) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename “${bucket.label}”") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { if (it.length <= 24) name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Emoji", style = MaterialTheme.typography.labelLarge)
                EmojiPicker(emoji) { emoji = it }
                TextButton(onClick = { name = bucket.defaultLabel; emoji = bucket.defaultEmoji }) {
                    Text("Use default (${bucket.defaultEmoji} ${bucket.defaultLabel})")
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), emoji) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Add a category ([key] null) or change one: name, emoji, which part of the plan it's in, hidden. */
@Composable
internal fun CategoryDialog(
    key: String?,
    startBucket: Bucket,
    rules: CategoryRules,
    onDismiss: () -> Unit,
    onSave: (CategoryRules) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val isNew = key == null
    val locked = key != null && key in Categories.locked
    var name by remember { mutableStateOf(if (key == null) "" else Categories.label(key)) }
    var emoji by remember { mutableStateOf(if (key == null) "🏷️" else Categories.emoji(key)) }
    var bucket by remember { mutableStateOf(startBucket) }
    var hidden by remember { mutableStateOf(key != null && key in rules.hidden) }
    var confirmDelete by remember { mutableStateOf(false) }

    val trimmed = name.trim()
    val taken = Categories.all.any { other ->
        other != key && (other.equals(trimmed, ignoreCase = true) || Categories.label(other).equals(trimmed, ignoreCase = true))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New category" else "Edit ${Categories.label(key!!)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    name, { if (it.length <= 24) name = it },
                    label = { Text("Name (e.g. Nails, Hair, Kids)") },
                    singleLine = true,
                    isError = taken,
                    supportingText = if (taken) ({ Text("You already have a category called that.") }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Emoji", style = MaterialTheme.typography.labelLarge)
                EmojiPicker(emoji) { emoji = it }
                if (locked) {
                    Text(
                        "${Categories.label(key!!)} always stays in ${Bucket.of(key).label} because it's tied to your " +
                            (if (key == Categories.SAVINGS) "savings goals." else "debt tracker."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text("Part of your plan", style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(
                        Bucket.entries.filter { it != Bucket.SAVINGS && it != Bucket.DEBT }.map { it.name },
                        bucket.name,
                        { bucket = Bucket.valueOf(it) },
                    ) { Bucket.valueOf(it).let { b -> "${b.emoji} ${b.label}" } }
                }
                if (!isNew && key != Categories.OTHER && key != Categories.SAVINGS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Hide from lists")
                            Text("Spending already in it still counts.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = hidden, onCheckedChange = { hidden = it })
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete this category", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = trimmed.isNotEmpty() && !taken, onClick = {
                val k = key ?: trimmed
                val defaultBucket = if (k in Categories.builtIn) Bucket.defaultOf(k) else null
                onSave(
                    rules.copy(
                        customCategories = if (isNew) rules.customCategories + k else rules.customCategories,
                        categoryLabels = if (trimmed == k) rules.categoryLabels - k else rules.categoryLabels + (k to trimmed),
                        categoryEmojis = rules.categoryEmojis + (k to emoji),
                        categoryBuckets = when {
                            locked -> rules.categoryBuckets - k
                            bucket == defaultBucket -> rules.categoryBuckets - k
                            else -> rules.categoryBuckets + (k to bucket)
                        },
                        hidden = if (hidden) rules.hidden + k else rules.hidden - k,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (confirmDelete && key != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${Categories.label(key)}?") },
            text = { Text("Anything you logged under it moves to ${Categories.label(Categories.OTHER)}. Nothing is lost.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete?.invoke() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
