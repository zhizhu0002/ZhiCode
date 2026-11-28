package com.zhizhu.zhicode.compose.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.ZipInputStream

internal object SkillImport {

    /** 整个下载的体积上限。SKILL.md 通常几 KB，2 MB 已经能装下整包技能目录。 */
    const val MAX_DOWNLOAD_BYTES = 2 * 1024 * 1024

    /** zip 里的条目数上限。 */
    const val MAX_ZIP_ENTRIES = 512

    /** zip 里单个 `SKILL.md` 的体积上限（与「从文件导入」同一档）。 */
    const val MAX_SKILL_BYTES = 256 * 1024

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val SKILL_FILE = "SKILL.md"

    /** 从 zip 里解出来的一份技能。 */
    data class ImportedSkill(val name: String, val content: String)

    /** 只接受 http(s)。`file:` / `content:` 会让"从 URL 导入"变成一条任意本地读路径。 */
    fun isHttpUrl(text: String): Boolean {
        val trimmed = text.trim().lowercase(Locale.US)
        return trimmed.startsWith("https://") || trimmed.startsWith("http://")
    }

    /** zip 的魔数（`PK\x03\x04`）。只读头两个字节，不解压就能分流。 */
    fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /** 像是文本吗？ */
    fun looksLikeText(bytes: ByteArray): Boolean = !bytes.take(4096).any { it == 0.toByte() }

    fun readAllBounded(input: InputStream, limit: Int = MAX_DOWNLOAD_BYTES): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val step = input.read(buffer)
            if (step <= 0) break
            total += step
            if (total > limit) throw IllegalStateException("内容太大（上限 ${limit / 1024} KB）")
            out.write(buffer, 0, step)
        }
        return out.toByteArray()
    }

    /** 下载一个 http(s) 地址。 */
    fun download(urlText: String): Result<ByteArray> = runCatching {
        val text = urlText.trim()
        require(isHttpUrl(text)) { "只支持 http:// 或 https:// 开头的链接" }
        val connection = (URL(text).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ZhiCode")
        }
        try {
            val status = connection.responseCode
            require(status == HttpURLConnection.HTTP_OK) { "服务器返回 HTTP $status" }
            connection.inputStream.use { readAllBounded(it) }
        } finally {
            // 不断开的话连接会一直挂着，连点几次就把连接池占满。
            connection.disconnect()
        }
    }

    /** 从 zip 里取出所有 `SKILL.md`。 */
    fun skillsFromZip(bytes: ByteArray): List<ImportedSkill> {
        require(looksLikeZip(bytes)) { "这不是一个 zip 压缩包" }
        val found = mutableListOf<ImportedSkill>()
        var entries = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                try {
                    entries++
                    if (entries > MAX_ZIP_ENTRIES) {
                        throw IllegalStateException("压缩包里条目太多（上限 $MAX_ZIP_ENTRIES 个）")
                    }
                    if (!entry.isDirectory && entry.name.substringAfterLast('/').equals(SKILL_FILE, ignoreCase = true)) {
                        // 单个条目也走有界读取：zip 头部声明的长度可以是假的
                        // （解压后比声明的大得多，就是 zip bomb 的形态）。
                        val text = readAllBounded(zip, MAX_SKILL_BYTES).toString(Charsets.UTF_8)
                        val name = SkillStore.nameFromContent(text)
                            ?: throw IllegalStateException("${entry.name} 的 frontmatter 里没有 name 字段")
                        found += ImportedSkill(name, text)
                    }
                } finally {
                    zip.closeEntry()
                }
            }
        }
        if (found.isEmpty()) throw IllegalStateException("压缩包里没有找到 SKILL.md")
        return found
    }
}
