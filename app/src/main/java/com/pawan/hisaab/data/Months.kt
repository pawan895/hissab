package com.pawan.hisaab.data

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zone: ZoneId get() = ZoneId.systemDefault()

/** [start, end) epoch millis of the month in the device time zone. */
fun YearMonth.range(): Pair<Long, Long> =
    atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() to
        plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

fun YearMonth.label(): String = format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))

fun Long.toYearMonth(): YearMonth = YearMonth.from(Instant.ofEpochMilli(this).atZone(zone))

fun Long.dayOfMonth(): Int = Instant.ofEpochMilli(this).atZone(zone).dayOfMonth

fun Long.formatDate(pattern: String): String =
    DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).format(Instant.ofEpochMilli(this).atZone(zone))
