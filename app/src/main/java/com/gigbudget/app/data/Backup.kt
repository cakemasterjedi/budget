package com.gigbudget.app.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saves everything (income, spending, goals, debts, bills, watched apps, categories and settings)
 * to one JSON file, and restores it. The notification log isn't included; it's just history.
 */
object Backup {
    private const val FORMAT = 1

    suspend fun export(db: AppDatabase, settings: SettingsStore, now: Long = System.currentTimeMillis()): String =
        JSONObject().apply {
            put("app", "Stack It, Niome")
            put("format", FORMAT)
            put("createdAt", now)
            put("income", JSONArray(db.incomeDao().list().map { it.toJson() }))
            put("expenses", JSONArray(db.expenseDao().list().map { it.toJson() }))
            put("goals", JSONArray(db.goalDao().list().map { it.toJson() }))
            put("debts", JSONArray(db.debtDao().list().map { it.toJson() }))
            put("bills", JSONArray(db.billDao().list().map { it.toJson() }))
            put("watchedApps", JSONArray(db.watchedAppDao().list().map { it.toJson() }))
            put("settings", settings.exportJson())
        }.toString(2)

    /**
     * Replaces the app's data with the backup's. The whole file is read and checked first, so a bad
     * file throws [IllegalArgumentException] and leaves the current data untouched.
     */
    suspend fun restore(db: AppDatabase, settings: SettingsStore, json: String) {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("That file isn't a Stack It backup.", e)
        }
        if (!root.has("format") || !root.has("settings")) throw IllegalArgumentException("That file isn't a Stack It backup.")
        if (root.getInt("format") > FORMAT) throw IllegalArgumentException("That backup is from a newer version of the app. Update the app first.")

        val parsed = try {
            Parsed(
                income = root.objects("income").map(::income),
                expenses = root.objects("expenses").map(::expense),
                goals = root.objects("goals").map(::goal),
                debts = root.objects("debts").map(::debt),
                bills = root.objects("bills").map(::bill),
                watchedApps = root.objects("watchedApps").map(::watchedApp),
                settings = root.getJSONObject("settings"),
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("That backup file is damaged.", e)
        }

        db.withTransaction {
            db.incomeDao().deleteAll(); db.incomeDao().insertAll(parsed.income)
            db.expenseDao().deleteAll(); db.expenseDao().insertAll(parsed.expenses)
            db.goalDao().deleteAll(); db.goalDao().insertAll(parsed.goals)
            db.debtDao().deleteAll(); db.debtDao().insertAll(parsed.debts)
            db.billDao().deleteAll(); db.billDao().insertAll(parsed.bills)
            db.watchedAppDao().deleteAll(); db.watchedAppDao().insertAll(parsed.watchedApps)
        }
        settings.importJson(parsed.settings)
    }

    private class Parsed(
        val income: List<Income>,
        val expenses: List<Expense>,
        val goals: List<SavingsGoal>,
        val debts: List<Debt>,
        val bills: List<Bill>,
        val watchedApps: List<WatchedApp>,
        val settings: JSONObject,
    )

    private fun JSONObject.objects(key: String): List<JSONObject> =
        optJSONArray(key)?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: emptyList()

    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else getLong(key)
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)
    private fun Any?.orNull(): Any = this ?: JSONObject.NULL

    private fun Income.toJson() = JSONObject()
        .put("id", id).put("source", source).put("amountCents", amountCents).put("date", date).put("note", note)
        .put("auto", auto).put("dedupeKey", dedupeKey.orNull()).put("sourcePackage", sourcePackage)

    private fun income(o: JSONObject) = Income(
        id = o.getLong("id"), source = o.getString("source"), amountCents = o.getLong("amountCents"), date = o.getLong("date"),
        note = o.optString("note"), auto = o.optBoolean("auto"), dedupeKey = o.stringOrNull("dedupeKey"),
        sourcePackage = o.optString("sourcePackage"),
    )

    private fun Expense.toJson() = JSONObject()
        .put("id", id).put("category", category).put("amountCents", amountCents).put("date", date).put("note", note)
        .put("auto", auto).put("sourceApp", sourceApp).put("dedupeKey", dedupeKey.orNull())
        .put("goalId", goalId.orNull()).put("debtId", debtId.orNull()).put("billId", billId.orNull())

    private fun expense(o: JSONObject) = Expense(
        id = o.getLong("id"), category = o.getString("category"), amountCents = o.getLong("amountCents"), date = o.getLong("date"),
        note = o.optString("note"), auto = o.optBoolean("auto"), sourceApp = o.optString("sourceApp"),
        dedupeKey = o.stringOrNull("dedupeKey"), goalId = o.longOrNull("goalId"), debtId = o.longOrNull("debtId"),
        billId = o.longOrNull("billId"),
    )

    private fun SavingsGoal.toJson() = JSONObject()
        .put("id", id).put("name", name).put("targetCents", targetCents).put("savedCents", savedCents)
        .put("dueDate", dueDate.orNull()).put("category", category)

    private fun goal(o: JSONObject) = SavingsGoal(
        id = o.getLong("id"), name = o.getString("name"), targetCents = o.getLong("targetCents"),
        savedCents = o.optLong("savedCents"), dueDate = o.longOrNull("dueDate"),
        category = o.optString("category", GoalCategory.OTHER.name),
    )

    private fun Debt.toJson() = JSONObject()
        .put("id", id).put("name", name).put("balanceCents", balanceCents).put("minPaymentCents", minPaymentCents).put("apr", apr)

    private fun debt(o: JSONObject) = Debt(
        id = o.getLong("id"), name = o.getString("name"), balanceCents = o.getLong("balanceCents"),
        minPaymentCents = o.optLong("minPaymentCents"), apr = o.optDouble("apr", 0.0),
    )

    private fun Bill.toJson() = JSONObject()
        .put("id", id).put("name", name).put("amountCents", amountCents).put("dueDay", dueDay).put("category", category)

    private fun bill(o: JSONObject) = Bill(
        id = o.getLong("id"), name = o.getString("name"), amountCents = o.getLong("amountCents"),
        dueDay = o.getInt("dueDay"), category = o.optString("category", Categories.BILLS),
    )

    private fun WatchedApp.toJson() = JSONObject()
        .put("packageName", packageName).put("label", label).put("role", role).put("lastSeen", lastSeen).put("lastSample", lastSample)

    private fun watchedApp(o: JSONObject) = WatchedApp(
        packageName = o.getString("packageName"), label = o.getString("label"), role = o.getString("role"),
        lastSeen = o.optLong("lastSeen"), lastSample = o.optString("lastSample"),
    )
}
