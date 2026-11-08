package com.zhizhu.zhicode.compose.ui.pages

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

/**
 * 「沙箱」管理页。
 *
 * ## 这一页刻意不碰任何 BlackBox 的类
 *
 * BlackBox 只在 `:iqsandbox` / `:black` / `:pN` 进程里加载
 * （判定见 `SandboxProcessRole.shouldAttachBlackBox`）。主界面进程一个都不碰 ——
 * 这是当初把沙盒移出主进程的全部意义。
 *
 * 所以每个动作都是一次 IPC，统一收在 [SandboxRepository] 里。
 * 在这里 `import top.niunaijun.blackbox.*` 会把那条边界毁掉，
 * 而且**不会编译失败** —— 症状只是主界面偶尔卡一下、启动慢几十毫秒。
 *
 * ## 按钮颜色为什么要用 ButtonDefaults 而不是自己挑容器色
 *
 * 第二版把磁贴写成 `secondaryContainer` 底 + `onSecondaryContainer` 字。
 * 那两个值在 Miuix 里是给**列表容器**的（深色下底 `#434343`、字 `#7C7C7C`），
 * 对比度只有 1.8:1 —— 于是整屏按钮看起来全是"灰的、像禁用了一样"。
 *
 * Miuix 的按钮有自己的取值：`ButtonDefaults.buttonColors()` 用的是
 * `secondaryVariant` / `onSecondaryVariant`（深色下 `#434343` / `#D9D9D9`）。
 * 所以**默认档就别传 colors**，要用主色档传 `buttonColorsPrimary()`，
 * 要红色就只覆写 color/contentColor 并保持与默认档同一族。见下面 ActionTile。
 *
 * ## 为什么不再有一个全局 `busy`
 *
 * 第一版每个动作都先把 `busy` 置真，而它被传给**每一个**按钮的 `enabled`：
 * 点一下「运行」时整页按钮同时变成禁用色、底部提示条被清空、紧接着整个列表
 * 被换掉 —— 三件事叠在一起就是「每次点一个按钮，整个 UI 都闪一下」。
 *
 * 现在改成 [inFlight]：只记住**是哪一个**动作在飞，只有那一个显示进度。
 */
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

    /**
     * 执行一个动作。
     *
     * @param affectsList 会不会改变「装了哪些应用 / 哪个在跑」。**只有这类动作
     *   才重读列表** —— 刷新、诊断、装 Frida 都不会改变它，跟着重读只会让
     *   卡片重建、列表跳一下。
     */
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

    /*
     * 实时刷新「哪个应用在跑」。
     *
     * ## 为什么是 2 秒
     *
     * 这一屏要看的是"我点了运行，它到底起来了没有"。2 秒足够覆盖用户从 guest
     * 切回来、或等着看进程变化的场景，又不至于把引擎问爆（每次轮询都是一次
     * Binder 往返 + 一次进程列表查询）。
     *
     * ⚠️ **只更新已经在列表里的应用的 `running` 位，不重读整个列表**。
     * 整体重读会让每张卡片重建、图标重新解码 —— 那正是"闪一下"。
     * 图标只在装/卸时才会变，那种时刻由动作自己触发完整重读。
     */
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(POLL_INTERVAL_MS)
            val snap = repository.pollRuntime().getOrNull() ?: continue
            status = snap.statusText
            apps = apps.map { it.copy(running = it.packageName in snap.runningPackages) }
        }
    }

    /*
     * 展开着的那张卡的实况轮询。
     *
     * 与上面那条的区别：这一条**贵得多**（每个 pid 都要走一次 guest 侧广播去读
     * `/proc/self/maps`），所以只在用户主动展开时才跑，收起即停 ——
     * 靠 `livePackages` 这个 key：展开一个包起一条协程，收起自动取消。
     */
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

    /*
     * 系统文件选择器。
     *
     * `application/vnd.android.package-archive` 是 APK 的规范 MIME，但各家文件管理器
     * 对 `.apk` 的上报并不统一（有的报 `application/octet-stream`）。只写规范值时，
     * 在那些管理器里 APK 会是**灰的、点不中**。
     */
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
        /*
         * ⚠️ 顶栏**关掉模糊采样**，这是"点了按钮闪一下"的根治处。
         *
         * `SettingsSubPage` 的顶栏走 `PageBlurBar`，它采样的是**内容层**
         * （`Modifier.layerBackdrop(backdrop)` 把那块 Box 录进一张纹理）。
         * 内容一旦变脏，纹理就要重录 —— 而这一页恰好是全工程唯一一页
         * "内容在持续自我刷新"的：
         *   · 每次点按钮都改 `inFlight` 与 `notice`；
         *   · 还有一条每 2 秒跑一次的 `pollRuntime()` 在换 `apps`。
         *
         * 于是几乎每次交互都赶上一次纹理重录，重录那一帧内容层是空的 ——
         * 看起来就是"点了按钮，整块被删掉了"。关掉之后这一页走的是
         * `SettingsSubPage` 里 `backdrop == null` 那条分支：不录纹理，
         * 顶栏用不透明 `surface` 垫底，闪屏在结构上不再可能发生。
         *
         * 其余二级页（技能 / MCP / API / 角色卡 / 记忆）没有轮询、也不在点击时
         * 整页重绘，保持模糊不变 —— 所以只在这一页传 false。
         */
        blurTopBar = false,
        // 诊断走 WindowDialog（独立窗口）。理由有二：
        //  · 日志可能很长，需要一个真正能占满中间区域、自己滚动的容器；
        //  · 它必须**盖住**页面里的一切（包括顶栏），而 overlay 系的弹层要挂在
        //    某个 Scaffold 的宿主上，二级页那一层没有 —— 见 SettingsSubPage 的说明。
        overlay = {
            val shown = rememberLastNonNull(snapshot)
            WindowDialog(
                title = "诊断",
                show = snapshot != null,
                onDismissRequest = { snapshot = null },
                maxWidth = ZhiDialogWidth.Wide,
                outsideMargin = DialogWideOutsideMargin,
                insideMargin = DialogWideInsideMargin,
                /*
                 * ⚠️ `largeScreen = true` 是**居中**的开关，不是"大屏才用"。
                 *
                 * 不传时 Miuix 按窗口尺寸自动判：手机竖屏被判成"非大屏"，
                 * 于是走**底部贴边 + 从下滑入**那一档 —— 也就是第二张截图里
                 * 那个"从底下冒出来、顶到屏幕边缘"的怪样子。
                 * 传 true 走官方中央形态：0.8→1 缩放 + 淡入，位置居中。
                 *
                 * 本工程其它需要中央形态的弹窗（文件面板、终端对话框）都这么传，
                 * 见 `ui/panes/FileDialogs.kt` 与 `TerminalDialogs.kt`。
                 */
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

        /*
         * 悬浮条权限警告 —— **只在未授权时出现**。
         *
         * ## 为什么不能砍掉这一块
         *
         * 没有「显示在其他应用上层」时，悬浮条退回 Activity 内浮层，**盖不住
         * guest 自己的对话框**（那是独立 Window）。用户看到的只是"点了确认框
         * 之后返回按钮不见了"，而这一屏是唯一能说明原因的地方。所以它不是
         * 可选装饰，是那条链路唯一的说明入口。
         *
         * ## 为什么改成"只在有问题时出现"
         *
         * 之前它是一张四行的「运行状态」卡（引擎 / 沙箱应用 / Frida / 悬浮条），
         * 而那四行里**三行是在重复上面那张绿卡已经说过的事**：
         * 绿卡副标题写「共 1 个应用，1 个正在运行」、标签又写「1 个应用」，
         * 这张卡再写第三遍。参考实现（InstallerX 首页）没有这种"状态行卡" ——
         * 它的第二排是两张**数字**卡，第三排是设备信息卡。
         *
         * 授权之后就整块消失，正常状态下不再占任何位置。
         */
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
                        /*
                         * 只把**这个包自己**的动作名传下去，不传全局的
                         * `inFlight != null`。
                         *
                         * 传全局值会让点一个包时**所有**包的按钮一起变灰 ——
                         * 那就是"点一下整个 UI 闪一下"的主要来源。
                         */
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
                            /*
                             * 再点一次**收起**，而不是重跑一遍。
                             *
                             * 收起同时把这一包从轮询集合里摘掉（`livePackages`）——
                             * 实况刷新跟着展开状态走，收起之后不该再占引擎的 IPC。
                             */
                            if (inspections.containsKey(app.packageName)) {
                                inspections.remove(app.packageName)
                                livePackages.remove(app.packageName)
                            } else {
                                livePackages[app.packageName] = Unit
                                run("进程 / SO 基址") {
                                    inspections[app.packageName] =
                                        repository.inspect(app.packageName).getOrThrow()
                                    // 返回 null：这份实况已经显示在卡片里的详情块中，
                                    // 再让 run() 把它当"操作结果"灌进底部提示条，
                                    // 屏幕上就会出现同一段信息两份。
                                    null
                                }
                            }
                        },
                        onFrida = {
                            /*
                             * 「Frida 尚未安装」改成**直接装**，而不是让用户去别处点。
                             *
                             * 之前它返回一句"先在「运行状态」里点安装"，而那张
                             * 状态卡已经删掉了 —— 那句话会把用户指到一个不存在的地方。
                             * 而且装 Frida 是这一屏唯一需要它的动作，入口放在这里最直接。
                             */
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

/**
 * 运行状态轮询间隔。
 *
 * 这一屏要看的是"我点了运行，它到底起来了没有"。2 秒足够覆盖用户从 guest
 * 切回来、或等着看进程变化的场景，又不至于把引擎问爆（每次轮询都是一次
 * Binder 往返 + 一次进程列表查询）。
 */
private const val POLL_INTERVAL_MS = 2_000L

/**
 * 展开的进程面板刷新间隔。
 *
 * 比上面慢：这一条**贵得多** —— 每个 pid 都要走一次 guest 侧广播去读
 * `/proc/self/maps`（跨进程 + 解析整张映射表）。3 秒足够让用户看到进程起来了、
 * pid 变了、模块基址跟着变。
 */
private const val INSPECT_INTERVAL_MS = 3_000L

/** 一张截图的三种状态。用 sealed 而不是「路径 + loading + error」三个字段。 */
private sealed interface ScreenshotState {
    data object Loading : ScreenshotState
    data class Ready(val path: String) : ScreenshotState
    data class Failed(val message: String) : ScreenshotState
}

/**
 * 引擎状态里"不可用"的判定。
 *
 * 单独一个函数是因为它被**三处**用到（卡片配色、状态行、主行动按钮），
 * 而原先那串判断是内联写的 —— 改一处忘一处，卡片就会静默退回"就绪"的样子。
 */
private fun isUnavailable(status: String): Boolean =
    com.zhizhu.zhicode.sandbox.SandboxHostClient.isUnsupportedHere() ||
        status.contains("异常") || status.contains("不可用") || status.contains("失败") ||
        status.contains("超时") || status.contains("尚未") || status.contains("没有回话")

// ─────────────────────────────── 组件 ───────────────────────────────

/**
 * 引擎状态卡。
 *
 * ## 形态与配色都取自 InstallerX Revived 首页那张「正在作为默认安装器工作」
 *
 * 它那张卡的关键不是版式，而是**用颜色直接回答"现在行不行"**：
 * 能用是绿色、不能用是红色。绿/红不是强调色，是结论本身。
 *
 * 所以这里不再用 Miuix 的通用容器色（`secondaryContainer` / `errorContainer`）——
 * 那两档在深色下都是灰的（`#434343` / `#2E0603`），"引擎就绪"和"引擎不可用"
 * 看起来几乎一样。改用 [ZhiColors.greenContainer] / [ZhiColors.redContainer]：
 * 它们是本工程为"成功/失败"专门调过对比度的一对，深色下分别是深绿与深红。
 *
 * ## 四条从它那里学来的规矩
 *
 * 1. **大标题就是结论**。用户不需要读副标题才知道"能不能用"；
 * 2. **副标题给下一步**，而不是重复状态；
 * 3. **整卡可点** —— 状态不可用时点进去就该看到诊断快照；
 * 4. **右下角一个低透明度的状态图标当水位**，纯装饰，`contentDescription = null`。
 *
 * ## 颜色不是唯一载体
 *
 * 绿色卡上写「引擎就绪」、红色卡上写「引擎不可用」，**文案本身**也说明状态。
 * 只靠颜色的话色弱用户看不出区别 —— 而那正是最需要看懂这一行的人。
 */
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

    /*
     * 三档：绿（能用）/ 红（不能用）/ 中性（还没起来，或环境结构性不支持）。
     *
     * 中性档用 `surfaceContainerHigh` 而不是某一族的容器色 —— "还在初始化"
     * 既不是成功也不是失败，染成红绿都会误导。
     */
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
            /*
             * ⚠️ 文字用**近白**（`onSurface`），不用绿/红强调色。
             *
             * 这是与参考实现对比后改的。那版把标题、副标题、底部标签**全染成绿色**，
             * 绿字压深绿底 —— 整张卡糊成一片"绿色调"，对比度很低。
             * InstallerX 那张绿卡的做法是：**底色承担颜色语义，文字保持白**。
             * 绿/红只在容器的底色上，读起来一眼是"绿=好、红=坏"，而字始终清楚。
             */
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

                /*
                 * 状态原文 —— 只在**引擎还没就绪**时出现。
                 *
                 * 就绪之后它只是一句"引擎就绪 · 用户 0"，没有任何信息量，
                 * 却把卡片撑高一行（参考实现那张绿卡只有标题 + 副标题两行）。
                 */
                if (!ready) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = status,
                        style = MiuixTheme.textStyles.footnote2,
                        color = textColor.copy(alpha = 0.60f),
                    )
                }

                /*
                 * 左下角的归属标签 —— 位置与参考图那个 `Shizuku` 标签一致。
                 *
                 * 这里**不再放按钮**。参考实现那张卡整张可点（点进去就是详情），
                 * 卡内没有第二个点击目标；多一颗按钮既平白撑高一行，
                 * 又让"点卡片"和"点按钮"变成两个语义重叠的入口。
                 * 主行动（导入 / 运行 / 看诊断）由下面那排常驻按钮承担。
                 */
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "BlackBox · 用户 0 · $appCount 个应用",
                    style = MiuixTheme.textStyles.footnote2,
                    color = textColor.copy(alpha = 0.68f),
                )
            }

            /*
             * 装饰性水位图标：右下角，裁掉一部分。
             *
             * ⚠️ alpha 从 0.16 提到 **0.30**，并换成一个更"实"的图形。
             * 0.16 时它几乎看不见，像一块脏印子 —— 而参考实现里那个大对勾
             * 是**卡片的主要视觉重量**，一眼看过去就知道是成功还是失败。
             *
             * 尺寸也从 84dp 提到 104dp：要让它真的被右下角裁掉一角，
             * 那是这种"水位图标"的标志性处理。
             */
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

