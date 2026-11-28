package com.zhizhu.zhicode.compose.model

object LiveOutput {

    /** 缓冲保留的尾部长度（原版 `final int keep = 40000`）。 */
    const val DEFAULT_KEEP = 40_000

    /** [tail] 的下限：即使调用方给了一个很小的值，也至少留这么多字符。 */
    private const val MIN_TAIL_CHARS = 1000

    /** 头部被裁掉时的提示（原版逐字）。 */
    const val OMITTED_TAIL = "… earlier output omitted …"

    /** [preview] 又按行裁了一次时的提示（原版逐字，与上面那条**不同**）。 */
    const val OMITTED_LIVE = "… earlier live output omitted …"

    /** 一条工具实时输出的累积状态。 */
    data class Buffer(
        val text: String = "",
        val stdoutChars: Int = 0,
        val stderrChars: Int = 0,
        val lastWasStderr: Boolean? = null,
    )

    /** 换行归一化：`\r\n` 与单独的 `\r` 都折成 `\n`。 */
    fun normalize(chunk: String): String =
        chunk.replace("\r\n", "\n").replace('\r', '\n')

    /** 追加一块输出，返回新的累积状态。 */
    fun append(
        previous: Buffer,
        chunk: String,
        stderr: Boolean,
        keep: Int = DEFAULT_KEEP,
    ): Buffer {
        if (chunk.isEmpty()) return previous
        val clean = normalize(chunk)

        val builder = StringBuilder(previous.text)
        if (builder.isNotEmpty() && previous.lastWasStderr != stderr) {
            if (builder[builder.length - 1] != '\n') builder.append('\n')
            builder.append(if (stderr) "[stderr]\n" else "[stdout]\n")
        } else if (builder.isEmpty() && stderr) {
            builder.append("[stderr]\n")
        }
        builder.append(clean)

        // 超出就丢头部。丢完可能把某一行斩成半截，这是可以接受的：
        // 尾部预览本来就会再按行裁一次，并且会用提示行说明"上面还有内容"。
        val overflow = builder.length - keep
        if (overflow > 0) builder.delete(0, overflow)

        return Buffer(
            text = builder.toString(),
            stdoutChars = if (stderr) previous.stdoutChars else previous.stdoutChars + clean.length,
            stderrChars = if (stderr) previous.stderrChars + clean.length else previous.stderrChars,
            lastWasStderr = stderr,
        )
    }

    /** 取尾部 [maxChars] 个字符，被裁过就在最前面加一行提示。 */
    fun tail(buffer: Buffer, maxChars: Int): String = tail(buffer.text, maxChars)

    fun tail(text: String, maxChars: Int): String {
        if (text.isEmpty()) return ""
        val start = (text.length - maxOf(MIN_TAIL_CHARS, maxChars)).coerceAtLeast(0)
        if (start <= 0) return text
        return OMITTED_TAIL + "\n" + text.substring(start)
    }

    fun preview(text: String, maxChars: Int, maxLines: Int): String {
        val value = tail(text, maxChars)
        if (value.isEmpty()) return value
        val lines = value.split('\n')
        var end = lines.size
        while (end > 0 && lines[end - 1].isEmpty()) end--
        val start = (end - maxOf(1, maxLines)).coerceAtLeast(0)

        val out = StringBuilder()
        if (start > 0) out.append(OMITTED_LIVE).append('\n')
        for (i in start until end) {
            if (out.isNotEmpty() && out[out.length - 1] != '\n') out.append('\n')
            out.append(lines[i])
        }
        return out.toString()
    }

    /** 运行中标签右半段的"输出体量"。 */
    fun volume(stdoutChars: Int, stderrChars: Int): String =
        "标准输出 " + count(stdoutChars) + " · 错误输出 " + count(stderrChars)

    /** 十进制换算（1000），与原版 `formatCharCount` 一致。 */
    fun count(chars: Int): String = when {
        chars < 1000 -> "$chars B"
        chars < 1_000_000 -> String.format(java.util.Locale.US, "%.1f KB", chars / 1000.0)
        else -> String.format(java.util.Locale.US, "%.1f MB", chars / 1_000_000.0)
    }
}
