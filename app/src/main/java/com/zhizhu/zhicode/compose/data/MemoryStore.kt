package com.zhizhu.zhicode.compose.data

import com.zhizhu.zhicode.compose.model.MemoryFile
import com.zhizhu.zhicode.compose.model.MemoryScope
import com.termux.shared.termux.TermuxConstants
import java.io.File

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

    /** 保存。 */
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
