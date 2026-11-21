package com.pawan.hisaab.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

object Money {
    private val inr: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 2
        }
    }

    /** "1,23,456.78" -> 12345678 paise. Accepts "(8,000.00)" as negative. Returns null if not a number. */
    fun parsePaise(text: String?): Long? {
        if (text == null) return null
        var s = text.trim()
        if (s.isEmpty()) return null
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true
            s = s.substring(1, s.length - 1)
        }
        if (s.startsWith("-")) {
            negative = !negative
            s = s.substring(1)
        }
        s = s.replace(",", "").replace("₹", "").replace("Rs.", "").replace("Rs", "").replace("INR", "").trim()
        val value = s.toBigDecimalOrNull() ?: return null
        val paise = value.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
        return if (negative) -paise else paise
    }

    /** ₹1,23,456.78, or ₹1,23,456 when there are no paise. */
    fun format(paise: Long, signed: Boolean = false): String {
        val rupees = BigDecimal(abs(paise)).movePointLeft(2)
        val fmt = inr.clone() as NumberFormat
        fmt.minimumFractionDigits = if (paise % 100 == 0L) 0 else 2
        val body = fmt.format(rupees)
        return when {
            paise < 0 -> "-$body"
            signed && paise > 0 -> "+$body"
            else -> body
        }
    }

    /** Compact form for charts: ₹1.2L, ₹45K, ₹900. */
    fun compact(paise: Long): String {
        val r = abs(paise) / 100.0
        val s = when {
            r >= 1_00_00_000 -> "%.1fCr".format(r / 1_00_00_000)
            r >= 1_00_000 -> "%.1fL".format(r / 1_00_000)
            r >= 1_000 -> "%.1fK".format(r / 1_000)
            else -> "%.0f".format(r)
        }.replace(".0", "")
        return (if (paise < 0) "-₹" else "₹") + s
    }

    /** Plain number for CSV: 84700.00 */
    fun plain(paise: Long): String = BigDecimal(paise).movePointLeft(2).setScale(2).toPlainString()
}
