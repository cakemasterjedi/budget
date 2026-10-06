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
            .apply()
        _state.value = next
    }

    private fun load() = Settings(
        taxPercent = prefs.getInt("taxPercent", 25),
        bottleWeeklyLimitCents = prefs.getLong("bottleWeeklyLimitCents", 0),
        prerollWeeklyLimitCents = prefs.getLong("prerollWeeklyLimitCents", 0),
        lastBottlePriceCents = prefs.getLong("lastBottlePriceCents", 0),
        lastPrerollPriceCents = prefs.getLong("lastPrerollPriceCents", 0),
    )
}
