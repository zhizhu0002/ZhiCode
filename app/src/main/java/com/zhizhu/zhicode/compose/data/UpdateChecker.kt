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

    /** `User-Agent`。 */
    private const val USER_AGENT = "ZhiCode-UpdateCheck"
    private const val PREFERRED_APK_SUFFIX = ".apk"

    private const val MIRROR_PREFIX = "https://ghfast.top/"

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

    /** 拉一次 Releases 列表。 */
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

    /** 读响应体，带状态码分流、长度上限与取消检查。 */
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

    /** 解析 Releases 列表。 */
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

    /** 取 assets 里第一个 `.apk`。 */
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

    fun downloadApk(
        url: String,
        target: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        cancellation: Cancellation = Cancellation { false },
        mirrorPrefix: String? = MIRROR_PREFIX,
    ): File {
        // 候选地址：镜像优先，原地址兜底。
        //
        // ⚠️ 顺序是"先镜像、失败再原址"而不是反过来：镜像存在的理由就是
        // 原地址在某些网络下**根本连不上**，先试原址等于让用户白等一次超时。
        //
        // 但如果镜像本身挂了（`ghfast.top` 是社区维护的公共服务，不保证可用），
        // 必须能回退 —— 否则我们把"某些网络连不上 GitHub"换成了
        // "所有网络都依赖一个第三方镜像"，那是更糟的可用性。
        val candidates = buildList {
            if (!mirrorPrefix.isNullOrBlank()) add(mirrorPrefix + url)
            add(url)
        }
        var lastFailure: Throwable? = null
        for ((index, candidate) in candidates.withIndex()) {
            try {
                return downloadFrom(
                    url = candidate,
                    target = target,
                    onProgress = onProgress,
                    cancellation = cancellation,
                )
            } catch (cancelled: InterruptedException) {
                // 用户主动取消：不该回退去试下一个地址。
                throw cancelled
            } catch (failure: Throwable) {
                lastFailure = failure
                // 还有下一个就继续；最后一个失败时把原因抛出去。
                if (index == candidates.lastIndex) throw failure
            }
        }
        throw lastFailure ?: IllegalStateException("下载失败")
    }

    /** 单个地址的实际下载。[downloadApk] 负责选地址与回退。 */
    private fun downloadFrom(
        url: String,
        target: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        cancellation: Cancellation,
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
            /* 进度必须在**循环里**报，不能只在结束时报一次。 */
            var lastReportedPercent = -1
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
                            if (declared > 0) {
                                val percent = ((written * 100) / declared).toInt().coerceIn(0, 100)
                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    onProgress(written, declared)
                                }
                            } else {
                                // 服务端没给 Content-Length：总量未知，只能按已下载量报。
                                // 界面那边 percent 会一直是 0 —— 这是诚实的，不编一个假分母。
                                onProgress(written, 0L)
                            }
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
            // 收尾那一报：确保界面一定走到 100%，不依赖某次循环刚好跨档。
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

    /** 去掉控制字符。 */
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
