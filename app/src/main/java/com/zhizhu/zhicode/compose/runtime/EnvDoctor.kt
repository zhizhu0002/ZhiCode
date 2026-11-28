package com.zhizhu.zhicode.compose.runtime

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.DocumentsContract
import android.provider.Settings
import com.termux.app.zhicode.core.DocumentTree
import com.termux.app.zhicode.core.ProviderLog
import com.termux.app.zhicode.core.StorageLinks
import com.termux.shared.termux.TermuxConstants
import com.zhizhu.zhicode.ContainerPaths
import com.zhizhu.zhicode.ProotCommand
import com.zhizhu.zhicode.RuntimeInstaller
import java.io.File

/** 环境自检：把定位问题需要的全部运行期事实汇总成一段可复制的纯文本。 */
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

    /** 本应用是跑在 ZhiCode 沙箱（BlackBox）里，还是直接跑在真机上。 */
    private fun runtimeShape(context: Context): String {
        val dataDir = context.applicationInfo?.dataDir.orEmpty()
        return if (dataDir.contains("blackbox")) {
            "ZhiCode 沙箱内（虚拟化数据目录，请在真机验证 PRoot / SAF）"
        } else {
            "真机/独立运行"
        }
    }

    // ------------------------------------------------------------------ 路径

    private fun pathInfo(sb: StringBuilder) {
        sb.appendLine("## Ubuntu 24.04 / PRoot 路径")
        sb.appendLine("configured  : ${TermuxConstants.isConfigured()}")
        sb.appendLine("PACKAGE_NAME: ${TermuxConstants.TERMUX_PACKAGE_NAME}")
        sb.appendLine("DATA_DIR    : ${TermuxConstants.TERMUX_DATA_DIR_PATH}")
        sb.appendLine("FILES_DIR   : ${TermuxConstants.TERMUX_FILES_DIR_PATH}")
        sb.appendLine("ROOTFS      : ${TermuxConstants.DEBIAN_ROOTFS_DIR_PATH}")
        sb.appendLine("HOME        : ${TermuxConstants.TERMUX_HOME_DIR_PATH}")
        sb.appendLine("PROOT_TMP   : ${TermuxConstants.PROOT_TMP_DIR_PATH}")
        sb.appendLine()
    }

    // ---------------------------------------------------------------- Ubuntu

    private fun termuxInfo(sb: StringBuilder) {
        val rootfs = ContainerPaths.rootfsDir()
        val home = ContainerPaths.homeDir()
        val bash = File(rootfs, "bin/bash")
        val node = File(rootfs, "usr/local/bin/node")
        val pi = File(rootfs, "usr/local/bin/pi")
        val marker = File(rootfs, "etc/zhicode-ubuntu-v1")

        sb.appendLine("## Ubuntu 24.04 容器")
        sb.appendLine("ROOTFS 存在 : ${rootfs.isDirectory}  (${countChildren(rootfs)} 项)")
        sb.appendLine("HOME 存在   : ${home.isDirectory}")
        sb.appendLine("bash 存在   : ${bash.exists()}")
        sb.appendLine("node 存在   : ${node.isFile}")
        sb.appendLine("pi 存在     : ${pi.exists()}")
        sb.appendLine("staging     : ${File(TermuxConstants.TERMUX_FILES_DIR_PATH, "ubuntu-staging").exists()}")
        sb.appendLine("backup      : ${File(TermuxConstants.TERMUX_FILES_DIR_PATH, "ubuntu-backup").exists()}")
        sb.appendLine("marker      : ${marker.isFile}")
        if (marker.isFile) {
            val lines = runCatching { marker.readLines() }.getOrNull().orEmpty()
            sb.appendLine("  版本      : ${lines.getOrNull(0) ?: "?"}")
            sb.appendLine("  sha256    : ${lines.getOrNull(1) ?: "?"}")
            sb.appendLine("  来源      : ${lines.getOrNull(2) ?: "?"}")
        }
        sb.appendLine()
    }

    // -------------------------------------------------- 内置资产与 native

    private fun nativeInfo(context: Context, sb: StringBuilder) {
        sb.appendLine("## 内置资产与 native")

        val listed = runCatching {
            context.assets.list("")?.toList().orEmpty()
        }.getOrElse { emptyList() }
        sb.appendLine("assets 根   : ${listed.joinToString(", ").take(200)}")

        val size = runCatching {
            context.assets.open(RuntimeInstaller.BOOTSTRAP_ASSET).use { it.available().toLong() }
        }.getOrNull()
        sb.appendLine("node+pi归档 : ${if (size != null) "$size 字节" else "读取失败"}")

        val proot = ProotCommand.prootBinary(context)
        val loader = ProotCommand.loaderBinary(context)
        sb.appendLine("libproot.so : ${if (proot.isFile) "${proot.length()} 字节" else "缺失"}")
        sb.appendLine("loader.so   : ${if (loader.isFile) "${loader.length()} 字节" else "缺失"}")

        val libDir = context.applicationInfo?.nativeLibraryDir
        val lib = libDir?.let { File(it, "libtermux.so") }
        sb.appendLine("libtermux.so: ${lib?.let { if (it.isFile) "${it.length()} 字节" else "缺失" } ?: "-"}")
        if (lib != null && lib.isFile) {
            val loaded = runCatching {
                System.load(lib.absolutePath)
                true
            }.getOrElse { false }
            sb.appendLine("System.load : ${if (loaded) "成功" else "失败"}")
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
        localMountInfo(context, sb)
    }

    private fun localMountInfo(context: Context, sb: StringBuilder) {
        val rootfs = ContainerPaths.rootfsDir()
        sb.appendLine("## 本地挂载（文件管理器访问 Debian 容器根目录 /）")
        for (line in DocumentTree.describe(rootfs)) sb.appendLine(line)

        val authority = "${context.packageName}.documents"
        sb.appendLine("provider     : $authority（DocumentsProvider，SAF 暴露容器 /）")
        sb.appendLine("已注册       : ${isDocumentsProviderRegistered(context, authority)}")
        val readable = rootfs.canRead() && rootfs.isDirectory
        sb.appendLine("文件管理器可用: ${if (readable) "是" else "否（容器根目录读不到）"}")
        for (line in providerRoundTrip(context, authority)) sb.appendLine(line)
        for (line in providerLogTail()) sb.appendLine(line)
        sb.appendLine()
    }

    private fun providerRoundTrip(context: Context, authority: String): List<String> {
        val out = ArrayList<String>()
        val resolver = context.contentResolver
        val root = DocumentsContract.buildDocumentUri(authority, DocumentTree.ROOT_DOCUMENT_ID)

        out += step("自检·打开根") {
            val cursor = resolver.query(
                root,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_FLAGS,
                ),
                null,
                null,
                null,
            )
            if (cursor == null) {
                "null（provider 没回 cursor：没注册，或被 MANAGE_DOCUMENTS 挡住）"
            } else {
                try {
                    if (!cursor.moveToFirst()) {
                        "空表"
                    } else {
                        val flags = cursor.getInt(2)
                        val canCreate = flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0
                        "ok（名字=${cursor.getString(1)}，flags=$flags，可建子项=$canCreate）"
                    }
                } finally {
                    cursor.close()
                }
            }
        }

        out += step("自检·列根目录") {
            val children = DocumentsContract.buildChildDocumentsUri(authority, DocumentTree.ROOT_DOCUMENT_ID)
            val cursor = resolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null,
            )
            if (cursor == null) {
                "null（列不出来）"
            } else {
                try {
                    if (cursor.count == 0) "0 项（HOME 是空的？）" else "${cursor.count} 项"
                } finally {
                    cursor.close()
                }
            }
        }

        val name = "zhicode-selftest-${System.currentTimeMillis()}"
        var created: Uri? = null
        out += step("自检·建目录") {
            created = DocumentsContract.createDocument(
                resolver,
                root,
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            )
            if (created == null) {
                "失败（provider 抛了异常 —— 框架吞成 null，原因见下方日志）"
            } else {
                "ok（$name）"
            }
        }

        out += step("自检·删掉它") {
            val uri = created
            when {
                uri == null -> "跳过（没建成）"
                DocumentsContract.deleteDocument(resolver, uri) -> "ok（没留下东西）"
                else -> "失败 —— 请手动删掉 $name"
            }
        }
        return out
    }

    /** 跑一步自检并渲染成一行。**绝不抛**：自检自己崩掉，等于把唯一的诊断手段也弄没了。 */
    private inline fun step(label: String, body: () -> String): String =
        "$label: " + runCatching(body).getOrElse { "${it.javaClass.simpleName}: ${it.message}" }

    /** provider 自己记的日志尾巴。 */
    private fun providerLogTail(): List<String> {
        val file = File(File(TermuxConstants.TERMUX_HOME_DIR_PATH, "tmp"), ProviderLog.FILE_NAME)
        if (!file.isFile) return listOf("provider 日志: 还没有（provider 一次都没被调用过）")
        val text = runCatching { file.readText() }.getOrNull()
            ?: return listOf("provider 日志: 读不出来（${file.absolutePath}）")
        val lines = ProviderLog.tail(text)
        return listOf("provider 日志: ${file.absolutePath}（末尾 ${lines.size} 行）") + lines
    }

    /** provider 是否真的被系统登记为 DocumentsProvider。 */
    private fun isDocumentsProviderRegistered(context: Context, authority: String): String {
        val provider = runCatching {
            context.packageManager.queryIntentContentProviders(
                android.content.Intent("android.content.action.DOCUMENTS_PROVIDER"),
                0,
            ).firstOrNull { it.providerInfo?.authority == authority }?.providerInfo
        }.getOrNull()
        return when {
            provider == null -> "否（清单里的 provider 没生效：检查 name/authorities/intent-filter）"
            else -> "是（${provider.name}，exported=${provider.exported}）"
        }
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
