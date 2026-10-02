package com.zhizhu.zhicode.compose.runtime

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import com.termux.app.zhicode.core.StorageLinks
import com.termux.shared.termux.TermuxConstants
import java.io.File

/**
 * 环境自检：把定位问题需要的全部运行期事实汇总成一段可复制的纯文本。
 *
 * 存在的理由很实际：真机往返验证很贵。与其在真机上让用户逐项描述"哪里不对"，
 * 不如让 App 自己把事实全部列出来，用户复制一次就够定位。
 *
 * 报告里的每一项都必须是**实测值**，不能是推断或常量：
 * 已经装了却没装、路径不对、native 库加载失败、权限没给 —— 都要如实呈现。
 */
object EnvDoctor {

    private const val ARCHIVE_MIN_ENTRIES = 3000

    fun report(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("# ZhiCode · 环境自检")
        sb.appendLine()

        buildInfo(context, sb)
        pathInfo(sb)
        termuxInfo(sb)
        nativeInfo(context, sb)
        storageInfo(context, sb)
        permissionInfo(context, sb)
        sb.appendLine()
        sb.appendLine("— 报告结束，可整段复制")
        return sb.toString()
    }

    // ------------------------------------------------------------------ 构建

    private fun buildInfo(context: Context, sb: StringBuilder) {
        val pm = context.packageManager
        val pkg = context.packageName
        val info = runCatching {
            pm.getPackageInfo(pkg, 0)
        }.getOrNull()

        sb.appendLine("## 构建")
        sb.appendLine("包名        : $pkg")
        sb.appendLine("Application : ${context.applicationInfo?.className ?: "-"}")
        sb.appendLine("版本        : ${info?.versionName ?: "?"} (${info?.longVersionCode ?: 0})")
        sb.appendLine("数据目录    : ${context.applicationInfo?.dataDir ?: "-"}")
        sb.appendLine("native 目录 : ${context.applicationInfo?.nativeLibraryDir ?: "-"}")
        sb.appendLine("APK 路径    : ${context.applicationInfo?.sourceDir ?: "-"}")
        sb.appendLine("设备 ABI    : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        sb.appendLine("API 级别    : ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        sb.appendLine("机型        : ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("运行形态    : ${runtimeShape(context)}")
        sb.appendLine()
    }

    /**
     * 本应用是跑在 ZhiCode 沙箱（BlackBox）里，还是直接跑在真机上。
     *
     * 这一条直接决定内置 Termux 能不能装：沙箱会把数据目录虚拟化成
     * `/data/user/0/<宿主包名>/blackbox/data/user/0/<pkg>`，前缀长度对不上，
     * bootstrap 里 337 个 ELF 就不能安全改写。所以必须在真机上装。
     */
    private fun runtimeShape(context: Context): String {
        val dataDir = context.applicationInfo?.dataDir.orEmpty()
        return if (dataDir.contains("blackbox")) {
            "ZhiCode 沙箱内（数据目录被虚拟化，内置 Termux 无法在此安装，请在真机上验证）"
        } else {
            "真机/独立运行"
        }
    }

    // ------------------------------------------------------------------ 路径

    private fun pathInfo(sb: StringBuilder) {
        sb.appendLine("## Termux 路径")
        sb.appendLine("configured  : ${TermuxConstants.isConfigured()}")
        sb.appendLine("PACKAGE_NAME: ${TermuxConstants.TERMUX_PACKAGE_NAME}")
        sb.appendLine("DATA_DIR    : ${TermuxConstants.TERMUX_DATA_DIR_PATH}")
        sb.appendLine("FILES_DIR   : ${TermuxConstants.TERMUX_FILES_DIR_PATH}")
        sb.appendLine("HOME        : ${TermuxConstants.TERMUX_HOME_DIR_PATH}")
        sb.appendLine("PREFIX      : ${TermuxConstants.TERMUX_PREFIX_DIR_PATH}")

        // 前缀改写必须等长，否则 bootstrap 里的 337 个 ELF 会被写坏。
        // 这里把它当成一条一等公民的自检项，而不是等安装时才炸。
        // 注意：沙箱里必然不等长（数据目录被虚拟化），这是预期结果而非缺陷。
        val oldLen = "/data/data/com.termux".length
        val newLen = TermuxConstants.TERMUX_DATA_DIR_PATH.length
        val expectedLen = oldLen - "/data/user/0/".length
        val lenOk = oldLen == newLen
        sb.appendLine("前缀等长    : ${if (lenOk) "通过" else "不通过"} ($oldLen -> $newLen)")
        if (!lenOk) {
            sb.appendLine("  期望      : 数据目录恰好 $oldLen 字符，即包名恰好 $expectedLen 字符")
            sb.appendLine("  实际包名  : ${TermuxConstants.TERMUX_PACKAGE_NAME} (${TermuxConstants.TERMUX_PACKAGE_NAME.length} 字符)")
            sb.appendLine("  原因      : 数据目录被前缀改写之外的东西改变了长度（沙箱虚拟化最常见）")
        }
        sb.appendLine()
    }

    // ---------------------------------------------------------------- Termux

    private fun termuxInfo(sb: StringBuilder) {
        val prefix = File(TermuxConstants.TERMUX_PREFIX_DIR_PATH)
        val home = File(TermuxConstants.TERMUX_HOME_DIR_PATH)
        val bash = File(TermuxConstants.TERMUX_BASH_PATH)
        val marker = File(prefix, "etc/${TermuxConstants.BRAND_SLUG}-runtime-v1")

        sb.appendLine("## 内置 Termux 环境")
        sb.appendLine("PREFIX 存在 : ${prefix.isDirectory}  (${countChildren(prefix)} 项)")
        sb.appendLine("HOME 存在   : ${home.isDirectory}")
        sb.appendLine("bash 存在   : ${bash.isFile}  可执行=${bash.canExecute()}")
        sb.appendLine("bin 条目数  : ${countChildren(File(prefix, "bin"))}")
        sb.appendLine("usr-staging : ${File(TermuxConstants.TERMUX_FILES_DIR_PATH, "usr-staging").exists()}")
        sb.appendLine("usr-backup  : ${File(TermuxConstants.TERMUX_FILES_DIR_PATH, "usr-backup").exists()}")
        sb.appendLine("marker      : ${marker.isFile}")
        if (marker.isFile) {
            val lines = runCatching { marker.readLines() }.getOrNull().orEmpty()
            sb.appendLine("  版本      : ${lines.getOrNull(0) ?: "?"}")
            sb.appendLine("  sha256    : ${lines.getOrNull(1) ?: "?"}")
        }

        // 兼容层：装了这些，apt 才能安装 Termux 官方仓库的包
        sb.appendLine("dpkg-wrapper: ${File(prefix, "bin/dpkg").isFile}")
        sb.appendLine("dpkg-real   : ${File(prefix, "bin/dpkg.${TermuxConstants.BRAND_SLUG}-real").isFile}")
        sb.appendLine("deb-patch   : ${File(prefix, "libexec/${TermuxConstants.BRAND_SLUG}/${TermuxConstants.BRAND_SLUG}-deb-patch").isFile}")
        sb.appendLine("apt 钩子    : ${File(prefix, "etc/apt/apt.conf.d/99${TermuxConstants.BRAND_SLUG}-prefix-rewrite").isFile}")
        sb.appendLine()
    }

    // -------------------------------------------------- 内置 bootstrap 资产

    private fun nativeInfo(context: Context, sb: StringBuilder) {
        sb.appendLine("## 内置资产与 native")

        val listed = runCatching {
            context.assets.list("")?.toList().orEmpty()
        }.getOrElse { emptyList() }
        sb.appendLine("assets 根   : ${listed.joinToString(", ").take(200)}")

        val size = runCatching {
            context.assets.open("bootstrap-aarch64.zip").use { it.available().toLong() }
        }.getOrNull()
        sb.appendLine("bootstrap   : ${if (size != null) "$size 字节" else "读取失败"}")

        val libDir = context.applicationInfo?.nativeLibraryDir
        val lib = libDir?.let { File(it, "libtermux.so") }
        sb.appendLine("libtermux.so: ${lib?.let { if (it.isFile) "${it.length()} 字节" else "缺失" } ?: "-"}")
        if (lib != null && lib.isFile) {
            val loaded = runCatching {
                System.load(lib.absolutePath)
                true
            }.getOrElse { false }
            sb.appendLine("System.load : ${if (loaded) "成功" else "失败"}")
            if (!loaded) {
                sb.appendLine("  提示      : 该 .so 必须按绝对路径加载；若 APK 用了 extractNativeLibs=false 会失败")
            }
        }
        sb.appendLine()
    }

    // ---------------------------------------------------------------- 存储

    private fun storageInfo(context: Context, sb: StringBuilder) {
        val home = File(TermuxConstants.TERMUX_HOME_DIR_PATH)
        val storage = File(home, "storage")
        sb.appendLine("## 存储访问")
        sb.appendLine("~/storage    : ${if (storage.isDirectory) "存在" else "未创建"}")
        // 逐条实测（链接在不在 / 指向哪 / 读不读得到）。三件事分别可能出问题，
        // 合成一个 OK/FAIL 就查不出是哪一种 —— 报告的价值全在"如实呈现"。
        for (line in StorageLinks.describe(home)) sb.appendLine(line)
        sb.appendLine("共享存储根   : ${StorageLinks.EXTERNAL_ROOT}  "
            + "存在=${File(StorageLinks.EXTERNAL_ROOT).isDirectory}  "
            + "可读=${runCatching { File(StorageLinks.EXTERNAL_ROOT).canRead() }.getOrDefault(false)}  "
            + "可写=${runCatching { File(StorageLinks.EXTERNAL_ROOT).canWrite() }.getOrDefault(false)}")
        sb.appendLine("外部存储状态 : ${Environment.getExternalStorageState()}")
        sb.appendLine("sdcard 可读  : ${runCatching { File("/sdcard").canRead() }.getOrDefault(false)}")
        sb.appendLine()
    }

    // ---------------------------------------------------------------- 权限

    private fun permissionInfo(context: Context, sb: StringBuilder) {
        val pm = context.packageManager
        sb.appendLine("## 权限")

        fun granted(permission: String): String {
            val ok = runCatching {
                pm.checkPermission(permission, context.packageName) == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            return if (ok) "已授予" else "未授予"
        }

        sb.appendLine("INTERNET              : ${granted("android.permission.INTERNET")}  (安装即授予)")
        sb.appendLine("READ_EXTERNAL_STORAGE : ${granted("android.permission.READ_EXTERNAL_STORAGE")}")
        sb.appendLine("WRITE_EXTERNAL_STORAGE: ${granted("android.permission.WRITE_EXTERNAL_STORAGE")}")
        sb.appendLine("MANAGE_EXTERNAL_STORAGE: ${allFilesAccess(context)}")
        sb.appendLine("POST_NOTIFICATIONS    : ${granted("android.permission.POST_NOTIFICATIONS")}")
        sb.appendLine("FOREGROUND_SERVICE    : ${granted("android.permission.FOREGROUND_SERVICE")}  (普通权限)")
        sb.appendLine("WAKE_LOCK             : ${granted("android.permission.WAKE_LOCK")}  (普通权限)")
        sb.appendLine("SYSTEM_ALERT_WINDOW   : ${if (canDrawOverlays(context)) "已授予" else "未授予"}  (悬浮窗)")

        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val ignoring = power?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        sb.appendLine("电池优化白名单        : ${if (ignoring) "已加入" else "未加入"}")
        sb.appendLine()
    }

    private fun allFilesAccess(context: Context): String = if (Build.VERSION.SDK_INT >= 30) {
        runCatching { Environment.isExternalStorageManager() }.getOrDefault(false).let {
            if (it) "已授予" else "未授予"
        }
    } else {
        "不适用（API < 30）"
    }

    private fun canDrawOverlays(context: Context): Boolean =
        runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    private fun countChildren(dir: File): Int = runCatching { dir.list()?.size ?: 0 }.getOrDefault(0)

    /** bootstrap 的完整性下限，供 UI 侧提示用。 */
    fun archiveLooksComplete(entryCount: Int): Boolean = entryCount >= ARCHIVE_MIN_ENTRIES
}
