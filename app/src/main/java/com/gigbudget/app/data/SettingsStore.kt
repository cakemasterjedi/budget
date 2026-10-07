package com.gigbudget.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    /** Percent of gig income to set aside for taxes (gig workers are self-employed). */
    val taxPercent: Int = 25,
    /** Weekly spending limits; 0 means no limit. */
    val bottleWeeklyLimitCents: Long = 0,
    val prerollWeeklyLimitCents: Long = 0,
    /** Remembered so the quick-add buttons can pre-fill the usual price. */
    val lastBottlePriceCents: Long = 0,
    val lastPrerollPriceCents: Long = 0,
    /** Monthly plan split, in percent of take-home pay. Keyed by [Bucket.name]. */
    val bucketPercents: Map<Bucket, Int> = Bucket.entries.associateWith { it.defaultPercent },
    /** What you expect to make in a month. 0 = estimate from the last 4 weeks of pay. */
    val expectedMonthlyIncomeCents: Long = 0,
    val notes: String = "",
    /** Post a Savings Scout notification after an auto-imported payout. */
    val scoutAlerts: Boolean = true,
    val lastScoutAlertAt: Long = 0,
    /** Monthly spending budget per category (Groceries $500, Gas $100…). Missing = no budget. */
    val categoryBudgets: Map<String, Long> = emptyMap(),
    /** Carry what's left over (or overspent) into the next month. */
    val carryOver: Boolean = true,
    /** Renamed buckets, custom / moved / hidden categories. */
    val categoryRules: CategoryRules = CategoryRules(),
    /** When the last backup file was saved by hand (0 = never). */
    val lastBackupAt: Long = 0,
    /** The Google Drive (or other) file that automatic backups overwrite; empty = not connected. */
    val autoBackupUri: String = "",
    val autoBackupName: String = "",
    val lastAutoBackupAt: Long = 0,
    /** Why the last automatic backup failed, or empty. */
    val autoBackupError: String = "",
    /** Which version of the built-in "apps to watch" list this install has picked up. */
    val knownAppsVersion: Int = 1,
)

private const val BUDGET_PREFIX = "cb_"

