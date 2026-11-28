@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.zhizhu.zhicode.compose.ui.pages.m3e

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zhizhu.zhicode.compose.data.SandboxApp
import com.zhizhu.zhicode.compose.data.SandboxInspection
import com.zhizhu.zhicode.compose.data.SandboxRepository
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val M3eSandboxPollMillis = 2_000L
private const val M3eSandboxInspectMillis = 3_000L

private data class M3eSandboxNotice(val text: String, val isError: Boolean)

private sealed interface M3eScreenshot {
    data object Loading : M3eScreenshot
    data class Ready(val path: String) : M3eScreenshot
    data class Failed(val message: String) : M3eScreenshot
}

@Composable
internal fun M3eSandboxBoardPage(onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SandboxRepository(context) }
    val scheme = MaterialTheme.colorScheme

    var loaded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("正在读取引擎状态…") }
    var hideRoot by remember { mutableStateOf(true) }
    var floatingLog by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<SandboxApp>>(emptyList()) }
    var fridaInstalled by remember { mutableStateOf<Boolean?>(null) }
    var fridaVersion by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<M3eSandboxNotice?>(null) }
    var inFlight by remember { mutableStateOf<String?>(null) }
    var snapshot by remember { mutableStateOf<String?>(null) }
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var viewingShot by remember { mutableStateOf<String?>(null) }

    val screenshots = remember { mutableStateMapOf<String, M3eScreenshot>() }
    val inspections = remember { mutableStateMapOf<String, SandboxInspection>() }
    val livePackages = remember { mutableStateMapOf<String, Unit>() }

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

    suspend fun reloadApps() {
        repository.readApps().onSuccess { apps = it }
    }

    fun run(label: String, affectsList: Boolean = false, block: suspend () -> String?) {
        if (inFlight != null) return
        inFlight = label
        scope.launch {
            val outcome = runCatching { block() }
            outcome.exceptionOrNull()?.let {
                notice = M3eSandboxNotice("$label 失败：${it.message ?: "未知原因"}", true)
            }
            outcome.getOrNull()?.let { notice = M3eSandboxNotice(it, false) }
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
        repository.readFrida().onSuccess {
            fridaInstalled = it.installed
            fridaVersion = it.version
        }
        runCatching { reloadApps() }
        loaded = true
    }

    suspend fun openDiagnostics() {
        snapshot = "正在读取诊断快照…"
        snapshot = repository.readSnapshot().getOrElse {
            "读取诊断失败：${it.message ?: "未知原因"}"
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
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

    LaunchedEffect(Unit) { refreshAll() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(M3eSandboxPollMillis)
            val snap = repository.pollRuntime().getOrNull() ?: continue
            status = snap.statusText
            apps = apps.map { it.copy(running = it.packageName in snap.runningPackages) }
        }
    }

    LaunchedEffect(livePackages.keys.toList()) {
        if (livePackages.isEmpty()) return@LaunchedEffect
        while (true) {
            for (pkg in livePackages.keys.toList()) {
                repository.inspect(pkg).onSuccess { inspections[pkg] = it }
            }
            delay(M3eSandboxInspectMillis)
        }
    }

    M3eSubPageScaffold(
        title = "沙箱",
        subtitle = "虚拟安卓运行环境 · BlackBox",
        onBack = onExit,
        actions = {
            TextButton(
                onClick = { run("诊断") { openDiagnostics(); null } },
                enabled = inFlight == null,
            ) { Text("诊断") }
        },
    ) { padding, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            M3eEngineStatusCard(
                status = status,
                appCount = apps.size,
                runningCount = apps.count { it.running },
                onDiagnose = { run("诊断") { openDiagnostics(); null } },
            )

            if (!canOverlay) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
                    shape = m3eSegmentShape(0, 1),
                    color = scheme.errorContainer,
                    contentColor = scheme.onErrorContainer,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        notice = M3eSandboxNotice(
                            "授予「显示在其他应用上层」后返回本页即可",
                            false,
                        )
                    },
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "缺少「悬浮条置顶」权限",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "点这里去授权，否则日志浮层盖不住应用自己的对话框",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            M3eSectionHeader("运行环境")
            M3eSegmentedColumn {
                M3eSwitchRow(
                    index = 0,
                    count = 2,
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
                M3eSwitchRow(
                    index = 1,
                    count = 2,
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
                                onSuccess = {
                                    if (next) "已开启日志悬浮窗" else "已关闭日志悬浮窗"
                                },
                                onFailure = {
                                    floatingLog = previous
                                    throw it
                                },
                            )
                        }
                    },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = 10.dp),
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
                        .height(M3eDimens.ControlHeight),
                    enabled = inFlight == null,
                ) {
                    Icon(
                        painter = ZhiIcons.add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("导入 APK")
                }
                OutlinedButton(
                    onClick = { run("刷新") { refreshAll(); null } },
                    modifier = Modifier
                        .weight(1f)
                        .height(M3eDimens.ControlHeight),
                    enabled = inFlight == null,
                ) {
                    val refreshMotion = MaterialTheme.motionScheme
                    AnimatedContent(
                        targetState = inFlight == "刷新",
                        transitionSpec = {
                            fadeIn(refreshMotion.defaultEffectsSpec()) togetherWith
                                fadeOut(refreshMotion.fastEffectsSpec())
                        },
                        label = "sandbox-refresh",
                    ) { refreshing ->
                        Text(if (refreshing) "读取中" else "刷新", maxLines = 1)
                    }
                }
            }

            M3eSectionHeader("沙箱应用")
            if (apps.isEmpty()) {
                Text(
                    text = if (loaded) "沙箱里还没有应用。点上面的「导入 APK」装一个。" else "正在读取…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = M3eDimens.RowPaddingH, vertical = 24.dp),
                )
            } else {
                apps.forEach { app ->
                    key(app.packageName) {
                        M3eSandboxAppCard(
                            app = app,
                            packageInFlight = inFlight?.takeIf { it.endsWith(app.packageName) },
                            screenshot = screenshots[app.packageName],
                            inspection = inspections[app.packageName],
                            onOpenScreenshot = { path -> viewingShot = path },
                            onScreenshot = {
                                screenshots[app.packageName] = M3eScreenshot.Loading
                                scope.launch {
                                    screenshots[app.packageName] =
                                        repository.screenshot(app.packageName).fold(
                                            onSuccess = { M3eScreenshot.Ready(it) },
                                            onFailure = {
                                                M3eScreenshot.Failed(it.message ?: "截图失败")
                                            },
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
                                if (inspections.containsKey(app.packageName)) {
                                    inspections.remove(app.packageName)
                                    livePackages.remove(app.packageName)
                                } else {
                                    livePackages[app.packageName] = Unit
                                    run("进程 / SO 基址") {
                                        inspections[app.packageName] =
                                            repository.inspect(app.packageName).getOrThrow()
                                        null
                                    }
                                }
                            },
                            onFrida = {
                                if (fridaInstalled == false) {
                                    run("安装 Frida") {
                                        repository.installFrida().fold(
                                            onSuccess = {
                                                fridaInstalled = it.installed
                                                fridaVersion = it.version
                                                "Frida Gadget ${it.version} 已安装"
                                            },
                                            onFailure = { throw it },
                                        )
                                    }
                                } else {
                                    run("Frida") {
                                        "Frida Gadget $fridaVersion 已就绪；" +
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
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
                    shape = m3eSegmentShape(0, 1),
                    color = if (it.isError) scheme.errorContainer else scheme.secondaryContainer,
                    contentColor = if (it.isError) scheme.onErrorContainer else scheme.onSecondaryContainer,
                ) {
                    Text(
                        text = it.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    snapshot?.let { text ->
        AlertDialog(
            onDismissRequest = { snapshot = null },
            title = { Text("诊断") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        run("清空日志") {
                            repository.clearDebug().fold(
                                onSuccess = { "引擎日志已清空" },
                                onFailure = { throw it },
                            )
                        }
                    },
                ) { Text("清空日志") }
            },
            dismissButton = {
                TextButton(
                    onClick = { run("诊断") { openDiagnostics(); null } },
                ) { Text("刷新") }
                TextButton(onClick = { snapshot = null }) { Text("关闭") }
            },
        )
    }

    viewingShot?.let { path ->
        AlertDialog(
            onDismissRequest = { viewingShot = null },
            title = { Text("截图") },
            text = {
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            },
            confirmButton = { TextButton(onClick = { viewingShot = null }) { Text("关闭") } },
        )
    }
}

@Composable
private fun M3eEngineStatusCard(
    status: String,
    appCount: Int,
    runningCount: Int,
    onDiagnose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val unsupported = com.zhizhu.zhicode.sandbox.SandboxHostClient.isUnsupportedHere()
    val failed = !unsupported && isM3eSandboxUnavailable(status)
    val ready = !failed && !unsupported && status.contains("就绪")

    val container = when {
        ready -> scheme.secondaryContainer
        failed -> scheme.errorContainer
        else -> scheme.surfaceContainer
    }
    val content = when {
        ready -> scheme.onSecondaryContainer
        failed -> scheme.onErrorContainer
        else -> scheme.onSurface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = M3eDimens.RowPaddingH, vertical = 8.dp),
        shape = m3eSegmentShape(0, 1),
        color = container,
        contentColor = content,
        onClick = onDiagnose,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = when {
                                ready -> scheme.primary
                                failed -> scheme.error
                                else -> scheme.onSurfaceVariant
                            },
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = "BlackBox 引擎",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                Text(
                    text = if (ready) "就绪" else if (failed) "不可用" else "未就绪",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "$appCount 个应用 · $runningCount 个运行中 · 点这张卡看诊断",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun M3eSandboxAppCard(
    app: SandboxApp,
    packageInFlight: String?,
    screenshot: M3eScreenshot?,
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
    val scheme = MaterialTheme.colorScheme
    val busy = packageInFlight != null
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = M3eDimens.RowPaddingH, vertical = 4.dp),
        shape = m3eSegmentShape(0, 1),
        color = scheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(M3eDimens.IconBadge)
                        .background(scheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = app.packageName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onPrimaryContainer,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val runningMotion = MaterialTheme.motionScheme
                    AnimatedContent(
                        targetState = app.running,
                        transitionSpec = {
                            fadeIn(runningMotion.defaultEffectsSpec()) togetherWith
                                fadeOut(runningMotion.fastEffectsSpec())
                        },
                        label = "sandbox-app-running",
                    ) { running ->
                        Text(
                            text = if (running) "运行中 · 已安装到沙箱" else "已安装到沙箱",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (running) scheme.primary else scheme.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    TextButton(onClick = { menuOpen = true }, enabled = !busy) {
                        Icon(
                            painter = ZhiIcons.more,
                            contentDescription = "更多操作",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("截图") },
                            onClick = { menuOpen = false; onScreenshot() },
                        )
                        DropdownMenuItem(
                            text = { Text("进程 / SO 基址") },
                            onClick = { menuOpen = false; onInspect() },
                        )
                        DropdownMenuItem(
                            text = { Text("Frida") },
                            onClick = { menuOpen = false; onFrida() },
                        )
                        DropdownMenuItem(
                            text = { Text("清除数据") },
                            onClick = { menuOpen = false; onClearData() },
                        )
                        DropdownMenuItem(
                            text = { Text("卸载", color = scheme.error) },
                            onClick = { menuOpen = false; onUninstall() },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (app.running) {
                    OutlinedButton(onClick = onStop, enabled = !busy) { Text("停止") }
                } else {
                    Button(onClick = onLaunch, enabled = !busy) { Text("运行") }
                }
                if (busy) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                }
            }

            when (screenshot) {
                is M3eScreenshot.Ready -> Text(
                    text = "截图已就绪：${screenshot.path}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                is M3eScreenshot.Failed -> Text(
                    text = "截图失败：${screenshot.message}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
                M3eScreenshot.Loading -> Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LoadingIndicator(modifier = Modifier.size(14.dp))
                    Text(
                        text = "正在截图…",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                null -> Unit
            }

            inspection?.let {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.surfaceContainerHigh,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "进程 / SO 基址",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurfaceVariant,
                        )
                        it.processes.forEach { process ->
                            Text(
                                text = "PID ${process.pid} · ${process.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = scheme.onSurface,
                            )
                            process.modules.take(4).forEach { module ->
                                Text(
                                    text = "  ${module.base}  ${module.path}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            if (process.modules.size > 4) {
                                Text(
                                    text = "  …共 ${process.modules.size} 个模块",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 引擎状态里"不可用"的判定。 */
private fun isM3eSandboxUnavailable(status: String): Boolean =
    com.zhizhu.zhicode.sandbox.SandboxHostClient.isUnsupportedHere() ||
        status.contains("异常") || status.contains("不可用") || status.contains("失败") ||
        status.contains("超时") || status.contains("尚未") || status.contains("没有回话")
