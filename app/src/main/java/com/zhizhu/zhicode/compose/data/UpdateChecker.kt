package com.zhizhu.zhicode.compose.data

import com.zhizhu.zhicode.compose.model.RELEASES_API_URL
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 检查更新：拉本仓库的 GitHub Releases。
 *
 * ## 为什么是裸 `HttpURLConnection`
 *
 * 本工程**没有** okhttp / retrofit / ktor（`app/build.gradle` 里一个都没有），
 * 既有联网代码一律裸 `HttpURLConnection`（见 `data/SkillImport.kt`、
 * `api/ModelCatalogClient.java`）。为了一个"查一下 tag"的请求引一个网络框架，
 * 换来的是包体、混淆规则和一条新的依赖审计面。
 *
 * ## 三条硬约束（照抄既有联网代码的标准）
 *
 * 1. **不跟随重定向**。对方是 GitHub，但"能过 3xx"这件事本身没有价值，
 *    而跟随重定向意味着把请求送到任意主机。遇到 3xx 直接报错。
 * 2. **响应体有上限**，且**边读边计数** —— 不是读完再判长度（那样限制形同虚设，
 *    内存已经在判定之前被吃光）。
 * 3. **超时短**。这是一个可选的、只在用户点开时才发生的请求，8 秒建连 / 12 秒读。
 *
 * ## 失败原因必须分开报
 *
 * "网络不通"、"被限流（403）"、"仓库没有 Release"、"返回格式不对"是四件
 * 完全不同的事，用户能做的动作也不同（检查网络 / 稍后再试 / 去看仓库 / 报 bug）。
 * 糊成一句"检查更新失败"等于把这四条信息丢掉。
 *
 * ## 只读公开信息
 *
 * 请求里**不带任何用户数据、不带应用标识、不带密钥** —— 只有 GitHub 强制要求的
 * `User-Agent`（缺了会 403）。隐私政策页对此有对应的一条说明。
 */
internal object UpdateChecker {

    /** 请求的接口。坐标来自 `model/ProjectRepo.kt`，不在这里重复写仓库名。 */
    private const val ENDPOINT = RELEASES_API_URL

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000

    /** 响应体字符上限。Releases 列表带全套 body 也只几十 KB，512KB 已经很宽松。 */
    private const val MAX_BODY_CHARS = 512 * 1024

    /** 只看前若干条 release：再多也没有意义，且要防止对方返回上千条。 */
    private const val MAX_RELEASES = 30

    /** 单个字段的字符上限（tag / 名称这类短字段）。 */
    private const val MAX_SHORT_FIELD = 160

    /** 发布说明正文的字符上限。界面上极限也读不完这么多。 */
    private const val MAX_BODY_FIELD = 20_000

    private const val READ_BUFFER_CHARS = 4096

    /** APK 下载的上限。整包自带 Termux 用户空间，几百 MB 的量级；给到 1GB 仍然拦得住异常响应。 */
    private const val MAX_APK_BYTES = 1024L * 1024 * 1024

    private const val HEADER_ACCEPT = "Accept"
    private const val HEADER_USER_AGENT = "User-Agent"
    private const val ACCEPT_GITHUB = "application/vnd.github+json"

    /**
     * `User-Agent`。**必须有**：GitHub API 对没有 UA 的请求一律回 403，
     * 而那个 403 的正文是 HTML，拿它去解析 JSON 只会得到与真正原因无关的报错。
     */
    private const val USER_AGENT = "ZhiCode-UpdateCheck"
    private const val PREFERRED_APK_SUFFIX = ".apk"

    private const val ERROR_REDIRECT = "更新检查请求被重定向，已拒绝"
    private const val ERROR_RATE_LIMITED = "GitHub 拒绝了这次请求（可能是访问过于频繁），请稍后再试"
    private const val ERROR_BODY_INVALID = "更新信息返回格式无效"
    private const val ERROR_TOO_LARGE = "更新信息响应过大"
    private const val ERROR_APK_TOO_LARGE = "安装包超过可下载的体积上限"
    private const val ERROR_NO_SPACE = "存储空间不足，装不下这个安装包"

    /** 一条 release（只留界面真正要用的字段）。 */
    data class Release(
        val tag: String,
        val name: String,
        val publishedAt: String,
        val body: String,
        val prerelease: Boolean,
        val pageUrl: String,
        val apkUrl: String?,
        val apkName: String?,
        val apkSize: Long,
    )

