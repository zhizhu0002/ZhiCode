package com.zhizhu.zhicode.compose.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object FileFormat {

    /** 「0 B」「715 B」「1.2 KB」「34.6 MB」「1.5 GB」。 */
    fun size(bytes: Long): String = when {
        bytes <= 0L -> "0 B"
        bytes < 1_000L -> "$bytes B"
        bytes < 1_000_000L -> fixed(bytes / 1_000.0, "KB")
        bytes < 1_000_000_000L -> fixed(bytes / 1_000_000.0, "MB")
        else -> fixed(bytes / 1_000_000_000.0, "GB")
    }

    private fun fixed(value: Double, unit: String): String =
        String.format(Locale.US, "%.1f %s", value, unit)

    /** 「今天 13:53」「昨天 09:07」「10-04 13:53」「2025-10-04」。 */
    fun time(millis: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): String {
        if (millis <= 0L) return ""
        val then = Calendar.getInstance(zone).apply { timeInMillis = millis }
        val today = Calendar.getInstance(zone).apply { timeInMillis = now }
        val clock = format(millis, "HH:mm", zone)
        return when {
            sameDay(then, today) -> "今天 $clock"
            isYesterday(then, today) -> "昨天 $clock"
            then.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
                format(millis, "MM-dd HH:mm", zone)
            else -> format(millis, "yyyy-MM-dd", zone)
        }
    }

    fun date(millis: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): String {
        if (millis <= 0L) return ""
        val then = Calendar.getInstance(zone).apply { timeInMillis = millis }
        val today = Calendar.getInstance(zone).apply { timeInMillis = now }
        return format(millis, if (then.get(Calendar.YEAR) == today.get(Calendar.YEAR)) "MM/dd" else "yyyy/MM/dd", zone)
    }

    fun dateFull(millis: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): String =
        if (millis <= 0L) "" else format(millis, "yyyy/MM/dd", zone)

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(then: Calendar, today: Calendar): Boolean {
        // 用「今天减一天」比手写 dayOfYear - 1 稳：跨年、跨月都由 Calendar 处理。
        val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return sameDay(then, yesterday)
    }

    private fun format(millis: Long, pattern: String, zone: TimeZone): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = zone }.format(Date(millis))
}
