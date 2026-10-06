package com.gigbudget.app.data

import android.content.Context
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
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()

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
            .also { editor ->
                next.bucketPercents.forEach { (bucket, pct) -> editor.putInt("pct_${bucket.name}", pct) }
            }
            .apply()
        _state.value = next
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
    )
}
