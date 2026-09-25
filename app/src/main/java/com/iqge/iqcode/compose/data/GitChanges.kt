package com.iqge.iqcode.compose.data

import android.content.Context
import com.iqge.iqcode.compose.model.DiffFile
import com.iqge.iqcode.compose.model.DiffState
import com.termux.app.iqcode.termux.TermuxShellExecutor
import java.io.File

/**
 * 变更面板：真实 `git status` + `git diff`。
 *
 * 为什么不用 `--numstat` 单独算增删：增删行数直接从**同一个补丁**里数出来，
 * 保证"卡片上显示的 +N/−N"与"展开后看到的补丁"永远自洽。
 * 两套来源很容易在重命名、二进制、模式变更这些情况下对不上。
 *
 * 未跟踪文件不在 `git diff` 里，用 `git diff --no-index /dev/null <path>` 单独取补丁 ——
 * 这样新文件也有真实的 +N 与可展开的内容，而不是显示成 "+0"（看着像 bug）。
 */
internal object GitChanges {

    /** 单个 git 命令的超时。diff 可能很大，但不能无限等。 */
    private const val TIMEOUT_MS = 30_000

    /** 未跟踪文件逐个取补丁的上限：避免一个几百文件的仓库把界面拖住。 */
    private const val MAX_UNTRACKED_PATCHES = 40

    fun read(context: Context, projectPath: String): DiffState {
        if (projectPath.isBlank() || !File(projectPath).isDirectory()) {
            return DiffState(note = "项目目录不存在：$projectPath")
        }
        val shell = TermuxShellExecutor(context)
        // git 不可用（内置 Termux 环境还没初始化）时给出可操作的提示，而不是空列表。
        if (!File(com.termux.shared.termux.TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash").isFile()) {
            return DiffState(note = "需要先初始化内置 Termux 环境才能读取 git 变更")
        }

        val topLevel = runCatching { git(shell, projectPath, "rev-parse --show-toplevel") }.getOrNull()
            ?: return DiffState(note = "无法执行 git（内置环境可能未就绪）")
        if (topLevel.exitCode != 0) {
            return DiffState(note = "当前项目不是 git 仓库：$projectPath")
        }
        val repoRoot = topLevel.stdout.trim().lineSequence().firstOrNull()?.trim().orEmpty()
            .ifEmpty { projectPath }

        val status = runCatching { git(shell, repoRoot, "status --porcelain") }.getOrNull()
            ?: return DiffState(note = "读取 git 状态失败")
        val unstaged = runCatching { git(shell, repoRoot, "diff") }.getOrNull()?.stdout.orEmpty()
        val staged = runCatching { git(shell, repoRoot, "diff --cached") }.getOrNull()?.stdout.orEmpty()

        val entries = parseStatus(status.stdout)
        val untrackedPatches = LinkedHashMap<String, String>()
        for (entry in entries) {
            if (!entry.untracked) continue
            if (untrackedPatches.size >= MAX_UNTRACKED_PATCHES) break
            // --no-index 在"有差异"时退出码就是 1，那是正常结果，不能当成失败。
            val patch = runCatching {
                git(shell, repoRoot, "diff --no-index -- /dev/null ${quote(entry.path)}")
            }.getOrNull()?.stdout
            if (!patch.isNullOrBlank()) untrackedPatches[entry.path] = patch
        }

        return parse(repoRoot, status.stdout, unstaged, staged, untrackedPatches)
    }

    /**
     * 纯函数：把 git 输出翻译成界面模型。
     *
     * 抽成纯函数是为了能用**真实 git 输出**离线验证 —— 内置 Termux 环境在沙箱里装不了，
     * 但解析逻辑（最易出错的部分）不依赖进程，可以直接喂样本数据核对。
     */
    fun parse(
        repoRoot: String,
        statusOutput: String,
        unstagedDiff: String,
        stagedDiff: String,
        untrackedPatches: Map<String, String>,
    ): DiffState {
        val entries = parseStatus(statusOutput)
        val patches = LinkedHashMap<String, StringBuilder>()
        fun addPatch(source: String) {
            for ((path, body) in splitPatches(source)) {
                patches.getOrPut(path) { StringBuilder() }.append(body)
            }
        }
        addPatch(stagedDiff)
        addPatch(unstagedDiff)
        for ((path, body) in untrackedPatches) {
            patches.getOrPut(path) { StringBuilder() }.append(body)
        }

        val files = entries.map { entry ->
            val patch = patches[entry.path]?.toString().orEmpty()
            val (added, deleted) = countChanges(patch)
            DiffFile(
                // 用仓库相对路径：绝对路径又长又难读，而且换机器就失效。
                name = entry.path,
                additions = added,
                deletions = deleted,
                diff = patch.ifEmpty { entry.fallbackNote },
            )
        }
        return DiffState(files = files, loading = false, note = "")
    }

    // ------------------------------------------------------------------

    private class Entry(val path: String, val untracked: Boolean) {
        /**
         * 没有补丁时的说明。
         *
         * 未跟踪文件一定有补丁（走 `--no-index`）；这里主要覆盖
         * "二进制文件"与"纯模式变更"这两种 `git diff` 不产出 +/- 行的情况。
         */
        val fallbackNote: String
            get() = if (untracked) "（新文件）" else "（无文本差异，可能是二进制或仅权限变更）"
    }

    /**
     * 解析 `git status --porcelain`。
     *
     * v1 格式：两位状态码 + 空格 + 路径。重命名是 `old -> new`。
     * 路径含特殊字符时 git 会用 C 风格引号包起来（`"a\tb"`），这里做最小反转义。
     */
    private fun parseStatus(output: String): List<Entry> {
        val out = mutableListOf<Entry>()
        for (raw in output.lineSequence()) {
            if (raw.length < 4) continue
            val x = raw[0]
            val y = raw[1]
            var path = raw.substring(3)
            if (path.length >= 2 && path.startsWith('"') && path.endsWith('"')) {
                path = unquote(path)
            }
            // 重命名只显示新路径：面板按路径索引补丁，旧路径没有补丁可显示。
            if (path.contains(" -> ")) path = path.substringAfterLast(" -> ")
            if (path.isBlank()) continue
            out.add(Entry(path = path, untracked = x == '?' && y == '?'))
        }
        return out
    }

    /** git 的 C 风格引号反转义（只处理最常见的几个转义）。 */
    private fun unquote(value: String): String {
        val body = value.substring(1, value.length - 1)
        val out = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val ch = body[i]
            if (ch != '\\' || i == body.length - 1) {
                out.append(ch)
                i++
                continue
            }
            when (val next = body[i + 1]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> out.append('\r')
                '"' -> out.append('"')
                '\\' -> out.append('\\')
                else -> out.append(next)
            }
            i += 2
        }
        return out.toString()
    }

