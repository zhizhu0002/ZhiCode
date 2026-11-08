package com.zhizhu.zhicode.compose.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import com.zhizhu.zhicode.sandbox.SandboxHostClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * 沙盒管理页的数据层。
 *
 * ## 为什么页面不该直接调 `SandboxHostClient`
 *
 * 那个类是 Java + `org.json` 的同步 IPC：每次调用都是一次 Binder 往返，
 * 失败时返回 `{ok:false, error:"…"}` 而不是抛异常。让 Compose 直接用它会有两个后果：
 *
 * 1. **在主线程做 Binder**。`ContentResolver.call` 在引擎初始化时要等最多 12 秒
 *    （`SandboxControlProvider.dispatch` 的 `awaitReady`），放在组合里就是 ANR。
 * 2. **"成功"的判定散落在界面里**。是否 `ok`、错误文案怎么取，会在每个按钮里
 *    各写一遍，然后总有一个漏判 —— 于是失败的时候界面显示成功。
 *
 * 这一层把两件事收干净：**一律在 IO 线程**、**一律返回 `Result`**。
 *
 * ## "运行中"是怎么算出来的
 *
 * 引擎的 `list` 只回包名，`process_list` 才回 PID。界面要显示"运行中"，
 * 必须把两者合起来 —— 这就是 [SandboxApp.running] 的来源。
 * 没有为此给引擎加一个新动作：那两个动作**各自都有别的使用者**
 * （Agent 的 `Sandbox list` / `Debug process_list`），合并只服务于这一页。
 */
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
        names.map { SandboxApp(packageName = it, running = it in running) }
    }

    /**
     * 从系统文件选择器拿到的 URI 导入一个 APK。
     *
     * ## 为什么必须先把字节拷出来
     *
     * `ACTION_OPEN_DOCUMENT` 给的是 `content://`，而引擎要的是一个**真实路径**
     * （`BlackBoxCore.installPackageAsUser(File, userId)`）。两者之间只能落一次盘。
     * 目标选 `cacheDir` 而不是 `filesDir`：装完就没用了，缓存可以被系统回收。
     *
     * ## 为什么先在本地校验包名
     *
     * 引擎的安装失败文案是 BlackBox 的通用错误（"解析失败"），
     * 而这里能给出精确原因：不是 APK / 不能装宿主自己 / 读不到文件。
     * 更早失败、更准的话。
     *
     * ⚠️ 在**沙箱里**运行时，`openInputStream` 可能因为 provider 不可达而失败
     * （与导出的 `No content provider` 同一个根因）。那种情况下给出的错误会
     * 指明"从文件选择器读不到"，而不是让 BlackBox 去报一个更含糊的解析错误。
     */
    suspend fun importApk(uri: Uri): Result<String> = io {
        /*
         * 落在 `cacheDir/sandbox-import/`。
         *
         * ⚠️ 这里曾经改成 `filesDir/sandbox/import/`，理由是"cacheDir 可能被系统清理"，
         * 结果**装不上**（引擎报 `parser apk error`）。所以改回原版 IQ-Code-Android
         * 用的 `cacheDir` —— 那条路是被验证过能装的。
         *
         * 教训：这类"看起来更稳妥"的改动必须有实测支撑。cacheDir 被清理是
         * 一个**理论风险**，而引擎能读到文件是**已证事实**；用一个理论风险去换掉
         * 一个已证事实，代价就是这个功能完全不可用。
         */
        val dir = File(app.cacheDir, "sandbox-import").apply { mkdirs() }
        /*
         * 文件名保留原样（前面加时间戳去重），与原版 IQ-Code-Android 一致：
         *   `<ts>-<原文件名>`，非 [A-Za-z0-9._-] 的字符替换成 `_`。
         *
         * 为什么连这个都照抄：BlackBox 的解析器理论上不该看文件名，
         * 但这是最后一处**尚未验证**的移植差异。在"第三方 APK 装不上"
         * 还没定位到真因之前，把已知差异清零比留着一个"应该没影响"更划算。
         */
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

            /*
             * 先用系统 PackageManager 解析一次，**解析不了就直接拒绝**。
             *
             * 🔴 这里曾经写成"解析为 null 就放行给引擎"，那是错的：
             * 一个连系统 `PackageManager.getPackageArchiveInfo` 都读不出包名的文件，
             * BlackBox 更不可能解析 —— 放行的结果就是用户看到引擎吐出
             * 一句 `parser apk error`（英文、无上下文、不可行动）。
             *
             * 原版 IQ-Code-Android 正是硬失败（`if (pi == null || pi.packageName == null) throw`），
             * 这里跟它一致。
             *
             * 已知会发生的情况：ZhiCode 自己的 Release 包经 R8 全量混淆 + 资源压缩后，
             * 部分 ROM 上 `getPackageArchiveInfo` 会返回 null。那恰好也是**我们最想拦住**
             * 的那个文件（不能把宿主装进自己的沙箱），所以"拒绝"在两个方向上都是对的。
             */
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
                /*
                 * 引擎侧的失败要补上下文：BlackBox 的原始报错不告诉用户
                 * "哪个文件、多大、什么包名" —— 而这三样正是排查的全部线索。
                 */
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

    /**
     * 该包的进程清单。
     *
     * 这里只取进程（`process_list`），**不**去读 `/proc/<pid>/maps`：
     * 后者是 guest 侧的能力（`Debug scope=sandbox action=modules`）。
     * 在宿主进程里读 guest 的 maps 拿不到 —— 那不是同一个进程。
     * 所以这一屏给的是"有哪些进程、PID 是多少"，想看模块再让 Agent 用 Debug。
     */
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

    /** 取一张 guest 截图并返回其本地路径（给应用卡里的预览用）。 */
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

    /**
     * 该包当前**可见的模块基址**。
     *
     * 走的是 guest 侧的 `proc_modules` —— 它在 guest 自己的进程里读自己的
     * `/proc/self/maps`，所以拿得到真实基址。宿主进程做不到这件事。
     */
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

/** 沙箱里的一个应用。 */
data class SandboxApp(
    val packageName: String,
    val running: Boolean,
)
