package com.iqge

import android.content.Context
import android.os.Build
import android.system.Os
import com.termux.app.iqcode.termux.TermuxShellExecutor
import com.termux.shared.termux.TermuxConstants
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * 内置 Termux 运行环境的安装器。
 *
 * <p>本类替代原版 `iqcode-termux-compat.jar` 里的同名类。原版那个是刻意的 binary-only 组件，
 * 且把 `com.iqge` 写死在字节码里（`OLD`/`NEW` 是 `static final byte[]`），无法用于独立包名，
 * 所以这里按它的可观测行为 1:1 重写并参数化。行为规格来自对原字节码的反汇编：
 *
 * - 常量：`bootstrap-aarch64.zip`、32,176,084 字节、sha256 `82aae307…0216`；
 * - 流程：校验 ABI → 解包到 `usr-staging` → 改写前缀 → 原子换入 `usr` → 写 apt/dpkg 兼容层 → 写 marker；
 * - 进度：35（已解包 userspace）→ 82（开始配置兼容层）→ 100（就绪）；
 * - `SYMLINKS.txt` 用 `←`(U+2190) 分隔，左侧是绝对目标、右侧是相对前缀的链接位置。
 *
 * ## 为什么前缀改写必须等长
 *
 * bootstrap 里有 **337 个 ELF 二进制**把 `/data/data/com.termux` 编进了字符串表。
 * ELF 的所有内部偏移都是基于原始字节布局的，替换串只要长度变一位，后面全部错位，二进制即损坏。
 * 因此这里沿用原版做法：**只在长度严格相等时做原地字节改写**，否则直接抛错而不是静默产出坏文件。
 * 这也是 `app/build.gradle` 里 applicationId 必须恰好 8 个字符的原因
 * （`/data/user/0/` 13 字符 + 8 = 21 = `/data/data/com.termux`）。
 */
class RuntimeInstaller(private val context: Context) {

    /** 安装进度回调。百分比 0~100，消息已本地化。 */
    fun interface Progress {
        fun onProgress(message: String, percent: Int)
    }

    companion object {
        const val BOOTSTRAP_VERSION = "bootstrap-2026.07.26-r1+apt.android-7"
        const val BOOTSTRAP_ASSET = "bootstrap-aarch64.zip"
        const val BOOTSTRAP_SIZE = 32_176_084L
        const val BOOTSTRAP_SHA256 = "82aae307c462bc911b02588228714438122e4f7f4492c78d8ab9914e78d10216"
        const val BOOTSTRAP_SOURCE =
            "https://github.com/termux/termux-packages/releases/tag/bootstrap-2026.07.26-r1%2Bapt.android-7"

        private const val OFFICIAL_MAIN_REPOSITORY = "https://packages.termux.dev/apt/termux-main"
        private const val OFFICIAL_ROOT_REPOSITORY = "https://packages.termux.dev/apt/termux-root"
        private const val OFFICIAL_X11_REPOSITORY = "https://packages.termux.dev/apt/termux-x11"

        /** bootstrap 里被编译进 ELF 的旧前缀，必须与目标等长。 */
        private const val OLD_PREFIX = "/data/data/com.termux"

        /** `SYMLINKS.txt` 的分隔符：U+2190 LEFTWARDS ARROW。 */
        private const val SYMLINK_ARROW = '\u2190'

        private const val SYMLINKS_ENTRY = "SYMLINKS.txt"

        /** 脚本模板放在 assets 的这个目录下。 */
        private const val TEMPLATE_DIR = "iqge"

        /** 可执行位判定用的文件头。 */
        private val ELF_MAGIC = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())
        private val SHEBANG_MAGIC = byteArrayOf('#'.code.toByte(), '!'.code.toByte())

