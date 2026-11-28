package com.termux.app.zhicode.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    /** 把参数渲染成可读且**无歧义**的形式。 */
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

    /** 只保留尾部、且从**行首**开始。 */
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
