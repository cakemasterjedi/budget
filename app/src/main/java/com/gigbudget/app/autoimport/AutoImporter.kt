package com.gigbudget.app.autoimport

import com.gigbudget.app.data.AppDatabase
import com.gigbudget.app.data.Expense
import com.gigbudget.app.data.Income
import com.gigbudget.app.data.Roles
import com.gigbudget.app.data.WatchedApp

class AutoImporter(private val db: AppDatabase) {

    suspend fun handle(
        packageName: String,
        appLabel: String,
        title: String,
        text: String,
        postedAt: Long,
    ) {
        val apps = db.watchedAppDao()
        val sample = listOf(title, text).filter { it.isNotBlank() }.joinToString(" — ").take(200)
        val app = apps.get(packageName)
        if (app == null) {
            // First money-looking notification from an unknown app: list it in Settings, switched off.
            apps.insertIgnore(WatchedApp(packageName, appLabel, Roles.IGNORE, postedAt, sample))
            return
        }
        apps.update(app.copy(lastSeen = postedAt, lastSample = sample))
        if (app.role == Roles.IGNORE) return

        val result = NotificationParser.parse(app.role, title, text) ?: return
        // Android re-posts the same notification when it updates; this key makes re-posts no-ops.
        val dedupeKey = "$packageName|$postedAt|${result.amountCents}|${(title + text).hashCode()}"
        when (result.kind) {
            NotificationParser.Kind.INCOME -> db.incomeDao().insert(
                Income(
                    source = result.incomeSource,
                    amountCents = result.amountCents,
                    date = postedAt,
                    note = "Auto: ${app.label}",
                    auto = true,
                    dedupeKey = dedupeKey,
                )
            )
            NotificationParser.Kind.EXPENSE -> db.expenseDao().insert(
                Expense(
                    category = result.category,
                    amountCents = result.amountCents,
                    date = postedAt,
                    note = result.merchant.ifBlank { title },
                    auto = true,
                    sourceApp = app.label,
                    dedupeKey = dedupeKey,
                )
            )
        }
    }
}
