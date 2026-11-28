@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)

package com.zhizhu.zhicode.compose.ui.pages.miuix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.COMPACT_PERCENT_MAX
import com.zhizhu.zhicode.compose.model.COMPACT_PERCENT_MIN
import com.zhizhu.zhicode.compose.model.MAX_CONCURRENT_SUBAGENTS
import com.zhizhu.zhicode.compose.model.MIN_CONCURRENT_SUBAGENTS
import com.zhizhu.zhicode.compose.model.EffortLevel
import com.zhizhu.zhicode.compose.model.ExtensionSummary
import com.zhizhu.zhicode.compose.model.PermissionMode
import com.zhizhu.zhicode.compose.model.SettingsDraft
import com.zhizhu.zhicode.compose.model.ThemeMode
import com.zhizhu.zhicode.compose.model.WEB_RESULTS_MAX
import com.zhizhu.zhicode.compose.model.WEB_RESULTS_MIN
import com.zhizhu.zhicode.compose.model.WEB_TIMEOUT_MAX_SEC
import com.zhizhu.zhicode.compose.model.WEB_TIMEOUT_MIN_SEC
import com.zhizhu.zhicode.compose.model.formatTokenCountShort
import com.zhizhu.zhicode.compose.model.parseTokenCount
import com.zhizhu.zhicode.compose.ui.PageFeedbackSlot
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideInsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideOutsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiDialogWidth
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import com.zhizhu.zhicode.compose.ui.settings.SettingsChoice
import com.zhizhu.zhicode.compose.ui.settings.SettingsEntry
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsIconPlate
import com.zhizhu.zhicode.compose.ui.settings.SettingsIntField
import com.zhizhu.zhicode.compose.ui.settings.SettingsPlateColors
import com.zhizhu.zhicode.compose.ui.settings.SettingsTextField
import com.zhizhu.zhicode.compose.ui.settings.SettingsToggle
import com.zhizhu.zhicode.compose.ui.settings.ShizukuAccessRow
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsPage(
    draft: SettingsDraft?,
    onChange: (SettingsDraft) -> Unit,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    /** 「扩展」组的数量摘要（MCP / 技能 / 角色卡）。 */
    extensions: ExtensionSummary = ExtensionSummary(),
    brandSignature: String = "",
    onBrandSignatureChange: (String) -> Unit = {},
) {
    // 整页设置，骨架全部用 Miuix 原生组件，照官方 example 的 SettingsPage 模式
    if (draft == null) return
    // 重组探针：日志里 `recompose/s SettingsPage=N` 说明这一层被反复重建。
    // 没有这一行就无法区分"设置页自己重组"和"下面某个分组在重组"。
    com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace.countRecompose("SettingsPage")

    val scheme = MiuixTheme.colorScheme
    val listState = androidx.compose.runtime.saveable.rememberSaveable(
        saver = androidx.compose.foundation.lazy.LazyListState.Saver,
    ) { androidx.compose.foundation.lazy.LazyListState() }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    var brandDialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            // rikkahub 用 LargeFlexibleTopAppBar（大标题 + 滚动折叠）；
            TopAppBar(
                title = "设置",
                largeTitle = "设置",
                scrollBehavior = topAppBarScrollBehavior,
                color = scheme.surface,
                navigationIcon = {
                    // 官方 BackNavigationIcon 同款：原生 IconButton + 左箭头。
                    // 字形走 ZhiIcons（Material Symbols），与整页其余图标同源。
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = onDismiss) {
                        Icon(
                            painter = ZhiIcons.back,
                            contentDescription = "返回",
                            tint = scheme.onBackground,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .overScrollVertical()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                // ---- 每个分组各自一个 item，而不是全部塞进一个 item 
                item(key = "general", contentType = "settings-group") {
                    SettingsGroup("通用") {
                        GeneralPage(
                            draft = draft,
                            onChange = onChange,
                            onNavigate = onNavigate,
                            brandSignature = brandSignature,
                            onEditBrandSignature = { brandDialogOpen = true },
                        )
                    }
                }
                item(key = "agent", contentType = "settings-group") { SettingsGroup("Agent 与安全") { AgentSecurityPage(draft, onChange) } }
                item(key = "extensions", contentType = "settings-group") { SettingsGroup("扩展") { ExtensionsPage(onNavigate, extensions) } }
                /* 「关于 ZhiCode」单独成组，并**给它一个标题**。 */
                item(key = "meta", contentType = "settings-group") {
                    SettingsGroup("关于") { AboutEntryPage(onNavigate) }
                }
            }
            // Miuix 原生滚动条
            PageFeedbackSlot(modifier = Modifier.align(Alignment.BottomCenter))
            BrandSignatureDialog(
                show = brandDialogOpen,
                initial = brandSignature,
                onConfirm = { next ->
                    brandDialogOpen = false
                    onBrandSignatureChange(next)
                },
                onDismiss = { brandDialogOpen = false },
            )
        }
    }
}

