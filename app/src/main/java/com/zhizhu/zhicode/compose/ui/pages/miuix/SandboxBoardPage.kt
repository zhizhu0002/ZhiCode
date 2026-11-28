package com.zhizhu.zhicode.compose.ui.pages.miuix

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zhizhu.zhicode.compose.data.Clipboard
import com.zhizhu.zhicode.compose.data.FridaStatus
import com.zhizhu.zhicode.compose.data.SandboxApp
import com.zhizhu.zhicode.compose.data.SandboxInspection
import com.zhizhu.zhicode.compose.data.SandboxRepository
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.ZhiFilledIconButton
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideInsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideOutsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiDialogWidth
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsSubPage
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File

@Composable
internal fun SandboxBoardPage(
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SandboxRepository(context) }

    var loaded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("正在读取引擎状态…") }
    var hideRoot by remember { mutableStateOf(true) }
    var floatingLog by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<SandboxApp>>(emptyList()) }
    var frida by remember { mutableStateOf<FridaStatus?>(null) }
    var notice by remember { mutableStateOf<Notice?>(null) }

    /** 正在执行的动作名（null = 空闲）。只给发起者那一个按钮显示进度。 */
    var inFlight by remember { mutableStateOf<String?>(null) }

    /** 诊断面板的文本。非 null 即面板打开。 */
    var snapshot by remember { mutableStateOf<String?>(null) }

    /** 屏幕上有"显示在其他应用上层"权限吗 —— 决定悬浮条能不能盖住对话框。 */
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // 从系统设置页授权回来时重新读一次。没有这一步，用户授权完回到本页
    // 看到的仍是"未授权"，只能杀进程重开。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canOverlay = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val screenshots = remember { mutableStateMapOf<String, ScreenshotState>() }
    /** 每包一次"进程 / 模块"实况。见 [SandboxInspection] 的说明。 */
    val inspections = remember { mutableStateMapOf<String, SandboxInspection>() }
    /** 正在实时刷新实况的包。只有用户展开「进程 / SO 基址」的卡才会进来。 */
    val livePackages = remember { mutableStateMapOf<String, Unit>() }
    /** 全屏查看中的截图路径（null = 没在看）。 */
    var viewingShot by remember { mutableStateOf<String?>(null) }

    suspend fun reloadApps() {
        repository.readApps().onSuccess { apps = it }
    }

    /** 执行一个动作。 */
    fun run(
        label: String,
        affectsList: Boolean = false,
        block: suspend () -> String?,
    ) {
        if (inFlight != null) return
        inFlight = label
        // 刻意**不清空** notice：清空会让底部提示突然消失、页面高度跳一下。
        // 新结果直接就地替换（见 NoticeCard 的 AnimatedContent）。
        scope.launch {
            val outcome = runCatching { block() }
            outcome.exceptionOrNull()?.let { notice = Notice("$label 失败：${it.message ?: "未知原因"}", true) }
            outcome.getOrNull()?.let { notice = Notice(it, false) }
            if (affectsList) runCatching { reloadApps() }
            inFlight = null
        }
    }

    suspend fun refreshAll() {
        repository.readStatus().onSuccess {
            status = it.text
            hideRoot = it.hideRoot
            floatingLog = it.floatingLog
        }.onFailure {
            status = "引擎不可用：${it.message ?: "未知原因"}"
        }
        repository.readFrida().onSuccess { frida = it }
        runCatching { reloadApps() }
        loaded = true
    }

    LaunchedEffect(Unit) { refreshAll() }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(POLL_INTERVAL_MS)
            val snap = repository.pollRuntime().getOrNull() ?: continue
            status = snap.statusText
            apps = apps.map { it.copy(running = it.packageName in snap.runningPackages) }
        }
    }

    /* 展开着的那张卡的实况轮询。 */
    LaunchedEffect(livePackages.keys.toList()) {
        if (livePackages.isEmpty()) return@LaunchedEffect
        while (true) {
            for (pkg in livePackages.keys.toList()) {
                repository.inspect(pkg).onSuccess { inspections[pkg] = it }
            }
            kotlinx.coroutines.delay(INSPECT_INTERVAL_MS)
        }
    }

    suspend fun openDiagnostics() {
        snapshot = "正在读取诊断快照…"
        snapshot = repository.readSnapshot().getOrElse {
            "读取诊断失败：${it.message ?: "未知原因"}"
        }
    }

    /* 系统文件选择器。 */
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            run("导入 APK", affectsList = true) {
                repository.importApk(uri).fold(
                    onSuccess = { "已安装到沙箱：$it" },
                    onFailure = { throw it },
                )
            }
        }
    }

    SettingsSubPage(
        title = "沙箱",
        subtitle = "虚拟安卓运行环境 · BlackBox",
        onBack = onExit,
        action = "诊断" to { run("诊断") { openDiagnostics(); null } },
        actionEnabled = inFlight == null,
        blurTopBar = false,
        // 诊断走 WindowDialog（独立窗口）。
        overlay = {
            val shown = rememberLastNonNull(snapshot)
            WindowDialog(
                title = "诊断",
                show = snapshot != null,
                onDismissRequest = { snapshot = null },
                maxWidth = ZhiDialogWidth.Wide,
                outsideMargin = DialogWideOutsideMargin,
                insideMargin = DialogWideInsideMargin,
                largeScreen = true,
            ) {
                DiagnosticsBody(
                    text = shown ?: "",
                    busy = inFlight != null,
                    onCopy = {
                        val ok = Clipboard.copy(context, "沙箱诊断", shown ?: "")
                        // Android 13+ 系统自己会弹"已复制"，再弹一次是重复打扰。
                        if (!ok) notice = Notice("复制失败：剪贴板不可用或内容为空", true)
                        else if (!Clipboard.systemShowsCopyToast()) notice = Notice("诊断快照已复制", false)
                    },
                    onClear = {
                        run("清空日志") {
                            repository.clearDebug().fold(
                                onSuccess = { "引擎日志已清空" },
                                onFailure = { throw it },
                            )
                        }
                    },
                    onRefresh = { run("诊断") { openDiagnostics(); null } },
                )
            }
        },
    ) {
        EngineStatusCard(
            status = status,
            appCount = apps.size,
            runningCount = apps.count { it.running },
            onDiagnose = { run("诊断") { openDiagnostics(); null } },
        )

        if (!canOverlay) {
            PermissionWarningCard(
                onGrant = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    notice = Notice("授予「显示在其他应用上层」后返回本页即可", false)
                },
            )
        }

        // ── 两个全局开关 ────────────────────────────────────────────
        SettingsGroup(title = "运行环境") {
            SwitchRow(
                title = "隐藏 Root",
                summary = "Guest 看不到常见的 root 路径与管理包",
                checked = hideRoot,
                enabled = inFlight == null,
                onCheckedChange = { next ->
                    val previous = hideRoot
                    hideRoot = next
                    run("隐藏 Root") {
                        repository.setHideRoot(next).fold(
                            onSuccess = {
                                if (next) "已开启 Root 隐藏（全部 Guest 已停止）"
                                else "已关闭 Root 隐藏（全部 Guest 已停止）"
                            },
                            onFailure = {
                                hideRoot = previous
                                throw it
                            },
                        )
                    }
                },
            )
            SwitchRow(
                title = "日志悬浮窗",
                summary = if (canOverlay) {
                    "在 Guest 界面显示可拖动的调试浮层"
                } else {
                    "需要上面的「悬浮条置顶」授权才能盖住应用自己的对话框"
                },
                checked = floatingLog,
                enabled = inFlight == null,
                onCheckedChange = { next ->
                    val previous = floatingLog
                    floatingLog = next
                    run("日志悬浮窗") {
                        repository.setFloatingLog(next).fold(
                            onSuccess = { if (next) "已开启日志悬浮窗" else "已关闭日志悬浮窗" },
                            onFailure = {
                                floatingLog = previous
                                throw it
                            },
                        )
                    }
                },
            )
        }

        // ── 导入 / 刷新 ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = {
                    importLauncher.launch(
                        arrayOf(
                            "application/vnd.android.package-archive",
                            "application/octet-stream",
                        ),
                    )
                },
                modifier = Modifier.weight(2f).height(48.dp),
                enabled = inFlight == null,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Icon(ZhiIcons.add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("导入 APK")
            }
            Button(
                onClick = { run("刷新") { refreshAll(); null } },
                modifier = Modifier.weight(1f).height(48.dp),
                enabled = inFlight == null,
            ) {
                AnimatedContent(
                    targetState = inFlight == "刷新",
                    transitionSpec = {
                        fadeIn(ZhiMotion.fadeInSpec) togetherWith fadeOut(ZhiMotion.fadeOutSpec)
                    },
                    label = "sandbox-refresh",
                ) { refreshing ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (refreshing) ZhiIcons.timeout else ZhiIcons.refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (refreshing) "读取中" else "刷新", maxLines = 1)
                    }
                }
            }
        }

        // ── 应用列表 ────────────────────────────────────────────────
        SmallTitle("沙箱应用")

        if (apps.isEmpty()) {
            EmptyAppsCard(loaded = loaded)
        } else {
            apps.forEach { app ->
                // key 让卡片在列表重读后**复用**而不是重建：没有它时每次
                // reloadApps() 都会把整列表当新内容，截图与展开状态跟着闪一下。
                key(app.packageName) {
                    SandboxAppCard(
                        app = app,
                        /* 只把**这个包自己**的动作名传下去，不传全局的 */
                        packageInFlight = inFlight?.takeIf { it.endsWith(app.packageName) },
                        launching = inFlight == "运行 ${app.packageName}",
                        stopping = inFlight == "停止 ${app.packageName}",
                        screenshot = screenshots[app.packageName],
                        inspection = inspections[app.packageName],
                        onOpenScreenshot = { path -> viewingShot = path },
                        onScreenshot = {
                            screenshots[app.packageName] = ScreenshotState.Loading
                            scope.launch {
                                screenshots[app.packageName] =
                                    repository.screenshot(app.packageName).fold(
                                        onSuccess = { ScreenshotState.Ready(it) },
                                        onFailure = { ScreenshotState.Failed(it.message ?: "截图失败") },
                                    )
                            }
                        },
                        onLaunch = {
                            run("运行 ${app.packageName}", affectsList = true) {
                                repository.launch(app.packageName).fold(
                                    onSuccess = { "已启动 ${app.packageName}" },
                                    onFailure = { throw it },
                                )
                            }
                        },
                        onStop = {
                            run("停止 ${app.packageName}", affectsList = true) {
                                repository.stop(app.packageName).fold(
                                    onSuccess = { "已停止 ${app.packageName}" },
                                    onFailure = { throw it },
                                )
                            }
                        },
                        onClearData = {
                            run("清除数据 ${app.packageName}") {
                                repository.clearData(app.packageName).fold(
                                    onSuccess = { "已清除 ${app.packageName} 的沙箱数据" },
                                    onFailure = { throw it },
                                )
                            }
                        },
                        onUninstall = {
                            run("卸载 ${app.packageName}", affectsList = true) {
                                repository.uninstall(app.packageName).fold(
                                    onSuccess = { "已从沙箱卸载 ${app.packageName}" },
                                    onFailure = { throw it },
                                )
                            }
                        },
                        onInspect = {
                            /* 再点一次**收起**，而不是重跑一遍。 */
                            if (inspections.containsKey(app.packageName)) {
                                inspections.remove(app.packageName)
                                livePackages.remove(app.packageName)
                            } else {
                                livePackages[app.packageName] = Unit
                                run("进程 / SO 基址") {
                                    inspections[app.packageName] =
                                        repository.inspect(app.packageName).getOrThrow()
                                    // 返回 null：这份实况已经显示在卡片里的详情块中
                                    null
                                }
                            }
                        },
                        onFrida = {
                            /* 「Frida 尚未安装」改成**直接装**，而不是让用户去别处点。 */
                            if (frida?.installed == false) {
                                run("安装 Frida") {
                                    repository.installFrida().fold(
                                        onSuccess = {
                                            frida = it
                                            "Frida Gadget ${it.version} 已安装"
                                        },
                                        onFailure = { throw it },
                                    )
                                }
                            } else {
                                run("Frida") {
                                    "Frida Gadget ${frida?.version ?: ""} 已就绪；" +
                                        "由 Agent 使用：Debug scope=sandbox " +
                                        "action=frida_load / frida_command / frida_events"
                                }
                            }
                        },
                    )
                }
            }
        }

        notice?.let {
            Spacer(Modifier.height(8.dp))
            NoticeCard(it)
        }

        Spacer(Modifier.height(24.dp))
    }

    // 全屏看图。挂在页面级（而不是卡片里）：它是整屏浮层，且同一时刻只可能看一张。
    SandboxScreenshotViewer(
        path = viewingShot,
        onDismiss = { viewingShot = null },
    )
}