/**
 * 悬浮条权限警告卡（**只在未授权时出现**）。
 *
 * ## 为什么必须存在
 *
 * 没有「显示在其他应用上层」时，悬浮条退回 Activity 内浮层，**盖不住 guest
 * 自己的对话框**（那是独立 Window）。用户看到的只是"点了确认框之后返回按钮
 * 不见了" —— 而这一屏是唯一能说明原因的地方，所以它不是可选装饰。
 *
 * ## 为什么是"只在有问题时出现"
 *
 * 原先这里是一张四行的「运行状态」卡（引擎 / 沙箱应用 / Frida / 悬浮条），
 * 而那四行里**三行在重复上面那张绿卡已经说过的事**：绿卡副标题写
 * 「共 1 个应用，1 个正在运行」、左下角标签又写「1 个应用」，这张卡再写第三遍。
 *
 * 参考实现（InstallerX Revived 首页）没有这种"状态行卡"：它的卡片分两类 ——
 * **结论卡**（绿/红，回答"现在行不行"）与**数据卡**（机型 / 系统 / 活动名，
 * 一屏只出现一次）。所以这里改成只在**真正需要用户动手**时出现一张警示卡，
 * 正常情况下整块不占位置。
 *
 * ## 配色沿用 InstallerX 的"结论卡"逻辑
 *
 * 红容器 + 红字标题（结论在标题里），而说明文字保持常规前景色 ——
 * 整卡都染红会让正文读不清（详见 [EngineStatusCard] 里那段说明）。
 */
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

