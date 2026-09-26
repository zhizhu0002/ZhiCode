package com.zhizhu.zhicode.compose.data

import com.zhizhu.zhicode.compose.model.MemoryFile
import com.zhizhu.zhicode.compose.model.MemoryScope
import com.termux.shared.termux.TermuxConstants
import java.io.File

/**
 * `ZhiCode.md`「记忆文件」的读写。
 *
 * ## 三个位置，两条规则
 *
 * 1. **写入只写当前名**：项目级 `<projectDirectory>/ZhiCode.md`、
 *    用户级 `$HOME/.zhicode/ZhiCode.md`。这两个位置同时是 `/init` 指令里
 *    让模型去读写的目标（见 `WorkspaceViewModel.runInitPrompt`）。
 *
 * 2. **读取新名优先、旧名兜底**。改名前的旧位置是项目级 `<projectDirectory>/IQ.md`
 *    与用户级 `$HOME/.iq/IQ.md`。用户级那一份会被 `LegacyDataMigration` 在启动时
 *    改名搬过来；**项目级那一份不能搬**——它躺在用户的 git 工作区里，
 *    未经允许改用户的版本库文件是不行的，所以只能读的时候两边都看。
 *
 * ## 一条必须说清楚的边界
 *
 * **引擎不会自动读它**。`SystemPromptBuilder` 里没有任何 ZhiCode.md / CLAUDE.md 引用
 * （原版同样没有），也就是说写入它 **不会自动改变模型行为** ——
 * 它的作用是被 Agent 用 Read 工具读到（`/init` 的指令就是这么要求它的），
 * 或者被用户当作项目说明随手查阅。
 * 所以界面上的说明文字不能写成"记忆会自动注入模型"，那是假的。
 */
internal object MemoryStore {

    /** 写入目标：当前名的文件。新建也落在这里。 */
    fun fileOf(projectPath: String, scope: MemoryScope): File = when (scope) {
        MemoryScope.PROJECT -> File(projectPath, TermuxConstants.MEMORY_FILE_NAME)
        MemoryScope.USER -> File(TermuxConstants.dataDir(), TermuxConstants.MEMORY_FILE_NAME)
    }

    /** 旧名的文件，只读兜底。 */
    private fun legacyFileOf(projectPath: String, scope: MemoryScope): File = when (scope) {
        MemoryScope.PROJECT -> File(projectPath, TermuxConstants.LEGACY_MEMORY_FILE_NAME)
        MemoryScope.USER -> File(TermuxConstants.legacyDataDir(), TermuxConstants.LEGACY_MEMORY_FILE_NAME)
    }

    /**
     * 实际要读哪个文件。
     *
     * 新名存在就用新名（哪怕它只有一字节），否则看旧名。两边都没有就返回写入目标，
     * 交给调用方按"尚未创建"处理。
     */
    private fun sourceFileOf(projectPath: String, scope: MemoryScope): File {
        val target = fileOf(projectPath, scope)
        if (target.isFile) return target
        val legacy = legacyFileOf(projectPath, scope)
        return if (legacy.isFile) legacy else target
    }

    /**
     * 列出记忆文件。不存在的也会列出（标记 `exists = false`），这样界面上能直接新建。
     *
     * [MemoryFile.path] 始终是**写入目标**路径，而当前读到的是旧文件时用
     * [MemoryFile.legacySource] 标出来 —— 否则用户会看到路径写着 `ZhiCode.md`、
     * 内容却来自 `IQ.md`，点保存之后又"换了文件"，那是界面在骗人。
     */
    fun list(projectPath: String): List<MemoryFile> = MemoryScope.entries.map { scope ->
        val target = fileOf(projectPath, scope)
        val legacy = legacyFileOf(projectPath, scope)
        val source = sourceFileOf(projectPath, scope)
        val fromLegacy = source != target && source == legacy
        MemoryFile(
            scope = scope,
            path = target.absolutePath,
            exists = source.isFile,
            sizeLabel = if (source.isFile) humanSize(source.length()) else "尚未创建",
            legacySource = if (fromLegacy) legacy.absolutePath else null,
        )
    }

    fun read(projectPath: String, scope: MemoryScope): Result<String> = runCatching {
        val file = sourceFileOf(projectPath, scope)
        if (!file.isFile) return@runCatching ""
        file.readText()
    }

    /**
     * 保存。**只写当前名**。
     *
     * 旧文件不顺手删掉：项目级那份在用户的版本库里，删它等于改用户的工作区。
     * 用户级那份由启动时的迁移负责改名，走到这里通常已经不存在了。
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
