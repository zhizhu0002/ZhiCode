package com.zhizhu.zhicode.compose.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 文件列表那两行小字：**大小**与**修改时间**。
 *
 * ## 为什么单独一个文件、而且放在 `model/` 里
 *
 * 这两串字符原先散在两个地方：`ui/Common.kt` 的 `zhiFormatSize` 与
 * `ui/panes/FileChrome.kt` 的 `formatFileSize` —— **两份字节完全相同的实现**
 * （注释里还写着"三处使用者…没必要各写一份"，结果自己就是重复的那一份）。
 * 现在收成一份，并且从 `ui/` 挪到 `model/`：ViewModel 里也要用它
 * （`"已附加：x（1.2 KB）"`），而 ViewModel 反向 import `ui` 是把界面层当工具库，
 * 分层就反了。
 *
 * 放在 `model/` 的另一个收获是：**它们可以被纯 JVM 单测覆盖**
 * （`FileFormatTest`，挂在 `test-jvm-fast.sh` 的快路径里，秒级）。
 * 显示格式是"看着对不对"的东西，靠源码守卫钉不住 —— 唯一能钉的就是把
 * 具体的输入输出写进测试，例如"1023 字节显示 1023 B"、"刚好 1000 显示 1.0 KB"。
 *
 * ## 为什么是 1000 进制
 *
 * 这是文件管理器的通行做法，也才与系统里「2.4 MB」这种读数对得上
 * （1024 进制会让同一个文件在我们这里比在系统里大一点）。
 * 所以这里**不**用 `MiB` / `KiB` 那套。
 */
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

    /**
     * 「今天 13:53」「昨天 09:07」「10-04 13:53」「2025-10-04」。
     *
     * 分档的理由是**用户要回答的问题不同**：刚改过的文件问"几点"，
     * 去年的文件问"哪一天"。一律给完整时间戳的话，一列里全是
     * 「2026-10-04 13:53」这种等长串，扫一列什么都看不出来。
     *
     * [now] 是显式参数而不是 `System.currentTimeMillis()`：这样它才是纯函数，
     * 测试才能把"今天/昨天"两档钉死（否则测试要么依赖真实时间、要么只能不测）。
     */
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
