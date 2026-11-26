package com.zhizhu.zhicode

import android.content.Context
import android.os.Build
import android.system.Os
import com.termux.app.zhicode.core.StorageLinks
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * 内置 Debian + PRoot 运行环境的安装器与升级器。
 */
class RuntimeInstaller(private val context: Context) {

    fun interface Progress {
        fun onProgress(message: String, percent: Int)
    }

    companion object {
        const val BOOTSTRAP_VERSION = "debian-12-arm64+node22+pi-1.6.1+v1"
        const val BOOTSTRAP_ASSET = "debian-arm64.tgz"
        const val BOOTSTRAP_SIZE = 136_560_524L
        const val BOOTSTRAP_SHA256 =
            "5e1bbc7d2344a514f5164b7eef017e60dfe0abadaad3e9fffc2e236d6ef7d913"
        const val BOOTSTRAP_SOURCE = "debian:bookworm-slim (linux/arm64) + nodejs v22.23.5 + pi-coding-agent 1.6.1"

        private const val MARKER_RELATIVE_PATH = "etc/zhicode-debian-v1"
    }

    /** 检查宿主 `libproot.so`、容器根目录与基础命令（bash/node/pi）是否全部就绪。 */
    fun isInstalled(): Boolean {
        val rootfs = ContainerPaths.rootfsDir()
        val bash = File(rootfs, "bin/bash")
        val node = File(rootfs, "usr/local/bin/node")
        val pi = File(rootfs, "usr/local/bin/pi")
        val proot = ProotCommand.prootBinary(context)
        val loader = ProotCommand.loaderBinary(context)
        return rootfs.isDirectory &&
            bash.exists() &&
            node.isFile &&
            pi.exists() &&
            proot.isFile &&
            loader.isFile &&
            markerFile(rootfs).isFile
    }