// ─────────────────────────────── 状态 ───────────────────────────────

private data class Notice(val text: String, val isError: Boolean)

/** 运行状态轮询间隔。 */
private const val POLL_INTERVAL_MS = 2_000L

/** 展开的进程面板刷新间隔。 */
private const val INSPECT_INTERVAL_MS = 3_000L

/** 一张截图的三种状态。用 sealed 而不是「路径 + loading + error」三个字段。 */
private sealed interface ScreenshotState {
    data object Loading : ScreenshotState
    data class Ready(val path: String) : ScreenshotState
    data class Failed(val message: String) : ScreenshotState
}

/** 引擎状态里"不可用"的判定。 */
private fun isUnavailable(status: String): Boolean =
    com.zhizhu.zhicode.sandbox.SandboxHostClient.isUnsupportedHere() ||
        status.contains("异常") || status.contains("不可用") || status.contains("失败") ||
        status.contains("超时") || status.contains("尚未") || status.contains("没有回话")

// ─────────────────────────────── 组件 ───────────────────────────────

@Composable
private fun EngineStatusCard(
    status: String,
    appCount: Int,
    runningCount: Int,
    onDiagnose: () -> Unit,
) {
    val unsupported = com.zhizhu.zhicode.sandbox.SandboxHostClient.isUnsupportedHere()
    val failed = !unsupported && isUnavailable(status)
    val ready = !failed && !unsupported && status.contains("就绪")

    /* 三档：绿（能用）/ 红（不能用）/ 中性（还没起来，或环境结构性不支持）。 */
    val container = when {
        ready -> ZhiColors.greenContainer()
        failed -> ZhiColors.redContainer()
        else -> MiuixTheme.colorScheme.surfaceContainerHigh
    }
    // 卡片上的字：绿/红档用同族的强调色（深色下是亮绿/亮红，压在深色容器上清晰），
    // 中性档用常规 onSurface。
    val onContainer = when {
        ready -> ZhiColors.green()
        failed -> ZhiColors.red()
        else -> MiuixTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        cornerRadius = 18.dp,
        colors = CardDefaults.defaultColors(color = container, contentColor = onContainer),
        // 按压反馈用 Tilt（整卡倾斜），不是默认的 None。
        // `showIndication = false`：倾斜本身就是反馈，再叠一层涟漪两个动效会打架。
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = false,
        onClick = onDiagnose,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val textColor = MiuixTheme.colorScheme.onSurface
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = when {
                        unsupported -> "当前环境不支持"
                        failed -> "引擎不可用"
                        ready -> "引擎就绪"
                        else -> "引擎初始化中"
                    },
                    style = MiuixTheme.textStyles.title3,
                    color = textColor,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        unsupported -> "本应用自己就装在一个沙箱里，内层引擎拿不到独立进程"
                        failed -> "点击查看诊断快照"
                        ready && appCount == 0 -> "导入一个 APK，或让 Agent 调用 Sandbox 工具"
                        ready -> "共 $appCount 个应用，$runningCount 个正在运行"
                        else -> "首次启动需要几秒初始化虚拟运行时"
                    },
                    style = MiuixTheme.textStyles.body2,
                    color = textColor.copy(alpha = 0.80f),
                )

                /* 状态原文 —— 只在**引擎还没就绪**时出现。 */
                if (!ready) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = status,
                        style = MiuixTheme.textStyles.footnote2,
                        color = textColor.copy(alpha = 0.60f),
                    )
                }

                /* 左下角的归属标签 —— 位置与参考图那个 `Shizuku` 标签一致。 */
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "BlackBox · 用户 0 · $appCount 个应用",
                    style = MiuixTheme.textStyles.footnote2,
                    color = textColor.copy(alpha = 0.68f),
                )
            }

            Icon(
                painter = when {
                    failed -> ZhiIcons.failed
                    ready -> ZhiIcons.done
                    else -> ZhiIcons.sandbox
                },
                contentDescription = null,
                tint = when {
                    failed -> ZhiColors.red()
                    ready -> ZhiColors.green()
                    else -> MiuixTheme.colorScheme.onSurface
                }.copy(alpha = 0.30f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(104.dp)
                    .offset(x = 22.dp, y = 22.dp),
            )
        }
    }
}

