package com.zhizhu.zhicode.compose.data

import com.zhizhu.zhicode.compose.model.FileEntry
import com.zhizhu.zhicode.compose.model.OpenFile
import com.termux.shared.termux.TermuxConstants
import java.io.File

/**
 * 真实文件系统读取。
 *
 * 设计取舍：
 * - **不缓存**。文件面板是"看一眼"的场景，缓存反而会在 Agent 改了文件之后显示旧内容；
 *   目录深度只有一层，`File.listFiles()` 足够快。
 * - **正文由编辑页负责写入**。这里仍只做文件系统读取；保存统一经 ViewModel → FileOps，
 *   避免列表、编辑页和 Agent 各自维护一套写路径。
 * - **大文件截断**。打开一个几 MB 的日志会把界面拖死，所以超过 [MAX_PREVIEW_BYTES]
 *   只读前一段并明确标注"已截断"，而不是假装完整。
 * - **二进制不显示**。用 NUL 字节判断：显示乱码比显示"这是一个二进制文件"更没用。
 */
internal object FileBrowser {

    /** 单次预览的上限。超出部分截断并标注。 */
    private const val MAX_PREVIEW_BYTES = 256 * 1024

    /** 预览时按字节读取的块大小。 */
    private const val READ_CHUNK = 64 * 1024

    /**
     * 项目的根目录。
     *
     * 内置 Termux 环境没装时 home 目录还不存在，此时回退到应用私有目录 ——
     * 让面板至少有个能打开的地方，而不是报"路径不存在"。
     */
    fun projectRoot(): String {
        val home = File(TermuxConstants.TERMUX_HOME_DIR_PATH)
        return if (home.isDirectory) home.absolutePath else TermuxConstants.TERMUX_FILES_DIR_PATH
    }

    /**
     * 列出一层子项。
     *
     * 排序：目录在前，然后按名称不区分大小写 —— 与常见文件管理器一致。
     * 读不到的项（权限、符号链接指向不存在的位置）直接跳过，不让整个列表失败。
     */
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

    /** 读取文本文件用于预览。默认 UTF-8；编辑页可传入用户选择的编码。 */
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

    /**
     * 为「附加到消息」读取文本内容。读不出来、或判定是二进制时返回 `null`。
     *
     * 为什么单独开一个函数而不是让调用方用 [read]：
     * [read] 对二进制返回的是「（二进制文件，N 字节，不在界面中显示）」这句**给人看的提示**。
     * 如果直接把它当正文附加，模型会收到一句毫无意义的说明 —— 所以要能区分
     * 「读到了文本」和「这根本不该附加」，[read] 的返回值表达不了这个区别。
     *
     * 截断与二进制判定都沿用 [read] 的那一套（[MAX_PREVIEW_BYTES] + NUL 检测），
     * 不另起一份，免得两处口径不一致。
     */
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
