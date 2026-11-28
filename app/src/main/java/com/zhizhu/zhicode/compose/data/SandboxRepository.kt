package com.zhizhu.zhicode.compose.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import com.zhizhu.zhicode.sandbox.SandboxHostClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class SandboxRepository(context: Context) {

    private val app = context.applicationContext

    /** 引擎状态 + 两个全局开关。 */
    suspend fun readStatus(): Result<SandboxStatus> = io {
        val r = SandboxHostClient.call(app, "status", JSONObject())
        if (!r.optBoolean("ok", false)) error(r.optString("error", r.toString()))
        SandboxStatus(
            text = r.optString("status", "未知状态"),
            hideRoot = r.optBoolean("hide_root", true),
            floatingLog = r.optBoolean("show_floating_log", true),
        )
    }

    /** 已安装应用 + 是否在跑。 */
    suspend fun readApps(): Result<List<SandboxApp>> = io {
        val listed = SandboxHostClient.call(app, "list", JSONObject())
        if (!listed.optBoolean("ok", false)) error(listed.optString("error", listed.toString()))

        val rows = listed.optJSONArray("apps")
        val names = buildList {
            if (rows != null) {
                for (i in 0 until rows.length()) {
                    val name = rows.optJSONObject(i)?.optString("package", "").orEmpty()
                    if (name.isNotEmpty()) add(name)
                }
            }
        }
        if (names.isEmpty()) return@io emptyList()

        // 进程列表拿不到不算失败：应用列表本身仍然有用，只是全部显示为未运行。
        val running = runCatching { runningPackages() }.getOrDefault(emptySet())
        // 图标同样：拿不到就回落成首字母方块，不该让整个列表失败。
        val icons = runCatching { iconPaths() }.getOrDefault(emptyMap())
        names.map {
            SandboxApp(
                packageName = it,
                running = it in running,
                iconPath = icons[it],
            )
        }
    }

    private fun iconPaths(): Map<String, String> {
        val r = SandboxHostClient.call(app, "app_icons", JSONObject())
        if (!r.optBoolean("ok", false)) return emptyMap()
        val icons = r.optJSONObject("icons") ?: return emptyMap()
        val out = mutableMapOf<String, String>()
        icons.keys().forEach { key ->
            val path = icons.optString(key, "")
            if (key.isNotEmpty() && path.isNotEmpty() && File(path).isFile) out[key] = path
        }
        return out
    }

    suspend fun importApk(uri: Uri): Result<String> = io {
        val dir = File(app.cacheDir, "sandbox-import").apply { mkdirs() }
        val sourceName = (uri.lastPathSegment ?: "import.apk")
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifEmpty { "import.apk" }
        val target = File(dir, "${System.currentTimeMillis()}-$sourceName")
        try {
            val stream = runCatching { app.contentResolver.openInputStream(uri) }.getOrNull()
                ?: error("读不到所选文件（沙箱内可能无法访问系统文件选择器的 URI；可先用 Sandbox 工具装已知路径的 APK）")
            stream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.length() <= 0L) error("所选文件是空的")

            val info = runCatching {
                app.packageManager.getPackageArchiveInfo(
                    target.absolutePath,
                    PackageManager.GET_ACTIVITIES,
                )
            }.getOrNull()

            if (info?.packageName == null) {
                val sizeMb = target.length() / (1024.0 * 1024.0)
                error(
                    buildString {
                        append("系统无法解析这个 APK，沙箱更装不上。")
                        append("\n文件: ").append(uri.lastPathSegment ?: target.name)
                        append(" · ").append(String.format("%.1f MB", sizeMb))
                        append("\n常见原因：")
                        append("\n· 它是经过 R8/加固/资源压缩的 Release 包（ZhiCode 自己的 Release 包就是这样）")
                        append("\n· 文件是分包的 .apks / .xapk，不是单个 APK")
                        append("\n· 下载不完整或被截断")
                        append("\n\n要验证沙箱，请导入**其它应用**普通签名的 APK。")
                    },
                )
            }
            val pkg = info.packageName

            if (pkg == app.packageName) {
                error(
                    "不能把 ZhiCode 自己安装进沙箱。" +
                        "沙箱是用来运行**其它**应用的；要调试本应用，请把它装到真机。",
                )
            }

            val r = SandboxHostClient.call(app, "install", JSONObject().put("path", target.absolutePath))
            if (!r.optBoolean("ok", false)) error(r.optString("error", r.toString()))
            if (!r.optBoolean("success", false)) {
                val msg = r.optString("message", "").ifEmpty { "沙箱安装失败" }
                /* 引擎侧的失败要补上下文：BlackBox 的原始报错不告诉用户 */
                val sizeMb = target.length() / (1024.0 * 1024.0)
                error(
                    "$msg\n文件: ${uri.lastPathSegment ?: target.name}"
                        + " · ${String.format("%.1f MB", sizeMb)} · 包名 $pkg",
                )
            }
            r.optString("package", pkg)
        } finally {
            target.delete()
        }
    }

    suspend fun launch(pkg: String): Result<Unit> = engine("launch", pkg) { r ->
        if (!r.optBoolean("success", false)) error("沙箱没有找到可启动的 Activity：$pkg")
    }

    suspend fun stop(pkg: String): Result<Unit> = engine("stop", pkg)

    suspend fun clearData(pkg: String): Result<Unit> = engine("clear_data", pkg)

    suspend fun uninstall(pkg: String): Result<Unit> = engine("uninstall", pkg)

    suspend fun setHideRoot(hidden: Boolean): Result<Unit> = io {
        val r = SandboxHostClient.call(app, "set_hide_root", JSONObject().put("hide_root", hidden))
        if (!r.optBoolean("ok", false)) error(r.optString("error", r.toString()))
    }

    suspend fun setFloatingLog(enabled: Boolean): Result<Unit> = io {
        val r = SandboxHostClient.call(
            app, "set_show_floating_log", JSONObject().put("show_floating_log", enabled),
        )
        if (!r.optBoolean("ok", false)) error(r.optString("error", r.toString()))
    }

    suspend fun readSnapshot(
        maxChars: Int = 60_000,
        includeLogcat: Boolean = false,
    ): Result<String> = io {
        val r = SandboxHostClient.call(
            app,
            "debug_snapshot",
            JSONObject()
                .put("max_chars", maxChars)
                .put("include_logcat", includeLogcat),
        )
        if (r.optBoolean("ok", false)) {
            val text = r.optString("snapshot", "")
            if (text.isNotEmpty()) return@io text
        }
        "（引擎进程不可达，以下是主进程视角，可能不含 attach/create 记录）\n" +
            r.optString("error", "") + "\n\n" +
            com.zhizhu.zhicode.sandbox.SandboxDebugLog.snapshot(app, maxChars, includeLogcat)
    }

    suspend fun clearDebug(): Result<Unit> = io {
        val r = SandboxHostClient.call(app, "clear_debug", JSONObject())
        if (!r.optBoolean("ok", false)) error(r.optString("error", r.toString()))
    }

    /** Frida Gadget 状态。 */
    suspend fun readFrida(): Result<FridaStatus> = io {
        val r = SandboxHostClient.call(app, "frida_status", JSONObject())
        if (!r.optBoolean("ok", false)) error(r.optString("error", r.toString()))
        val f = r.optJSONObject("frida") ?: error("引擎没有返回 Frida 状态")
        FridaStatus(
            installed = f.optBoolean("installed", false),
            version = f.optString("version", ""),
            sizeBytes = f.optLong("size", 0L),
        )
    }

    /** 下载并安装 Frida Gadget。 */
    suspend fun installFrida(): Result<FridaStatus> = io {
        val r = SandboxHostClient.call(app, "frida_install", JSONObject())
        if (!r.optBoolean("ok", false)) error(r.optString("error", r.toString()))
        val f = r.optJSONObject("frida") ?: error("引擎没有返回 Frida 状态")
        FridaStatus(
            installed = f.optBoolean("installed", false),
            version = f.optString("version", ""),
            sizeBytes = f.optLong("size", 0L),
        )
    }

    /** 该包的进程清单。 */
    suspend fun processes(pkg: String): Result<String> = io {
        val r = SandboxHostClient.call(app, "process_list", JSONObject().put("package", pkg))
        if (!r.optBoolean("ok", false)) error(r.optString("error", r.toString()))
        val list = r.optJSONArray("processes")
        if (list == null || list.length() == 0) return@io "$pkg 没有正在运行的进程"
        buildString {
            append(pkg).append(" 运行中：\n")
            for (i in 0 until list.length()) {
                val row = list.optJSONObject(i) ?: continue
                append("- pid ").append(row.optInt("pid", 0))
                append("  ").append(row.optString("process", ""))
                append("  uid=").append(row.optInt("uid", 0)).append('\n')
            }
        }
    }

    suspend fun inspect(pkg: String): Result<SandboxInspection> = io {
        val r = SandboxHostClient.call(app, "process_list", JSONObject().put("package", pkg))
        if (!r.optBoolean("ok", false)) error(r.optString("error", r.toString()))
        val rows = r.optJSONArray("processes")
        val processes = buildList {
            if (rows != null) {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val pid = row.optInt("pid", 0)
                    if (pid <= 0) continue
                    add(
                        SandboxProcess(
                            pid = pid,
                            name = row.optString("process", ""),
                            uid = row.optInt("uid", 0),
                            modules = loadModules(pkg, pid),
                        ),
                    )
                }
            }
        }
        SandboxInspection(packageName = pkg, processes = processes)
    }

    /** 某个 guest 进程的模块基址。 */
    private fun loadModules(pkg: String, pid: Int): List<SandboxModule> = runCatching {
        val r = com.zhizhu.zhicode.sandbox.SandboxAgentBridge.request(
            app, "proc_modules", pkg, pid, JSONObject().put("pid", pid), 6000,
        )
        if (!r.optBoolean("ok", false)) return@runCatching emptyList()
        val rows = r.optJSONArray("modules") ?: return@runCatching emptyList()
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val path = row.optString("path", "")
                if (path.isEmpty()) continue
                add(
                    SandboxModule(
                        path = path,
                        base = row.optString("base", ""),
                        sizeBytes = row.optLong("size", 0L),
                        perms = row.optString("perms", ""),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    suspend fun pollRuntime(): Result<RuntimeSnapshot> = io {
        val statusResult = SandboxHostClient.call(app, "status", JSONObject())
        val running = runCatching { runningPackages() }.getOrDefault(emptySet())
        RuntimeSnapshot(
            statusText = if (statusResult.optBoolean("ok", false)) {
                statusResult.optString("status", "未知状态")
            } else {
                "引擎不可用：" + statusResult.optString("error", "")
            },
            hideRoot = statusResult.optBoolean("hide_root", true),
            floatingLog = statusResult.optBoolean("show_floating_log", true),
            runningPackages = running,
        )
    }
    suspend fun screenshot(pkg: String): Result<String> = io {
        val payload = JSONObject().put("annotate", false)
        val r = com.zhizhu.zhicode.sandbox.SandboxAgentBridge.request(
            app, "screenshot", pkg, payload, 12000,
        )
        if (!r.optBoolean("ok", false)) {
            val hint = r.optString("hint", "")
            error(r.optString("error", r.toString()) + if (hint.isEmpty()) "" else "\n$hint")
        }
        val path = r.optString("path", "")
        if (path.isEmpty()) error("截图没有返回路径")
        path
    }

    /** 该包当前**可见的模块基址**。 */
    suspend fun modules(pkg: String): Result<String> = io {
        val pid = firstPid(pkg) ?: error("$pkg 没有正在运行的进程")
        val payload = JSONObject().put("pid", pid)
        val r = com.zhizhu.zhicode.sandbox.SandboxAgentBridge.request(
            app, "proc_modules", pkg, pid, payload, 8000,
        )
        if (!r.optBoolean("ok", false)) error(r.optString("error", r.toString()))
        r.toString(2)
    }

    // ─────────────────────────── 内部 ───────────────────────────

    private fun firstPid(pkg: String): Int? {
        val r = SandboxHostClient.call(app, "process_list", JSONObject().put("package", pkg))
        val list = r.optJSONArray("processes") ?: return null
        for (i in 0 until list.length()) {
            val pid = list.optJSONObject(i)?.optInt("pid", 0) ?: 0
            if (pid > 0) return pid
        }
        return null
    }

    private fun runningPackages(): Set<String> {
        val r = SandboxHostClient.call(app, "process_list", JSONObject())
        if (!r.optBoolean("ok", false)) return emptySet()
        val list = r.optJSONArray("processes") ?: return emptySet()
        val out = mutableSetOf<String>()
        for (i in 0 until list.length()) {
            val pkg = list.optJSONObject(i)?.optString("package", "").orEmpty()
            if (pkg.isNotEmpty()) out.add(pkg)
        }
        return out
    }

    /** 一个只带包名的引擎动作，统一判 `ok` 并提取错误。 */
    private suspend fun engine(
        action: String,
        pkg: String,
        check: (JSONObject) -> Unit = {},
    ): Result<Unit> = io {
        val r = SandboxHostClient.call(app, action, JSONObject().put("package", pkg))
        if (!r.optBoolean("ok", false)) error(r.optString("error", r.toString()))
        check(r)
        Unit
    }

    private suspend fun <T> io(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        runCatching { block() }
    }
}

/** 引擎状态。 */
data class SandboxStatus(
    val text: String,
    val hideRoot: Boolean,
    val floatingLog: Boolean,
)

/** 轮询拿到的运行实况（**不含**图标与 Frida：那些不是"每几秒会变"的东西）。 */
data class RuntimeSnapshot(
    val statusText: String,
    val hideRoot: Boolean,
    val floatingLog: Boolean,
    val runningPackages: Set<String>,
)

/** Frida Gadget 的安装状态。 */
data class FridaStatus(
    val installed: Boolean,
    val version: String,
    val sizeBytes: Long,
)

/** 沙箱里的一个应用。 */
data class SandboxApp(
    val packageName: String,
    val running: Boolean,
    /** 引擎导出的图标 PNG 路径。 */
    val iconPath: String? = null,
)

/** 一个 guest 进程（+ 它当前映射的模块）。 */
data class SandboxProcess(
    val pid: Int,
    val name: String,
    val uid: Int,
    val modules: List<SandboxModule>,
)

/** guest 进程里的一个模块。字段与 `proc_modules` 的输出逐项对应。 */
data class SandboxModule(
    val path: String,
    /** 虚拟基址，形如 `0x7f…`。字符串形式保留：它就是要显示出来的东西。 */
    val base: String,
    val sizeBytes: Long,
    val perms: String,
) {
    /** 只留文件名 —— 整条路径太长，界面上放不下。 */
    val shortName: String get() = path.substringAfterLast('/').ifEmpty { path }
}

/** 一个包在**某一时刻**的运行实况。 */
data class SandboxInspection(
    val packageName: String,
    val processes: List<SandboxProcess>,
) {
    val running: Boolean get() = processes.isNotEmpty()
    val moduleCount: Int get() = processes.sumOf { it.modules.size }
}
