@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)

package com.zhizhu.zhicode.compose.ui.pages.miuix

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zhizhu.zhicode.RuntimeInstaller
import com.zhizhu.zhicode.UbuntuMirrorSource
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.runtime.OobePermissions
import com.zhizhu.zhicode.compose.runtime.OobePermissionsSnapshot
import com.zhizhu.zhicode.compose.runtime.OobeStep
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.PageFeedbackSlot
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsIconPlate
import com.zhizhu.zhicode.compose.ui.settings.SettingsPlateColors
import com.zhizhu.zhicode.shizuku.ShizukuBridge
import rikka.shizuku.Shizuku
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 三步初始引导页：系统权限 -> Ubuntu 24.04 运行环境 -> 模型供应商配置（可跳过）。
 */
@Composable
fun OobePage(
    state: WorkspaceUiState,
    canDismissDirectly: Boolean,
    onRefreshPermissions: () -> Unit,
    onNextStep: () -> Unit,
    onBackStep: () -> Unit,
    onDismissReopened: () -> Unit,
    onSelectRuntimeMirror: (String) -> Unit = {},
    onInstallRuntime: () -> Unit,
    onRepairRuntime: () -> Unit,
    onToggleRoot: (Boolean) -> Unit,
    onOpenApiConfig: () -> Unit,
    onSkipOrFinish: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MiuixTheme.colorScheme
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollBehavior = MiuixScrollBehavior()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onRefreshPermissions()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var shizukuHint by remember { mutableStateOf<String?>(null) }
    var storagePromptTried by remember { mutableStateOf(false) }
    var notifPromptTried by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val onPerm = Shizuku.OnRequestPermissionResultListener { _, _ -> onRefreshPermissions() }
        val onDead = Shizuku.OnBinderDeadListener { onRefreshPermissions() }
        val onRecv = Shizuku.OnBinderReceivedListener { onRefreshPermissions() }
        runCatching { Shizuku.addRequestPermissionResultListener(onPerm) }
        runCatching { Shizuku.addBinderDeadListener(onDead) }
        runCatching { Shizuku.addBinderReceivedListenerSticky(onRecv) }
        onDispose {
            runCatching { Shizuku.removeRequestPermissionResultListener(onPerm) }
            runCatching { Shizuku.removeBinderDeadListener(onDead) }
            runCatching { Shizuku.removeBinderReceivedListener(onRecv) }
        }
    }

    val openIntentSafe: (Intent, Intent?) -> Unit = { primary, fallback ->
        val ok = runCatching { context.startActivity(primary); true }.getOrDefault(false)
        if (!ok && fallback != null) {
            runCatching { context.startActivity(fallback) }
        }
    }

    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        onRefreshPermissions()
        val snap = OobePermissions.inspect(context)
        if (snap.legacyStorageGranted && !snap.allFilesGranted && Build.VERSION.SDK_INT >= 30) {
            openIntentSafe(
                OobePermissions.allFilesAccessIntent(context),
                OobePermissions.allFilesAccessFallbackIntent(),
            )
        } else if (!snap.legacyStorageGranted && storagePromptTried) {
            openIntentSafe(OobePermissions.appDetailsIntent(context), null)
        }
        storagePromptTried = true
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onRefreshPermissions()
        if (!granted && notifPromptTried) {
            openIntentSafe(
                OobePermissions.notificationSettingsIntent(context),
                OobePermissions.appDetailsIntent(context),
            )
        }
        notifPromptTried = true
    }

    val step = state.oobeStep
    val perms = state.oobePermissions
    val hasActiveApi = state.profileName != "未配置" && state.modelLabel != "未设置"
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(step) { keyboardController?.hide() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = step.title,
                largeTitle = "欢迎使用 ZhiCode",
                scrollBehavior = scrollBehavior,
                color = scheme.surface,
                navigationIcon = {
                    when {
                        step != OobeStep.PERMISSIONS -> {
                            IconButton(
                                onClick = onBackStep,
                                enabled = !(step == OobeStep.RUNTIME && state.runtimeInstalling),
                            ) {
                                Icon(
                                    painter = ZhiIcons.back,
                                    contentDescription = "上一步",
                                    tint = scheme.onBackground,
                                )
                            }
                        }
                        canDismissDirectly -> {
                            IconButton(onClick = onDismissReopened) {
                                Icon(
                                    painter = ZhiIcons.close,
                                    contentDescription = "关闭引导",
                                    tint = scheme.onBackground,
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                color = scheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    OobeBottomBar(
                        state = state,
                        perms = perms,
                        hasActiveApi = hasActiveApi,
                        onNextStep = onNextStep,
                        onBackStep = onBackStep,
                        onOpenApiConfig = onOpenApiConfig,
                        onSkipOrFinish = onSkipOrFinish,
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                item(key = "steps") {
                    OobeStepStrip(current = step, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
                when (step) {
                    OobeStep.PERMISSIONS -> {
                        item(key = "requiredPerms") {
                            SettingsGroup("基础权限（必需）") {
                                PermissionRow(
                                    title = "存储空间访问",
                                    summary = when {
                                        perms.storageGranted -> "已授予"
                                        !perms.legacyStorageGranted -> "未授予读写权限 · 点击授权"
                                        else -> "未开启所有文件访问权限 · 点击前往设置"
                                    },
                                    granted = perms.storageGranted,
                                    icon = ZhiIcons.directory,
                                    plate = SettingsPlateColors.blue,
                                    onClick = {
                                        if (!perms.legacyStorageGranted) {
                                            storageLauncher.launch(OobePermissions.STORAGE_RUNTIME_PERMISSIONS)
                                        } else if (!perms.allFilesGranted) {
                                            openIntentSafe(
                                                OobePermissions.allFilesAccessIntent(context),
                                                OobePermissions.allFilesAccessFallbackIntent(),
                                            )
                                        }
                                    },
                                )
                                PermissionRow(
                                    title = "通知权限",
                                    summary = if (perms.notificationsGranted) "已授予" else "未开启 · 点击授权",
                                    granted = perms.notificationsGranted,
                                    icon = ZhiIcons.info,
                                    plate = SettingsPlateColors.green,
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= 33 && !notifPromptTried) {
                                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            openIntentSafe(
                                                OobePermissions.notificationSettingsIntent(context),
                                                OobePermissions.appDetailsIntent(context),
                                            )
                                        }
                                    },
                                )
                                PermissionRow(
                                    title = "忽略电池优化",
                                    summary = if (perms.batteryOptimizationIgnored) "已加入白名单" else "未加入 · 点击允许后台运行",
                                    granted = perms.batteryOptimizationIgnored,
                                    icon = ZhiIcons.timeout,
                                    plate = SettingsPlateColors.orange,
                                    onClick = {
                                        openIntentSafe(
                                            OobePermissions.batteryOptimizationIntent(context),
                                            OobePermissions.batterySettingsFallbackIntent(),
                                        )
                                    },
                                )
                            }
                        }
                        item(key = "optionalCaps") {
                            SettingsGroup("可选能力") {
                                PermissionRow(
                                    title = "安装未知应用",
                                    summary = if (perms.installPackagesGranted) "已允许" else "未开启 · Agent 安装 APK 时需要",
                                    granted = perms.installPackagesGranted,
                                    icon = ZhiIcons.apps,
                                    plate = SettingsPlateColors.purple,
                                    onClick = {
                                        openIntentSafe(
                                            OobePermissions.unknownAppSourcesIntent(context),
                                            OobePermissions.appDetailsIntent(context),
                                        )
                                    },
                                )
                                PermissionRow(
                                    title = "Shizuku",
                                    summary = shizukuHint ?: perms.shizukuLabel,
                                    granted = perms.shizukuState == ShizukuBridge.GRANTED,
                                    icon = ZhiIcons.terminal,
                                    plate = SettingsPlateColors.green,
                                    onClick = {
                                        shizukuHint = null
                                        when (ShizukuBridge.state(context)) {
                                            ShizukuBridge.GRANTED -> shizukuHint = "已授权"
                                            ShizukuBridge.NOT_RUNNING -> {
                                                if (!ShizukuBridge.openManager(context)) {
                                                    shizukuHint = "未安装 Shizuku 管理端"
                                                }
                                            }
                                            ShizukuBridge.UNAVAILABLE -> {
                                                openIntentSafe(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(ShizukuBridge.PROJECT_URL))
                                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                                    null,
                                                )
                                            }
                                            else -> {
                                                shizukuHint = ShizukuBridge.requestPermission(OOBE_SHIZUKU_REQ)
                                            }
                                        }
                                    },
                                )
                                val rootEnabled = state.settings.rootExecutionEnabled
                                PermissionRow(
                                    title = "Agent Root",
                                    summary = when {
                                        rootEnabled -> "已启用（su）"
                                        perms.rootBinaryPresent -> "检测到 su · 点击申请并启用"
                                        else -> "未启用 · 若设备已 Root 可点击申请授权"
                                    },
                                    granted = rootEnabled,
                                    icon = ZhiIcons.lock,
                                    plate = SettingsPlateColors.red,
                                    onClick = { onToggleRoot(!rootEnabled) },
                                )
                            }
                        }
                    }
                    OobeStep.RUNTIME -> {
                        val selectedMirror = UbuntuMirrorSource.findById(state.ubuntuMirrorId)
                        item(key = "runtimeInstall") {
                            var showReinstallOptions by remember(state.runtimeReady, state.runtimeInstalling) {
                                mutableStateOf(false)
                            }
                            SettingsGroup("Ubuntu 24.04 运行环境") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SettingsIconPlate(
                                            icon = if (state.runtimeReady) ZhiIcons.done else ZhiIcons.runtime,
                                            color = if (state.runtimeReady) SettingsPlateColors.green else SettingsPlateColors.blue,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (state.runtimeReady && !state.runtimeInstalling) {
                                                    "Ubuntu 24.04 运行环境安装完成"
                                                } else if (state.runtimeInstalling) {
                                                    "正在从 ${selectedMirror?.displayName ?: "云端"} 下载安装"
                                                } else {
                                                    "请选择 Ubuntu 24.04 下载源"
                                                },
                                                color = if (state.runtimeReady && !state.runtimeInstalling) scheme.primary else scheme.onBackground,
                                                fontSize = MiuixTheme.textStyles.headline2.fontSize,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Text(
                                                text = if (state.runtimeReady && !state.runtimeInstalling) {
                                                    selectedMirror?.let { "下载源：${it.displayName} · 内置 Node.js + Pi 已就绪，请点击「下一步」" }
                                                        ?: "Ubuntu 24.04 + 内置 Node.js + Pi 已就绪，请点击「下一步」"
                                                } else {
                                                    RuntimeInstaller.BOOTSTRAP_SOURCE
                                                },
                                                color = scheme.onSurfaceVariantSummary,
                                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                            )
                                        }
                                        Icon(
                                            painter = if (state.runtimeReady && !state.runtimeInstalling) ZhiIcons.done else ZhiIcons.pending,
                                            contentDescription = null,
                                            tint = if (state.runtimeReady && !state.runtimeInstalling) scheme.primary else scheme.onSurfaceVariantSummary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }

                                    if (state.runtimeInstalling) {
                                        Spacer(Modifier.height(14.dp))
                                        LinearProgressIndicator(
                                            progress = (state.runtimeProgress.coerceIn(0, 100)) / 100f,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp),
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "${state.runtimeProgress}% · ${state.runtimeMessage}",
                                            color = scheme.onSurfaceVariantSummary,
                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        )
                                    } else if (!state.runtimeReady && state.runtimeMessage.isNotBlank()) {
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            text = state.runtimeMessage,
                                            color = scheme.onSurfaceVariantSummary,
                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        )
                                    }
                                }

                                if (!state.runtimeInstalling) {
                                    if (!state.runtimeReady || showReinstallOptions) {
                                        UbuntuMirrorSource.entries.forEach { mirror ->
                                            val picked = mirror == selectedMirror
                                            BasicComponent(
                                                title = mirror.displayName,
                                                titleColor = BasicComponentDefaults.titleColor(
                                                    color = if (picked) scheme.primary else scheme.onBackground,
                                                ),
                                                summary = mirror.summary,
                                                summaryColor = BasicComponentDefaults.summaryColor(
                                                    color = scheme.onSurfaceVariantSummary,
                                                ),
                                                startAction = {
                                                    SettingsIconPlate(
                                                        icon = ZhiIcons.cloud,
                                                        color = if (picked) SettingsPlateColors.blue else SettingsPlateColors.gray,
                                                    )
                                                },
                                                endActions = {
                                                    if (picked) {
                                                        Icon(
                                                            painter = ZhiIcons.done,
                                                            contentDescription = null,
                                                            tint = scheme.primary,
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                    }
                                                },
                                                onClick = { onSelectRuntimeMirror(mirror.id) },
                                                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                            )
                                        }
                                    }

                                    if (!state.runtimeReady) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                        ) {
                                            PrimaryButton(
                                                text = selectedMirror?.let { "从 ${it.displayName} 下载并安装" }
                                                    ?: "请先在上方选择下载源",
                                                onClick = onInstallRuntime,
                                                enabled = selectedMirror != null,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    } else {
                                        if (showReinstallOptions) {
                                            BasicComponent(
                                                title = "校准挂载链接与配置",
                                                summary = "校验容器路径并修复共享存储链接",
                                                onClick = onRepairRuntime,
                                                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                            )
                                            BasicComponent(
                                                title = selectedMirror?.let { "使用 ${it.displayName} 重新下载安装" }
                                                    ?: "选择上方下载源后重新下载安装",
                                                summary = "保留 /root 工作区，重新拉取 Ubuntu 24.04 系统层",
                                                enabled = selectedMirror != null,
                                                onClick = onInstallRuntime,
                                                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                            )
                                        }
                                        BasicComponent(
                                            title = if (showReinstallOptions) "收起维护与重装选项" else "维护 / 切换镜像重新安装",
                                            summary = if (showReinstallOptions) {
                                                "折叠下载源与校准入口"
                                            } else {
                                                "仅在环境异常或需更换镜像源时使用"
                                            },
                                            onClick = { showReinstallOptions = !showReinstallOptions },
                                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    OobeStep.MODEL -> {
                        item(key = "modelConfig") {
                            SettingsGroup("模型供应商（可跳过）") {
                                BasicComponent(
                                    title = if (hasActiveApi) state.profileName else "尚未配置模型供应商",
                                    titleColor = BasicComponentDefaults.titleColor(
                                        color = if (hasActiveApi) scheme.primary else scheme.onBackground,
                                    ),
                                    summary = if (hasActiveApi) {
                                        "${state.modelLabel} · ${if (state.apiKeyConfigured) "密钥已保存" else "未填密钥"}"
                                    } else {
                                        "未配置，也可先跳过并稍后在设置里添加"
                                    },
                                    summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
                                    startAction = {
                                        SettingsIconPlate(icon = ZhiIcons.link, color = SettingsPlateColors.blue)
                                    },
                                    endActions = {
                                        Icon(
                                            painter = if (hasActiveApi && state.apiKeyConfigured) ZhiIcons.done else ZhiIcons.arrowRight,
                                            contentDescription = null,
                                            tint = if (hasActiveApi && state.apiKeyConfigured) scheme.primary else scheme.onSurfaceVariantSummary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    onClick = onOpenApiConfig,
                                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
            PageFeedbackSlot(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun OobeStepStrip(current: OobeStep, modifier: Modifier = Modifier) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OobeStep.entries.forEach { step ->
            val active = step == current
            val completed = step.index < current.index
            val bg = when {
                active -> scheme.primary
                completed -> scheme.secondaryContainer
                else -> scheme.surfaceContainer
            }
            val fg = when {
                active -> scheme.onPrimary
                completed -> scheme.primary
                else -> scheme.onSurfaceVariantSummary
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(ZhiRadius.button))
                    .background(bg)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "${step.index + 1}. ${step.title}",
                    color = fg,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    summary: String,
    granted: Boolean,
    icon: androidx.compose.ui.graphics.painter.Painter,
    plate: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    BasicComponent(
        title = title,
        titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
        summary = summary,
        summaryColor = BasicComponentDefaults.summaryColor(
            color = if (granted) scheme.primary else scheme.onSurfaceVariantSummary,
        ),
        startAction = { SettingsIconPlate(icon = icon, color = plate) },
        endActions = {
            Icon(
                painter = if (granted) ZhiIcons.done else ZhiIcons.arrowRight,
                contentDescription = null,
                tint = if (granted) scheme.primary else scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = onClick,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun OobeBottomBar(
    state: WorkspaceUiState,
    perms: OobePermissionsSnapshot,
    hasActiveApi: Boolean,
    onNextStep: () -> Unit,
    onBackStep: () -> Unit,
    onOpenApiConfig: () -> Unit,
    onSkipOrFinish: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    when (state.oobeStep) {
        OobeStep.PERMISSIONS -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (perms.allRequiredGranted) "基础权限已就绪" else "请先授予全部基础权限",
                    color = if (perms.allRequiredGranted) scheme.primary else scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                PrimaryButton(
                    text = "下一步",
                    onClick = onNextStep,
                    enabled = perms.allRequiredGranted,
                )
            }
        }
        OobeStep.RUNTIME -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.runtimeInstalling) {
                    SecondaryButton(
                        text = "上一步",
                        onClick = onBackStep,
                        modifier = Modifier.weight(1f),
                    )
                }
                PrimaryButton(
                    text = if (state.runtimeInstalling) "安装中…" else "下一步",
                    onClick = onNextStep,
                    enabled = state.runtimeReady && !state.runtimeInstalling,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        OobeStep.MODEL -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (hasActiveApi) {
                    SecondaryButton(
                        text = "前往 API 配置",
                        onClick = onOpenApiConfig,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "开始使用",
                        onClick = onSkipOrFinish,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    SecondaryButton(
                        text = "跳过",
                        onClick = onSkipOrFinish,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "前往 API 配置",
                        onClick = onOpenApiConfig,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private const val OOBE_SHIZUKU_REQ = 4272