    /** 读已装 Debian 运行时版本，未安装或损坏返回 null。 */
    fun installedVersion(): String? {
        val marker = markerFile(ContainerPaths.rootfsDir())
        if (!marker.isFile) return null
        val lines = runCatching { marker.readLines() }.getOrNull().orEmpty()
        return lines.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    /** 已装容器是否与当前 APK 内置镜像完全一致。 */
    fun isUpToDate(): Boolean {
        if (!isInstalled()) return false
        val marker = markerFile(ContainerPaths.rootfsDir())
        val lines = runCatching { marker.readLines() }.getOrNull().orEmpty()
        if (lines.size < 2) return false
        return lines[0] == BOOTSTRAP_VERSION && lines[1].equals(BOOTSTRAP_SHA256, ignoreCase = true)
    }

    fun bundledVersion(): String = BOOTSTRAP_VERSION

    fun bootstrapIdentity(): String = "$BOOTSTRAP_VERSION ($BOOTSTRAP_SIZE bytes)"

    /** 幂等修复：清理半成品目录、补齐 proot 所需配置与共享存储软链。 */
    @Throws(Exception::class)
    fun repairIfInstalled() {
        val filesDir = context.filesDir
        FileTree.deleteRecursive(File(filesDir, "debian-staging"))
        FileTree.deleteRecursive(File(filesDir, "debian-backup"))
        if (!isInstalled()) return

        val rootfs = ContainerPaths.rootfsDir()
        ensureContainerEssentials(rootfs)
        setupStorageLinks()
    }

    /** 安装或升级内置 Debian 容器；升级时保留原有 `/root` 工作目录。 */
    @Throws(Exception::class)
    fun install(progress: Progress? = null) {
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
        val staging = File(filesDir, "debian-staging")
        val backup = File(filesDir, "debian-backup")
        FileTree.deleteRecursive(staging)
        FileTree.deleteRecursive(backup)
        if (!staging.mkdirs() && !staging.isDirectory) {
            throw IOException("无法创建临时解压目录：$staging")
        }

        try {
            report(progress, "正在校验内置 Debian 容器镜像…", 2)
            verifyBundledArchive()

            report(progress, "正在解压 Debian + Node.js + Pi 环境…", 8)
            context.assets.open(BOOTSTRAP_ASSET).use { input ->
                TarGzExtractor.extract(input, staging) { readBytes ->
                    val pct = 8 + ((readBytes.coerceAtMost(BOOTSTRAP_SIZE) * 76) / BOOTSTRAP_SIZE).toInt()
                    report(progress, "正在解压 Debian + Node.js + Pi 环境…", pct.coerceIn(8, 84))
                }
            }

            report(progress, "正在配置容器权限与运行环境…", 88)
            ensureContainerEssentials(staging)

            // 迁移旧版 Termux home/.zhicode（若用户此前存在会话或技能数据）。
            migrateLegacyZhiCodeData(filesDir, File(staging, "root"))

            report(progress, "正在切换容器目录…", 94)
            activateRootfs(staging, rootfs, backup)

            report(progress, "正在挂载共享存储链接…", 97)
            setupStorageLinks()
            ProotCommand.ensureProotTmpDir()

            writeText(
                markerFile(rootfs),
                "$BOOTSTRAP_VERSION\n$BOOTSTRAP_SHA256\n$BOOTSTRAP_SOURCE\n",
            )
            report(progress, "内置 Debian 运行环境已就绪", 100)
        } catch (t: Throwable) {
            FileTree.deleteRecursive(staging)
            throw t
        }
    }

    @Throws(Exception::class)
    private fun verifyBundledArchive() {
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
            throw IOException("内置 Debian 归档长度异常：$total（期望 $BOOTSTRAP_SIZE）")
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (!sha.equals(BOOTSTRAP_SHA256, ignoreCase = true)) {
            throw IOException("内置 Debian 归档校验失败（sha256=$sha）")
        }
    }

    private fun ensureContainerEssentials(rootfs: File) {
        val rootHome = File(rootfs, "root")
        val tmpDir = File(rootfs, "tmp")
        val varTmp = File(rootfs, "var/tmp")
        val aptConf = File(rootfs, "etc/apt/apt.conf.d")
        val listsPartial = File(rootfs, "var/lib/apt/lists/partial")
        val archivesPartial = File(rootfs, "var/cache/apt/archives/partial")
        listOf(rootHome, tmpDir, varTmp, aptConf, listsPartial, archivesPartial).forEach { dir ->
            if (!dir.isDirectory) dir.mkdirs()
        }

        // 保证容器目录树和 /tmp 权限正确，避免后续执行与 apt 写锁受阻。
        runCatching { Os.chmod(rootfs.absolutePath, 0b111101101) }
        runCatching { Os.chmod(rootHome.absolutePath, 0b111101101) }
        runCatching { Os.chmod(tmpDir.absolutePath, 0b111111111) }
        runCatching { Os.chmod(varTmp.absolutePath, 0b111111111) }

        val resolv = File(rootfs, "etc/resolv.conf")
        if (!resolv.isFile || resolv.length() == 0L) {
            writeText(resolv, "nameserver 223.5.5.5\nnameserver 8.8.8.8\n")
        }
        val hosts = File(rootfs, "etc/hosts")
        if (!hosts.isFile || hosts.length() == 0L) {
            writeText(hosts, "127.0.0.1 localhost\n::1 localhost\n")
        }
        writeText(File(aptConf, "01-proot"), "APT::Sandbox::User \"root\";\n")
        ProotCommand.ensureProotTmpDir()
    }

    private fun migrateLegacyZhiCodeData(filesDir: File, stagingRootHome: File) {
        val legacyMeta = File(filesDir, "home/${TermuxConstants.DATA_DIR_NAME}")
        val targetMeta = File(stagingRootHome, TermuxConstants.DATA_DIR_NAME)
        if (legacyMeta.isDirectory && !targetMeta.exists()) {
            legacyMeta.parentFile?.let { _ ->
                targetMeta.parentFile?.mkdirs()
                legacyMeta.renameTo(targetMeta)
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
                throw IOException("Cannot preserve current Debian rootfs: $rootfs")
            }
        }

        // 升级容器系统目录时保留用户原有的 /root 工作区与会话。
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
                throw IOException("Cannot restore previous Debian rootfs: $rootfs")
            }
            throw IOException("Cannot activate Debian rootfs: $rootfs")
        }
        if (hadPrevious) FileTree.deleteRecursive(backup)
    }

    fun setupStorageLinks() {
        val home = ContainerPaths.homeDir()
        if (!home.isDirectory && !home.mkdirs() && !home.isDirectory) return
        val external = File(StorageLinks.EXTERNAL_ROOT)
        if (!external.isDirectory) return
        runCatching { StorageLinks.setup(home, external) }
    }

    private fun markerFile(rootfs: File): File = File(rootfs, MARKER_RELATIVE_PATH)

    private fun writeText(target: File, text: String) {
        target.parentFile?.mkdirs()
        target.writeText(text)
        runCatching { Os.chmod(target.absolutePath, 0b110100100) }
    }

    private fun report(progress: Progress?, message: String, percent: Int) {
        runCatching { progress?.onProgress(message, percent) }
    }
}