// ------------------------------------------------------------------ 各分类页

@Composable
private fun AboutEntryPage(onNavigate: (String) -> Unit) {
    com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace.countRecompose("AboutEntryPage")
    SettingsIconEntry(
        title = "初始引导",
        summary = "权限授权、运行环境与模型配置向导",
        icon = ZhiIcons.runtime,
        plate = SettingsPlateColors.blue,
        onClick = { onNavigate("oobe") },
    )
    SettingsIconEntry(
        title = "关于 ZhiCode",
        summary = "版本、源码、许可证与隐私政策",
        icon = ZhiIcons.info,
        plate = SettingsPlateColors.gray,
        onClick = { onNavigate("about") },
    )
}

/** 主题模式的显示文案对齐参考图的「夜间模式 / 白天模式」。 */
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "白天模式"
    ThemeMode.DARK -> "夜间模式"
}

@Composable
private fun GeneralPage(
    draft: SettingsDraft,
    onChange: (SettingsDraft) -> Unit,
    onNavigate: (String) -> Unit,
    brandSignature: String,
    onEditBrandSignature: () -> Unit,
) {
    com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace.countRecompose("GeneralPage")
    SettingsEntry(
        title = "供应商管理",
        valueText = "${draft.profileName} · ${draft.modelLabel}",
        onClick = { onNavigate("apiProfiles") },
        summary = "配置多供应商、模型能力、思考档位及上下文压缩与提示词",
        icon = ZhiIcons.link,
        plate = SettingsPlateColors.blue,
    )
    SettingsEntry(
        title = "顶栏签名",
        valueText = brandSignature.trim().ifBlank { "ZhiCode（默认）" },
        onClick = onEditBrandSignature,
        icon = ZhiIcons.edit,
        plate = SettingsPlateColors.purple,
    )
    SettingsEntry(
        title = "主题设置",
        valueText = "深浅 · ${draft.themeMode.label} · 预设色 / 模糊 / 缩放",
        onClick = { onNavigate("theme") },
        // 与顶栏那个明暗按钮同一个字形（半明半暗的圆）。
        icon = ZhiIcons.theme,
        plate = SettingsPlateColors.orange,
        // 不写「右上角 ☼/☾ 可快速切换」——顶栏早就没有这个快捷键了，
        // 指向不存在入口的说明比没有说明更糟。
    )
    SettingsEntry(
        title = "聊天显示",
        valueText = "字体 · 正文字号",
        onClick = { onNavigate("chatDisplay") },
        summary = "自定义聊天正文字体（ttf/otf/ttc）与字号比例；不影响按钮与图标大小",
        icon = ZhiIcons.chat,
        plate = SettingsPlateColors.blue,
    )
    NetworkPage(draft, onChange, onNavigate)
}