    /** 检查结果。`releases` 为空表示**仓库还没有发布过 Release**（一个正常状态，不是错误）。 */
    data class Result(val releases: List<Release>)

    /** 取消信号。界面销毁 / 用户点取消时用它中断一次正在进行的请求。 */
    fun interface Cancellation {
        fun isCancelled(): Boolean
    }

    /**
     * 拉一次 Releases 列表。
     *
     * @return 解析成功的 release 列表（**可能为空**：仓库还没发过 Release）
     * @throws IllegalStateException 网络不可达 / 被限流 / 非 2xx / 响应格式不对 / 过大
     * @throws InterruptedException  被取消（线程中断或 [cancellation] 报告已取消）
     */
    fun fetch(cancellation: Cancellation = Cancellation { false }): List<Release> {
        checkCancelled(cancellation)
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            setInstanceFollowRedirects(false)
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty(HEADER_ACCEPT, ACCEPT_GITHUB)
            setRequestProperty(HEADER_USER_AGENT, USER_AGENT)
        }
        try {
            val body = readBounded(connection, cancellation)
            return parse(body)
        } finally {
            // 不断开的话连接会挂在连接池里，连点几次就把池占满。
            connection.disconnect()
        }
    }

    /**
     * 读响应体，带状态码分流、长度上限与取消检查。
     *
     * 先判状态码再读正文：错误响应的正文常常是 HTML，
     * 拿它去 `JSONArray` 只会得到一个与真正原因无关的解析错误。
     */
    private fun readBounded(connection: HttpURLConnection, cancellation: Cancellation): String {
        val status = connection.responseCode
        if (status in 300..399) throw IllegalStateException(ERROR_REDIRECT)
        if (status == 403 || status == 429) throw IllegalStateException(ERROR_RATE_LIMITED)
        if (status < 200 || status >= 300) {
            throw IllegalStateException("更新检查失败（HTTP $status）")
        }
        val builder = StringBuilder()
        try {
            val stream: InputStream = connection.inputStream
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                val buffer = CharArray(READ_BUFFER_CHARS)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    checkCancelled(cancellation)
                    if (builder.length + count > MAX_BODY_CHARS) throw IllegalStateException(ERROR_TOO_LARGE)
                    builder.append(buffer, 0, count)
                }
            }
        } catch (io: java.io.IOException) {
            // 取消时 `disconnect()` 会让正在读的流抛 IOException。区分开，
            // 否则用户点「取消」会看到一句"网络错误"。
            checkCancelled(cancellation)
            throw io
        }
        checkCancelled(cancellation)
        return builder.toString()
    }

    private fun checkCancelled(cancellation: Cancellation) {
        if (Thread.currentThread().isInterrupted || cancellation.isCancelled()) {
            throw InterruptedException("Cancelled")
        }
    }

    /**
     * 解析 Releases 列表。
     *
     * 条目级容错：某一条解析不了就**跳过它**，不让一条脏数据把整次检查废掉 ——
     * 但列表整体不是 JSON 数组时是真错误，照抛。
     */
    internal fun parse(body: String): List<Release> {
        val array: JSONArray = try {
            JSONArray(body)
        } catch (_: Exception) {
            throw IllegalStateException(ERROR_BODY_INVALID)
        }
        val out = ArrayList<Release>(minOf(array.length(), MAX_RELEASES))
        for (i in 0 until array.length()) {
            if (out.size >= MAX_RELEASES) break
            val item = array.optJSONObject(i) ?: continue
            parseRelease(item)?.let { out.add(it) }
        }
        return out
    }

    private fun parseRelease(item: JSONObject): Release? {
        // draft 是还没发布的草稿，对用户不存在；跳过而不是显示出来。
        if (item.optBoolean("draft", false)) return null
        val tag = short(item.optString("tag_name", ""))
        if (tag.isEmpty()) return null
        val apk = firstApkAsset(item.optJSONArray("assets"))
        return Release(
            tag = tag,
            name = short(item.optString("name", "")).ifEmpty { tag },
            publishedAt = short(item.optString("published_at", item.optString("created_at", ""))),
            body = clip(item.optString("body", ""), MAX_BODY_FIELD),
            prerelease = item.optBoolean("prerelease", false),
            pageUrl = short(item.optString("html_url", "")),
            apkUrl = apk?.first,
            apkName = apk?.second,
            apkSize = apk?.third ?: 0L,
        )
    }

    /**
     * 取 assets 里第一个 `.apk`。
     *
     * 找不到就返回 null（源码-only 的 Release 确实存在）—— 界面据此如实说
     * "这次发布没有附带安装包"，而不是给一个点了没反应的按钮。
     */
    private fun firstApkAsset(assets: JSONArray?): Triple<String, String, Long>? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = short(asset.optString("name", ""))
            if (!name.endsWith(PREFERRED_APK_SUFFIX, ignoreCase = true)) continue
            val url = short(asset.optString("browser_download_url", ""))
            if (url.isEmpty()) continue
            return Triple(url, name, asset.optLong("size", 0L))
        }
        return null
    }

    /**
     * 下载一个 APK 到 [target]。
     *
     * ## 为什么单独一个函数而不是复用 [fetch]
     *
     * 两者的约束不同：`fetch` 读的是几十 KB 的 JSON，这里是几百 MB 的二进制；
     * 这里还要写盘、报进度、检查剩余空间。混进一个函数会让两边都变模糊。
     *
     * ## 三道检查（顺序不能变）
     *
     * 1. **体积上限**：`Content-Length` 超限直接拒，不开始下载；
     * 2. **剩余空间**：在写之前判，避免下到一半磁盘满；
     * 3. **边写边计数**：服务端不给 `Content-Length` 时也要拦得住。
     *
     * 写入**先落 `.part` 再改名**：中途失败时不会留下一个看起来完整的半截 APK，
     * 那个半截包交给安装器只会得到一句语焉不详的解析失败。
     */
    fun downloadApk(
        url: String,
        target: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        cancellation: Cancellation = Cancellation { false },
    ): File {
        checkCancelled(cancellation)
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, target.name + ".part")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            setInstanceFollowRedirects(true)
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty(HEADER_ACCEPT, "application/octet-stream")
            setRequestProperty(HEADER_USER_AGENT, USER_AGENT)
        }
        try {
            val status = connection.responseCode
            if (status < 200 || status >= 300) {
                throw IllegalStateException("下载失败（HTTP $status）")
            }
            val declared = connection.contentLengthLong
            if (declared > MAX_APK_BYTES) throw IllegalStateException(ERROR_APK_TOO_LARGE)
            val freeSpace = target.parentFile?.usableSpace
            if (declared > 0 && freeSpace != null && declared > freeSpace) {
                throw IllegalStateException(ERROR_NO_SPACE)
            }

            var written = 0L
            try {
                connection.inputStream.use { input ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            checkCancelled(cancellation)
                            written += count
                            if (written > MAX_APK_BYTES) throw IllegalStateException(ERROR_APK_TOO_LARGE)
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                        output.fd.sync()
                    }
                }
            } catch (failure: Throwable) {
                // 失败一律清掉半截文件：留着它，下次"已下载"的判据就可能误命中。
                partial.delete()
                throw failure
            }
            if (partial.length() <= 0L) {
                partial.delete()
                throw IllegalStateException("下载到的安装包是空的")
            }
            onProgress(written, if (declared > 0) declared else written)
            if (target.exists() && !target.delete()) {
                partial.delete()
                throw IllegalStateException("无法覆盖已存在的安装包")
            }
            if (!partial.renameTo(target)) {
                partial.delete()
                throw IllegalStateException("无法保存安装包")
            }
            return target
        } finally {
            connection.disconnect()
        }
    }

    /** 短字段：去控制字符、限长。tag / 名称 / URL 会直接进界面，必须先消毒。 */
    private fun short(raw: String): String = clip(sanitize(raw).trim(), MAX_SHORT_FIELD)

    private fun clip(text: String, limit: Int): String =
        if (text.length <= limit) text else text.take(limit)

    /**
     * 去掉控制字符。
     *
     * 换行**保留**（发布说明是分段的），其余 C0 控制字符丢掉 ——
     * 它们会把一行的排版搞乱，而且不该出现在 API 的文本字段里。
     */
    private fun sanitize(raw: String): String {
        if (raw.isEmpty()) return raw
        val builder = StringBuilder(raw.length)
        for (ch in raw) {
            when {
                ch == '\n' || ch == '\t' -> builder.append(ch)
                ch.isISOControl() -> {}
                else -> builder.append(ch)
            }
        }
        return builder.toString()
    }
}
