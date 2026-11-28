package com.zhizhu.zhicode.compose.data

import com.zhizhu.zhicode.compose.model.FileEntry
import com.zhizhu.zhicode.compose.model.OpenFile
import com.termux.shared.termux.TermuxConstants
import java.io.File

/** 真实文件系统读取。 */
internal object FileBrowser {

    /** 单次预览的上限。超出部分截断并标注。 */
    private const val MAX_PREVIEW_BYTES = 256 * 1024

    /** 预览时按字节读取的块大小。 */
    private const val READ_CHUNK = 64 * 1024

    /** 项目的根目录。 */
    fun projectRoot(): String {
        val home = File(TermuxConstants.TERMUX_HOME_DIR_PATH)
        return if (home.isDirectory) home.absolutePath else TermuxConstants.TERMUX_FILES_DIR_PATH
    }

    /** 列出一层子项。 */
    fun children(path: String): List<FileEntry> {
        val dir = File(path)
        val listed = if (dir.isDirectory) dir.listFiles() else null
        if (listed == null) return emptyList()
        return listed
            .asSequence()
            .filter { it.exists() }
            .map { file ->
                FileEntry(
                    name = file.name,
                    path = file.absolutePath,
                    // 符号链接指向目录时也要当成目录，否则点进去会变成"打开文件"。
                    directory = runCatching { file.isDirectory }.getOrDefault(false),
                    size = runCatching { if (file.isFile) file.length() else 0L }.getOrDefault(0L),
                    // 目录也取一次 lastModified：它对目录同样有意义（"这个目录里最后动过的是什么时候"），
                    // 而列表行只在文件上显示时间，所以这次取值不会白花 —— 它同时给排序留了余地。
                    modifiedAt = runCatching { file.lastModified() }.getOrDefault(0L),
                )
            }
            .sortedWith(compareByDescending<FileEntry> { it.directory }.thenBy { it.name.lowercase() })
            .toList()
    }

    fun read(path: String, charsetName: String = "UTF-8"): OpenFile {
        val file = File(path)
        val name = file.name
        if (!file.isFile) {
            return OpenFile(name, path, "", "（文件不存在或不可读：$path）")
        }
        val size = runCatching { file.length() }.getOrDefault(0L)
        val bytes = runCatching { readPrefix(file, MAX_PREVIEW_BYTES) }.getOrNull()
            ?: return OpenFile(name, path, "", "（读取失败，可能是权限不足）")

        if (bytes.any { it == 0.toByte() }) {
            return OpenFile(
                name = name,
                path = path,
                language = "",
                content = "（二进制文件，$size 字节，不在界面中显示）",
            )
        }

        val charset = runCatching { java.nio.charset.Charset.forName(charsetName) }
            .getOrDefault(Charsets.UTF_8)
        val text = String(bytes, charset)
        val content = if (size > MAX_PREVIEW_BYTES) {
            text + "\n\n…（文件共 $size 字节，此处只显示前 $MAX_PREVIEW_BYTES 字节）"
        } else {
            text
        }
        return OpenFile(
            name = name,
            path = path,
            language = languageFor(name),
            content = content,
            truncated = size > MAX_PREVIEW_BYTES,
            charsetName = charset.name(),
        )
    }

    fun readTextForAttachment(path: String): String? {
        val file = File(path)
        if (!file.isFile) return null
        val bytes = runCatching { readPrefix(file, MAX_PREVIEW_BYTES) }.getOrNull() ?: return null
        if (bytes.isEmpty()) return ""
        if (bytes.any { it == 0.toByte() }) return null
        return String(bytes, Charsets.UTF_8)
    }

    private fun readPrefix(file: File, limit: Int): ByteArray {        file.inputStream().use { input ->
            val out = java.io.ByteArrayOutputStream(minOf(limit, READ_CHUNK))
            val buffer = ByteArray(READ_CHUNK)
            var remaining = limit
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read <= 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
            return out.toByteArray()
        }
    }

    /** 按扩展名猜语言，用于代码高亮。不认识就返回空串（按纯文本渲染）。 */
    fun languageFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "js", "mjs", "cjs" -> "javascript"
        "ts", "tsx" -> "typescript"
        "py" -> "python"
        "sh", "bash", "zsh" -> "shell"
        "json" -> "json"
        "xml" -> "xml"
        "html", "htm" -> "html"
        "css" -> "css"
        "md", "markdown" -> "markdown"
        "yml", "yaml" -> "yaml"
        "toml", "ini", "cfg", "properties", "gradle" -> "ini"
        "c", "h" -> "c"
        "cpp", "cc", "hpp" -> "cpp"
        "go" -> "go"
        "rs" -> "rust"
        "sql" -> "sql"
        else -> ""
    }
}
