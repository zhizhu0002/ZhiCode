package com.zhizhu.zhicode.sandbox

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import com.termux.app.zhicode.json.JsonItems
import com.termux.app.zhicode.termux.TermuxShellExecutor
import com.termux.shared.termux.TermuxConstants
import com.zhizhu.zhicode.compose.theme.LocalZhiDark
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.zhiTextStyles
import com.zhizhu.zhicode.compose.ui.sandbox.SandboxBoardUiState
import com.zhizhu.zhicode.compose.ui.sandbox.SandboxDialog
import com.zhizhu.zhicode.compose.ui.sandbox.SandboxStatusTone
import com.zhizhu.zhicode.compose.ui.sandbox.ZhiSandboxScreen
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 沙箱管理界面的宿主。
 *
 * ## 它为什么跑在主进程而不是控制器进程
 *
 * BlackBox 引擎在 `:zhisandbox` 里，但那是个会 hook ART、替换系统服务的进程 —— 把它和界面绑在
 * 一起，意味着后端一崩界面也跟着消失，用户看到的是"点了没反应"。本 Activity 留在稳定的主进程，
 * 只通过 [SandboxRpc] 发 IPC 指令；后端挂了它就显示失败原因与启动阶段，而不是一起死掉。
 *
 * ## 界面部分已改为 Compose + Miuix
 *
 * 本类以前是手写的 View 树（`LinearLayout` / `TextView` / 平台 `Switch` / `AlertDialog`），
 * 而那个 Activity 没有声明 `android:theme`，于是继承了应用级主题
 * `@android:style/Theme.Material.NoActionBar.Fullscreen` —— 框架的 Material v1。
 * 结果就是这一屏**根本没进 Miuix 主题树**：开关是 Material v1 的青色 accent、弹窗是平台样式，
 * 与其余界面完全不是一路。现在绘制交给
 * [com.zhizhu.zhicode.compose.ui.sandbox.ZhiSandboxScreen]，走与 `ZhiCodeApp` 相同的主题栈。
 *
 * 本类因此只剩三件事：**持有状态**、**调后端**、**把动作接到后端上**。
 *
 * ## 主题深浅为什么跟随系统
 *
 * 应用自己的 `ThemeMode` 目前只存在于 `WorkspaceViewModel` 的内存状态里、**没有落盘**
 * （`AppSettings` 刻意不含它，见 `SettingsModels` 的说明）。本 Activity 是独立启动的，
 * 拿不到那个 ViewModel，所以按系统深浅来 —— 这与应用默认值 `ThemeMode.SYSTEM` 一致，
 * 也是这里唯一不会说谎的选择。等 `ThemeMode` 落盘后，这里改成读它即可。
 *
 * ## 诊断信息的两个来源
 *
 * 1. [SandboxStage] 的按 pid 阶段文件（每个进程一条，互不覆盖）；
 * 2. 引擎写的 `sandbox/startup-stage.txt`（单文件，兼容用途）。
 *
 * 前者是定位"卡在哪一步"的主要依据，后者保留是因为引擎仍在写它。
 *
 * ## 两个开关都是乐观更新 + 回滚
 *
 * 切换时先压住交互（`*Interactive = false`），服务端确认后再落定；失败则回到原值并提示。
 * `rootSettingInFlight` / `floatingLogInFlight` 是这两个流程的互斥位，防止连点造成状态错乱。
 */
class SandboxBoard : ComponentActivity() {

    private companion object {
        const val MAX_STARTUP_RETRY = 2
        const val LEGACY_STAGE_FILE = "startup-stage.txt"
    }

    /**
     * 界面状态。
     *
     * 用 `mutableStateOf` 而不是 `StateFlow` + ViewModel：这一屏没有跨配置变更需要保留的复杂状态，
     * 后端调用又全是短任务。少一层 ViewModel 就少一处「界面重建后状态从哪来」的问题。
     */
    private val ui = mutableStateOf(SandboxBoardUiState())