@Composable
private fun AgentSecurityPage(draft: SettingsDraft, onChange: (SettingsDraft) -> Unit) {
    com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace.countRecompose("AgentSecurityPage")
    /* 这里原本第一项是「Agent 沙箱全权调试」（sandboxAgentFullAccess） */

    SettingsIntField(
        title = "子 Agent 最大并发数",
        value = draft.maxConcurrentSubagents,
        min = MIN_CONCURRENT_SUBAGENTS,
        max = MAX_CONCURRENT_SUBAGENTS,
        parse = { it.trim().toIntOrNull() },
        onValueChange = { onChange(draft.copy(maxConcurrentSubagents = it)) },
        summary = "同时运行的子 Agent 上限（${MIN_CONCURRENT_SUBAGENTS}–${MAX_CONCURRENT_SUBAGENTS}）；超过上限的新调用会提示上限并需稍后重试",
        icon = ZhiIcons.tune,
        plate = SettingsPlateColors.purple,
    )

    SettingsToggle(
        title = "Agent Root 权限",
        checked = draft.rootExecutionEnabled,
        onCheckedChange = { onChange(draft.copy(rootExecutionEnabled = it)) },
        summary = "高风险：需要 Magisk/KernelSU 授权；普通模式仍逐次确认",
        warn = true,
        // 终端字形：这一项开通的正是"在终端里以 root 身份跑命令"。
        icon = ZhiIcons.terminal,
        plate = SettingsPlateColors.red,
    )

    /* Shizuku：不 root 也能拿到 shell 身份（uid 2000）。 */
    SettingsToggle(
        title = "Agent Shizuku 权限",
        checked = draft.shizukuExecutionEnabled,
        onCheckedChange = { onChange(draft.copy(shizukuExecutionEnabled = it)) },
        summary = "无 Root 的 shell 身份：pm/am/settings/dumpsys/input 这类系统命令；普通模式仍逐次确认",
        warn = true,
        icon = ZhiIcons.terminal,
        plate = SettingsPlateColors.green,
    )
    ShizukuAccessRow()

    SettingsToggle(
        title = "强制后台保活",
        checked = draft.forcedKeepAliveEnabled,
        onCheckedChange = { onChange(draft.copy(forcedKeepAliveEnabled = it)) },
        summary = "开启前台服务 + WakeLock；Root 时还会强制系统后台策略。",
        // 循环箭头 = 心跳/保活。不借 `refresh` 作"刷新"，两者本来就是同一个字形，
        // 区别在语义：这里是"一直活着"，不是"重新拉一次"。
        icon = ZhiIcons.refresh,
        plate = SettingsPlateColors.green,
    )

    // 输入法：两种输入类型各有代价，只能让用户按自己机型选（默认关 = 正常输入法）。
    SettingsToggle(
        title = "终端字符模式输入",
        checked = draft.terminalCharMode,
        onCheckedChange = { onChange(draft.copy(terminalCharMode = it)) },
        summary = "若终端里调出的不是你常用的输入法（而是安全/密码键盘），保持关闭；" +
            "若个别机型在终端里切换输入法后状态残留，再开启。",
        icon = ZhiIcons.keyboard,
        plate = SettingsPlateColors.gray,
    )
}

