package com.zhizhu.zhicode.compose.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * 文件列表那两行的文字格式：[FileFormat.size] / [FileFormat.time]。
 *
 * <p>为什么要为"显示成什么样"写单测：这两串字符**错了不会崩**。
 * 少一档大小就一直是「1234.5 KB」、时间差一天就是「昨天 23:10」写成「今天」——
 * 看起来都像正常数据，只有对着真实文件才可能发现。而它们现在长在
 * 三个界面上（文件行、附加提示、附加面板），改一处口径不一致就更难发现。
 *
 * <p>时间的用例全部走**显式传入的 now 与固定时区**：这是 `FileFormat.time`
 * 把 `now` 做成参数而不是内部取 `System.currentTimeMillis()` 的唯一目的 ——
 * 否则"今天/昨天"这两档根本没法钉，只能靠运气。
 */
class FileFormatTest {

    // ---- 大小 -------------------------------------------------------------

    @Test
    fun `大小 按 1000 进制分档`() {
        assertEquals("0 B", FileFormat.size(0))
        assertEquals("0 B", FileFormat.size(-1))
        assertEquals("1 B", FileFormat.size(1))
        // 边界：**恰好 1000** 就进位，不能等到 1001。
        assertEquals("999 B", FileFormat.size(999))
        assertEquals("1.0 KB", FileFormat.size(1_000))
        assertEquals("999.9 KB", FileFormat.size(999_949))
        assertEquals("1.0 MB", FileFormat.size(1_000_000))
        assertEquals("34.6 MB", FileFormat.size(34_603_000))
        assertEquals("1.0 GB", FileFormat.size(1_000_000_000))
        assertEquals("1.5 GB", FileFormat.size(1_500_000_000))
    }

    @Test
    fun `大小 是 1000 进制而不是 1024`() {
        // 1024 进制会说这个文件是「1.0 KB」的另一半：1048576 字节。
        // 用 1000 进制时它必须显示 1.0 MB（1048576 / 1e6 = 1.048…），
        // 与系统里显示的读数一致 —— 这是文件管理器的通行做法。
        assertEquals("1.0 MB", FileFormat.size(1_048_576))
        assertEquals("1.0 KB", FileFormat.size(1_024))
    }

    // ---- 时间 -------------------------------------------------------------

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    /** 固定「现在」＝ 2026-10-04 13:00（本地时区）。 */
    private val now: Long = millisOf(2026, 10, 4, 13, 0)

    private fun millisOf(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(y, m - 1, d, h, min)
        }.timeInMillis

    @Test
    fun `时间 今天 昨天 同年 往年 四档`() {
        assertEquals("今天 13:53", FileFormat.time(millisOf(2026, 10, 4, 13, 53), now, zone))
        // 今天凌晨也算「今天」，不能因为跨过某个小时数就变成「昨天」。
        assertEquals("今天 00:07", FileFormat.time(millisOf(2026, 10, 4, 0, 7), now, zone))
        assertEquals("昨天 23:10", FileFormat.time(millisOf(2026, 10, 3, 23, 10), now, zone))
        assertEquals("09-04 13:53", FileFormat.time(millisOf(2026, 9, 4, 13, 53), now, zone))
        assertEquals("2025-10-04", FileFormat.time(millisOf(2025, 10, 4, 13, 53), now, zone))
    }

    @Test
    fun `时间 昨天 跨月跨年都算对`() {
        // 10-01 的「昨天」是 09-30：手写 `dayOfYear - 1` 在跨月时最容易错。
        val firstOfMonth = millisOf(2026, 10, 1, 9, 0)
        assertEquals("昨天 22:00", FileFormat.time(millisOf(2026, 9, 30, 22, 0), firstOfMonth, zone))

        // 元旦的「昨天」是去年 12-31 —— 但那一档显示的是「昨天」而不是年份，
        // 因为昨天这一条判据比"同年"更靠前。
        val newYear = millisOf(2026, 1, 1, 9, 0)
        assertEquals("昨天 23:59", FileFormat.time(millisOf(2025, 12, 31, 23, 59), newYear, zone))
    }

    @Test
    fun `时间 拿不到修改时间时返回空串`() {
        // File.length() 之类的调用失败时会落到 0 —— 显示「今天 08:00」是在编数据。
        assertEquals("", FileFormat.time(0, now, zone))
        assertEquals("", FileFormat.time(-1, now, zone))
    }
}
