package com.zhizhu.zhicode

import android.content.Context
import android.os.Build
import android.system.Os
import com.termux.app.zhicode.core.StorageLinks
import com.termux.shared.termux.TermuxConstants
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Ubuntu 24.04 (PRoot) 云端下载 + 内置 Node.js / Pi 运行环境安装器。 */
class RuntimeInstaller(private val context: Context) {

    fun interface Progress {
        fun onProgress(message: String, percent: Int)
    }

    companion object {
        const val BOOTSTRAP_VERSION = "ubuntu-24.04-arm64+node22+pi-1.6.1+v1"
        const val BOOTSTRAP_ASSET = "node-pi-linux-arm64.tgz"
        const val BOOTSTRAP_SIZE = 62_072_194L
        const val BOOTSTRAP_SHA256 =
            "b709002353a43e45e4891824aa9a90968d1b580a92b12af594bcdf161ff09a99"
        const val BOOTSTRAP_SOURCE = "Ubuntu 24.04 LTS (arm64 云端 rootfs) + 内置 Node.js v22.23.5 + Pi 1.6.1"

        private const val PREFS_NAME = "zhicode_runtime"
        private const val KEY_MIRROR = "ubuntu_mirror"
        private const val MARKER_RELATIVE_PATH = "etc/zhicode-ubuntu-v1"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val MAX_DOWNLOAD_RETRIES = 3
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun savedMirrorOrNull(): UbuntuMirrorSource? =
        UbuntuMirrorSource.findById(prefs.getString(KEY_MIRROR, null))

    fun selectedMirror(): UbuntuMirrorSource = savedMirrorOrNull() ?: UbuntuMirrorSource.TUNA

    fun setSelectedMirror(source: UbuntuMirrorSource) {
        prefs.edit().putString(KEY_MIRROR, source.id).apply()
    }

    /** 检查宿主 `libproot.so`、Ubuntu 容器根目录与基础命令（bash/node/pi）是否全部就绪。 */
    fun isInstalled(): Boolean {
        val rootfs = ContainerPaths.rootfsDir()
        val bash = File(rootfs, "bin/bash")
        val usrBash = File(rootfs, "usr/bin/bash")
        val node = File(rootfs, "usr/local/bin/node")
        val pi = File(rootfs, "usr/local/bin/pi")
        val proot = ProotCommand.prootBinary(context)
        val loader = ProotCommand.loaderBinary(context)
        return rootfs.isDirectory &&
            (bash.exists() || usrBash.exists()) &&
            node.isFile &&
            pi.exists() &&
            proot.isFile &&
            loader.isFile &&
            markerFile(rootfs).isFile
    }

    /** 读已装 Ubuntu 运行时版本，未安装或损坏返回 null。 */
    fun installedVersion(): String? {
        val marker = markerFile(ContainerPaths.rootfsDir())
        if (!marker.isFile) return null
        val lines = runCatching { marker.readLines() }.getOrNull().orEmpty()
        return lines.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    /** 已装容器内置 Node/Pi 层是否与当前 APK 一致。 */
    fun isUpToDate(): Boolean {
        if (!isInstalled()) return false
        val marker = markerFile(ContainerPaths.rootfsDir())
        val lines = runCatching { marker.readLines() }.getOrNull().orEmpty()
        if (lines.size < 2) return false
        return lines[0] == BOOTSTRAP_VERSION && lines[1].equals(BOOTSTRAP_SHA256, ignoreCase = true)
    }

    fun bundledVersion(): String = BOOTSTRAP_VERSION

    fun bootstrapIdentity(): String = "$BOOTSTRAP_VERSION (node+pi=$BOOTSTRAP_SIZE bytes)"

    /** 幂等修复：清理半成品目录、补齐 proot 所需配置与共享存储软链。 */
    @Throws(Exception::class)
    fun repairIfInstalled() {
        val filesDir = context.filesDir
        FileTree.deleteRecursive(File(filesDir, "ubuntu-staging"))
        FileTree.deleteRecursive(File(filesDir, "ubuntu-backup"))
        if (!isInstalled()) return

        val rootfs = ContainerPaths.rootfsDir()
        ensureContainerEssentials(rootfs, selectedMirror())
        setupStorageLinks()
    }

    /** 若已存在 Ubuntu 容器但内置 Node/Pi 归档更新，仅离线刷新 `usr/local`。 */
    @Throws(Exception::class)
    fun upgradeNodePiIfInstalled(progress: Progress? = null) {
        if (!isInstalled()) return
        val rootfs = ContainerPaths.rootfsDir()
        report(progress, "正在校验内置 Node.js 与 Pi 归档…", 10)
        verifyBundledNodePiArchive()
        report(progress, "正在更新内置 Node.js 与 Pi…", 35)
        context.assets.open(BOOTSTRAP_ASSET).use { input ->
            TarGzExtractor.extract(input, rootfs) { readBytes ->
                val pct = 35 + ((readBytes.coerceAtMost(BOOTSTRAP_SIZE) * 60) / BOOTSTRAP_SIZE).toInt()
                report(progress, "正在更新内置 Node.js 与 Pi…", pct.coerceIn(35, 95))
            }
        }
        ensureContainerEssentials(rootfs, selectedMirror())
        writeMarker(rootfs, selectedMirror().id)
        report(progress, "内置 Node.js 与 Pi 已更新", 100)
    }

    /** 从选定云端镜像下载 Ubuntu 24.04 rootfs 并叠加内置 Node.js + Pi。 */
    @Throws(Exception::class)
    fun install(progress: Progress? = null) {
        val mirror = savedMirrorOrNull()
            ?: throw IllegalStateException("请先选择 Ubuntu 24.04 下载源")
        install(mirror, progress)
    }

    @Throws(Exception::class)
    fun install(mirror: UbuntuMirrorSource, progress: Progress? = null) {
        setSelectedMirror(mirror)
        val abis = Build.SUPPORTED_ABIS ?: emptyArray()
        if (!abis.any { it == "arm64-v8a" }) {
            throw UnsupportedOperationException(
                "仅支持 arm64-v8a 设备，当前设备 ABI: ${abis.joinToString()}"
            )
        }
        val proot = ProotCommand.prootBinary(context)
        val loader = ProotCommand.loaderBinary(context)
        if (!proot.isFile || !loader.isFile) {
            throw IllegalStateException("缺少原生 PRoot 二进制：${proot.parent}")
        }

        val filesDir = context.filesDir
        if (!filesDir.isDirectory && !filesDir.mkdirs() && !filesDir.isDirectory) {
            throw IOException("无法创建应用私有目录：$filesDir")
        }

        val rootfs = ContainerPaths.rootfsDir()
        val staging = File(filesDir, "ubuntu-staging")
        val backup = File(filesDir, "ubuntu-backup")
        val downloadPart = File(filesDir, "ubuntu-rootfs.download")
        FileTree.deleteRecursive(staging)
        FileTree.deleteRecursive(backup)
        downloadPart.delete()
        if (!staging.mkdirs() && !staging.isDirectory) {
            throw IOException("无法创建临时解压目录：$staging")
        }

        try {
            report(progress, "正在校验内置 Node.js 与 Pi 资源包…", 2)
            verifyBundledNodePiArchive()

            report(progress, "正在从 ${mirror.displayName} 获取 Ubuntu 24.04 索引…", 5)
            val spec = resolveDownloadSpec(mirror)

            report(progress, "正在从 ${mirror.displayName} 下载 Ubuntu 24.04…", 8)
            val actualSha = downloadWithResumeAndSha256(spec.archiveUrl, downloadPart) { downloaded, total ->
                val pct = if (total > 0L) {
                    8 + ((downloaded.coerceAtMost(total) * 52) / total).toInt()
                } else {
                    25
                }
                val mb = downloaded / (1024L * 1024L)
                val totalMb = total / (1024L * 1024L)
                val label = if (totalMb > 0L) {
                    "正在从 ${mirror.displayName} 下载 Ubuntu 24.04（${mb} / ${totalMb} MB）…"
                } else {
                    "正在从 ${mirror.displayName} 下载 Ubuntu 24.04（${mb} MB）…"
                }
                report(progress, label, pct.coerceIn(8, 60))
            }
            if (!actualSha.equals(spec.expectedSha256, ignoreCase = true)) {
                throw IOException("Ubuntu 24.04 镜像 SHA-256 校验失败（$actualSha != ${spec.expectedSha256}）")
            }

            val archiveTotal = downloadPart.length().coerceAtLeast(1L)
            report(progress, "正在解压 Ubuntu 24.04 根文件系统…", 62)
            downloadPart.inputStream().use { input ->
                TarGzExtractor.extract(
                    input = input,
                    targetRoot = staging,
                    useXz = spec.isXz,
                    stripComponents = spec.stripComponents,
                ) { readBytes ->
                    val pct = 62 + ((readBytes.coerceAtMost(archiveTotal) * 20) / archiveTotal).toInt()
                    report(progress, "正在解压 Ubuntu 24.04 根文件系统…", pct.coerceIn(62, 82))
                }
            }
            downloadPart.delete()

            report(progress, "正在安装内置 Node.js 与 Pi 运行时…", 84)
            context.assets.open(BOOTSTRAP_ASSET).use { input ->
                TarGzExtractor.extract(input, staging) { readBytes ->
                    val pct = 84 + ((readBytes.coerceAtMost(BOOTSTRAP_SIZE) * 9) / BOOTSTRAP_SIZE).toInt()
                    report(progress, "正在安装内置 Node.js 与 Pi 运行时…", pct.coerceIn(84, 93))
                }
            }

            report(progress, "正在配置 Ubuntu 24.04 软件源与 PRoot 环境…", 95)
            ensureContainerEssentials(staging, mirror)
            migrateLegacyZhiCodeData(filesDir, File(staging, "root"))

            activateRootfs(staging, rootfs, backup)
            setupStorageLinks()
            ProotCommand.ensureProotTmpDir()
            writeMarker(rootfs, "${mirror.id} · ${spec.versionLabel}")

            report(progress, "Ubuntu 24.04 + Node.js + Pi 已就绪", 100)
        } catch (t: Throwable) {
            FileTree.deleteRecursive(staging)
            throw t
        } finally {
            downloadPart.delete()
        }
    }

    @Throws(Exception::class)
    private fun verifyBundledNodePiArchive() {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        context.assets.open(BOOTSTRAP_ASSET).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                digest.update(buffer, 0, read)
            }
        }
        if (total != BOOTSTRAP_SIZE) {
            throw IOException("内置 Node.js / Pi 归档长度异常：$total（期望 $BOOTSTRAP_SIZE）")
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (!sha.equals(BOOTSTRAP_SHA256, ignoreCase = true)) {
            throw IOException("内置 Node.js / Pi 归档校验失败（sha256=$sha）")
        }
    }

    @Throws(Exception::class)
    private fun resolveDownloadSpec(mirror: UbuntuMirrorSource): UbuntuDownloadSpec {
        if (mirror == UbuntuMirrorSource.GITHUB) {
            return UbuntuRootfsSources.githubSpec()
        }
        val sumsUrl = UbuntuRootfsSources.sha256SumsUrl(mirror)
            ?: throw IllegalStateException("未配置 SHA256SUMS 地址：${mirror.displayName}")
        val sumsText = fetchText(sumsUrl)
        return UbuntuRootfsSources.parseUbuntuBaseSha256Sums(sumsText, mirror)
            ?: throw IOException("在 ${mirror.displayName} 的 SHA256SUMS 中未找到 Ubuntu 24.04 arm64 归档")
    }

    private fun fetchText(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ZhiCode")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IOException("请求失败 HTTP $code: $urlString")
            }
            return conn.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
        } finally {
            conn.disconnect()
        }
    }

    /** 支持 HTTP Range 断点续传的下载，并对全文件计算 SHA-256。 */
    private fun downloadWithResumeAndSha256(
        urlString: String,
        target: File,
        onBytes: (Long, Long) -> Unit,
    ): String {
        var lastError: Throwable? = null
        var knownTotal = -1L

        for (attempt in 1..MAX_DOWNLOAD_RETRIES) {
            val existingBytes = if (target.isFile) target.length() else 0L
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "ZhiCode")
                if (existingBytes > 0L) {
                    setRequestProperty("Range", "bytes=$existingBytes-")
                }
            }
            try {
                val code = conn.responseCode
                val append = code == HttpURLConnection.HTTP_PARTIAL && existingBytes > 0L
                if (code !in 200..299 && !append) {
                    throw IOException("下载失败 HTTP $code: $urlString")
                }
                val contentLen = conn.contentLengthLong
                if (knownTotal <= 0L && contentLen > 0L) {
                    knownTotal = if (append) existingBytes + contentLen else contentLen
                }
                var downloaded = if (append) existingBytes else 0L
                var lastReport = downloaded
                BufferedInputStream(conn.inputStream, 64 * 1024).use { input ->
                    FileOutputStream(target, append).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                            downloaded += read
                            if (downloaded - lastReport >= 512 * 1024 || (knownTotal > 0L && downloaded >= knownTotal)) {
                                lastReport = downloaded
                                onBytes(downloaded, knownTotal)
                            }
                        }
                    }
                }
                if (knownTotal > 0L && target.length() < knownTotal) {
                    throw IOException("下载未完成（${target.length()} / $knownTotal 字节）")
                }
                lastError = null
                break
            } catch (t: Throwable) {
                lastError = t
                if (attempt == MAX_DOWNLOAD_RETRIES) break
            } finally {
                conn.disconnect()
            }
        }
        if (lastError != null) throw lastError

        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        target.inputStream().use { input ->
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun ensureContainerEssentials(rootfs: File, mirror: UbuntuMirrorSource) {
        val rootHome = File(rootfs, "root")
        val tmpDir = File(rootfs, "tmp")
        val varTmp = File(rootfs, "var/tmp")
        val aptConf = File(rootfs, "etc/apt/apt.conf.d")
        val listsPartial = File(rootfs, "var/lib/apt/lists/partial")
        val archivesPartial = File(rootfs, "var/cache/apt/archives/partial")
        listOf(rootHome, tmpDir, varTmp, aptConf, listsPartial, archivesPartial).forEach { dir ->
            if (!dir.isDirectory) dir.mkdirs()
        }

        runCatching { Os.chmod(rootfs.absolutePath, 0b111101101) }
        runCatching { Os.chmod(rootHome.absolutePath, 0b111101101) }
        runCatching { Os.chmod(tmpDir.absolutePath, 0b111111111) }
        runCatching { Os.chmod(varTmp.absolutePath, 0b111111111) }
        runCatching { Os.chmod(File(rootfs, "usr/local/bin/node").absolutePath, 0b111101101) }
        runCatching { Os.chmod(File(rootfs, "usr/local/bin/pi").absolutePath, 0b111101101) }

        val resolv = File(rootfs, "etc/resolv.conf")
        if (!resolv.isFile || resolv.length() == 0L) {
            writeText(resolv, "nameserver 223.5.5.5\nnameserver 8.8.8.8\n")
        }
        val hosts = File(rootfs, "etc/hosts")
        if (!hosts.isFile || hosts.length() == 0L) {
            writeText(hosts, "127.0.0.1 localhost\n::1 localhost\n")
        }
        // 阻止 /etc/bash.bashrc 在交互式 shell 启动时调用 `groups` 打印 Android GID 找不到名称的告警。
        val hushlogin = File(rootHome, ".hushlogin")
        if (!hushlogin.exists()) {
            writeText(hushlogin, "")
        }
        writeText(
            File(aptConf, "01-proot"),
            "APT::Sandbox::User \"root\";\nAcquire::http::Pipeline-Depth \"0\";\n",
        )
        configureAptSources(rootfs, mirror)
        ProotCommand.ensureProotTmpDir()
    }

    private fun configureAptSources(rootfs: File, mirror: UbuntuMirrorSource) {
        val portsUrl = UbuntuRootfsSources.aptPortsMirrorUrl(mirror)
        val deb822 = """
Types: deb
URIs: $portsUrl
Suites: noble noble-updates noble-backports noble-security
Components: main restricted universe multiverse
""".trimStart()
        val sourcesFile = File(rootfs, "etc/apt/sources.list.d/ubuntu.sources")
        if (sourcesFile.parentFile?.isDirectory == true) {
            writeText(sourcesFile, deb822)
        } else {
            val legacy = """
deb $portsUrl noble main restricted universe multiverse
deb $portsUrl noble-updates main restricted universe multiverse
deb $portsUrl noble-security main restricted universe multiverse
""".trimStart()
            writeText(File(rootfs, "etc/apt/sources.list"), legacy)
        }
    }

    private fun migrateLegacyZhiCodeData(filesDir: File, stagingRootHome: File) {
        val targetMeta = File(stagingRootHome, TermuxConstants.DATA_DIR_NAME)
        if (targetMeta.exists()) return
        for (legacyPath in listOf("debian/root/${TermuxConstants.DATA_DIR_NAME}", "home/${TermuxConstants.DATA_DIR_NAME}")) {
            val legacyMeta = File(filesDir, legacyPath)
            if (legacyMeta.isDirectory) {
                targetMeta.parentFile?.mkdirs()
                if (legacyMeta.renameTo(targetMeta)) return
            }
        }
    }

    @Throws(Exception::class)
    private fun activateRootfs(staging: File, rootfs: File, backup: File) {
        val hadPrevious = rootfs.exists()
        val preservedRoot = if (hadPrevious) File(rootfs, "root").takeIf { it.isDirectory } else null
        val stagedRoot = File(staging, "root")

        if (hadPrevious) {
            FileTree.deleteRecursive(backup)
            if (!rootfs.renameTo(backup)) {
                throw IOException("Cannot preserve current Ubuntu rootfs: $rootfs")
            }
        }

        val backupRoot = File(backup, "root")
        if (preservedRoot != null && backupRoot.isDirectory) {
            val defaultBashrc = File(stagedRoot, ".bashrc").takeIf { it.isFile }?.readText()
            val defaultProfile = File(stagedRoot, ".profile").takeIf { it.isFile }?.readText()
            FileTree.deleteRecursive(stagedRoot)
            if (backupRoot.renameTo(stagedRoot)) {
                if (defaultBashrc != null && !File(stagedRoot, ".bashrc").exists()) {
                    writeText(File(stagedRoot, ".bashrc"), defaultBashrc)
                }
                if (defaultProfile != null && !File(stagedRoot, ".profile").exists()) {
                    writeText(File(stagedRoot, ".profile"), defaultProfile)
                }
            }
        }

        if (!staging.renameTo(rootfs)) {
            if (hadPrevious && backup.exists() && !backup.renameTo(rootfs)) {
                throw IOException("Cannot restore previous Ubuntu rootfs: $rootfs")
            }
            throw IOException("Cannot activate Ubuntu rootfs: $rootfs")
        }
        if (hadPrevious) FileTree.deleteRecursive(backup)
    }

    fun setupStorageLinks() {
        val home = ContainerPaths.homeDir()
        if (!home.isDirectory && !home.mkdirs() && !home.isDirectory) return
        runCatching { StorageLinks.setup(home, File(StorageLinks.EXTERNAL_ROOT)) }
    }

    private fun markerFile(rootfs: File): File = File(rootfs, MARKER_RELATIVE_PATH)

    private fun writeMarker(rootfs: File, detail: String) {
        writeText(
            markerFile(rootfs),
            "$BOOTSTRAP_VERSION\n$BOOTSTRAP_SHA256\n$BOOTSTRAP_SOURCE ($detail)\n",
        )
    }

    private fun writeText(target: File, text: String) {
        target.parentFile?.mkdirs()
        target.writeText(text)
        runCatching { Os.chmod(target.absolutePath, 0b110100100) }
    }

    private fun report(progress: Progress?, message: String, percent: Int) {
        runCatching { progress?.onProgress(message, percent) }
    }
}
