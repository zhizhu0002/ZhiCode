package com.zhizhu.zhicode.compose.ui.pages.m3e

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zhizhu.zhicode.UbuntuMirrorSource
import com.zhizhu.zhicode.RuntimeInstaller
import com.zhizhu.zhicode.compose.data.OobeStore
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.runtime.OobePermissions
import com.zhizhu.zhicode.compose.runtime.OobeStep
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.shizuku.ShizukuBridge
import rikka.shizuku.Shizuku

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun M3eOobePage(state: WorkspaceUiState, viewModel: WorkspaceViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var shizukuHint by remember { mutableStateOf<String?>(null) }
    var storagePromptTried by remember { mutableStateOf(false) }
    var notificationPromptTried by remember { mutableStateOf(false) }
    var showMaintenance by remember(state.runtimeReady, state.runtimeInstalling) { mutableStateOf(false) }
    val perms = state.oobePermissions
    val step = state.oobeStep
    val hasApi = state.profileName != "未配置" && state.modelLabel != "未设置"

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshOobePermissions() }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.refreshOobePermissions()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ -> viewModel.refreshOobePermissions() }
        val deadListener = Shizuku.OnBinderDeadListener { viewModel.refreshOobePermissions() }
        val binderListener = Shizuku.OnBinderReceivedListener { viewModel.refreshOobePermissions() }
        runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }
        runCatching { Shizuku.addBinderDeadListener(deadListener) }
        runCatching { Shizuku.addBinderReceivedListenerSticky(binderListener) }
        onDispose {
            runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
            runCatching { Shizuku.removeBinderDeadListener(deadListener) }
            runCatching { Shizuku.removeBinderReceivedListener(binderListener) }
        }
    }
    val openIntent: (Intent, Intent?) -> Unit = { primary, fallback ->
        val started = runCatching { context.startActivity(primary); true }.getOrDefault(false)
        if (!started && fallback != null) runCatching { context.startActivity(fallback) }
    }
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refreshOobePermissions()
        val snapshot = OobePermissions.inspect(context)
        if (snapshot.legacyStorageGranted && !snapshot.allFilesGranted && Build.VERSION.SDK_INT >= 30) {
            openIntent(OobePermissions.allFilesAccessIntent(context), OobePermissions.allFilesAccessFallbackIntent())
        } else if (!snapshot.legacyStorageGranted && storagePromptTried) {
            openIntent(OobePermissions.appDetailsIntent(context), null)
        }
        storagePromptTried = true
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshOobePermissions()
        if (!granted && notificationPromptTried) openIntent(
            OobePermissions.notificationSettingsIntent(context), OobePermissions.appDetailsIntent(context),
        )
        notificationPromptTried = true
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(step.title) },
                subtitle = { Text("欢迎使用 ZhiCode") },
                navigationIcon = {
                    if (step != OobeStep.PERMISSIONS) {
                        androidx.compose.material3.IconButton(onClick = viewModel::goBackOobeStep, enabled = !(step == OobeStep.RUNTIME && state.runtimeInstalling)) {
                            androidx.compose.material3.Icon(ZhiIcons.back, contentDescription = "上一步")
                        }
                    } else if (!OobeStore.isRequired(context)) {
                        androidx.compose.material3.IconButton(onClick = viewModel::dismissReopenedOobe) {
                            androidx.compose.material3.Icon(ZhiIcons.close, contentDescription = "关闭引导")
                        }
                    }
                },
                scrollBehavior = scroll,
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (step) {
                    OobeStep.PERMISSIONS -> {
                        Text(if (perms.allRequiredGranted) "权限已就绪" else "请授予基础权限", modifier = Modifier.weight(1f).padding(top = 14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = viewModel::advanceOobeStep, enabled = perms.allRequiredGranted) { Text("下一步") }
                    }
                    OobeStep.RUNTIME -> {
                        // M3E 连接式按钮组：首尾形状按位置配对，相邻角收窄
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        ) {
                            OutlinedButton(
                                onClick = viewModel::goBackOobeStep,
                                enabled = !state.runtimeInstalling,
                                modifier = Modifier.weight(1f),
                                shape = ButtonGroupDefaults.connectedLeadingButtonShape,
                            ) { Text("上一步") }
                            Button(
                                onClick = viewModel::advanceOobeStep,
                                enabled = state.runtimeReady && !state.runtimeInstalling,
                                modifier = Modifier.weight(1f),
                                shape = ButtonGroupDefaults.connectedTrailingButtonShape,
                            ) { Text("下一步") }
                        }
                    }
                    OobeStep.MODEL -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        ) {
                            OutlinedButton(
                                onClick = viewModel::completeOobe,
                                modifier = Modifier.weight(1f),
                                shape = ButtonGroupDefaults.connectedLeadingButtonShape,
                            ) { Text(if (hasApi) "稍后" else "跳过") }
                            Button(
                                onClick = if (hasApi) viewModel::completeOobe else viewModel::openApiConfigFromOobe,
                                modifier = Modifier.weight(1f),
                                shape = ButtonGroupDefaults.connectedTrailingButtonShape,
                            ) { Text(if (hasApi) "开始使用" else "配置模型") }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SingleChoiceSegmentedButtonRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                OobeStep.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = item == step,
                        onClick = { viewModel.selectPreviousOobeStep(item) },
                        enabled = item.index <= step.index && !state.runtimeInstalling,
                        shape = SegmentedButtonDefaults.itemShape(index, OobeStep.entries.size),
                        label = { Text(item.title, maxLines = 1) },
                    )
                }
            }
            // 换步骤时整块重进：滑入 + 淡入，与 ZL2 的 WelcomeScreen 同一手法。
            Box(modifier = Modifier.m3eStepEntrance(step)) {
            when (step) {
                OobeStep.PERMISSIONS -> {
                    M3eSectionHeader("基础权限")
                    M3eSegmentedColumn {
                        PermissionRow(0, 3, "存储空间访问", when {
                            perms.storageGranted -> "已授予"
                            !perms.legacyStorageGranted -> "未授予 · 点击授权"
                            else -> "未开启所有文件访问 · 前往设置"
                        }, perms.storageGranted) {
                            if (!perms.legacyStorageGranted) storageLauncher.launch(OobePermissions.STORAGE_RUNTIME_PERMISSIONS)
                            else if (!perms.allFilesGranted) openIntent(OobePermissions.allFilesAccessIntent(context), OobePermissions.allFilesAccessFallbackIntent())
                        }
                        PermissionRow(1, 3, "通知权限", if (perms.notificationsGranted) "已授予" else "未开启 · 点击授权", perms.notificationsGranted) {
                            if (Build.VERSION.SDK_INT >= 33 && !notificationPromptTried) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else openIntent(OobePermissions.notificationSettingsIntent(context), OobePermissions.appDetailsIntent(context))
                        }
                        PermissionRow(2, 3, "忽略电池优化", if (perms.batteryOptimizationIgnored) "已加入白名单" else "未加入 · 点击设置", perms.batteryOptimizationIgnored) {
                            openIntent(OobePermissions.batteryOptimizationIntent(context), OobePermissions.batterySettingsFallbackIntent())
                        }
                    }
                    M3eSectionHeader("可选能力")
                    M3eSegmentedColumn {
                        PermissionRow(0, 3, "安装未知应用", if (perms.installPackagesGranted) "已允许" else "未开启", perms.installPackagesGranted) {
                            openIntent(OobePermissions.unknownAppSourcesIntent(context), OobePermissions.appDetailsIntent(context))
                        }
                        PermissionRow(1, 3, "Shizuku", shizukuHint ?: perms.shizukuLabel, perms.shizukuState == ShizukuBridge.GRANTED) {
                            shizukuHint = null
                            when (ShizukuBridge.state(context)) {
                                ShizukuBridge.GRANTED -> shizukuHint = "已授权"
                                ShizukuBridge.NOT_RUNNING -> if (!ShizukuBridge.openManager(context)) shizukuHint = "未安装 Shizuku 管理端"
                                ShizukuBridge.UNAVAILABLE -> openIntent(Intent(Intent.ACTION_VIEW, Uri.parse(ShizukuBridge.PROJECT_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null)
                                else -> shizukuHint = ShizukuBridge.requestPermission(4272)
                            }
                        }
                        val root = state.settings.rootExecutionEnabled
                        PermissionRow(2, 3, "Agent Root", when {
                            root -> "已启用（su）"
                            perms.rootBinaryPresent -> "检测到 su · 点击申请"
                            else -> "未启用"
                        }, root) { viewModel.toggleRootFromOobe(!root) }
                    }
                }
                OobeStep.RUNTIME -> {
                    val selected = UbuntuMirrorSource.findById(state.ubuntuMirrorId)
                    M3eSectionHeader("Ubuntu 24.04")
                    M3eSegmentedColumn {
                        M3eSegmentedItem(0, 1) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(when {
                                    state.runtimeInstalling -> "正在安装 ${selected?.displayName.orEmpty()}"
                                    state.runtimeReady -> "运行环境已就绪"
                                    else -> "选择下载源"
                                }, style = MaterialTheme.typography.titleMedium)
                                if (state.runtimeInstalling) {
                                    LinearWavyProgressIndicator(progress = { state.runtimeProgress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
                                    Text("${state.runtimeProgress}% · ${state.runtimeMessage}")
                                } else if (state.runtimeMessage.isNotBlank()) Text(state.runtimeMessage)
                                if (!state.runtimeReady) Text(RuntimeInstaller.BOOTSTRAP_SOURCE, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (!state.runtimeReady || showMaintenance) {
                        M3eSectionHeader("下载源")
                        M3eSegmentedColumn {
                            UbuntuMirrorSource.entries.forEachIndexed { index, mirror ->
                                M3eNavigationRow(index, UbuntuMirrorSource.entries.size, mirror.displayName, summary = mirror.summary, valueText = if (mirror == selected) "已选择" else null, onClick = { viewModel.selectUbuntuMirror(mirror.id) })
                            }
                        }
                    }
                    M3eSegmentedColumn {
                        if (!state.runtimeReady) {
                            Button(onClick = viewModel::installRuntime, enabled = selected != null && !state.runtimeInstalling, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("下载并安装") }
                        } else {
                            TextButton(onClick = viewModel::repairRuntime, modifier = Modifier.fillMaxWidth()) { Text("校准挂载链接与配置") }
                            TextButton(onClick = { showMaintenance = !showMaintenance }, modifier = Modifier.fillMaxWidth()) { Text(if (showMaintenance) "收起下载源" else "切换镜像并重新安装") }
                            if (showMaintenance) Button(onClick = viewModel::installRuntime, enabled = selected != null && !state.runtimeInstalling, modifier = Modifier.fillMaxWidth()) { Text("重新安装") }
                        }
                    }
                }
                OobeStep.MODEL -> {
                    M3eSectionHeader("模型供应商")
                    M3eSegmentedColumn {
                        M3eNavigationRow(
                            index = 0, count = 1,
                            title = if (hasApi) state.profileName else "尚未配置模型供应商",
                            summary = if (hasApi) "${state.modelLabel} · ${if (state.apiKeyConfigured) "密钥已保存" else "未填密钥"}" else null,
                            valueText = if (hasApi) "已配置" else null,
                            icon = ZhiIcons.link,
                            onClick = viewModel::openApiConfigFromOobe,
                        )
                    }
                }
            }
            }
        }
    }
}

/** 步骤内容进场：从右侧滑入并淡入，缓动沿用 ZL2 的 (0.05, 0.7, 0.1, 1)。 */
@Composable
private fun Modifier.m3eStepEntrance(step: OobeStep): Modifier {
    var appeared by remember(step) { mutableStateOf(false) }
    LaunchedEffect(step) { appeared = true }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 380, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)),
        label = "oobeStepEntrance",
    )
    return this.graphicsLayer {
        translationX = 48.dp.toPx() * (1f - progress)
        alpha = progress
    }
}

@Composable
private fun PermissionRow(index: Int, count: Int, title: String, status: String, granted: Boolean, onClick: () -> Unit) {
    M3eNavigationRow(
        index = index, count = count, title = title, summary = status,
        valueText = if (granted) "✓" else null,
        onClick = onClick,
    )
}