@Composable
private fun PermissionWarningCard(onGrant: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        cornerRadius = 18.dp,
        colors = CardDefaults.defaultColors(
            color = ZhiColors.redContainer(),
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
        // 与 [EngineStatusCard] 同一套按压反馈：整卡 Tilt，不叠涟漪。
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = false,
        onClick = onGrant,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = ZhiIcons.failed,
                contentDescription = null,
                tint = ZhiColors.red(),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "悬浮条盖不住对话框",
                    style = MiuixTheme.textStyles.body1,
                    color = ZhiColors.red(),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "缺少「显示在其他应用上层」。点击去授权，返回后自动生效。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsBody(
    text: String,
    busy: Boolean,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onCopy,
                modifier = Modifier.weight(1f),
                cornerRadius = 12.dp,
                insideMargin = PaddingValues(vertical = 8.dp),
            ) { Text("复制全部", style = MiuixTheme.textStyles.footnote1) }
            Button(
                onClick = onRefresh,
                modifier = Modifier.weight(1f),
                enabled = !busy,
                cornerRadius = 12.dp,
                insideMargin = PaddingValues(vertical = 8.dp),
            ) { Text("刷新", style = MiuixTheme.textStyles.footnote1) }
            Button(
                onClick = onClear,
                modifier = Modifier.weight(1f),
                enabled = !busy,
                cornerRadius = 12.dp,
                insideMargin = PaddingValues(vertical = 8.dp),
                // 危险档只覆写底色/文字色，保持与默认档同一族 —— 见 ActionTile 的说明。
                colors = ButtonDefaults.buttonColors(
                    color = scheme.errorContainer,
                    contentColor = scheme.onErrorContainer,
                ),
            ) { Text("清空", style = MiuixTheme.textStyles.footnote1) }
        }

        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.surfaceContainerHigh),
        ) {
            SelectionContainer {
                Text(
                    text = text,
                    style = MiuixTheme.textStyles.footnote2,
                    fontFamily = FontFamily.Monospace,
                    color = scheme.onSurface,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            }
        }
    }
}

