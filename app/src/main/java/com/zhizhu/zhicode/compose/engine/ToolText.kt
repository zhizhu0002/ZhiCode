package com.zhizhu.zhicode.compose.engine

import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal object ToolText {

    // ---------- 字符串裁剪 ----------

    /** 超出就截断并在末尾加省略号（注意是 `n-1` 个字符 + `…`）。 */
    fun shorten(value: String?, max: Int): String {
        val s = value ?: ""
        if (max <= 0) return ""
        return if (s.length <= max) s else s.substring(0, maxOf(0, max - 1)) + "…"
    }

    /** 中间省略：保留头尾，中间换成 `…`。用于折叠批的副标题。 */
    fun shortenMiddle(value: String?, max: Int): String {
        val s = value ?: ""
        if (s.length <= max) return s
        val left = maxOf(6, (max - 1) / 2)
        val right = maxOf(6, max - left - 1)
        if (s.length <= left + right) return s
        return s.substring(0, left) + "…" + s.substring(s.length - right)
    }

    fun truncateCommand(command: String?): String {
        val raw = command ?: ""
        val lines = raw.split('\n')
        val joined = if (lines.size > 2) "${lines[0]}\\n${lines[1]}…" else raw
        return shorten(joined, 190)
    }

    // ---------- 工具名与摘要 ----------

    /** 行首的大字。只有 7 个中文映射，其余原样返回英文名。 */
    fun userFacingName(name: String?): String {
        if (name == null) return "Tool"
        return when (name.lowercase(Locale.US)) {
            "root" -> "Root 命令"
            // shell 身份与 root 是两回事（uid 2000 vs 0），所以名字也不能共用。
            "shizuku" -> "Shizuku 命令"
            "edit", "multiedit" -> "修改文件"
            "write" -> "写入文件"
            "read" -> "读取文件"
            "ls", "list" -> "列出文件"
            "androidintent" -> "手机操作"
            else -> name
        }
    }

    fun isFileDiff(name: String?, output: String): Boolean {
        val tool = name?.lowercase(Locale.US) ?: return false
        val writesFile = tool == "write" || tool == "edit" || tool == "multiedit" || tool == "delete"
        if (!writesFile) return false
        if (output.isEmpty()) return false

        // ⚠️ 这里**不能用 `output.split('\n')`**（曾经就是那么写的）。
        //
        // 展开的工具输出上限是 40 000 字符（`WorkspaceViewModel.LIVE_OUTPUT_LIMIT`），
        // 一条 2000 行的输出就会先建出 2000 个 String，**然后**才开始找 `@@` ——
        // 哪怕第一行就是 hunk 头也白建。而这个函数在工具行每次重组时都会被调用
        // （滚动、状态变化、展开/收起都算），于是几百 KB 的垃圾按次计费。
        //
        // 改成按下标逐行扫描并**提前 return**：命中 hunk 头就结束，
        // 最坏情况也与原来一样是 O(n)，但不产生任何中间对象。
        var sawMinus = false
        var sawPlus = false
        var start = 0
        val n = output.length
        while (start <= n) {
            var end = output.indexOf('\n', start)
            if (end < 0) end = n
            when {
                startsWithAt(output, start, end, "@@") -> return true
                startsWithAt(output, start, end, "--- ") -> sawMinus = true
                startsWithAt(output, start, end, "+++ ") -> sawPlus = true
            }
            if (end >= n) break
            start = end + 1
        }
        return sawMinus && sawPlus
    }

    /** 区间 `[start, end)` 是否以 [prefix] 开头。 */
    private fun startsWithAt(text: String, start: Int, end: Int, prefix: String): Boolean {
        if (end - start < prefix.length) return false
        for (k in prefix.indices) {
            if (text[start + k] != prefix[k]) return false
        }
        return true
    }

    /** 工具行右侧的摘要（等宽小字）。与 [activityHint] 是**两套**模板，别合并。 */
    fun summary(name: String?, input: JSONObject?): String? {
        if (name == null || input == null) return ""
        return when (name.lowercase(Locale.US)) {
            "bash" -> truncateCommand(input.optString("command", "").replace('\n', ' '))
            "agent", "task" -> shorten(
                input.optString("subagent_type", "general-purpose") + " · " + input.optString("description", ""),
                82,
            )
            "taskoutput", "taskstop" -> shorten(
                input.optString("task_id", input.optString("taskId", "")),
                82,
            )
            "read", "write", "edit" -> shorten(input.optString("file_path", input.optString("path", "")), 82)
            "multiedit" -> {
                val edits = input.optJSONArray("edits")
                when {
                    edits == null || edits.length() == 0 -> ""
                    edits.length() == 1 -> shorten(edits.optJSONObject(0)?.optString("path", ""), 82)
                    else -> {
                        val first = edits.optJSONObject(0)?.optString("path", "") ?: ""
                        shorten(first, 58) + " · " + edits.length() + " edits"
                    }
                }
            }
            "grep", "glob" -> shorten(input.optString("pattern", ""), 64)
            "tree", "list", "ls" -> shorten(input.optString("path", "."), 72)
            "move" -> shorten(
                input.optString("source", "") + " → " + input.optString("destination", ""),
                82,
            )
            "delete", "删除", "mkdir", "stat" -> shorten(input.optString("path", ""), 82)
            else -> ""
        }
    }

    /** 折叠批条目的副标题。 */
    fun activityHint(name: String?, input: JSONObject?): String {
        if (name == null || input == null) return ""
        val value = when (name.lowercase(Locale.US)) {
            "read" -> input.optString("file_path", "")
            "readmany" -> {
                val paths = input.optJSONArray("paths") ?: JSONArray()
                val first = paths.optString(0, "")
                if (paths.length() > 1) "$first · +${paths.length() - 1}" else first
            }
            "grep", "glob" -> {
                val pattern = input.optString("pattern", "")
                val path = input.optString("path", "")
                when {
                    pattern.isEmpty() -> path
                    path.isEmpty() -> pattern
                    else -> "$pattern · $path"
                }
            }
            "ls", "tree", "stat" -> input.optString("path", input.optString("file_path", ""))
            else -> ""
        }
        return shortenMiddle(value, 72)
    }

    /** `ReadMany` 这一次读了几个文件（其余工具是 0）。 */
    fun readRequestCount(name: String?, input: JSONObject?): Int {
        if (name == null || input == null) return 0
        if (!name.equals("ReadMany", ignoreCase = true)) return 0
        return input.optJSONArray("paths")?.length() ?: 0
    }

    /** 没有进度事件时的 `+/−` 预估。 */    fun delta(name: String?, input: JSONObject?): Pair<Int, Int> {
        if (name == null || input == null) return 0 to 0
        var adds = 0
        var dels = 0
        when (name.lowercase(Locale.US)) {
            "edit" -> {
                dels += countNonEmptyLines(input.optString("old_string", ""))
                adds += countNonEmptyLines(input.optString("new_string", ""))
            }
            "write" -> adds += countNonEmptyLines(input.optString("content", ""))
            "multiedit" -> {
                val edits = input.optJSONArray("edits") ?: JSONArray()
                for (i in 0 until edits.length()) {
                    val edit = edits.optJSONObject(i) ?: continue
                    dels += countNonEmptyLines(edit.optString("old_text", edit.optString("old_string", "")))
                    adds += countNonEmptyLines(edit.optString("new_text", edit.optString("new_string", "")))
                }
            }
        }
        return adds to dels
    }

    private fun countNonEmptyLines(text: String?): Int =
        (text ?: "").split('\n').count { it.isNotBlank() }

    // ---------- 结果摘要 ----------

    /** 折叠态的结果行（前缀 `⎿` 由界面加）。 */
    fun compactResult(name: String?, result: String?, exitCode: Int): String {
        val text = result ?: ""
        val lines = if (text.isEmpty()) 0 else text.split('\n').size
        val isShell = name != null && (name.equals("Bash", true) || name.equals("Root", true)
            || name.equals("Shizuku", true))
        if (isShell && exitCode != 0) {
            val firstLine = text.split('\n').firstOrNull { it.isNotBlank() }?.trim() ?: ""
            val why = if (firstLine.isEmpty()) "" else " · " + shorten(firstLine, 105)
            return "退出码 $exitCode" + why + if (lines > 1) " · 点按展开" else ""
        }
        if (isShell) return "$lines 行 · 点按展开"
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return "已完成"
        if (lines <= 1 && trimmed.length <= 96) return trimmed
        return "$lines 行 · 点按展开"
    }

    // ---------- 状态与错误 ----------

    /** 把引擎给的英文/中文 detail 映射成界面文案。 */
    fun displayStatus(detail: String?, phase: String, busy: Boolean): String {
        val d = (detail ?: "").trim()
        if (d.isNotEmpty()) {
            val lower = d.lowercase(Locale.US)
            return when {
                lower.contains("thinking") -> "正在思考…"
                lower.contains("continuing") -> "正在继续处理…"
                lower.contains("compact") -> "正在压缩上下文…"
                lower.contains("cancel") -> "正在停止…"
                else -> d
            }
        }
        return when (phase) {
            "tool" -> "正在执行工具…"
            "thinking" -> "正在思考…"
            else -> if (busy) "正在处理…" else ""
        }
    }

    /** 把底层网络/协议错误翻译成用户能照着做的提示，其余原样透出。 */
    fun friendlyError(message: String?, error: Throwable?): String {
        val raw = message ?: error?.toString() ?: ""
        val lower = raw.lowercase(Locale.US)
        return when {
            lower.contains("model_capability_not_supported") && lower.contains("vision") ->
                "当前模型不支持视觉图片输入。请打开 API 设置，将「视觉图片输入（Vision）」切换为关闭，或改用支持视觉的模型。"
            lower.contains("cleartext") ->
                "系统或网络组件仍阻止了该 HTTP 地址；请检查设备网络策略，或改用 HTTPS。"
            else -> raw
        }
    }

    // ---------- 面板类工具 ----------

    /** 这些工具不生成工具卡，只影响任务/计划面板。 */
    fun isWorkflowTool(name: String?): Boolean = when (name) {
        "TaskCreate", "TaskGet", "TaskList", "TaskUpdate", "TodoWrite",
        "EnterPlanMode", "ExitPlanMode",
        -> true
        else -> false
    }

    /** 失败时自动展开输出（包管理器命令的报错通常很长）。 */
    fun isPackageManagerTool(command: String?): Boolean {
        val lower = (command ?: "").lowercase(Locale.US)
        return lower.contains("pkg ") || lower.contains("apt ") ||
            lower.contains("apt-get ") || lower.contains("dpkg ")
    }

    // ---------- 格式化 ----------

    /** 已用时长。≥1 小时带上小时位。 */
    fun formatElapsed(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours >= 1) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /** 流式输出的字节数。这里按十进制（1000）换算，与原版一致。 */
    fun formatCharCount(count: Int): String = when {
        count < 1000 -> "$count B"
        count < 1_000_000 -> String.format(Locale.US, "%.1f KB", count / 1000.0)
        else -> String.format(Locale.US, "%.1f MB", count / 1_000_000.0)
    }

    /** token 数的紧凑写法：`1.5m` / `128k` / `4096`。 */
    fun formatTokenCount(tokens: Long): String {
        if (tokens <= 0) return "0"
        if (tokens >= 1_000_000) {
            val millions = tokens / 1_000_000.0
            return if (tokens % 1_000_000 == 0L) String.format(Locale.US, "%.0fm", millions)
            else String.format(Locale.US, "%.1fm", millions)
        }
        if (tokens >= 1_000) {
            val thousands = tokens / 1000.0
            return if (tokens % 1_000 == 0L) String.format(Locale.US, "%.0fk", thousands)
            else String.format(Locale.US, "%.1fk", thousands)
        }
        return tokens.toString()
    }
}

/** 工具卡上的实时计时。 */
internal class ToolClock {
    val startedAt: Long = SystemClock.elapsedRealtime()

    fun elapsedMs(): Long = SystemClock.elapsedRealtime() - startedAt
}