/**
 * 诊断面板的内容。
 *
 * ## 为什么是等宽小字 + 可选中
 *
 * 里面是 `SandboxDebugLog.snapshot()` 的原文：事件流、BlackBox 自己的
 * `startup-stage.txt`、以及 guest 的 logcat。它是**给人看和给 Agent 看的日志**，
 * 不是排版过的说明文 —— 等宽字体才看得清 pid 与时间戳对齐。
 *
 * ## 为什么日志要限长
 *
 * 全量快照 42 万字符，交给 Compose 测量排版时一帧要处理几十万个字形 ——
 * 那正是"点开诊断很卡"的来源。仓库层已经按 6 万字符截过尾部，
 * 这里不再二次处理（截中间会让日志看起来"缺了一段"，反而更难读）。
 */
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
                /*
                 * 每颗磁贴用的都是**动作自己的**字形，而不是就近借一个通用图标。
                 *
                 * 第一版这里是 `send`（↑）/ `stop`（‖）/ `clear`（橡皮擦）/ `close`（×）——
                 * 四个全是别处的语义：↑ 是"发送消息"、‖ 是"中断当前回合"、
                 * 橡皮擦是"擦掉屏幕上的字"、× 是"关闭这一页"。而这里做的是
                 * 启动进程 / 杀掉进程 / 清数据 / 卸载，四件事一个都没表达对。
                 * 用户的原话是「图标风格不统一」，根因就是这个。
                 */
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

            /*
             * 进程 / 模块面板。
             *
             * ## ⚠️ 为什么内容要读 `rememberLastNonNull` 而不是直接读 `inspection`
             *
             * `AnimatedVisibility` 在**退场期间仍然会组合它的内容** —— 但那时的
             * `inspection` 已经是 null 了。直接写 `if (inspection != null) { … }`
             * 的话，整个退场过程里内容是一块**空盒子**：`shrinkVertically`
             * 忠实地把 0 高度缩到 0 高度，看起来就是"内容啪地消失、没有动画"。
             *
             * 这正是本工程其它地方（`ScreenshotPreview`、`ExportSheetOverlay`）
             * 都用了 `rememberLastNonNull` 的原因，也是它当初被抽出来的原因。
             *
             * ## 为什么要限高
             *
             * 一个 guest 进程的模块表通常几十行，全部铺开会把卡片撑到几屏高。
             */
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

