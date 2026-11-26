package com.zhizhu.zhicode.compose.ui.pages.m3e

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.BuildConfig
import com.zhizhu.zhicode.compose.model.COMPACT_PERCENT_MAX
import com.zhizhu.zhicode.compose.model.COMPACT_PERCENT_MIN
import com.zhizhu.zhicode.compose.model.EffortLevel
import com.zhizhu.zhicode.compose.model.ExtensionSummary
import com.zhizhu.zhicode.compose.model.MAX_CONCURRENT_SUBAGENTS
import com.zhizhu.zhicode.compose.model.MIN_CONCURRENT_SUBAGENTS
import com.zhizhu.zhicode.compose.model.PermissionMode
import com.zhizhu.zhicode.compose.model.SettingsDraft
import com.zhizhu.zhicode.compose.model.WEB_RESULTS_MAX
import com.zhizhu.zhicode.compose.model.WEB_RESULTS_MIN
import com.zhizhu.zhicode.compose.model.WEB_TIMEOUT_MAX_SEC
import com.zhizhu.zhicode.compose.model.WEB_TIMEOUT_MIN_SEC
import com.zhizhu.zhicode.compose.model.formatTokenCountShort
import com.zhizhu.zhicode.compose.model.parseTokenCount
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.shizuku.ShizukuBridge
import rikka.shizuku.Shizuku

/**
 * Material 3 Expressive 设置主页：
 * - `LargeFlexibleTopAppBar` 折叠大标题；
 * - 分组分段圆角卡片（外角 `24.dp`、内角 `6.dp`）；
 * - 原生 MD3 `Switch` / `Slider` / `ModalBottomSheet` / `AlertDialog`。
 */
