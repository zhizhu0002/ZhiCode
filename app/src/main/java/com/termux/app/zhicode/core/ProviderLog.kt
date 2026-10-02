package com.termux.app.zhicode.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * `DocumentsProvider` 的**调用日志**（纯逻辑：刻意不 import 任何 `android.*`）。
 *
 * ## 为什么需要它
 *
 * SAF 的失败在两端都是**静音**的：
 *
 * 1. 框架在 `DocumentsProvider.call()` 里把 provider 抛出的异常接住、记进 logcat、
 *    只回一个 `null`。于是文件管理器那一侧只显示自己的一句通用文案
 *    （实机上见到的是 `Failed to create directory: 1`）—— 看不到我们的原因；
 * 2. 而真机上拿 logcat 需要 adb 或 root，恰好这两样在这个环境里都没有。
 *
 * 结论是：**原因必须由我们自己记下来，并且记在用户能打开的地方**
 * （日志落在 `~/tmp/` 下 —— 应用内的文件面板和文件管理器都读得到）。
 *
 * ## 两个刻意的小决定
 *
 * - **参数区分 `null` 与空串**（见 [arg]）。这不是洁癖：文件管理器在"新建"流程里
 *   传空名字是完全可能的，而那与"没传"是两种不同的 bug，日志里必须一眼分得出。
 * - **时间戳手写**而不是 `java.time`：本工程 `minSdk 24`，用 `java.time` 要开核心库
 *   脱糖。为了一个诊断日志引入那套机制不划算，而 `SimpleDateFormat` 从 API 1 就有。
 *
 * 这里的函数都必须是**可单测的纯函数** —— 它们进得了 `test-jvm-fast.sh` 的秒级回路。
 * 轮转/截断写错会让日志要么无限长、要么把最关键的那一段截掉，而这两种错都不会报错。
 */
object ProviderLog {

    /** 日志文件名，落在 `$HOME/tmp/` 下。 */
    const val FILE_NAME = "documents-provider.log"

    /** 超过这个大小就截断（保留尾部 [KEEP_BYTES] 字节）。 */
    const val MAX_BYTES = 128L * 1024

    /** 截断后保留的尾部字节数：刚发生的事才有诊断价值。 */
    const val KEEP_BYTES = 64 * 1024

    /** 诊断页里展示的行数上限 —— 报告是要被人读的，不是越全越好。 */
    const val MAX_TAIL_LINES = 40

    /** 单个参数的显示长度上限。 */
    private const val MAX_ARG_CHARS = 160

    /**
     * 把参数渲染成可读且**无歧义**的形式。
     *
     * `null` → `null`（裸字），空串 → `""`（一对引号）。两者长得像但原因完全不同：
     * 前者是"调用方没给这个名字"，后者是"给了但给了个空的"。
     */
    fun arg(value: String?): String {
        if (value == null) return "null"
        val shown = if (value.length > MAX_ARG_CHARS) value.take(MAX_ARG_CHARS) + "…" else value
        return "\"" + shown
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\"", "\\\"") + "\""
    }

    /** 一行日志：`时间 方法(参数) -> 结果`。 */
    fun format(atMillis: Long, method: String, args: List<String?>, outcome: String): String {
        val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date(atMillis))
        return "$time $method(${args.joinToString(", ")}) -> $outcome"
    }

    /** 文件是否已经该截断了。 */
    fun shouldTrim(sizeBytes: Long): Boolean = sizeBytes > MAX_BYTES

    /**
     * 只保留尾部、且从**行首**开始。
     *
     * 从行首开始不是为了好看：UTF-8 里一个汉字占 3 字节，按字节切会把一个字劈成
     * 半个，读出来是乱码，而日志里最要紧的恰恰可能是中文报错。
     */
    fun trimTail(text: String, maxBytes: Int): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return text
        var start = bytes.size - maxBytes
        while (start < bytes.size && bytes[start] != '\n'.code.toByte()) start++
        if (start < bytes.size) start++
        if (start >= bytes.size) return ""
        return String(bytes, start, bytes.size - start, Charsets.UTF_8)
    }

    /** 报告里要展示的最后若干行（跳过空行）。 */
    fun tail(text: String, maxLines: Int = MAX_TAIL_LINES): List<String> =
        text.split('\n').filter { it.isNotBlank() }.takeLast(maxLines)
}