/**
 * 进程 / 模块实况面板。
 *
 * ## 为什么不用一个大 `Text` 装「进程表 + 模块表」
 *
 * 第二版就是那样（把整段文本塞进一个等宽 `Text`）。问题是它**只在展开那一刻
 * 取一次**：用户看着"运行中"，而里面列的 pid 早就退出了 —— 那一屏显示的是
 * 一个已经过期的世界。现在这一块跟着 [SandboxInspection] 每两秒重画。
 *
 * ## 版式：进程一行、模块带基址
 *
 * 用一行一个进程、进程下面缩进列模块，而不是等宽文本对齐 ——
 * 手机上等宽文本一旦宽度不够就会折行，折行之后那些"对齐"反而更难读。
 * 结构化之后每行都很短，窄屏也不会折。
 */
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

/**
 * 应用图标：优先用引擎导出的真实图标，拿不到才回落成首字母方块。
 *
 * ## 为什么要回落而不是留空
 *
 * 图标解析要读 guest 的 APK，会失败（APK 被删、格式怪异、权限）。
 * 那时留一块空白比一个字母更糟 —— 字母至少还说明了"这是哪一个"。
 *
 * ## 为什么按 96px 采样解码
 *
 * 引擎导出的 PNG 最大 256px。这张卡上只显示 44dp（约 130px @3x），
 * 按原尺寸解码等于白占 4 倍内存；列表里十几张卡同时展开就是几 MB 堆。
 */
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