@Composable
fun M3eSettingsPage(
    draft: SettingsDraft?,
    onChange: (SettingsDraft) -> Unit,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    extensions: ExtensionSummary = ExtensionSummary(),
) {
    if (draft == null) return
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    M3eSubPageScaffold(
        title = "设置",
        subtitle = "${draft.profileName} · ${draft.modelLabel}",
        onBack = onDismiss,
    ) { innerPadding, _ ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "general") {
                M3eSectionHeader("通用")
                M3eSegmentedColumn {
                    val generalCount = if (draft.webSearchEnabled) 6 else 3
                    var idx = 0
                    M3eNavigationRow(
                        index = idx++,
                        count = generalCount,
                        title = "主题设置",
                        valueText = "界面体系 · ${draft.themeMode.label} · 预设色 / 模糊 / 缩放",
                        icon = ZhiIcons.theme,
                        onClick = { onNavigate("theme") },
                    )
                    M3eNavigationRow(
                        index = idx++,
                        count = generalCount,
                        title = "聊天显示",
                        valueText = "字体 · 正文字号",
                        summary = "自定义聊天正文字体（ttf/otf/ttc）与字号比例",
                        icon = ZhiIcons.chat,
                        onClick = { onNavigate("chatDisplay") },
                    )
                    M3eSwitchRow(
                        index = idx++,
                        count = generalCount,
                        title = "联网搜索",
                        checked = draft.webSearchEnabled,
                        onCheckedChange = { onChange(draft.copy(webSearchEnabled = it)) },
                        icon = ZhiIcons.search,
                    )
                    AnimatedVisibility(
                        visible = draft.webSearchEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)) {
                            M3eNavigationRow(
                                index = 3,
                                count = 6,
                                title = "搜索服务",
                                summary = "添加 Tavily / Exa / Brave / Perplexity / SearXNG 等",
                                icon = ZhiIcons.cloud,
                                showChevron = true,
                                onClick = { onNavigate("searchServices") },
                            )
                            M3eSliderRow(
                                index = 4,
                                count = 6,
                                title = "默认搜索结果数（1-50）",
                                value = draft.webSearchMaxResults.toFloat(),
                                valueText = "${draft.webSearchMaxResults} 条",
                                summary = "服务未单独指定条数时使用",
                                valueRange = WEB_RESULTS_MIN.toFloat()..WEB_RESULTS_MAX.toFloat(),
                                steps = WEB_RESULTS_MAX - WEB_RESULTS_MIN - 1,
                                onValueChange = { onChange(draft.copy(webSearchMaxResults = it.toInt())) },
                                icon = ZhiIcons.listCount,
                            )
                            M3eSliderRow(
                                index = 5,
                                count = 6,
                                title = "联网超时（秒）",
                                value = draft.webSearchTimeoutSec.toFloat(),
                                valueText = "${draft.webSearchTimeoutSec} 秒",
                                summary = "支持 ${WEB_TIMEOUT_MIN_SEC}-${WEB_TIMEOUT_MAX_SEC} 秒",
                                valueRange = WEB_TIMEOUT_MIN_SEC.toFloat()..WEB_TIMEOUT_MAX_SEC.toFloat(),
                                steps = WEB_TIMEOUT_MAX_SEC - WEB_TIMEOUT_MIN_SEC - 1,
                                onValueChange = { onChange(draft.copy(webSearchTimeoutSec = it.toInt())) },
                                icon = ZhiIcons.timeout,
                            )
                        }
                    }
                }
            }

            item(key = "model") {
                M3eSectionHeader("模型与服务")
                M3eSegmentedColumn {
                    M3eNavigationRow(
                        index = 0,
                        count = 4,
                        title = "API 配置",
                        valueText = "${draft.profileName} · ${draft.modelLabel}",
                        icon = ZhiIcons.link,
                        onClick = { onNavigate("apiProfiles") },
                    )
                    M3eSwitchRow(
                        index = 1,
                        count = 4,
                        title = "视觉图片输入（Vision）",
                        summary = "发送图片给模型；模型不支持 vision 时请关闭",
                        checked = draft.visionEnabled,
                        onCheckedChange = { onChange(draft.copy(visionEnabled = it)) },
                        icon = ZhiIcons.image,
                    )
                    M3eChoiceRow(
                        index = 2,
                        count = 4,
                        title = "推理强度",
                        options = EffortLevel.entries.map { it.label },
                        selectedIndex = EffortLevel.entries.indexOf(draft.effort),
                        onSelect = { onChange(draft.copy(effort = EffortLevel.entries[it])) },
                        icon = ZhiIcons.tune,
                    )
                    M3eChoiceRow(
                        index = 3,
                        count = 4,
                        title = "权限模式",
                        options = PermissionMode.entries.map { it.label },
                        selectedIndex = PermissionMode.entries.indexOf(draft.permissionMode),
                        onSelect = { onChange(draft.copy(permissionMode = PermissionMode.entries[it])) },
                        summary = draft.permissionMode.detail,
                        icon = ZhiIcons.lock,
                    )
                }
            }

            item(key = "context") {
                M3eSectionHeader("上下文与项目")
                M3eSegmentedColumn {
                    val count = if (draft.autoCompact) 6 else 5
                    M3eIntDialogRow(
                        index = 0,
                        count = count,
                        title = "上下文窗口",
                        value = draft.contextWindow,
                        min = 1,
                        max = Int.MAX_VALUE,
                        parse = ::parseTokenCount,
                        format = ::formatTokenCountShort,
                        onValueChange = { onChange(draft.copy(contextWindow = it)) },
                        summary = "支持 128k / 1.5m 写法",
                        icon = ZhiIcons.chat,
                    )
                    M3eSwitchRow(
                        index = 1,
                        count = count,
                        title = "上下文压缩",
                        checked = draft.autoCompact,
                        onCheckedChange = { onChange(draft.copy(autoCompact = it)) },
                        icon = ZhiIcons.compress,
                    )
                    var nextIndex = 2
                    if (draft.autoCompact) {
                        M3eSliderRow(
                            index = nextIndex++,
                            count = count,
                            title = "自动压缩上限（50-100%）",
                            value = draft.autoCompactPercent.toFloat(),
                            valueText = "${draft.autoCompactPercent}%",
                            summary = "达到上下文窗口的 ${draft.autoCompactPercent}% 时触发压缩",
                            valueRange = COMPACT_PERCENT_MIN.toFloat()..COMPACT_PERCENT_MAX.toFloat(),
                            steps = COMPACT_PERCENT_MAX - COMPACT_PERCENT_MIN - 1,
                            onValueChange = { onChange(draft.copy(autoCompactPercent = it.toInt())) },
                            icon = ZhiIcons.tune,
                        )
                    }
                    M3eTextFieldDialogRow(
                        index = nextIndex++,
                        count = count,
                        title = "项目目录",
                        value = draft.projectPath,
                        onCommit = { onChange(draft.copy(projectPath = it.trim())) },
                        summary = "与上下文历史一一绑定，保存后可恢复该项目记录",
                        icon = ZhiIcons.directory,
                    )
                    M3eTextFieldDialogRow(
                        index = nextIndex++,
                        count = count,
                        title = "自定义头部提示词",
                        value = draft.customSystemPrompt,
                        displayValue = if (draft.customSystemPrompt.isBlank()) "未设置" else "当前 ${draft.customSystemPrompt.length} 字",
                        onCommit = { onChange(draft.copy(customSystemPrompt = it)) },
                        summary = "随请求发送，下一任务生效；不能覆盖安全规则，勿填密钥",
                        icon = ZhiIcons.edit,
                        singleLine = false,
                    )
                    M3eNavigationRow(
                        index = nextIndex,
                        count = count,
                        title = "提示词模板",
                        valueText = "自定义 /init、压缩摘要的系统提示与开场段",
                        icon = ZhiIcons.edit,
                        onClick = { onNavigate("prompts") },
                    )
                }
            }

            item(key = "agent") {
                M3eSectionHeader("Agent 与安全")
                M3eSegmentedColumn {
                    M3eIntDialogRow(
                        index = 0,
                        count = 6,
                        title = "子 Agent 最大并发数",
                        value = draft.maxConcurrentSubagents,
                        min = MIN_CONCURRENT_SUBAGENTS,
                        max = MAX_CONCURRENT_SUBAGENTS,
                        parse = { it.trim().toIntOrNull() },
                        onValueChange = { onChange(draft.copy(maxConcurrentSubagents = it)) },
                        summary = "同时运行的子 Agent 上限（${MIN_CONCURRENT_SUBAGENTS}–${MAX_CONCURRENT_SUBAGENTS}）",
                        icon = ZhiIcons.tune,
                    )
                    M3eSwitchRow(
                        index = 1,
                        count = 6,
                        title = "Agent Root 权限",
                        checked = draft.rootExecutionEnabled,
                        onCheckedChange = { onChange(draft.copy(rootExecutionEnabled = it)) },
                        summary = "高风险：需要 Magisk/KernelSU 授权；普通模式仍逐次确认",
                        warn = true,
                        icon = ZhiIcons.terminal,
                    )
                    M3eSwitchRow(
                        index = 2,
                        count = 6,
                        title = "Agent Shizuku 权限",
                        checked = draft.shizukuExecutionEnabled,
                        onCheckedChange = { onChange(draft.copy(shizukuExecutionEnabled = it)) },
                        summary = "无 Root 的 shell 身份：pm/am/settings/dumpsys/input 等命令",
                        warn = true,
                        icon = ZhiIcons.terminal,
                    )
                    M3eShizukuAccessRow(index = 3, count = 6)
                    M3eSwitchRow(
                        index = 4,
                        count = 6,
                        title = "强制后台保活",
                        checked = draft.forcedKeepAliveEnabled,
                        onCheckedChange = { onChange(draft.copy(forcedKeepAliveEnabled = it)) },
                        summary = "开启前台服务 + WakeLock；Root 时还会强制系统后台策略",
                        icon = ZhiIcons.refresh,
                    )
                    M3eSwitchRow(
                        index = 5,
                        count = 6,
                        title = "终端字符模式输入",
                        checked = draft.terminalCharMode,
                        onCheckedChange = { onChange(draft.copy(terminalCharMode = it)) },
                        summary = "仅在终端输入法兼容性异常时开启",
                        icon = ZhiIcons.keyboard,
                    )
                }
            }

            item(key = "extensions") {
                M3eSectionHeader("扩展")
                val totalCount = if (BuildConfig.DEBUG) 5 else 4
                M3eSegmentedColumn {
                    M3eNavigationRow(
                        index = 0,
                        count = totalCount,
                        title = "Model Context Protocol（MCP）",
                        summary = if (!extensions.loaded) {
                            "正在统计…"
                        } else if (extensions.mcpTotal > 0) {
                            buildString {
                                append("${extensions.mcpTotal} 台服务器 · ${extensions.mcpEnabled} 台启用")
                                if (extensions.mcpConnected > 0) append(" · ${extensions.mcpConnected} 台已连接")
                            }
                        } else {
                            "还没有配置"
                        },
                        icon = ZhiIcons.mindMap,
                        onClick = { onNavigate("mcp") },
                    )
                    M3eNavigationRow(
                        index = 1,
                        count = totalCount,
                        title = "Skill 管理器",
                        summary = if (!extensions.loaded) {
                            "正在统计…"
                        } else if (extensions.skillTotal == 0) {
                            "还没有技能 · 可按需加载的资料包"
                        } else {
                            "${extensions.skillTotal} 个技能 · 项目级 ${extensions.projectSkills} / 用户级 ${extensions.userSkills}"
                        },
                        icon = ZhiIcons.skill,
                        onClick = { onNavigate("skills") },
                    )
                    M3eNavigationRow(
                        index = 2,
                        count = totalCount,
                        title = "自定义角色卡",
                        summary = when {
                            !extensions.loaded -> "正在统计…"
                            extensions.roleCardTotal == 0 -> "还没有角色卡 · 可切换的多份人设指令"
                            else -> "${extensions.roleCardTotal} 张角色卡 · 同一时刻只有一张生效"
                        },
                        icon = ZhiIcons.roleCard,
                        onClick = { onNavigate("roleCards") },
                    )
                    M3eNavigationRow(
                        index = 3,
                        count = totalCount,
                        title = "记忆文件 · ZhiCode.md",
                        summary = "项目级与用户级说明文件",
                        icon = ZhiIcons.file,
                        onClick = { onNavigate("memory") },
                    )
                    if (BuildConfig.DEBUG) {
                        M3eNavigationRow(
                            index = 4,
                            count = totalCount,
                            title = "UI 调试",
                            summary = "铺开全部组件与状态（仅 debug 构建）",
                            icon = ZhiIcons.layers,
                            onClick = { onNavigate("uiDebug") },
                        )
                    }
                }
            }

            item(key = "about") {
                M3eSectionHeader("关于")
                M3eSegmentedColumn {
                    M3eNavigationRow(
                        index = 0,
                        count = 1,
                        title = "关于 ZhiCode",
                        summary = "版本、源码、许可证与隐私政策",
                        icon = ZhiIcons.info,
                        onClick = { onNavigate("about") },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun M3eShizukuAccessRow(index: Int, count: Int) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(ShizukuBridge.state(context)) }
    var requesting by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        fun refresh() {
            state = ShizukuBridge.state(context)
        }
        val onPermission = Shizuku.OnRequestPermissionResultListener { _, _ ->
            requesting = false
            refresh()
        }
        val onBinderDead = Shizuku.OnBinderDeadListener { refresh() }
        val onBinderReceived = Shizuku.OnBinderReceivedListener { refresh() }
        runCatching { Shizuku.addRequestPermissionResultListener(onPermission) }
        runCatching { Shizuku.addBinderDeadListener(onBinderDead) }
        runCatching { Shizuku.addBinderReceivedListenerSticky(onBinderReceived) }
        refresh()
        onDispose {
            runCatching { Shizuku.removeRequestPermissionResultListener(onPermission) }
            runCatching { Shizuku.removeBinderDeadListener(onBinderDead) }
            runCatching { Shizuku.removeBinderReceivedListener(onBinderReceived) }
        }
    }

    val summary = when {
        requesting -> "等待授权结果…"
        hint != null -> hint.orEmpty()
        state == ShizukuBridge.GRANTED -> {
            val detail = ShizukuBridge.detail()
            ShizukuBridge.stateLabel(context) + (detail?.let { " · $it" } ?: "")
        }
        else -> ShizukuBridge.stateLabel(context) + " · Agent 还需要这一步才能真正执行命令"
    }

    M3eNavigationRow(
        index = index,
        count = count,
        title = "Shizuku 授权",
        valueText = summary,
        icon = ZhiIcons.terminal,
        onClick = {
            hint = null
            when (ShizukuBridge.state(context)) {
                ShizukuBridge.GRANTED -> hint = "已授权，无需重复申请"
                ShizukuBridge.NOT_RUNNING -> {
                    if (!ShizukuBridge.openManager(context)) {
                        hint = "没有找到 Shizuku 管理端：请先安装 Shizuku"
                    }
                }
                ShizukuBridge.UNAVAILABLE -> {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(ShizukuBridge.PROJECT_URL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.onFailure { hint = "无法打开项目主页：${it.message ?: it}" }
                }
                else -> {
                    requesting = true
                    hint = ShizukuBridge.requestPermission(SHIZUKU_REQUEST_CODE_M3E)
                    if (hint != null) requesting = false
                }
            }
        },
    )
}

private const val SHIZUKU_REQUEST_CODE_M3E = 4271