/** 开关行：标题 + 说明 + 右侧开关。 */
@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MiuixTheme.textStyles.body1)
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** 空态。区分"还没读完"与"确实没有应用" —— 两者的下一步完全不同。 */
@Composable
private fun EmptyAppsCard(loaded: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        cornerRadius = 16.dp,
    ) {
        Text(
            text = if (loaded) {
                "还没有应用。导入一个 APK，或让 Agent 调用 Sandbox 工具安装。"
            } else {
                "正在读取沙箱…"
            },
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** 单个沙箱应用卡。 */
@Composable
private fun SandboxAppCard(
    app: SandboxApp,
    /** 这个包自己的在飞动作名（null = 这个包空闲）。 */
    packageInFlight: String?,
    launching: Boolean,
    stopping: Boolean,
    screenshot: ScreenshotState?,
    inspection: SandboxInspection?,
    onOpenScreenshot: (String) -> Unit,
    onScreenshot: () -> Unit,
    onLaunch: () -> Unit,
    onStop: () -> Unit,
    onClearData: () -> Unit,
    onUninstall: () -> Unit,
    onInspect: () -> Unit,
    onFrida: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    // 这个包自己有没有动作在飞 —— 与全局无关，别的包在动不该影响这一张卡。
    val busy = packageInFlight != null
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        cornerRadius = 16.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.packageName,
                        style = MiuixTheme.textStyles.body1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    // 运行状态用 AnimatedContent 就地切换：文字直接换会在轮询
                    // （每 2 秒）时"跳"一下，看起来像列表在闪。
                    AnimatedContent(
                        targetState = app.running,
                        transitionSpec = {
                            fadeIn(ZhiMotion.fadeInSpec) togetherWith fadeOut(ZhiMotion.fadeOutSpec)
                        },
                        label = "sandbox-app-running",
                    ) { running ->
                        Text(
                            text = if (running) "运行中 · 已安装到沙箱" else "已安装到沙箱",
                            style = MiuixTheme.textStyles.footnote1,
                            color = if (running) ZhiColors.green() else scheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            // 截图预览。只有用户主动点过才出现，不自动抓 ——
            // 一次截图要走一条广播 + PixelCopy，列表一滚十几张卡会把 guest 拖死。
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onScreenshot,
                enabled = !busy,
                cornerRadius = 12.dp,
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(ZhiIcons.image, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when (screenshot) {
                        null -> "截取画面"
                        ScreenshotState.Loading -> "正在截取…"
                        is ScreenshotState.Ready -> "重新截取"
                        is ScreenshotState.Failed -> "截图失败，重试"
                    },
                    style = MiuixTheme.textStyles.footnote1,
                )
            }

            when (screenshot) {
                is ScreenshotState.Ready -> {
                    Spacer(Modifier.height(6.dp))
                    ScreenshotPreview(
                        path = screenshot.path,
                        onOpen = { onOpenScreenshot(screenshot.path) },
                    )
                }
                is ScreenshotState.Failed -> {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = screenshot.message,
                        style = MiuixTheme.textStyles.footnote2,
                        color = scheme.error,
                    )
                }
                else -> Unit
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                /* 每颗磁贴用的都是**动作自己的**字形，而不是就近借一个通用图标。 */
                ActionTile("运行", ZhiIcons.play, Modifier.weight(1f), !busy, progress = launching, onClick = onLaunch)
                ActionTile("停止", ZhiIcons.forceStop, Modifier.weight(1f), !busy, progress = stopping, onClick = onStop)
                ActionTile("清数据", ZhiIcons.clearData, Modifier.weight(1f), !busy, onClick = onClearData)
                ActionTile("卸载", ZhiIcons.uninstall, Modifier.weight(1f), !busy, danger = true, onClick = onUninstall)
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile(
                    // 文案跟着状态变：展开后写「收起」，否则用户不知道再点一次会发生什么。
                    label = if (inspection == null) "进程 / SO 基址" else "收起进程信息",
                    // 箭头方向也要跟着变：收起态配"往下展开"、展开态配"往上收"。
                    // 两者都写 `collapse`（向下）的话，写着"收起"却指下面，方向是反的。
                    icon = if (inspection == null) ZhiIcons.memory else ZhiIcons.collapseUp,
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    onClick = onInspect,
                )
                ActionTile("Frida 动态调试", ZhiIcons.debug, Modifier.weight(1f), !busy, onClick = onFrida)
            }

            val shownInspection = rememberLastNonNull(inspection)
            AnimatedVisibility(
                visible = inspection != null,
                enter = expandVertically(
                    animationSpec = ZhiMotion.sizeSpec,
                    expandFrom = Alignment.Top,
                ) + fadeIn(ZhiMotion.fadeInSpec),
                exit = shrinkVertically(
                    animationSpec = ZhiMotion.sizeSpec,
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(ZhiMotion.fadeOutSpec),
            ) {
                shownInspection?.let {
                    ProcessPanel(
                        inspection = it,
                        // 有 Live 标记：这一块每两秒自己刷新一次，用户要能看出来。
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessPanel(
    inspection: SandboxInspection,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
        colors = CardDefaults.defaultColors(
            color = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (inspection.running) "实时进程" else "当前没有进程",
                    style = MiuixTheme.textStyles.footnote1,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                // "实时"这个标记是必须的：没有它，用户会以为这是一张静态快照。
                if (inspection.running) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ZhiColors.green()),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "实时",
                        style = MiuixTheme.textStyles.footnote2,
                        color = ZhiColors.green(),
                    )
                }
            }

            if (!inspection.running) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "点「运行」启动它之后，这里会显示 pid 与模块基址。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = scheme.onSurfaceVariantSummary,
                )
                return@Column
            }

            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Column {
                    inspection.processes.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = p.name.ifEmpty { inspection.packageName },
                                style = MiuixTheme.textStyles.footnote2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "pid ${p.pid}",
                                style = MiuixTheme.textStyles.footnote2,
                                fontFamily = FontFamily.Monospace,
                                color = scheme.onSurfaceVariantSummary,
                            )
                        }
                        Text(
                            text = "uid ${p.uid} · ${p.modules.size} 个模块",
                            style = MiuixTheme.textStyles.footnote2,
                            color = scheme.onSurfaceVariantSummary,
                        )
                        p.modules.forEach { m ->
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = m.shortName,
                                    style = MiuixTheme.textStyles.footnote2,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = m.base,
                                    style = MiuixTheme.textStyles.footnote2,
                                    fontFamily = FontFamily.Monospace,
                                    color = scheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(app: SandboxApp) {
    val scheme = MiuixTheme.colorScheme
    val path = app.iconPath
    val bitmap = remember(path) {
        if (path == null) {
            null
        } else {
            runCatching {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0) return@runCatching null
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 96) sample *= 2
                android.graphics.BitmapFactory.decodeFile(
                    path,
                    android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
                )
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(scheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                painter = remember(bitmap) { BitmapPainter(bitmap.asImageBitmap()) },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)),
            )
        } else {
            Text(
                text = app.packageName.firstOrNull()?.uppercase() ?: "?",
                style = MiuixTheme.textStyles.body1,
                color = scheme.onSecondaryContainer,
            )
        }
    }
}