/**
 * 截图预览。按尺寸采样解码本地 PNG，不引入 Coil —— 这张图来自本应用私有目录。
 *
 * 点一下进全屏查看（见 [SandboxScreenshotViewer]）。预览本身只有 180dp 高、
 * `ContentScale.Fit`，竖屏截图在里面只有手指宽的一条 —— 光看它读不出任何界面细节。
 */
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

    /*
     * 先 inJustDecodeBounds 量尺寸，再按 720px 的上限折算 inSampleSize。
     *
     * 不做采样的话：一张 1080p 的 PixelCopy 截图是 2 MB 左右的 ARGB_8888 位图，
     * 而预览只显示 180dp 高。列表里几张卡同时展开就是好几 MB 堆。
     */
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

/**
 * 全屏看图。
 *
 * ## 为什么要「全屏 + 纯黑底 + Fit」
 *
 * 这里复刻的是 `ZhiImage.kt` 里 `ZhiImageViewer` 那套形态与它的两条教训：
 *
 * 1. **不能是普通弹窗**。Miuix 弹窗对内容高度不设上限，一张竖屏截图按比例
 *    会撑到六七百 dp，直接越过屏幕；而弹窗自带的圆角与内边距会把截图包进
 *    一张小卡片里，四周留一圈底色 —— 就是"预览做得很奇怪"。
 * 2. **点任意处关闭**。全屏查看时用户的第一反应是点一下退出去，
 *    让他去找右上角那个 × 是不必要的（× 仍然保留，两处都能关）。
 *
 * 解码上限给 2048：截图是 PixelCopy 出来的原尺寸位图，不设限在长截图上
 * 会一次吃掉几十 MB。
 */
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

