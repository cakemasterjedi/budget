package com.gigbudget.app.autoimport

import com.gigbudget.app.data.AppDatabase
import com.gigbudget.app.data.Expense
import com.gigbudget.app.data.Income
import com.gigbudget.app.data.IncomeSources
import com.gigbudget.app.data.Money
import com.gigbudget.app.data.NotificationLog
import com.gigbudget.app.data.Outcomes
import com.gigbudget.app.data.Roles
import com.gigbudget.app.data.WatchedApp
import java.util.concurrent.TimeUnit

/** @param onIncomeAdded called after an auto-imported income is saved (used for Savings Scout alerts). */
class AutoImporter(
    private val db: AppDatabase,
    private val onIncomeAdded: suspend (Income) -> Unit = {},
) {

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

        // Android re-posts the same notification when it updates; this key makes re-posts no-ops.
        val logKey = "$packageName|$postedAt|${(title + text).hashCode()}"
        val log = db.notificationLogDao()
        if (log.exists(logKey)) return

        fun entry(amount: Long?, outcome: String, detail: String) = NotificationLog(
            packageName = packageName, appLabel = app.label, title = title, text = text, postedAt = postedAt,
            amountCents = amount, outcome = outcome, detail = detail, dedupeKey = logKey,
        )

        val parsed = NotificationParser.parse(
            app.role, title, text, KnownApps.incomeSourceFor(packageName) ?: IncomeSources.OTHER,
        )
        val logged = when (parsed) {
            is NotificationParser.Parsed.Skip -> entry(parsed.amountCents, Outcomes.SKIPPED, parsed.reason)
            is NotificationParser.Parsed.Match -> record(app, parsed.result, title, postedAt, logKey, ::entry)
        }
        log.insert(logged)
        log.prune()
    }

    private suspend fun record(
        app: WatchedApp,
        result: NotificationParser.Result,
        title: String,
        postedAt: Long,
        dedupeKey: String,
        entry: (Long?, String, String) -> NotificationLog,
    ): NotificationLog {
        val amount = result.amountCents
        return when (result.kind) {
            NotificationParser.Kind.INCOME -> {
                countedByGigApp(app, result.incomeSource, postedAt)?.let { gigRole ->
                    return entry(amount, Outcomes.SKIPPED, "Already counted from your ${Roles.label(gigRole)} app")
                }
                val income = Income(
                    source = result.incomeSource,
                    amountCents = amount,
                    date = postedAt,
                    note = listOf("Auto: ${app.label}", result.merchant).filter { it.isNotBlank() }.joinToString(" · "),
                    auto = true,
                    dedupeKey = dedupeKey,
                    sourcePackage = app.packageName,
                )
                if (db.incomeDao().insert(income) != -1L) onIncomeAdded(income)
                entry(amount, Outcomes.INCOME, "${result.incomeSource} income +${Money.format(amount)}")
            }
            NotificationParser.Kind.EXPENSE -> {
                db.expenseDao().insert(
                    Expense(
                        category = result.category,
                        amountCents = amount,
                        date = postedAt,
                        note = result.merchant.ifBlank { title },
                        auto = true,
                        sourceApp = app.label,
                        dedupeKey = dedupeKey,
                    )
                )
                entry(amount, Outcomes.EXPENSE, "${result.category} −${Money.format(amount)}")
            }
        }
    }

    /**
     * A DoorDash/Spark deposit in a bank app is skipped when the DoorDash/Spark app itself has logged
     * pay in the last two weeks — otherwise the same money would be counted twice. Returns that role.
     */
    private suspend fun countedByGigApp(app: WatchedApp, source: String, postedAt: Long): String? {
        if (app.role != Roles.BANK) return null
        val gigRole = when (source) {
            IncomeSources.DOORDASH -> Roles.DOORDASH
            IncomeSources.SPARK -> Roles.SPARK
            else -> return null
        }
        val since = postedAt - TimeUnit.DAYS.toMillis(GIG_APP_WINDOW_DAYS)
        return gigRole.takeIf { db.incomeDao().countAutoFromRole(source, gigRole, since) > 0 }
    }

    companion object {
        const val GIG_APP_WINDOW_DAYS = 14L
    }
}
