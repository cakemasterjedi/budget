package com.gigbudget.app.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object Money {
    private val formatter = NumberFormat.getCurrencyInstance(Locale.US)

    fun format(cents: Long): String = formatter.format(cents / 100.0)

    /** Parses user input like "12", "12.5", "$1,234.56" into cents. Returns null for invalid or negative input. */
    fun parse(input: String): Long? {
        val clean = input.replace("$", "").replace(",", "").trim()
        if (clean.isEmpty()) return null
        val value = clean.toBigDecimalOrNull() ?: return null
        if (value.signum() < 0) return null
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    fun toInput(cents: Long): String =
        if (cents == 0L) "" else BigDecimal(cents).movePointLeft(2).toPlainString()
}

enum class Period(val label: String) { WEEK("This week"), MONTH("This month") }

object Dates {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val shortFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    private val longFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    fun toLocalDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun startOf(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** [start, end) in epoch millis. Weeks start on Monday. */
    fun range(period: Period, today: LocalDate = LocalDate.now()): Pair<Long, Long> = when (period) {
        Period.WEEK -> {
            val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            startOf(start) to startOf(start.plusWeeks(1))
        }
        Period.MONTH -> {
            val start = today.withDayOfMonth(1)
            startOf(start) to startOf(start.plusMonths(1))
        }
    }

    fun formatShort(millis: Long): String = shortFormat.format(toLocalDate(millis))
    fun formatLong(millis: Long): String = longFormat.format(toLocalDate(millis))

    /** Material's DatePicker works in UTC midnight millis. */
    fun toPickerMillis(localMillis: Long): Long =
        toLocalDate(localMillis).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** Converts a DatePicker selection to local noon, so it never slips into the previous/next day. */
    fun fromPickerMillis(utcMillis: Long): Long =
        Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            .atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
}
