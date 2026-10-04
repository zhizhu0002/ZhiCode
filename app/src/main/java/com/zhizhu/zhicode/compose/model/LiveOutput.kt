package com.zhizhu.zhicode.compose.model

/**
 * 运行中工具的**实时输出**缓冲与裁剪（纯逻辑，无 Android 依赖）。
 *
 * 逐字对齐参考实现（IQ Code `MainActivity.appendLiveChunk` / `liveOutputTail` /
 * `liveOutputPreview`，见 `projects/IQ-Code-Android`）：
 *
 * - 换行统一：`\r\n` 与单独的 `\r` 都折成 `\n`（PTY 的输出常常是 `\r\n`，
 *   不折的话界面上每行末尾会多一个看不见的回车，行数还会算错）；
 * - **stdout / stderr 分开计数**：运行标签要显示「标准输出 12.3 KB · 错误输出 0 B」，
 *   直接量缓冲总长做不到这件事；
 * - **只在流切换时插一次 `[stdout]` / `[stderr]` 标记**：以前是"每个 stderr chunk
 *   都插一行"，于是连续报错的命令会把缓冲刷成一片 `[stderr]`；
 * - 缓冲只保留最后 [DEFAULT_KEEP] 个字符（原版 `keep = 40000`）。
 *
 * 裁成"最后几行"的两段逻辑（[tail] / [preview]）也在这里：它们是纯字符串运算，
 * 而且**必须在纯逻辑层**，否则"省略提示该不该出现"这种事只能靠肉眼看界面。
 */
object LiveOutput {

    /** 缓冲保留的尾部长度（原版 `final int keep = 40000`）。 */
    const val DEFAULT_KEEP = 40_000

    /** [tail] 的下限：即使调用方给了一个很小的值，也至少留这么多字符。 */
    private const val MIN_TAIL_CHARS = 1000

    /** 头部被裁掉时的提示（原版逐字）。 */
    const val OMITTED_TAIL = "… earlier output omitted …"

    /** [preview] 又按行裁了一次时的提示（原版逐字，与上面那条**不同**）。 */
    const val OMITTED_LIVE = "… earlier live output omitted …"

    /**
     * 一条工具实时输出的累积状态。
     *
     * @param text          已经落到界面上的那段文本（已归一化换行、已插好流标记）
     * @param stdoutChars   标准输出累计字符数（**含**标记行，与原版一致）
     * @param stderrChars   错误输出累计字符数
     * @param lastWasStderr 上一块来自哪个流。null = 还没有任何一块
     */
    data class Buffer(
        val text: String = "",
        val stdoutChars: Int = 0,
        val stderrChars: Int = 0,
        val lastWasStderr: Boolean? = null,
    )

    /** 换行归一化：`\r\n` 与单独的 `\r` 都折成 `\n`。 */
    fun normalize(chunk: String): String =
        chunk.replace("\r\n", "\n").replace('\r', '\n')

    /**
     * 追加一块输出，返回新的累积状态。
     *
     * 标记规则（与原版逐条对应）：
     * - 已经有内容、且**这一块与上一块来自不同的流** → 先补一个换行（若缓冲末尾不是换行），
     *   再插一行标记；
     * - 缓冲还是空的、且这一块来自 stderr → 直接插 `[stderr]`；
     * - 其余情况不插（这正是"连续 stderr 只插一次"的实现）。
     *
     * 空块直接返回原状态：原版也是 `if (chunk == null || chunk.isEmpty()) return`。
     */
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

    /**
     * 取尾部 [maxChars] 个字符，被裁过就在最前面加一行提示。
     *
     * 返回值里带不带提示是**语义**（用户要知道"上面还有内容"），所以这里返回
     * 拼好的文本 + 是否裁过，而不是只返回文本让界面去猜。
     */
    fun tail(buffer: Buffer, maxChars: Int): String = tail(buffer.text, maxChars)

    fun tail(text: String, maxChars: Int): String {
        if (text.isEmpty()) return ""
        val start = (text.length - maxOf(MIN_TAIL_CHARS, maxChars)).coerceAtLeast(0)
        if (start <= 0) return text
        return OMITTED_TAIL + "\n" + text.substring(start)
    }

    /**
     * "运行中那一行"要显示的内容：尾部 [maxChars] 个字符里**最后 [maxLines] 行**。
     *
     * 为什么要再按行裁一次：字符数控制不了高度 —— 20 行短输出只有几百字符，
     * 照样能把工具行撑得很高，而这一段的用途只是"让人看见它在动"。
     *
     * 两个细节与原版一致：
     * - 末尾的空行先去掉（`while (end > 0 && lines[end-1].isEmpty()) end--`），
     *   否则命令输出以换行结尾时会白白多出一行空白；
     * - 只裁了行（不是裁字符）时用 **另一条**提示 `… earlier live output omitted …`，
     *   于是用户能分清"这一行上面还有更多行"与"这块输出本身被截断过"。
     */
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

    /**
     * 运行中标签右半段的"输出体量"。
     *
     * 原版：`实时 00:12 · 标准输出 12.3 KB · 错误输出 0 B · 进程运行中`。
     * 显示成"体量"而不是"行数"是因为流式输出没有行边界可言（半行也算），
     * 字符数则是单调递增的，看起来才像在前进。
     */
    fun volume(stdoutChars: Int, stderrChars: Int): String =
        "标准输出 " + count(stdoutChars) + " · 错误输出 " + count(stderrChars)

    /** 十进制换算（1000），与原版 `formatCharCount` 一致。 */
    fun count(chars: Int): String = when {
        chars < 1000 -> "$chars B"
        chars < 1_000_000 -> String.format(java.util.Locale.US, "%.1f KB", chars / 1000.0)
        else -> String.format(java.util.Locale.US, "%.1f MB", chars / 1_000_000.0)
    }
}