        private const val MODE_EXECUTABLE = true
        private const val MODE_PLAIN = false
    }

    // ------------------------------------------------------------------ 路径

    private fun filesDir() = File(TermuxConstants.TERMUX_FILES_DIR_PATH)
    private fun prefixDir() = File(TermuxConstants.TERMUX_PREFIX_DIR_PATH)
    private fun stagingDir() = File(filesDir(), "usr-staging")
    private fun backupDir() = File(filesDir(), "usr-backup")
    private fun tempBootstrap() = File(filesDir(), ".bootstrap-verify.zip")
    private fun markerFile() = File(prefixDir(), "etc/iqge-runtime-v1")
    private fun bashFile() = File(TermuxConstants.TERMUX_BASH_PATH)

    /** 目标前缀（`/data/user/0/<pkg>`），必须与 [OLD_PREFIX] 等长。 */
    private fun newPrefix() = TermuxConstants.TERMUX_DATA_DIR_PATH

    // ------------------------------------------------------------ 公开接口

    /** 环境是否已安装就绪：marker 与 shell 都在。 */
    fun isInstalled(): Boolean = markerFile().isFile && bashFile().isFile

    /**
     * 幂等修复已安装的环境：清掉半成品目录、补齐 apt/dpkg 兼容层、清理孤儿包管理器。
     * 适用于 `apt` 升级过、或上次安装被中断的情况。
     */
    fun repairIfInstalled() {
        if (!isInstalled()) return
        deleteRecursive(stagingDir())
        deleteRecursive(backupDir())
        val prefix = prefixDir()
        File(prefix, "tmp").mkdirs()
        File(prefix, "var/cache/apt/archives/partial").mkdirs()
        File(prefix, "var/lib/apt/lists/partial").mkdirs()
        installAptCompatibility(prefix)
        installDpkgWrapper(prefix)
        TermuxShellExecutor.cleanupOrphanedPackageManagers()
    }

    /**
     * 首次安装内置 Termux 环境。
     *
     * @throws Exception 校验失败、解包失败或前缀无法换入时
     */
    @Throws(Exception::class)
    fun install(progress: Progress?) {
        ensureArm64()
        ensureSafePrefixRewrite()

        val files = filesDir()
        val home = File(TermuxConstants.TERMUX_HOME_DIR_PATH)
        val prefix = prefixDir()
        val staging = stagingDir()
        val backup = backupDir()

        if (!files.isDirectory && !files.mkdirs()) {
            throw IOException("Cannot create bootstrap directory: $files")
        }
        if (!home.isDirectory && !home.mkdirs()) {
            throw IOException("Cannot create HOME: $home")
        }
        deleteRecursive(staging)
        deleteRecursive(backup)
        if (!staging.mkdirs()) {
            throw IOException("Cannot create staging prefix: $staging")
        }

        report(progress, "正在读取内置 Termux 基础环境…", 1)
        val verified = verifyBundledBootstrap()

        try {
            report(progress, "正在查询内置 Termux 基础环境…", 5)
            extractBootstrap(verified, staging)
        } finally {
            verified.delete()
        }

        report(progress, "正在安装内置 Termux 原生环境…", 35)

        if (!staging.isDirectory) {
            throw IOException("Cannot create staging prefix: $staging")
        }
        activatePrefix(staging, prefix, backup)

        report(progress, "正在配置 ${TermuxConstants.TERMUX_PACKAGE_NAME} 软件包兼容层…", 82)
        File(prefix, "tmp").mkdirs()
        installAptCompatibility(prefix)
        installDpkgWrapper(prefix)
        installOfficialRepositories(prefix)

        // marker：内容即版本三元组，便于升级后识别需要重装
        val marker = markerFile()
        marker.parentFile?.mkdirs()
        writeText(
            marker,
            "$BOOTSTRAP_VERSION\n$BOOTSTRAP_SHA256\n$BOOTSTRAP_SOURCE\n",
            MODE_PLAIN,
        )

        File(home, "tmp").mkdirs()
        File(home, "projects").mkdirs()

        deleteRecursive(staging)
        deleteRecursive(backup)

        report(progress, "Termux 已就绪", 100)
    }

    // -------------------------------------------------------------- 前置校验

    /** 内置用户空间只有 aarch64 一份。 */
    private fun ensureArm64() {
        val abis = Build.SUPPORTED_ABIS ?: emptyArray()
        val ok = abis.any { it.equals("arm64-v8a", true) || it.equals("aarch64", true) }
        if (!ok) {
            throw UnsupportedOperationException(
                "This build currently supports ARM64 only. Device ABI: " + abis.joinToString(", ")
            )
        }
    }

    /**
     * 前缀改写必须等长——见类文档。这里显式拦下，避免静默产出损坏的 ELF。
     */
    private fun ensureSafePrefixRewrite() {
        val old = OLD_PREFIX.toByteArray(Charsets.UTF_8)
        val new = newPrefix().toByteArray(Charsets.UTF_8)
        if (old.size != new.size) {
            throw IllegalStateException(
                "prefix rewrite is not equal-length: '${OLD_PREFIX}' (${old.size}) -> " +
                    "'${newPrefix()}' (${new.size}). " +
                    "applicationId 必须恰好 8 个字符，见 app/build.gradle 中的说明。"
            )
        }
    }

    // ---------------------------------------------------------------- 解包

    /**
     * 把 assets 里的 bootstrap 复制到私有目录并校验大小与摘要，返回校验过的临时 zip。
     *
     * 先落盘再解包的原因：摘要要读完整 32MB，而 [ZipInputStream] 只能顺序消费一次。
     */
    @Throws(Exception::class)
    private fun verifyBundledBootstrap(): File {
        val out = tempBootstrap()
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        context.assets.open(BOOTSTRAP_ASSET).use { input ->
            FileOutputStream(out).use { sink ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    sink.write(buffer, 0, read)
                    total += read
                }
            }
        }
        if (total != BOOTSTRAP_SIZE) {
            out.delete()
            throw IOException("读取内置 Termux 基础环境失败，长度异常（读取到的值：$total，应为 $BOOTSTRAP_SIZE）")
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (!sha.equals(BOOTSTRAP_SHA256, ignoreCase = true)) {
            out.delete()
            throw IOException("内置 Termux 基础环境校验失败，请重新安装本应用")
        }
        return out
    }

    /**
     * 解包到 [staging]，同时：
     * 1. 对每个文件的字节做**等长**前缀原地改写；
     * 2. 按内容推断可执行位（实测 bootstrap 里 424 个可执行文件全是 ELF 或 `#!` 脚本，零例外）；
     * 3. 收集 [SYMLINKS_ENTRY] 并在全部文件落地后创建符号链接。
     */
    @Throws(Exception::class)
    private fun extractBootstrap(zip: File, staging: File) {
        val oldBytes = OLD_PREFIX.toByteArray(Charsets.UTF_8)
        val newBytes = newPrefix().toByteArray(Charsets.UTF_8)
        val stagingRoot = staging.canonicalPath
        val symlinkLines = ArrayList<String>()

        ZipInputStream(FileInputStream(zip)).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory) {
                    if (name.endsWith(SYMLINKS_ENTRY)) {
                        val text = String(readAll(zin), Charsets.UTF_8)
                        symlinkLines += text.split(Regex("\\r?\\n"))
                    } else {
                        val target = safeBootstrapPath(stagingRoot, name)
                        val data = readAll(zin)
                        replaceAllInPlace(data, oldBytes, newBytes)
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { it.write(data) }
                        target.setExecutable(isExecutableContent(data), false)
                    }
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }

        for (raw in symlinkLines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val arrow = line.indexOf(SYMLINK_ARROW)
            if (arrow <= 0 || arrow >= line.length - 1) {
                throw IOException("Malformed symlink line: $line")
            }
            val linkTarget = line.substring(0, arrow).trim()
            val linkName = line.substring(arrow + 1).trim()
            if (linkTarget.isEmpty() || linkName.isEmpty()) {
                throw IOException("Malformed symlink line: $line")
            }
            val linkPath = safeBootstrapPath(stagingRoot, linkName)
            linkPath.parentFile?.mkdirs()
            kotlin.runCatching { linkPath.delete() }
            Os.symlink(linkTarget, linkPath.absolutePath)
        }
    }

    /** zip-slip 防护：解析后的路径必须落在 [rootCanonical] 之内。 */
    @Throws(Exception::class)
    private fun safeBootstrapPath(rootCanonical: String, entryName: String): File {
        val candidate = File(rootCanonical, entryName).canonicalFile
        val root = File(rootCanonical)
        if (candidate != root && !candidate.path.startsWith(rootCanonical + File.separator)) {
            throw IOException("Unsafe bootstrap entry: $entryName")
        }
        return candidate
    }

    /**
     * 等长原地替换。长度不等说明调用方漏了 [ensureSafePrefixRewrite]，这里直接拒绝。
     *
     * @return 替换次数
     */
    private fun replaceAllInPlace(data: ByteArray, old: ByteArray, replacement: ByteArray): Int {
        if (old.isEmpty()) return 0
        if (old.size != replacement.size) {
            throw IllegalStateException("prefix rewrite is not equal-length")
        }
        var hits = 0
        var i = 0
        val limit = data.size - old.size
        while (i <= limit) {
            var match = true
            var j = 0
            while (j < old.size) {
                if (data[i + j] != old[j]) { match = false; break }
                j++
            }
            if (match) {
                System.arraycopy(replacement, 0, data, i, replacement.size)
                hits++
                i += replacement.size
            } else {
                i++
            }
        }
        return hits
    }

    private fun isExecutableContent(data: ByteArray): Boolean {
        if (data.size >= ELF_MAGIC.size) {
            var elf = true
            for (k in ELF_MAGIC.indices) if (data[k] != ELF_MAGIC[k]) { elf = false; break }
            if (elf) return true
        }
        if (data.size >= SHEBANG_MAGIC.size) {
            var sh = true
            for (k in SHEBANG_MAGIC.indices) if (data[k] != SHEBANG_MAGIC[k]) { sh = false; break }
            if (sh) return true
        }
        return false
    }

    // ------------------------------------------------------------ 前缀换入

    /**
     * 原子换入：先把现有 `usr` 挪到 `usr-backup`，再把 `usr-staging` 改名成 `usr`。
     * 任何一步失败都尝试把 backup 还原回去，绝不留下半个前缀。
     */
    @Throws(Exception::class)
    private fun activatePrefix(staging: File, prefix: File, backup: File) {
        val hadPrevious = prefix.exists()
        if (hadPrevious) {
            deleteRecursive(backup)
            if (!prefix.renameTo(backup)) {
                throw IOException("Cannot preserve current Termux prefix: $prefix")
            }
        }
        if (!staging.renameTo(prefix)) {
            if (hadPrevious && backup.exists() && !backup.renameTo(prefix)) {
                throw IOException("Cannot recover previous Termux prefix: $prefix")
            }
            throw IOException("Cannot activate or restore Termux prefix: $prefix")
        }
        if (hadPrevious) deleteRecursive(backup)
    }

    // ------------------------------------------------- apt / dpkg 兼容层

    /**
     * 让 `apt` / `dpkg` 能安装 Termux 官方仓库的包：包里的路径是 `/data/data/com.termux`，
     * 装完不能用，所以注册一个预安装钩子，在真正安装前把 `.deb` 里的路径改写成本应用前缀。
     */
    @Throws(Exception::class)
    private fun installAptCompatibility(prefix: File) {
        File(prefix, "tmp").mkdirs()
        File(prefix, "var/cache/apt/archives/partial").mkdirs()
        File(prefix, "var/lib/apt/lists/partial").mkdirs()
        File(prefix, "libexec/iqge").mkdirs()
        File(prefix, "etc/apt/apt.conf.d").mkdirs()

        writeTemplate("iq-deb-patch.sh", File(prefix, "libexec/iqge/iq-deb-patch"), MODE_EXECUTABLE)
        writeTemplate("apt-pre-install.sh", File(prefix, "libexec/iqge/apt-pre-install"), MODE_EXECUTABLE)
        writeTemplate("iq-patch-deb.sh", File(prefix, "bin/iq-patch-deb"), MODE_EXECUTABLE)
        writeTemplate(
            "99iqge-prefix-rewrite.conf",
            File(prefix, "etc/apt/apt.conf.d/99iqge-prefix-rewrite"),
            MODE_PLAIN,
        )
    }

    /**
     * 把真正的 `dpkg` 挪到 `dpkg.iqge-real`，原位置换成包装器：
     * 包装器会在调用真实 dpkg 之前，先把命令行里出现的 `.deb` 全部改写一遍。
     * 这样即使用户绕过 apt 直接 `dpkg -i` 也不会装进一个路径错误的前缀。
     */
    @Throws(Exception::class)
    private fun installDpkgWrapper(prefix: File) {
        val real = File(prefix, "bin/dpkg.iqge-real")
        val launcher = File(prefix, "bin/dpkg")
        if (launcher.isFile && !real.exists()) {
            if (!launcher.renameTo(real)) {
                throw IOException("Cannot preserve real dpkg as $real")
            }
        }
        writeTemplate("dpkg-wrapper.sh", launcher, MODE_EXECUTABLE)
    }

    /**
     * 把 apt 源固定到 Termux 官方仓库，并压制镜像自动切换与冲突的旧条目。
     * 否则 `pkg` 会按网络状况挑选镜像，而镜像包里的路径前缀我们未必能正确改写。
     */
    @Throws(Exception::class)
    private fun installOfficialRepositories(prefix: File) {
        File(prefix, "etc/profile.d").mkdirs()
        File(prefix, "etc/apt/sources.list.d").mkdirs()

        writeTemplate(
            "99-iqge-official-repository.sh",
            File(prefix, "etc/profile.d/99-iqge-official-repository.sh"),
            MODE_EXECUTABLE,
        )

        val mainLine = "deb $OFFICIAL_MAIN_REPOSITORY stable main\n"
        writeText(File(prefix, "etc/apt/sources.list"), mainLine, MODE_PLAIN)
        writeText(File(prefix, "etc/apt/sources.list.d/iqge-main.list"), mainLine, MODE_PLAIN)

        disableLegacyMainEntry(prefix)
        normalizeDeb822Source(prefix)
    }

    /** 把 sources.list.d 里其它指向 termux-main 的旧条目全部禁用。 */
    private fun disableLegacyMainEntry(prefix: File) {
        val dir = File(prefix, "etc/apt/sources.list.d")
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (!file.isFile) continue
            if (file.name == "iqge-main.list") continue
            if (!file.name.endsWith(".list") && !file.name.endsWith(".sources")) continue
            val text = runCatching { file.readText() }.getOrNull() ?: continue
            if (!text.contains("termux-main")) continue
            val rewritten = StringBuilder()
            for (line in text.split("\n")) {
                if (line.trimStart().startsWith("#")) {
                    rewritten.append(line).append('\n')
                } else {
                    rewritten.append("# IQGE fixed official main:").append(line).append('\n')
                }
            }
            rewritten.append("Enabled: no\n")
            runCatching { file.writeText(rewritten.toString()) }
        }
    }

    /** deb822 格式（`*.sources`）里把仓库地址统一成官方地址，并关掉 termux-main 条目。 */
    private fun normalizeDeb822Source(prefix: File) {
        val dir = File(prefix, "etc/apt/sources.list.d")
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".sources") } ?: return
        val patterns = listOf(
            Regex("https?://[^\\s]+termux-main/?") to OFFICIAL_MAIN_REPOSITORY,
            Regex("https?://[^\\s]+termux-root/?") to OFFICIAL_ROOT_REPOSITORY,
            Regex("https?://[^\\s]+termux-x11/?") to OFFICIAL_X11_REPOSITORY,
        )
        for (file in files) {
            var text = runCatching { file.readText() }.getOrNull() ?: continue
            var touched = false
            for ((pattern, replacement) in patterns) {
                if (pattern.containsMatchIn(text)) {
                    text = pattern.replace(text, replacement)
                    touched = true
                }
            }
            if (!text.contains("termux-main")) {
                if (touched) runCatching { file.writeText(text) }
                continue
            }
            text = text.replace(Regex("(?m)^[ \\t]*Enabled:.*$"), "Enabled: no")
            if (!Regex("(?m)^[ \\t]*Enabled:").containsMatchIn(text)) {
                text = text.trimEnd('\n') + "\nEnabled: no\n"
            }
            runCatching { file.writeText(text) }
        }
    }

    // -------------------------------------------------------------- 工具

    /** 读 assets 模板、替换占位符、落到 [target] 并设置权限位。 */
    @Throws(Exception::class)
    private fun writeTemplate(asset: String, target: File, executable: Boolean) {
        val raw = context.assets.open("$TEMPLATE_DIR/$asset").use { String(readAll(it), Charsets.UTF_8) }
        val dataDir = TermuxConstants.TERMUX_DATA_DIR_PATH
        val text = raw
            .replace("@PREFIX@", TermuxConstants.TERMUX_PREFIX_DIR_PATH)
            .replace("@OLD@", OLD_PREFIX)
            .replace("@NEW@", dataDir)
            .replace("@OLDDIRFRAG@", OLD_PREFIX.trimStart('/'))
            .replace("@NEWDIRFRAG@", dataDir.trimStart('/'))
            .replace("@NEWPARENTFRAG@", File(dataDir).parent ?: dataDir)
        writeText(target, text, executable)
    }

    private fun writeText(target: File, text: String, executable: Boolean) {
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        target.setReadable(true, false)
        target.setWritable(true, true)
        target.setExecutable(executable, false)
    }

    private fun readAll(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream(64 * 1024)
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun deleteRecursive(file: File?) {
        if (file == null || !file.exists()) return
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) for (child in children) deleteRecursive(child)
        }
        kotlin.runCatching { file.delete() }
    }

    private fun report(progress: Progress?, message: String, percent: Int) {
        kotlin.runCatching { progress?.onProgress(message, percent) }
    }

    /** 供诊断页展示：内置 bootstrap 的版本三元组。 */
    fun bootstrapIdentity(): String = "$BOOTSTRAP_VERSION ($BOOTSTRAP_SIZE bytes)"
}