    /**
     * 串行执行后端调用。
     *
     * 单线程是刻意的：安装 / 启动 / 停止这类动作会改动引擎的全局状态，并发发起时服务端收到的
     * 顺序可能与用户点击顺序不同，界面就显示成"点了但没生效"。
     */
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var startupRetry = 0

    @Volatile
    private var destroyed = false

    private var rootSettingInFlight = false
    private var floatingLogInFlight = false

    /** 等用户在 Frida 确认框里点「安装并加载」的那一对 (包名, pid)。 */
    private var pendingFridaTarget: Pair<String, Int>? = null

    /**
     * 选 APK。用 Activity Result API 而不是 `startActivityForResult` ——
     * 后者在 Compose 时代已被弃用，而且这里不需要自己记请求码。
     */
    private val pickApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) worker.execute { installFromUri(uri) }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContent {
            val isDark = isSystemInDarkTheme()
            val colors = if (isDark) darkColorScheme() else lightColorScheme()
            // 顺序与 ZhiCodeApp 一致：LocalZhiDark 必须**先**提供，之后才能求任何
            // ZhiColors 层级色 —— 那些函数靠它判断深浅，读早了会拿到默认浅色。
            CompositionLocalProvider(LocalZhiDark provides isDark) {
                MiuixTheme(
                    colors = colors.copy(background = ZhiColors.backdrop()),
                    textStyles = zhiTextStyles(),
                ) {
                    ZhiSandboxScreen(
                        state = ui.value,
                        onBack = { finish() },
                        onRefresh = { reload() },
                        onDiagnostics = { showDiagnostics() },
                        onToggleHideRoot = { wanted -> confirmRootVisibilityChange(wanted) },
                        onToggleFloatingLog = { wanted -> applyFloatingLog(wanted) },
                        onPickApk = {
                            pickApk.launch(arrayOf("application/vnd.android.package-archive"))
                        },
                        onLaunch = { pkg -> rpcAction("launch", pkg, "已启动") },
                        onStop = { pkg -> rpcAction("stop", pkg, "已停止") },
                        onClearData = { pkg ->
                            confirmAction("清除沙箱数据？", pkg, "clear_data", "已清除")
                        },
                        onUninstall = { pkg ->
                            confirmAction("从 ZhiCode 沙箱卸载？", pkg, "uninstall", "已卸载")
                        },
                        onProcesses = { pkg -> showProcesses(pkg) },
                        onFrida = { pkg -> showFrida(pkg) },
                        onDismissDialog = { dismissDialog() },
                        onConfirmRootVisibility = { hidden -> applyRootVisibility(hidden) },
                        onConfirmAction = { dialog ->
                            dismissDialog()
                            rpcAction(dialog.action, dialog.packageName, dialog.successText)
                        },
                        onConfirmFridaInstall = { dialog ->
                            dismissDialog()
                            worker.execute { installAndLoadFrida(dialog.packageName, dialog.pid) }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到本页时后端状态可能已经变了（例如刚从 guest 里退出来），重新拉一次。
        if (!destroyed) reload()
    }

    override fun onDestroy() {
        destroyed = true
        worker.shutdownNow()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ 状态更新

    private fun dismissDialog() {
        ui.value = ui.value.copy(dialog = null)
    }

    /** 只在 UI 线程写状态。后台线程要改状态一律用 `runOnUiThread`。 */
    private fun setStatus(text: String, tone: SandboxStatusTone = SandboxStatusTone.NORMAL) {
        runOnUiThread {
            if (!destroyed) ui.value = ui.value.copy(status = text, statusTone = tone)
        }
    }

    // ------------------------------------------------------------------ 数据加载

    private fun reload() {
        if (destroyed) return
        worker.execute {
            val status = SandboxRpc.call(this, "status", JSONObject())
            if (destroyed) return@execute
            if (!status.optBoolean("ok")) {
                retryOrShow(status.optString("error", "沙箱后端不可用"))
                return@execute
            }
            val listing = SandboxRpc.call(this, "list", JSONObject())
            if (destroyed) return@execute
            if (!listing.optBoolean("ok")) {
                retryOrShow(listing.optString("error", "读取沙箱应用失败"))
                return@execute
            }
            startupRetry = 0
            val packages = collectPackages(listing)
            val summary = status.optString("status", "沙箱后端在线")
            val hideRoot = status.optBoolean("hide_root", true)
            val showLog = status.optBoolean("show_floating_log", false)
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                ui.value = ui.value.copy(
                    status = "$summary · ${packages.size} 个应用",
                    statusTone = SandboxStatusTone.SUCCESS,
                    packages = packages,
                    errorDetail = null,
                    dialog = null,
                )
                setRootSwitch(hideRoot, !rootSettingInFlight)
                setFloatingLog(showLog, !floatingLogInFlight)
            }
        }
    }

    private fun collectPackages(listing: JSONObject): List<String> {
        val packages = mutableListOf<String>()
        val apps: JSONArray? = listing.optJSONArray("apps")
        if (apps != null) {
            for (app in JsonItems.of(apps)) {
                val pkg = app.optString("package", "")
                if (pkg.isNotEmpty()) packages.add(pkg)
            }
        }
        return packages.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    /**
     * 后端还没起来时重试，其余情况直接报错。
     *
     * 只在错误文本确实表示"还没初始化好"时才重试 —— 无条件重试会把真正的配置错误也拖成
     * 两轮无谓等待，反而更难看清原因。
     */
    private fun retryOrShow(error: String?) {
        if (destroyed) return
        val message = error ?: ""
        val transientFailure = message.contains("初始化") ||
            message.contains("timeout") ||
            message.contains("超时") ||
            message.contains("Application.onCreate")
        if (startupRetry < MAX_STARTUP_RETRY && transientFailure) {
            startupRetry++
            setStatus("沙箱后端启动中… 自动重试 $startupRetry/$MAX_STARTUP_RETRY")
            try {
                Thread.sleep(450L * startupRetry)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
            reload()
            return
        }
        showBackendError(message)
    }

    private fun showBackendError(error: String) {
        if (destroyed) return
        val stage = readStartupStage()
        runOnUiThread {
            if (destroyed) return@runOnUiThread
            ui.value = ui.value.copy(
                status = "沙箱后端异常",
                statusTone = SandboxStatusTone.DANGER,
                packages = emptyList(),
                hideRootInteractive = false,
                errorDetail = "BlackBox 后端没有正常响应。\n\n" + error +
                    "\n\n最后启动阶段：\n" + stage +
                    "\n\n点右上角「诊断」可查看完整 ZhiCode 沙箱事件与各进程阶段。",
            )
        }
    }

    // ------------------------------------------------------------------ Root 隐藏

    /**
     * Root 隐藏的确认流程。
     *
     * 先 [setRootSwitch] 把滑块复位（Miuix 的开关点下去就已经翻到新值了），
     * 等用户在确认框里点「确定」才真正提交 —— 这个开关会重启所有 Guest，不能一下手就生效。
     */
    private fun confirmRootVisibilityChange(hidden: Boolean) {
        if (destroyed || rootSettingInFlight) return
        val previous = !hidden
        setRootSwitch(previous, true)
        ui.value = ui.value.copy(dialog = SandboxDialog.RootVisibility(hidden))
    }

    private fun applyRootVisibility(hidden: Boolean) {
        if (destroyed) return
        val previous = !hidden
        dismissDialog()
        rootSettingInFlight = true
        setRootSwitch(previous, false)
        worker.execute {
            try {
                val response = SandboxRpc.call(
                    this, "set_hide_root", JSONObject().put("hide_root", hidden),
                )
                if (!response.optBoolean("ok")) {
                    throw IllegalStateException(response.optString("error", "设置失败"))
                }
                val effective = response.optBoolean("hide_root", hidden)
                runOnUiThread {
                    if (destroyed) return@runOnUiThread
                    rootSettingInFlight = false
                    setRootSwitch(effective, true)
                    toast("Root 隐藏已" + if (effective) "开启" else "关闭")
                    reload()
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    if (destroyed) return@runOnUiThread
                    rootSettingInFlight = false
                    setRootSwitch(previous, true)
                    toast("Root 隐藏设置失败: ${error.message}")
                }
            }
        }
    }

    /** 写入「隐藏 Root」的实际值与「能否交互」。服务端确认前一律禁止交互。 */
    private fun setRootSwitch(hidden: Boolean, interactive: Boolean) {
        ui.value = ui.value.copy(hideRoot = hidden, hideRootInteractive = interactive)
    }

    // ------------------------------------------------------------------ 日志悬浮窗

    private fun applyFloatingLog(enabled: Boolean) {
        if (destroyed || floatingLogInFlight) return
        floatingLogInFlight = true
        // 乐观更新：先按用户意图显示，服务端确认后再以实际值落定
        setFloatingLog(enabled, false)
        worker.execute {
            try {
                val response = SandboxRpc.call(
                    this, "set_show_floating_log", JSONObject().put("show_floating_log", enabled),
                )
                if (!response.optBoolean("ok")) {
                    throw IllegalStateException(response.optString("error", "设置失败"))
                }
                val effective = response.optBoolean("show_floating_log", enabled)
                runOnUiThread {
                    if (destroyed) return@runOnUiThread
                    floatingLogInFlight = false
                    setFloatingLog(effective, true)
                    toast("日志悬浮窗已" + if (effective) "开启" else "关闭")
                    reload()
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    if (destroyed) return@runOnUiThread
                    floatingLogInFlight = false
                    setFloatingLog(!enabled, true)
                    toast("日志悬浮窗设置失败: ${error.message}")
                }
            }
        }
    }

    private fun setFloatingLog(enabled: Boolean, interactive: Boolean) {
        ui.value = ui.value.copy(floatingLog = enabled, floatingLogInteractive = interactive)
    }

    // ------------------------------------------------------------------ 列表动作

    private fun confirmAction(title: String, pkg: String, action: String, successText: String) {
        if (destroyed) return
        ui.value = ui.value.copy(
            dialog = SandboxDialog.ConfirmAction(
                title = title,
                packageName = pkg,
                action = action,
                successText = successText,
            ),
        )
    }

    private fun rpcAction(action: String, pkg: String, successText: String) {
        if (destroyed) return
        worker.execute {
            try {
                val response = SandboxRpc.call(this, action, JSONObject().put("package", pkg))
                if (!response.optBoolean("ok")) {
                    throw IllegalStateException(response.optString("error", "操作失败"))
                }
                // launch 的 ok 只代表 RPC 成功，是否真的拉起要看 success
                if ("launch" == action && !response.optBoolean("success", true)) {
                    throw IllegalStateException("没有可启动 Activity")
                }
                runOnUiThread {
                    toast(successText)
                    reload()
                }
            } catch (error: Throwable) {
                runOnUiThread { toast("$action: ${error.message}") }
            }
        }
    }

    // ------------------------------------------------------------------ APK 导入

    /**
     * 先把选中的 APK 复制到自己的 cache，再交给后端安装。
     *
     * 必须先复制：SAF 给的 `content://` 授权是给本进程的，控制器进程未必能读；
     * 而且本地路径也能让后面校验与安装用同一份字节。
     *
     * 安装前用 `getPackageArchiveInfo` 验一遍，是为了把"选了张图片"这类用户错误在界面侧就
     * 挡掉，不至于把后端拖进一次注定失败的解析。
     */
    private fun installFromUri(uri: Uri) {
        try {
            val directory = File(cacheDir, "sandbox-import")
            directory.mkdirs()
            val displayName = displayName(uri).replace(Regex("[^A-Za-z0-9._-]"), "_")
            val staged = File(directory, System.currentTimeMillis().toString() + "-" + displayName)

            val input: InputStream = contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("无法读取 APK")
            input.use { from ->
                FileOutputStream(staged).use { to ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val read = from.read(buffer)
                        if (read == -1) break
                        to.write(buffer, 0, read)
                    }
                }
            }

            val info: PackageInfo? = packageManager
                .getPackageArchiveInfo(staged.absolutePath, PackageManager.GET_ACTIVITIES)
            val archivedPackage = info?.packageName
            if (archivedPackage == null) {
                throw IllegalArgumentException("不是有效普通 APK")
            }
            if (packageName == archivedPackage) {
                throw IllegalArgumentException("不能导入 ZhiCode 自身")
            }

            val response = SandboxRpc.call(
                this, "install", JSONObject().put("path", staged.absolutePath),
            )
            if (!response.optBoolean("ok") || !response.optBoolean("success")) {
                throw IllegalStateException(
                    response.optString("error", response.optString("message", "安装失败")),
                )
            }
            val installed = response.optString("package", archivedPackage)
            runOnUiThread {
                toast("已安装到沙箱: $installed")
                reload()
            }
        } catch (error: Throwable) {
            runOnUiThread { toast("安装失败: ${error.message}") }
        }
    }

    private fun displayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor: Cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        if (!name.isNullOrEmpty()) return name
                    }
                }
        } catch (ignored: Throwable) {
            // 拿不到就退回默认名
        }
        return "imported.apk"
    }

    // ------------------------------------------------------------------ 进程与 Frida

    private fun fetchProcesses(pkg: String): List<JSONObject> {
        val response = SandboxRpc.call(this, "process_list", JSONObject().put("package", pkg))
        if (!response.optBoolean("ok")) {
            throw IllegalStateException(response.optString("error", "读取进程失败"))
        }
        val processes = mutableListOf<JSONObject>()
        val rows: JSONArray? = response.optJSONArray("processes")
        if (rows != null) {
            for (row in JsonItems.of(rows)) processes.add(row)
        }
        return processes
    }

    private fun showProcesses(pkg: String) {
        showDetail(pkg + " · 进程 / SO 基址")
        worker.execute {
            try {
                val text = StringBuilder()
                val processes = fetchProcesses(pkg)
                if (processes.isEmpty()) text.append("当前没有运行进程。先启动这个 Guest。\n")
                for (process in processes) {
                    val pid = process.optInt("pid", -1)
                    text.append("PID ").append(pid).append(" · ")
                        .append(process.optString("process")).append('\n')
                    text.append(describeModules(pkg, pid))
                    text.append('\n')
                }
                updateDetail(text.toString())
            } catch (error: Throwable) {
                runOnUiThread {
                    toast("读取进程失败: ${error.message}")
                    dismissDialog()
                }
            }
        }
    }

    /** 列出 guest 进程里已映射的 .so / apk 基址，供后续 Frida 或手动调试参照。 */
    private fun describeModules(pkg: String, pid: Int): String {
        val text = StringBuilder()
        try {
            val response = SandboxGuestHost.request(
                this, "proc_modules", pkg, pid,
                JSONObject().put("pid", pid).put("max_modules", 80), 4000,
            )
            if (!response.optBoolean("ok")) {
                return "  [调试桥] " + response.optString("error", "未响应") + "\n"
            }
            val modules: JSONArray = response.optJSONArray("modules") ?: return ""
            var shown = 0
            var index = 0
            while (index < modules.length() && shown < 24) {
                val module = modules.optJSONObject(index)
                index++
                if (module == null) continue
                val path = module.optString("path", "")
                if (!(path.endsWith(".so") || path.contains(".apk"))) continue
                text.append("  ").append(module.optString("base", "?"))
                    .append("  ").append(File(path).name).append('\n')
                shown++
            }
        } catch (error: Throwable) {
            text.append("  [调试桥] ").append(error.message).append('\n')
        }
        return text.toString()
    }

    private fun showFrida(pkg: String) {
        worker.execute {
            try {
                val processes = fetchProcesses(pkg)
                if (processes.isEmpty()) {
                    runOnUiThread { toast("先运行 Guest，再加载 Frida") }
                    return@execute
                }
                // 优先选主进程（process == 包名），否则退而取第一个
                var chosen = processes[0]
                for (process in processes) {
                    if (pkg == process.optString("process")) {
                        chosen = process
                        break
                    }
                }
                val pid = chosen.optInt("pid", -1)
                if (pid <= 0) throw IllegalStateException("无有效 PID")

                if (FridaEnv.isInstalled(this)) {
                    loadFrida(pkg, pid)
                    return@execute
                }
                // 首次使用：确认后再下载 + 校验 + 加载。安装是本地行为，不走 RPC，
                // 所以用专门的对话框类型而不是 ConfirmAction（后者会把 action 当后端动作发出去）。
                pendingFridaTarget = pkg to pid
                runOnUiThread {
                    if (destroyed) return@runOnUiThread
                    ui.value = ui.value.copy(
                        dialog = SandboxDialog.FridaInstall(
                            packageName = pkg,
                            pid = pid,
                            version = FridaEnv.VERSION,
                        ),
                    )
                }
            } catch (error: Throwable) {
                runOnUiThread { toast("Frida: ${error.message}") }
            }
        }
    }

    private fun installAndLoadFrida(pkg: String, pid: Int) {
        try {
            setStatus("正在安装 Frida Gadget " + FridaEnv.VERSION + "…")
            FridaEnv.install(this, TermuxShellExecutor(this), TermuxConstants.TERMUX_HOME_DIR_PATH)
            loadFrida(pkg, pid)
        } catch (error: Throwable) {
            runOnUiThread { toast("Frida 安装失败: ${error.message}") }
        }
    }

    private fun loadFrida(pkg: String, pid: Int) {
        try {
            val response = SandboxGuestHost.request(
                this, "proc_frida_load", pkg, pid, JSONObject().put("pid", pid), 15000,
            )
            showDetail(pkg + " · Frida")
            updateDetail(response.toString())
        } catch (error: Throwable) {
            runOnUiThread { toast("Frida 加载失败: ${error.message}") }
        }
    }

    // ------------------------------------------------------------------ 诊断

    private fun showDiagnostics() {
        setStatus("正在读取诊断信息…")
        showDetail("ZhiCode 沙箱诊断")
        worker.execute {
            val text = StringBuilder()
            text.append(SandboxConsole.snapshot(this))
            text.append("\n===== 各进程启动阶段 =====\n")
            text.append(fetchStagesFromBackend())
            text.append("\n===== 引擎阶段文件（$LEGACY_STAGE_FILE）=====\n")
            text.append(readStartupStage())
            updateDetail(text.toString())
        }
    }

    /**
     * 向控制器索取按 pid 分的阶段。
     *
     * 这是定位"卡在哪一步"的主要依据：每个进程一条记录，不会被别的进程覆盖。
     * 拿不到就说明控制器本身没起来 —— 那正是最需要知道的信息，所以把错误原样返回。
     */
    private fun fetchStagesFromBackend(): String = try {
        val response = SandboxRpc.call(this, "stages", JSONObject())
        if (response.optBoolean("ok")) {
            response.optString("stages", "(无阶段记录)\n")
        } else {
            "(控制器未响应: " + response.optString("error", "未知原因") + ")\n"
        }
    } catch (error: Throwable) {
        "(读取失败: $error)\n"
    }

    private fun readStartupStage(): String {
        val file = File(filesDir, "sandbox/$LEGACY_STAGE_FILE")
        if (!file.isFile) return "(尚无后端启动记录)\n"
        return try {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(minOf(file.length(), 8192L).toInt())
                val read = input.read(buffer)
                if (read <= 0) "(空)\n" else String(buffer, 0, read, StandardCharsets.UTF_8).trim() + "\n"
            }
        } catch (error: Throwable) {
            "读取失败: $error\n"
        }
    }

    // ------------------------------------------------------------------ 长文本面板

    private fun showDetail(title: String) {
        runOnUiThread {
            if (!destroyed) {
                ui.value = ui.value.copy(
                    dialog = SandboxDialog.Detail(title = title, body = "", loading = true),
                )
            }
        }
    }

    private fun updateDetail(body: String) {
        runOnUiThread {
            if (destroyed) return@runOnUiThread
            val current = ui.value.dialog
            if (current is SandboxDialog.Detail) {
                ui.value = ui.value.copy(dialog = current.copy(body = body, loading = false))
            }
        }
    }

    private fun toast(message: String?) {
        val text = message ?: ""
        runOnUiThread {
            if (!destroyed) Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }
}