/**
 * 一个动作磁贴。
 *
 * ## 颜色为什么**不**传次级容器色
 *
 * 上一版写成 `secondaryContainer` 底 + `onSecondaryContainer` 字。那两个值在
 * Miuix 里是给**列表容器**的：深色下底 `#434343`、字 `#7C7C7C`，对比度 1.8:1。
 * 结果整屏磁贴看起来像全部禁用 —— 用户的原话是"为什么按钮是灰色的"。
 *
 * 现在默认档**不传 colors**，用 `ButtonDefaults.buttonColors()`
 * （`secondaryVariant` / `onSecondaryVariant`，深色下 `#434343` / `#D9D9D9`，
 * 对比度 7.4:1）。危险档只覆写这两个值并保持同一族，
 * 这样"红色"与"灰色"是同一套层级里的两个档，而不是两套配色。
 *
 * ## 进度为什么不靠 `enabled = false`
 *
 * `enabled=false` 会让按钮整体换成禁用色 —— 而每个动作都是各自独立的，
 * 一个在飞不该让其它五个一起变灰（那正是"整页闪一下"的一部分）。
 * 所以进度由 `progress` 参数单独表达：图标换成计时器。
 */
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

/**
 * 底部提示条。
 *
 * ## 为什么用 AnimatedContent 而不是「有就画、没有就不画」
 *
 * 原先是 `notice?.let { … }`，而 `run()` 一开始就把 `notice` 置 null ——
 * 于是每次点按钮，底部这条先消失、执行完再出现，整页高度跟着跳两次。
 * 这就是"整体闪一下"里最明显的那一下。
 */
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