    /**
     * 按 `diff --git a/<path> b/<path>` 切分补丁。
     *
     * ⚠️ 路径**必须**从这一行取，不能从 `+++ b/...` 取：
     * 删除文件的 `+++` 是 `/dev/null`，从那里取会得到错误的文件名。
     */
    private fun splitPatches(diff: String): List<Pair<String, String>> {
        if (diff.isBlank()) return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        val current = StringBuilder()
        var currentName: String? = null

        fun flush() {
            val name = currentName
            if (name != null && current.isNotEmpty()) result.add(name to current.toString())
            current.setLength(0)
            currentName = null
        }

        for (line in diff.lineSequence()) {
            if (line.startsWith("diff --git ")) {
                flush()
                currentName = pathFromDiffHeader(line)
            }
            if (currentName != null) {
                current.append(line).append('\n')
            }
        }
        flush()
        return result
    }

    /** 从 `diff --git a/x/y b/x/y` 里取 `x/y`。带引号的路径按空格切分会切错，所以先探测引号。 */
    private fun pathFromDiffHeader(line: String): String? {
        val body = line.removePrefix("diff --git ")
        val bIndex = body.lastIndexOf(" b/")
        val candidate = when {
            bIndex >= 0 -> body.substring(bIndex + 3)
            else -> body.substringAfterLast(" b/", "")
        }
        if (candidate.isBlank()) return null
        val cleaned = if (candidate.startsWith('"') && candidate.endsWith('"')) unquote(candidate) else candidate
        // `--no-index` 对未跟踪文件产生的头是 `a/src/added.txt`，但 b 侧就是真实路径；
        // 若两边不一致（重命名），以 b 侧为准。
        return cleaned.trim().ifEmpty { null }
    }

    /**
     * 数增删行。
     *
     * ⚠️ 判断依据是**"只数第一个 `@@` 之后的行"**，而不是"跳过以 `+++`/`---` 开头的行"。
     * 后者有个真实缺陷：若文件内容里有一行本身就是 `--foo`，它在补丁里表现为 `---foo`，
     * 会被当成文件头跳过，于是少算一行 —— 而且只在特定内容下才复现，极难排查。
     * `@@` 之前只可能出现元数据（`diff --git`/`index`/`new file mode`/`+++`/`---`），
     * 用这个边界既正确又简单。
     *
     * 二进制文件没有 `@@`，结果是 0/0 —— 与补丁里 "Binary files differ" 一致。
     */
    private fun countChanges(patch: String): Pair<Int, Int> {
        var added = 0
        var deleted = 0
        var inHunk = false
        for (line in patch.lineSequence()) {
            if (line.startsWith("@@")) {
                inHunk = true
                continue
            }
            if (!inHunk) continue
            // `\ No newline at end of file` 不是内容行。
            if (line.startsWith("\\")) continue
            when {
                line.startsWith("+") -> added++
                line.startsWith("-") -> deleted++
            }
        }
        return added to deleted
    }

    private fun quote(path: String): String =
        if (path.any { it == ' ' || it == '\'' || it == '"' || it == '\\' }) "'" + path.replace("'", "'\\''") + "'"
        else path

    private fun git(shell: TermuxShellExecutor, cwd: String, args: String): TermuxShellExecutor.Result =
        shell.execute("git $args", cwd, TIMEOUT_MS)
}
