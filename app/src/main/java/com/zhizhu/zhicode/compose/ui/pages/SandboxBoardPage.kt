package com.zhizhu.zhicode.compose.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.zhizhu.zhicode.compose.data.SandboxApp
import com.zhizhu.zhicode.compose.data.SandboxRepository
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsSubPage
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
 * ## 为什么所有按钮都走同一个 [run] 包装
 *
 * 每个按钮要做四件事：置"在飞"→ 执行 → 清"在飞" → 重读列表 + 报结果。
 * 各写一遍的话总有一个漏掉清标记，于是"那个按钮点一次就永远灰了"。
 *
 * ## 截图为什么按包名分槽位
 *
 * 一个包可能同时有多个在跑的进程，而截图请求只认"最前面的那个 guest Activity"。
 * 每张卡各自保存自己的截图路径与加载状态，避免 A 应用的 loading 把 B 应用的按钮
 * 也带进加载态。
 */
@Composable
internal fun SandboxBoardPage(
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SandboxRepository(context) }

    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("正在读取引擎状态…") }
    var hideRoot by remember { mutableStateOf(true) }
    var floatingLog by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<SandboxApp>>(emptyList()) }
    var notice by remember { mutableStateOf<Notice?>(null) }

    // 每包一张截图 + 它的加载/失败状态。用 Map 而不是塞进 SandboxApp：
    // 截图是**纯界面状态**，不属于引擎的事实，混进去会让"刷新列表"把截图清掉。
    val screenshots = remember { mutableStateMapOf<String, ScreenshotState>() }
    // 每包一次动作的输出（进程 / 模块），按需展开显示。
    val details = remember { mutableStateMapOf<String, String>() }

    suspend fun reloadApps() {
        repository.readApps().onSuccess { apps = it }
    }

    fun run(label: String, block: suspend () -> String?) {
        if (busy) return
        busy = true
        notice = null
        scope.launch {
            val outcome = runCatching { block() }
            outcome.exceptionOrNull()?.let { notice = Notice("$label 失败：${it.message ?: "未知原因"}", true) }
            outcome.getOrNull()?.let { notice = Notice(it, false) }
            // 所有动作都会改变"哪个应用还在跑"，所以一律重读列表 ——
            // 卡上的运行/停止可用性完全由它决定。
            runCatching { reloadApps() }
            busy = false
        }
    }

    suspend fun refreshAll() {
        val s = repository.readStatus()
        s.onSuccess {
            status = it.text
            hideRoot = it.hideRoot
            floatingLog = it.floatingLog
        }.onFailure {
            status = "引擎不可用：${it.message ?: "未知原因"}"
        }
        runCatching { reloadApps() }
        loaded = true
    }

    LaunchedEffect(Unit) { refreshAll() }

    /*
     * 系统文件选择器。
     *
     * ## MIME 为什么要给两个
     *
     * `application/vnd.android.package-archive` 是 APK 的规范 MIME，但各家文件管理器
     * 对 `.apk` 的上报并不统一（有的报 `application/octet-stream`）。只写规范值时，
     * 在那些管理器里 APK 会是**灰的、点不中** —— 用户会以为应用坏了。
     *
     * ## 为什么 launcher 写在页面里而不是 Activity 上
     *
     * 它只服务这一个按钮，而结果又要直接改本页状态（列表、提示条）。
     * 放 Activity 上就得把结果用事件回传进来，多一层；`rememberLauncherForActivityResult`
     * 本来就是组合作用域的工具，用在这里最合适。
     */
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        // null 是用户取消：不是错误，安静返回。
        if (uri != null) {
            run("导入 APK") {
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
        action = "诊断" to {
            run("诊断") {
                repository.readStatus().fold(
                    onSuccess = { "引擎状态：${it.text}" },
                    onFailure = { throw it },
                )
            }
        },
        actionEnabled = !busy,
    ) {
        // ── 状态卡 ──────────────────────────────────────────────────
        EngineStatusCard(
            status = status,
            appCount = apps.size,
            onDiagnose = {
                run("诊断") {
                    repository.readStatus().fold(
                        onSuccess = { "引擎状态：${it.text}" },
                        onFailure = { throw it },
                    )
                }
            },
        )

        // ── 两个全局开关 ────────────────────────────────────────────
        SettingsGroup(title = "运行环境") {
            SwitchRow(
                title = "隐藏 Root",
                summary = "Guest 看不到常见的 root 路径与管理包",
                checked = hideRoot,
                enabled = !busy,
                onCheckedChange = { next ->
                    val previous = hideRoot
                    hideRoot = next
                    run("隐藏 Root") {
                        repository.setHideRoot(next).fold(
                            onSuccess = { if (next) "已开启 Root 隐藏（全部 Guest 已停止）" else "已关闭 Root 隐藏（全部 Guest 已停止）" },
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
                summary = "在 Guest 界面显示可拖动的调试浮层",
                checked = floatingLog,
                enabled = !busy,
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
                modifier = Modifier
                    .weight(2f)
                    .height(48.dp),
                enabled = !busy,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Icon(ZhiIcons.add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("导入 APK")
            }
            Button(
                onClick = { run("刷新") { null } },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                enabled = !busy,
            ) {
                Icon(ZhiIcons.refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("刷新", maxLines = 1)
            }
        }

        // ── 应用列表 ────────────────────────────────────────────────
        SmallTitle("沙箱应用")

        if (apps.isEmpty()) {
            EmptyAppsCard(loaded = loaded)
        } else {
            apps.forEach { app ->
                SandboxAppCard(
                    app = app,
                    busy = busy,
                    screenshot = screenshots[app.packageName],
                    detail = details[app.packageName],
                    onScreenshot = {
                        screenshots[app.packageName] = ScreenshotState.Loading
                        scope.launch {
                            screenshots[app.packageName] = repository.screenshot(app.packageName).fold(
                                onSuccess = { ScreenshotState.Ready(it) },
                                onFailure = { ScreenshotState.Failed(it.message ?: "截图失败") },
                            )
                        }
                    },
                    onLaunch = {
                        run("运行 ${app.packageName}") {
                            repository.launch(app.packageName).fold(
                                onSuccess = { "已启动 ${app.packageName}" },
                                onFailure = { throw it },
                            )
                        }
                    },
                    onStop = {
                        run("停止 ${app.packageName}") {
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
                        run("卸载 ${app.packageName}") {
                            repository.uninstall(app.packageName).fold(
                                onSuccess = { "已从沙箱卸载 ${app.packageName}" },
                                onFailure = { throw it },
                            )
                        }
                    },
                    onInspect = {
                        run("进程 / SO 基址") {
                            val procs = repository.processes(app.packageName).getOrThrow()
                            val mods = runCatching { repository.modules(app.packageName).getOrThrow() }
                                .getOrElse { "模块基址不可用：${it.message}" }
                            val text = procs + "\n" + mods
                            details[app.packageName] = text
                            text
                        }
                    },
                    onFrida = {
                        run("Frida") {
                            "Frida 由 Agent 使用：Debug scope=sandbox action=frida_status / frida_load / frida_command"
                        }
                    },
                )
            }
        }

        notice?.let {
            Spacer(Modifier.height(8.dp))
            NoticeCard(it)
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────── 状态 ───────────────────────────────

private data class Notice(val text: String, val isError: Boolean)

/** 一张截图的三种状态。用 sealed 而不是「路径 + loading + error」三个字段。 */
private sealed interface ScreenshotState {
    data object Loading : ScreenshotState
    data class Ready(val path: String) : ScreenshotState
    data class Failed(val message: String) : ScreenshotState
}

// ─────────────────────────────── 组件 ───────────────────────────────

/**
 * 引擎状态卡。
 *
 * ## 形态取自 InstallerX Revived 的「未设置为默认安装器」那张
 *
 * 它比一行圆点强在四件事，正好是这一屏需要的：
 *
 * 1. **大标题就是结论**。用户不需要读副标题才知道"能不能用"；
 * 2. **副标题给下一步**（"点击查看详情" / "导入一个 APK…"），而不是重复状态；
 * 3. **整卡可点**。状态不可用时点进去就该看到诊断快照，
 *    而不是让人满屏找那个「诊断」按钮；
 * 4. **右下角一个低透明度的状态图标当水位**。它是**纯装饰**，
 *    因此 `contentDescription = null` —— 语义已经由标题承载了。
 *
 * ## 颜色不是唯一的语义载体
 *
 * 异常时底色走 `errorContainer`、标题走 `onErrorContainer`，但**文案本身**
 * 也写了"引擎不可用"。只靠颜色的话，色弱用户看不出区别 —— 而那正是最需要
 * 看懂这一行的人。
 */
@Composable
private fun EngineStatusCard(
    status: String,
    appCount: Int,
    onDiagnose: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    /*
     * 三种"不可用"要分开说，因为**下一步完全不同**：
     *
     * · unsupported —— 本应用自己就跑在沙箱里。引擎拿不到独立进程，
     *   这是结构性的，用户能做的是换到真机（或直接用 AndroidIntent 装包）。
     * · failed      —— 引擎应在跑但报错了（超时、异常）。该看诊断。
     * · ready / init —— 正常。
     *
     * 把 unsupported 也归进 failed 会把"换个环境"这条唯一有效的建议淹没在
     * 一句"点击查看详情"里。
     */
    val unsupported = com.zhizhu.zhicode.sandbox.SandboxHostClient.isUnsupportedHere()
    val failed = !unsupported && (status.contains("异常") || status.contains("不可用") ||
        status.contains("失败") || status.contains("超时") || status.contains("尚未") ||
        status.contains("没有回话"))
    val ready = status.contains("就绪")

    val container = when {
        unsupported -> scheme.surfaceContainerHigh
        failed -> scheme.errorContainer
        ready -> scheme.secondaryContainer
        else -> scheme.surfaceContainerHigh
    }
    val onContainer = when {
        unsupported -> scheme.onSurface
        failed -> scheme.onErrorContainer
        ready -> scheme.onSecondaryContainer
        else -> scheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        cornerRadius = 18.dp,
        colors = CardDefaults.defaultColors(color = container, contentColor = onContainer),
        onClick = onDiagnose,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = when {
                        unsupported -> "当前环境不支持"
                        failed -> "引擎不可用"
                        ready -> "引擎就绪"
                        else -> "引擎初始化中"
                    },
                    style = MiuixTheme.textStyles.title3,
                    color = onContainer,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        unsupported -> "本应用自己就装在一个沙箱里，内层引擎拿不到独立进程"
                        failed -> "点击查看详情"
                        else -> "导入一个 APK，或让 Agent 调用 Sandbox 工具"
                    },
                    style = MiuixTheme.textStyles.body2,
                    color = onContainer.copy(alpha = 0.72f),
                )

                // 状态原文单独一行、小字：它是**诊断信息**（含超时毫秒与异常类型），
                // 放进副标题会把"下一步做什么"挤掉。
                if (!ready) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = status,
                        style = MiuixTheme.textStyles.footnote2,
                        color = onContainer.copy(alpha = 0.55f),
                    )
                }

                // 左下角"归属标签"：说明这条状态是谁给的，与参考图那个 Shizuku 标签同一个位置。
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "BlackBox · 用户 0 · $appCount 个应用",
                    style = MiuixTheme.textStyles.footnote2,
                    color = onContainer.copy(alpha = 0.70f),
                )
            }

            // 装饰性水位图标：右下角，裁掉一部分，低透明度。
            Icon(
                painter = if (failed) ZhiIcons.failed else ZhiIcons.sandbox,
                contentDescription = null,
                tint = onContainer.copy(alpha = 0.16f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(84.dp)
                    .offset(x = 20.dp, y = 20.dp),
            )
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
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
    busy: Boolean,
    screenshot: ScreenshotState?,
    detail: String?,
    onScreenshot: () -> Unit,
    onLaunch: () -> Unit,
    onStop: () -> Unit,
    onClearData: () -> Unit,
    onUninstall: () -> Unit,
    onInspect: () -> Unit,
    onFrida: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        cornerRadius = 16.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 图标 + 包名 + 状态
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(scheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = app.packageName.firstOrNull()?.uppercase() ?: "?",
                        style = MiuixTheme.textStyles.body1,
                        color = scheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.packageName,
                        style = MiuixTheme.textStyles.body1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (app.running) "运行中 · 已安装到沙箱" else "已安装到沙箱",
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (app.running) HealthyGreen else scheme.onSurfaceVariantSummary,
                    )
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
                    ScreenshotPreview(path = screenshot.path)
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

            // 第一排：运行 / 停止 / 清数据 / 卸载
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile("运行", ZhiIcons.send, Modifier.weight(1f), !busy, onClick = onLaunch)
                ActionTile("停止", ZhiIcons.stop, Modifier.weight(1f), !busy, onClick = onStop)
                ActionTile("清数据", ZhiIcons.clear, Modifier.weight(1f), !busy, onClick = onClearData)
                ActionTile("卸载", ZhiIcons.close, Modifier.weight(1f), !busy, danger = true, onClick = onUninstall)
            }

            Spacer(Modifier.height(8.dp))

            // 第二排：进程 / SO 基址、Frida
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile("进程 / SO 基址", ZhiIcons.directory, Modifier.weight(1f), !busy, onClick = onInspect)
                ActionTile("Frida 动态调试", ZhiIcons.add, Modifier.weight(1f), !busy, onClick = onFrida)
            }

            if (!detail.isNullOrEmpty()) {
                Spacer(Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp,
                    colors = CardDefaults.defaultColors(
                        color = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                    ),
                ) {
                    Text(
                        text = detail,
                        style = MiuixTheme.textStyles.footnote2,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

/** 截图预览。按尺寸采样解码本地 PNG，不引入 Coil —— 这张图来自本应用私有目录。 */
@Composable
private fun ScreenshotPreview(path: String) {
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
     * 而预览只显示 180dp 高。列表里几张卡同时展开就是好几 MB 堆，
     * 而这个应用本来就在 largeHeap 边缘上跑。
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

    val painter = remember(bitmap) { BitmapPainter(bitmap.asImageBitmap()) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceContainerHigh),
    ) {
        Image(
            painter = painter,
            contentDescription = "沙箱截图",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        )
    }
}

/**
 * 一个动作磁贴。
 *
 * ⚠️ 刻意**不用** `TextButton` 的默认内边距：那一档是给顶栏用的，
 * 左右留白太大，四个并排会把「清数据」挤成「清数…」。
 * 这里用固定高度 + 8dp 圆角，与 Miuix 的次级档一致。
 */
@Composable
private fun ActionTile(
    label: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        enabled = enabled,
        cornerRadius = 12.dp,
        colors = ButtonDefaults.buttonColors(
            color = if (danger) scheme.errorContainer else scheme.secondaryContainer,
            contentColor = if (danger) scheme.onErrorContainer else scheme.onSecondaryContainer,
        ),
        insideMargin = PaddingValues(horizontal = 4.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = if (danger) scheme.onErrorContainer else scheme.onSecondaryContainer,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (danger) scheme.onErrorContainer else scheme.onSecondaryContainer,
        )
    }
}

/** 底部提示条。错误档沿用全应用同一个 errorContainer 分量。 */
@Composable
private fun NoticeCard(notice: Notice) {
    val scheme = MiuixTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        cornerRadius = 12.dp,
        colors = CardDefaults.defaultColors(
            color = if (notice.isError) scheme.errorContainer else scheme.secondaryContainer,
            contentColor = if (notice.isError) scheme.onErrorContainer else scheme.onSecondaryContainer,
        ),
    ) {
        Text(
            text = notice.text,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/** 就绪态的绿。Miuix 配色里没有"成功"色，用一个低饱和度的固定值。 */
private val HealthyGreen = Color(0xFF7BC67E)