/** 截图预览。 */
@Composable
private fun ScreenshotPreview(path: String, onOpen: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    val file = remember(path) { File(path) }
    if (!file.isFile) {
        Text(
            text = "截图文件已过期",
            style = MiuixTheme.textStyles.footnote2,
            color = scheme.onSurfaceVariantSummary,
        )
        return
    }

    /* 先 inJustDecodeBounds 量尺寸，再按 720px 的上限折算 inSampleSize。 */
    val bitmap = remember(path) {
        runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 720) sample *= 2
            android.graphics.BitmapFactory.decodeFile(
                path,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }.getOrNull()
    }

    if (bitmap == null) {
        Text(
            text = "截图无法解码",
            style = MiuixTheme.textStyles.footnote2,
            color = scheme.error,
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            ),
    ) {
        Image(
            painter = remember(bitmap) { BitmapPainter(bitmap.asImageBitmap()) },
            contentDescription = "沙箱截图，点击放大",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
    }
}

@Composable
private fun SandboxScreenshotViewer(path: String?, onDismiss: () -> Unit) {
    val shown = rememberLastNonNull(path)
    OverlayDialog(
        show = path != null,
        onDismissRequest = onDismiss,
        // 全屏：外边距 / 内边距 / 圆角全清零，背景纯黑。maxWidth 也要放开 ——
        // 手机上宽度本来就被屏幕夹住，平板/分屏下不放开就还是一张小卡。
        maxWidth = 4096.dp,
        outsideMargin = androidx.compose.ui.unit.DpSize(0.dp, 0.dp),
        insideMargin = androidx.compose.ui.unit.DpSize(0.dp, 0.dp),
        cornerRadius = 0.dp,
        backgroundColor = Color.Black,
        enableWindowDim = false,
        // 全屏看图时内容顶到状态栏下面才叫全屏；× 按钮自己避让即可。
        defaultWindowInsetsPadding = false,
    ) {
        val target = shown ?: return@OverlayDialog
        val bitmap = remember(target) {
            runCatching {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(target, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 2048) sample *= 2
                android.graphics.BitmapFactory.decodeFile(
                    target,
                    android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
                )
            }.getOrNull()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                // 点任意处关闭。
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            if (bitmap != null) {
                Image(
                    painter = remember(bitmap) { BitmapPainter(bitmap.asImageBitmap()) },
                    contentDescription = "沙箱截图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = "截图文件已过期或无法解码",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            ZhiFilledIconButton(
                icon = ZhiIcons.close,
                description = "关闭截图",
                onClick = onDismiss,
                containerColor = Color.White.copy(alpha = 0.14f),
                contentColor = Color.White,
                iconSize = 16.dp,
                size = 40.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 12.dp),
            )
        }
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    danger: Boolean = false,
    progress: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        enabled = enabled,
        cornerRadius = 12.dp,
        colors = if (danger) {
            ButtonDefaults.buttonColors(
                color = scheme.errorContainer,
                contentColor = scheme.onErrorContainer,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
        insideMargin = PaddingValues(horizontal = 4.dp),
    ) {
        val tint = if (danger) scheme.onErrorContainer else Color.Unspecified
        Icon(
            if (progress) ZhiIcons.timeout else icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = tint,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (progress) "…" else label,
            style = MiuixTheme.textStyles.footnote1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NoticeCard(notice: Notice) {
    val scheme = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        cornerRadius = 12.dp,
        colors = CardDefaults.defaultColors(
            color = if (notice.isError) scheme.errorContainer else scheme.secondaryContainer,
            contentColor = if (notice.isError) scheme.onErrorContainer else scheme.onSecondaryContainer,
        ),
    ) {
        AnimatedContent(
            targetState = notice,
            transitionSpec = {
                fadeIn(ZhiMotion.fadeInSpec) togetherWith fadeOut(ZhiMotion.fadeOutSpec)
            },
            label = "sandbox-notice",
        ) { current ->
            Text(
                text = current.text,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
