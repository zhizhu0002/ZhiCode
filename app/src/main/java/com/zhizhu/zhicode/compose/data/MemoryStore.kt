package com.zhizhu.zhicode.compose.data

import com.zhizhu.zhicode.compose.model.MemoryFile
import com.zhizhu.zhicode.compose.model.MemoryScope
import com.termux.shared.termux.TermuxConstants
import java.io.File

/**
 * `ZhiCode.md`「记忆文件」的读写。
 *
 * ## 两个位置
 *
 * - 项目级 `<projectDirectory>/ZhiCode.md`
 * - 用户级 `$HOME/.zhicode/ZhiCode.md`
 *
 * 这两个位置同时是 `/init` 指令里让模型去读写的目标
 * （见 `WorkspaceViewModel.runInitPrompt`），所以读写必须用同一处路径，
 * 否则会出现「Agent 写了一份、界面读的是另一份」。
 *
 * ## 一条必须说清楚的边界
 *
 * **引擎不会自动读它**。`SystemPromptBuilder` 里没有任何 ZhiCode.md / CLAUDE.md 引用，
 * 也就是说写入它 **不会自动改变模型行为** ——
 * 它的作用是被 Agent 用 Read 工具读到（`/init` 的指令就是这么要求它的），
 * 或者被用户当作项目说明随手查阅。
 * 所以界面上的说明文字不能写成"记忆会自动注入模型"，那是假的。
 */
internal object MemoryStore {

    /** 记忆文件的位置。新建、保存、读取都用它。 */
    fun fileOf(projectPath: String, scope: MemoryScope): File = when (scope) {
        MemoryScope.PROJECT -> File(projectPath, TermuxConstants.MEMORY_FILE_NAME)
        MemoryScope.USER -> File(TermuxConstants.dataDir(), TermuxConstants.MEMORY_FILE_NAME)
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
     * 用户级文件要顺手建出父目录（`$HOME/.zhicode` 通常存在，但不能假设——
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
