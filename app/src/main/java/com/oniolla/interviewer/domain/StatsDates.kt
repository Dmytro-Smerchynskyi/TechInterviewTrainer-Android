package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.model.StudyProgressDay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object StatsDates {
    fun deviceTimeZone(): String = TimeZone.getDefault().id.takeIf {
        it in TimeZone.getAvailableIDs()
    } ?: "UTC"

    private fun formatter(pattern: String = "yyyy-MM-dd", zone: String = "UTC") =
        SimpleDateFormat(pattern, Locale.ROOT).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone(zone)
        }

    private fun parseDate(value: String): Date? = runCatching {
        if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)) return null
        formatter().parse(value)?.takeIf { formatter().format(it) == value }
    }.getOrNull()

    fun currentWeek(timeZone: String, nowMillis: Long = System.currentTimeMillis()): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZone), Locale.ROOT).apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
        }
        return formatter(zone = timeZone).format(calendar.time)
    }

    fun validMonday(value: String?): String? {
        val date = value?.let(::parseDate) ?: return null
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply { time = date }
        return value.takeIf { calendar.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY }
    }

    fun shiftDays(value: String, days: Int): String {
        val date = requireNotNull(parseDate(value))
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply {
            time = date
            add(Calendar.DAY_OF_MONTH, days)
        }
        return formatter().format(calendar.time)
    }

    fun serverTimeMillis(value: String?): Long? {
        if (value == null) return null
        return listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX")
            .firstNotNullOfOrNull { pattern -> runCatching { formatter(pattern).parse(value)?.time }.getOrNull() }
    }

    fun weekDays(weekStart: String, progress: List<StudyProgressDay>): List<StudyProgressDay> {
        val counts = progress.groupBy { it.date }.mapValues { (_, days) ->
            days.sumOf { it.answered.toLong().coerceAtLeast(0) }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        return (0..6).map { offset ->
            val date = shiftDays(weekStart, offset)
            StudyProgressDay(date, counts[date] ?: 0)
        }
    }

    fun weekLabel(weekStart: String, locale: Locale = Locale.getDefault()): String {
        val date = requireNotNull(parseDate(weekStart))
        return SimpleDateFormat("d MMM", locale).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)
    }

    fun dayLabel(date: String, locale: Locale = Locale.getDefault()): String {
        val parsed = requireNotNull(parseDate(date))
        return SimpleDateFormat("EEE", locale).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(parsed)
    }
}
