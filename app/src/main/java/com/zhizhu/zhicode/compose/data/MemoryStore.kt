package com.zhizhu.zhicode.compose.data

import com.zhizhu.zhicode.compose.model.MemoryFile
import com.zhizhu.zhicode.compose.model.MemoryScope
import com.termux.shared.termux.TermuxConstants
import java.io.File

/**
 * IQ.md「记忆文件」的读写。
 *
 * ## 两点必须说清楚
 *
 * 1. **路径与原版一致**：项目级 `<projectDirectory>/IQ.md`、用户级 `$HOME/.iq/IQ.md`。
 *    这两个位置是 `/init` 指令里让模型去读写的目标，改路径会让 `/init` 与 `/memory`
 *    指到两个地方。
 *
 * 2. **引擎不会自动读它**。`SystemPromptBuilder` 里没有任何 IQ.md / CLAUDE.md 引用
 *    （原版同样没有），也就是说写入 IQ.md **不会自动改变模型行为**——
 *    它的作用是被 Agent 用 Read 工具读到（`/init` 的指令就是这么要求它的），
 *    或者被用户当作项目说明随手查阅。
 *    所以界面上的说明文字不能写成"记忆会自动注入模型"，那是假的。
 */
internal object MemoryStore {

    fun fileOf(projectPath: String, scope: MemoryScope): File = when (scope) {
        MemoryScope.PROJECT -> File(projectPath, "IQ.md")
        MemoryScope.USER -> File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".iq/IQ.md")
    }

    /** 列出记忆文件。不存在的也会列出（标记 `exists = false`），这样界面上能直接新建。 */
    fun list(projectPath: String): List<MemoryFile> = MemoryScope.entries.map { scope ->
        val file = fileOf(projectPath, scope)
        MemoryFile(
            scope = scope,
            path = file.absolutePath,
            exists = file.isFile,
            sizeLabel = if (file.isFile) humanSize(file.length()) else "尚未创建",
        )
    }

    fun read(projectPath: String, scope: MemoryScope): Result<String> = runCatching {
        val file = fileOf(projectPath, scope)
        if (!file.isFile) return@runCatching ""
        file.readText()
    }

    /**
     * 保存。
     *
     * 用户级文件要顺手建出父目录（`$HOME/.iq` 通常存在，但不能假设——
     * 全新安装且从未用过 MCP / 技能时它可能还没有）。
     */
    fun save(projectPath: String, scope: MemoryScope, body: String): Result<Unit> = runCatching {
        val file = fileOf(projectPath, scope)
        file.parentFile?.let { parent ->
            if (!parent.isDirectory && !parent.mkdirs()) {
                throw IllegalStateException("无法创建目录：${parent.absolutePath}")
            }
        }
        file.writeText(body)
        Unit
    }

    private fun humanSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
