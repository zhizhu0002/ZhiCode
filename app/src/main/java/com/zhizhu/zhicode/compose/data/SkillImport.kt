package com.zhizhu.zhicode.compose.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * 从 URL 导入技能。
 *
 * ## 为什么是"URL 导入"而不是"GitHub 导入"
 *
 * 参考实现（rikkahub）做的是 GitHub 专用导入：走 Contents API 递归列目录、
 * 逐个文件下载。那条路把"能不能导入"绑死在一个站点上，且整条链路的错误分支
 * （目录不存在 / 限流 / 分支名不对 / 子目录递归）都得单独处理。
 *
 * 这里改成**任意 http(s) 地址**：
 * - 地址指向一份 `SKILL.md`（或任何文本）→ 进「手动添加」让用户确认；
 * - 地址指向一个 **zip** → 把里面每个 `SKILL.md` 解开逐个导入（子目录也认）。
 *
 * zip 这条路已经覆盖了"从仓库下载整个技能目录"的场景（GitHub 的
 * `/archive/refs/heads/main.zip` 就是一个普通 zip 地址），却只依赖 JDK 自带的
 * `HttpURLConnection` + `ZipInputStream`，**不引任何新依赖**。
 *
 * ## 三道硬限制
 *
 * 网络 + 解压是外部输入，任何一处不设限都会变成"点一下就把应用卡死/吃光内存"：
 * 1. [readAllBounded] 边读边计数，超限**立刻断开**，不是先读完再判长度；
 * 2. 连接/读取都有超时，否则一个不回包的服务器能把协程挂到天荒地老；
 * 3. zip 的条目数、单个条目的大小、总大小都有上限（zip bomb）。
 */
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

    /**
     * 像是文本吗？
     *
     * 判据是**没有 NUL 字节**：`SKILL.md` 是 UTF-8 文本，而二进制（图片、`.so`）
     * 里几乎必然出现 NUL。没有这道判断的话，一个指向图片的地址会被当成
     * "一份 SKILL.md" 塞进对话框，用户看到的是一屏乱码。
     */
    fun looksLikeText(bytes: ByteArray): Boolean = !bytes.take(4096).any { it == 0.toByte() }

    /**
     * 边读边计数。
     *
     * ⚠️ **不能**改成 `readBytes()` 再判长度：那样限制就形同虚设 ——
     * 内存已经在判定之前被吃光了。这里读满 [limit] + 1 就抛，连接随即被上层关掉。
     */
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

    /**
     * 下载一个 http(s) 地址。
     *
     * 失败原因必须**分开报**，不能都糊成"下载失败"：
     * 地址写错、服务器 404、内容太大是三种完全不同的下一步动作。
     */
    fun download(urlText: String): Result<ByteArray> = runCatching {
        val text = urlText.trim()
        require(isHttpUrl(text)) { "只支持 http:// 或 https:// 开头的链接" }
        val connection = (URL(text).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            // 只要一个 UA 就够了：不自己设 Accept，让服务端按默认的"什么都接受"回内容。
            // ⚠️ 也别往这个文件里写注释形状的字符（块注释的开头两字符）：本仓库的守卫脚本
            // 用正则剔除注释，看到那对字符就会把它后面半段源码整块吃掉，断言于是静默失效。
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

    /**
     * 从 zip 里取出所有 `SKILL.md`。
     *
     * 只认**文件名为 SKILL.md** 的条目（不认大小写区别之外的变体），
     * 但**认任意深度的子目录** —— 仓库压缩包的第一层永远是 `repo-main/`。
     * 每份内容的技能名取 frontmatter 的 `name`（与手动添加同一条规则），
     * 取不到就报错并指出是哪个条目：猜一个目录名会建出用户没打算建的东西。
     */
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