@Composable
private fun NetworkPage(
    draft: SettingsDraft,
    onChange: (SettingsDraft) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    SettingsToggle(
        title = "联网搜索",
        checked = draft.webSearchEnabled,
        onCheckedChange = { onChange(draft.copy(webSearchEnabled = it)) },
        icon = ZhiIcons.search,
        plate = SettingsPlateColors.blue,
    )

    // 关闭联网搜索时后续配置全部折叠（官方 SettingsPage 的 AnimatedVisibility 模式）
    AnimatedVisibility(
        visible = draft.webSearchEnabled,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            // 搜索服务已改成**独立整页**（RikkaHub 形态：多服务列表 + 每服务专属选项）。
            BasicComponent(
                title = "搜索服务",
                titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
                summary = "添加 Tavily / Exa / Brave / Perplexity / SearXNG 等；点一个设为当前使用",
                summaryColor = BasicComponentDefaults.summaryColor(
                    color = scheme.onSurfaceVariantSummary,
                ),
                startAction = {
                    SettingsIconPlate(icon = ZhiIcons.cloud, color = SettingsPlateColors.blue)
                },
                onClick = { onNavigate("searchServices") },
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            )

            SliderPreference(
                title = "默认搜索结果数（1-50）",
                value = draft.webSearchMaxResults.toFloat(),
                onValueChange = { onChange(draft.copy(webSearchMaxResults = it.toInt())) },
                valueRange = WEB_RESULTS_MIN.toFloat()..WEB_RESULTS_MAX.toFloat(),
                steps = WEB_RESULTS_MAX - WEB_RESULTS_MIN - 1,
                valueText = "${draft.webSearchMaxResults} 条",
                summary = "服务未单独指定条数时用它",
                startAction = { SettingsIconPlate(icon = ZhiIcons.listCount, color = SettingsPlateColors.blue) },
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            )

            SliderPreference(
                title = "联网超时（秒）",
                value = draft.webSearchTimeoutSec.toFloat(),
                onValueChange = { onChange(draft.copy(webSearchTimeoutSec = it.toInt())) },
                valueRange = WEB_TIMEOUT_MIN_SEC.toFloat()..WEB_TIMEOUT_MAX_SEC.toFloat(),
                steps = WEB_TIMEOUT_MAX_SEC - WEB_TIMEOUT_MIN_SEC - 1,
                valueText = "${draft.webSearchTimeoutSec} 秒",
                summary = "支持 ${WEB_TIMEOUT_MIN_SEC}-${WEB_TIMEOUT_MAX_SEC} 秒",
                startAction = { SettingsIconPlate(icon = ZhiIcons.timeout, color = SettingsPlateColors.blue) },
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/** 扩展功能。 */
@Composable
private fun ExtensionsPage(onNavigate: (String) -> Unit, extensions: ExtensionSummary) {
    com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace.countRecompose("ExtensionsPage")
    // rikkahub 式入口行：图标 + 标题 + 副标题；Miuix 用 ArrowPreference 的 startAction。
    SettingsIconEntry(
        title = "Model Context Protocol（MCP）",
        summary = if (!extensions.loaded) {
            "正在统计…"
        } else {
            buildString {
                append("${extensions.mcpTotal} 台服务器")
                if (extensions.mcpTotal > 0) {
                    append(" · ${extensions.mcpEnabled} 台启用")
                    if (extensions.mcpConnected > 0) append(" · ${extensions.mcpConnected} 台已连接")
                } else {
                    append(" · 还没有配置")
                }
            }
        },
        icon = ZhiIcons.mindMap,
        plate = SettingsPlateColors.purple,
        onClick = { onNavigate("mcp") },
    )
    SettingsIconEntry(
        title = "Skill 管理器",
        summary = if (!extensions.loaded) {
            "正在统计…"
        } else if (extensions.skillTotal == 0) {
            "还没有技能 · 可按需加载的资料包"
        } else {
            "${extensions.skillTotal} 个技能 · 项目级 ${extensions.projectSkills} / 用户级 ${extensions.userSkills}"
        },
        icon = ZhiIcons.skill,
        plate = SettingsPlateColors.purple,
        onClick = { onNavigate("skills") },
    )
    SettingsIconEntry(
        title = "自定义角色卡",
        summary = when {
            !extensions.loaded -> "正在统计…"
            extensions.roleCardTotal == 0 -> "还没有角色卡 · 可切换的多份人设指令"
            else -> "${extensions.roleCardTotal} 张角色卡 · 同一时刻只有一张生效"
        },
        icon = ZhiIcons.roleCard,
        plate = SettingsPlateColors.green,
        onClick = { onNavigate("roleCards") },
    )
    /* 「记忆文件」放在这一组里。 */
    SettingsIconEntry(
        title = "记忆文件 · ZhiCode.md",
        summary = "项目级与用户级说明文件",
        icon = ZhiIcons.file,
        plate = SettingsPlateColors.orange,
        onClick = { onNavigate("memory") },
    )

    // 「关于」已移到独立的「关于」分组（`AboutEntryPage`）：它是**元信息**，
    // 不是扩展能力，留在这里会被当成又一个扩展入口。

    // 仅 debug 构建：把工程真正在用的组件按类别铺开（含对话流/任务卡/输入器），
    // 用来复现"看起来不对"这类问题 —— 详见 ui/debug/UiDebugPage.kt。
    if (com.zhizhu.zhicode.compose.BuildConfig.DEBUG) {
        SettingsIconEntry(
            title = "UI 调试",
            summary = "铺开全部组件与状态（仅 debug 构建）",
            icon = ZhiIcons.layers,
            plate = SettingsPlateColors.gray,
            onClick = { onNavigate("uiDebug") },
        )
    }
}

/** 设置页的入口行：图标块 + 标题 + 副标题 + 箭头（Miuix [ArrowPreference]）。 */
@Composable
private fun SettingsIconEntry(
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.painter.Painter,
    plate: Color,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        summary = summary,
        startAction = { SettingsIconPlate(icon = icon, color = plate) },
        onClick = onClick,
    )
}

@Composable
private fun BrandSignatureDialog(
    show: Boolean,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(show, initial) { mutableStateOf(initial) }
    OverlayDialog(
        show = show,
        onDismissRequest = onDismiss,
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        DialogShell(
            title = "顶栏签名",
            actions = {
                if (initial.isNotBlank() || text.isNotBlank()) {
                    SecondaryButton(
                        text = "恢复默认",
                        onClick = { onConfirm("") },
                    )
                }
                SecondaryButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.padding(start = 8.dp),
                )
                PrimaryButton(
                    text = "确定",
                    onClick = { onConfirm(text.trim()) },
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            ZhiTextField(
                value = text,
                onValueChange = { text = it.replace('\n', ' ') },
                label = "输入自定义签名（留空显示 ZhiCode）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ------------------------------------------------------------------ 工具