/** Settings tied to this phone (its permission to the Drive file), left out of backup files. */
private val DEVICE_ONLY_KEYS = setOf("autoBackupUri", "autoBackupName", "lastAutoBackupAt", "autoBackupError")

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load().also { CategoryRules.current = it.categoryRules })
    val state: StateFlow<Settings> = _state.asStateFlow()

    /**
     * Read-modify-write of all settings. Synchronized because updates come from the UI and from
     * background work (auto-import, backups, startup); without it, two at once could lose one change.
     */
    @Synchronized
    fun update(transform: (Settings) -> Settings) {
        val next = transform(_state.value)
        prefs.edit()
            .putInt("taxPercent", next.taxPercent)
            .putLong("bottleWeeklyLimitCents", next.bottleWeeklyLimitCents)
            .putLong("prerollWeeklyLimitCents", next.prerollWeeklyLimitCents)
            .putLong("lastBottlePriceCents", next.lastBottlePriceCents)
            .putLong("lastPrerollPriceCents", next.lastPrerollPriceCents)
            .putLong("expectedMonthlyIncomeCents", next.expectedMonthlyIncomeCents)
            .putString("notes", next.notes)
            .putBoolean("scoutAlerts", next.scoutAlerts)
            .putLong("lastScoutAlertAt", next.lastScoutAlertAt)
            .putBoolean("carryOver", next.carryOver)
            .putString("categoryRules", next.categoryRules.toJson())
            .putLong("lastBackupAt", next.lastBackupAt)
            .putString("autoBackupUri", next.autoBackupUri)
            .putString("autoBackupName", next.autoBackupName)
            .putLong("lastAutoBackupAt", next.lastAutoBackupAt)
            .putString("autoBackupError", next.autoBackupError)
            .putInt("knownAppsVersion", next.knownAppsVersion)
            .also { editor ->
                next.bucketPercents.forEach { (bucket, pct) -> editor.putInt("pct_${bucket.name}", pct) }
                // Category budgets live under "cb_<category>"; rewrite them all so removed ones disappear.
                prefs.all.keys.filter { it.startsWith(BUDGET_PREFIX) }.forEach { editor.remove(it) }
                next.categoryBudgets.filterValues { it > 0 }.forEach { (category, budget) -> editor.putLong(BUDGET_PREFIX + category, budget) }
            }
            .apply()
        CategoryRules.current = next.categoryRules
        _state.value = next
    }

    /** Re-reads everything from disk (after a restore). */
    @Synchronized
    fun reload() {
        val loaded = load()
        CategoryRules.current = loaded.categoryRules
        _state.value = loaded
    }

    /** Every saved preference, tagged with its type, for the backup file. */
    fun exportJson(): JSONObject = JSONObject().also { out ->
        prefs.all.forEach { (key, value) ->
            if (key in DEVICE_ONLY_KEYS) return@forEach
            val (type, v) = when (value) {
                is Boolean -> "boolean" to value
                is Int -> "int" to value
                is Long -> "long" to value
                is Float -> "float" to value.toDouble()
                is String -> "string" to value
                is Set<*> -> "stringSet" to JSONArray(value.map { it.toString() })
                else -> return@forEach
            }
            out.put(key, JSONObject().put("type", type).put("value", v))
        }
    }

    /** Replaces all preferences with the ones from a backup file, then reloads. */
    @Synchronized
    fun importJson(json: JSONObject) {
        // The Drive connection belongs to this phone, so restoring a backup keeps it as it is.
        val keep = prefs.all.filterKeys { it in DEVICE_ONLY_KEYS }
        val editor = prefs.edit().clear()
        keep.forEach { (k, v) ->
            when (v) {
                is String -> editor.putString(k, v)
                is Long -> editor.putLong(k, v)
            }
        }
        json.keys().forEach { key ->
            if (key in DEVICE_ONLY_KEYS) return@forEach
            val entry = json.getJSONObject(key)
            when (entry.getString("type")) {
                "boolean" -> editor.putBoolean(key, entry.getBoolean("value"))
                "int" -> editor.putInt(key, entry.getInt("value"))
                "long" -> editor.putLong(key, entry.getLong("value"))
                "float" -> editor.putFloat(key, entry.getDouble("value").toFloat())
                "string" -> editor.putString(key, entry.getString("value"))
                "stringSet" -> editor.putStringSet(key, entry.getJSONArray("value").let { a -> List(a.length()) { a.getString(it) }.toSet() })
            }
        }
        editor.commit()
        reload()
    }

    private fun load() = Settings(
        taxPercent = prefs.getInt("taxPercent", 25),
        bottleWeeklyLimitCents = prefs.getLong("bottleWeeklyLimitCents", 0),
        prerollWeeklyLimitCents = prefs.getLong("prerollWeeklyLimitCents", 0),
        lastBottlePriceCents = prefs.getLong("lastBottlePriceCents", 0),
        lastPrerollPriceCents = prefs.getLong("lastPrerollPriceCents", 0),
        bucketPercents = Bucket.entries.associateWith { prefs.getInt("pct_${it.name}", it.defaultPercent) },
        expectedMonthlyIncomeCents = prefs.getLong("expectedMonthlyIncomeCents", 0),
        notes = prefs.getString("notes", "").orEmpty(),
        scoutAlerts = prefs.getBoolean("scoutAlerts", true),
        lastScoutAlertAt = prefs.getLong("lastScoutAlertAt", 0),
        categoryBudgets = prefs.all.filterKeys { it.startsWith(BUDGET_PREFIX) }
            .mapNotNull { (k, v) -> (v as? Long)?.takeIf { it > 0 }?.let { k.removePrefix(BUDGET_PREFIX) to it } }.toMap(),
        carryOver = prefs.getBoolean("carryOver", true),
        categoryRules = runCatching { CategoryRules.fromJson(prefs.getString("categoryRules", null)) }.getOrDefault(CategoryRules()),
        lastBackupAt = prefs.getLong("lastBackupAt", 0),
        autoBackupUri = prefs.getString("autoBackupUri", "").orEmpty(),
        autoBackupName = prefs.getString("autoBackupName", "").orEmpty(),
        lastAutoBackupAt = prefs.getLong("lastAutoBackupAt", 0),
        autoBackupError = prefs.getString("autoBackupError", "").orEmpty(),
        knownAppsVersion = prefs.getInt("knownAppsVersion", 1),
    )
}
