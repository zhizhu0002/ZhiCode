package com.iqge.iqcode.compose.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.iqge.iqcode.compose.model.COMPACT_PERCENT_MAX
import com.iqge.iqcode.compose.model.COMPACT_PERCENT_MIN
import com.iqge.iqcode.compose.model.CONTEXT_WINDOW_PRESETS
import com.iqge.iqcode.compose.model.EffortLevel
import com.iqge.iqcode.compose.model.PermissionMode
import com.iqge.iqcode.compose.model.SettingsCategory
import com.iqge.iqcode.compose.model.SettingsDraft
import com.iqge.iqcode.compose.model.ThemeMode
import com.iqge.iqcode.compose.model.WEB_RESULTS_MAX
import com.iqge.iqcode.compose.model.WEB_RESULTS_MIN
import com.iqge.iqcode.compose.model.WEB_TIMEOUT_MAX_SEC
import com.iqge.iqcode.compose.model.WEB_TIMEOUT_MIN_SEC
import com.iqge.iqcode.compose.model.WebSearchProvider
import com.iqge.iqcode.compose.model.formatTokenCountShort
import com.iqge.iqcode.compose.theme.IqColors
import com.iqge.iqcode.compose.ui.IqIcons
import com.iqge.iqcode.compose.ui.dialogs.DialogShell
import com.iqge.iqcode.compose.ui.dialogs.DialogWideInsideMargin
import com.iqge.iqcode.compose.ui.dialogs.DialogWideOutsideMargin
import com.iqge.iqcode.compose.ui.dialogs.PrimaryButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * IQ Code 设置页：标题 + × / 分类 Tab / 设置行 / 取消·保存。
 *
 * ## 设置行不是手写的
 *
 * 页面骨架（弹窗外壳、分类 Tab、标题、底部按钮）用 Miuix 组件搭；
 * 每一条设置项走 `ui/settings/SettingsRows.kt`，那里全部转发到 `miuix-preference`
 * 的 `*Preference` 组件（见该文件表格）。本文件只负责**把 SettingsDraft 的字段
 * 接到那些组件上**，不再自己画值框、下拉和滚轮。
 *
 * ## 容器为什么是 `OverlayDialog`
 *
 * `WindowDialog` 创建**独立 Android Window**，`Scaffold` 提供的 `popupHost` 不会被它继承，
 * 于是 `Overlay*` 系列（本页所有选择器）在里面不可靠，而且主窗口的 `LayerBackdrop`
 * 也采不到它的背景。`OverlayDialog` 参数集与它一致，是 drop-in 替换。
 *
 * ## draft 语义
 *
 * 所有改动都通过 [onChange] 回传新的 [SettingsDraft]，「保存」才写回 state，
 * 「取消」直接丢弃 draft，所以取消是真正无副作用的。
 */
@Composable
fun SettingsDialog(
    draft: SettingsDraft?,
    onChange: (SettingsDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    top.yukonga.miuix.kmp.overlay.OverlayDialog(
        show = draft != null,
        onDismissRequest = onDismiss,
        largeScreen = true,
        maxWidth = 860.dp,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        if (draft == null) return@OverlayDialog

        DialogShell(
            title = "IQ Code 设置",
            titleAction = {
                IconButton(onClick = { onDismiss() }) {
                    Icon(
                        imageVector = IqIcons.close,
                        contentDescription = "关闭设置",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
            header = {
                CategoryTabs(
                    selected = draft.category,
                    onSelect = { onChange(draft.copy(category = it)) },
                )
            },
            groupBody = true,
            fillBody = true,
            actions = {
                // 取消＝纯文字。这里以前手挑了三个颜色，其中 `disabledColor = Transparent`
                // 会让禁用态完全看不见。Miuix 的 DialogShell.SecondaryButton 已经得出
                // 正确结论：直接用 TextButton 默认配色即可。
                TextButton(text = "取消", onClick = onDismiss)
                PrimaryButton(
                    text = "保存",
                    onClick = onSave,
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            when (draft.category) {
                SettingsCategory.APPEARANCE -> AppearancePage(draft, onChange)
                SettingsCategory.MODEL_PERMISSION -> ModelPermissionPage(draft, onChange, onNavigate)
                SettingsCategory.AGENT_SECURITY -> AgentSecurityPage(draft, onChange)
                SettingsCategory.NETWORK -> NetworkPage(draft, onChange)
                SettingsCategory.CONTEXT_PROJECT -> ContextProjectPage(draft, onChange)
                SettingsCategory.EXTENSIONS -> ExtensionsPage(onNavigate)
            }
        }
    }
}

// ------------------------------------------------------------------ 分类 Tab

/**
 * 分类 Tab。转发到 Miuix [TabRow]（自带胶囊选中态、等分与滚动行为）。
 *
 * [TabRow] 支持 `tabs` 数量超过一屏时横向滚动，且选中项会自动滚入视野 ——
 * 这两件事以前是手写 `Row + horizontalScroll + LaunchedEffect(animateScrollTo)` 做的。
 */
@Composable
private fun CategoryTabs(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
) {
    TabRow(
        // 用简写标签：Miuix TabRow 会按容器宽度分配各项宽度，长标签（"模型与权限"）
        // 在手机宽度下必然被省略号截断。全称仍保留在 [SettingsCategory.label] 里。
        tabs = SettingsCategory.entries.map { it.tabLabel },
        selectedTabIndex = SettingsCategory.entries.indexOf(selected).coerceAtLeast(0),
        onTabSelected = { index -> SettingsCategory.entries.getOrNull(index)?.let(onSelect) },
        // 6 个分类在手机上放不下。给足最小宽度让汉字完整显示，放不下时 TabRow 自己
        // 横向滚动（minWidth 太小会变成"上…"这种截断，比滚动更难看）。
        minWidth = 72.dp,
        maxWidth = 104.dp,
    )
}

// ------------------------------------------------------------------ 各分类页

/** 主题模式的显示文案对齐参考图的「夜间模式 / 白天模式」。 */
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "白天模式"
    ThemeMode.DARK -> "夜间模式"
}

@Composable
private fun AppearancePage(draft: SettingsDraft, onChange: (SettingsDraft) -> Unit) {
    val scheme = MiuixTheme.colorScheme

    SettingsGroupHeader("外观")
    SettingsChoice(
        title = "主题模式",
        options = ThemeMode.entries.map(::themeLabel),
        selectedIndex = ThemeMode.entries.indexOf(draft.themeMode),
        onSelect = { onChange(draft.copy(themeMode = ThemeMode.entries[it])) },
        summary = "右上角 ☼/☾ 可快速切换；切换时会平滑重建界面。",
    )

    // 这几行展示**本工程实际生效**的层级色，而不是主题里的原始 token：
    // 背板/面板/卡片三层的取值由 IqColors 按深浅切换，直接读主题会显示成另一套值。
    SettingsReadOnly("背板色", hexOf(IqColors.backdrop()))
    SettingsReadOnly("面板色", hexOf(IqColors.panelSurface()))
    SettingsReadOnly("卡片色", hexOf(IqColors.cardSurface()))
    SettingsReadOnly("文字色", hexOf(scheme.onBackground), scheme.onBackground)
    SettingsReadOnly("强调色", hexOf(scheme.primary), scheme.primary)
    SettingsReadOnly("错误色", hexOf(scheme.error), scheme.error)

    SettingsFootnote(
        "层级由 theme/IqTheme.kt 的 IqColors 统一给出：背板深色 #242424 / 浅色 #EDEDED，" +
            "侧栏与面板为纯黑/纯白，卡片再偏一档。" +
            "其余颜色来自 Miuix 主题与系统动态取色。",
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun ModelPermissionPage(
    draft: SettingsDraft,
    onChange: (SettingsDraft) -> Unit,
    onNavigate: (String) -> Unit,
) {
    SettingsGroupHeader("模型与权限")
    SettingsEntry(
        title = "API 配置记录",
        valueText = "${draft.profileName} · ${draft.modelLabel}",
        summary = "API 地址、协议、默认模型和密钥按配置记录独立保存；密钥不会在设置页回填。" +
            "点模型按钮可自动获取当前 API 的模型。",
        onClick = { onNavigate("apiProfiles") },
    )

    SettingsChoice(
        title = "视觉图片输入（Vision）",
        options = listOf("开启（发送图片给模型）", "关闭（仅保留本地预览）"),
        selectedIndex = if (draft.visionEnabled) 0 else 1,
        onSelect = { onChange(draft.copy(visionEnabled = it == 0)) },
        summary = "模型不支持 vision 时请选择关闭；当前图片和历史图片都不会发送，" +
            "但聊天预览与会话记录仍会保留。",
    )

    SettingsChoice(
        title = "推理强度",
        options = EffortLevel.entries.map { it.label },
        selectedIndex = EffortLevel.entries.indexOf(draft.effort),
        onSelect = { onChange(draft.copy(effort = EffortLevel.entries[it])) },
    )

    SettingsChoice(
        title = "权限模式",
        options = PermissionMode.entries.map { it.label },
        selectedIndex = PermissionMode.entries.indexOf(draft.permissionMode),
        onSelect = { onChange(draft.copy(permissionMode = PermissionMode.entries[it])) },
        summary = draft.permissionMode.detail,
    )
}

@Composable
private fun AgentSecurityPage(draft: SettingsDraft, onChange: (SettingsDraft) -> Unit) {
    SettingsGroupHeader("Agent 与安全")
    SettingsToggle(
        title = "Agent 沙箱全权调试",
        checked = draft.sandboxAgentFullAccess,
        onCheckedChange = { onChange(draft.copy(sandboxAgentFullAccess = it)) },
        summary = "只绕过 IQSandbox/Sandbox Debug 的逐次确认：允许容器内安装、UI 操作、动态内存、" +
            "Frida Hook/脚本。不会自动放开真机 host、Root、zygote/SystemUI 或其他无关 App。",
        warn = true,
    )

    SettingsToggle(
        title = "Agent Root 权限",
        checked = draft.rootExecutionEnabled,
        onCheckedChange = { onChange(draft.copy(rootExecutionEnabled = it)) },
        summary = "高风险功能：需要 Magisk/KernelSU 和 su 授权。开启后主 Agent 与通用子 Agent " +
            "可请求 Root；普通权限模式仍会逐次确认。",
        warn = true,
    )

    SettingsToggle(
        title = "强制后台保活",
        checked = draft.forcedKeepAliveEnabled,
        onCheckedChange = { onChange(draft.copy(forcedKeepAliveEnabled = it)) },
        summary = "开启前台服务 + WakeLock；Root 时还会强制系统后台策略。",
    )
}

@Composable
private fun NetworkPage(draft: SettingsDraft, onChange: (SettingsDraft) -> Unit) {
    SettingsGroupHeader("联网")
    SettingsToggle(
        title = "联网搜索",
        checked = draft.webSearchEnabled,
        onCheckedChange = { onChange(draft.copy(webSearchEnabled = it)) },
    )

    SettingsChoice(
        title = "搜索后端",
        options = WebSearchProvider.entries.map { it.label },
        selectedIndex = WebSearchProvider.entries.indexOf(draft.webSearchProvider),
        onSelect = { onChange(draft.copy(webSearchProvider = WebSearchProvider.entries[it])) },
        summary = draft.webSearchProvider.detail,
    )

    // 纯数字项走 Miuix 滚轮选择器
    SettingsNumber(
        title = "默认搜索结果数（1-10）",
        value = draft.webSearchMaxResults,
        options = (WEB_RESULTS_MIN..WEB_RESULTS_MAX).toList(),
        onValueChange = { onChange(draft.copy(webSearchMaxResults = it)) },
    )

    SettingsNumber(
        title = "联网超时（秒）",
        value = draft.webSearchTimeoutSec,
        options = (WEB_TIMEOUT_MIN_SEC..WEB_TIMEOUT_MAX_SEC step 5).toList(),
        onValueChange = { onChange(draft.copy(webSearchTimeoutSec = it)) },
    )
}

/** 上下文窗口的候选：预设值 + 当前值（当前值可能来自自定义输入）。 */
private fun contextWindowOptions(current: Int): List<Int> =
    (CONTEXT_WINDOW_PRESETS + current).distinct().sorted()

@Composable
private fun ContextProjectPage(draft: SettingsDraft, onChange: (SettingsDraft) -> Unit) {
    val windowOptions = contextWindowOptions(draft.contextWindow)

    SettingsGroupHeader("上下文与项目")
    SettingsChoice(
        title = "上下文窗口",
        options = windowOptions.map(::formatTokenCountShort),
        selectedIndex = windowOptions.indexOf(draft.contextWindow),
        onSelect = { onChange(draft.copy(contextWindow = windowOptions[it])) },
        summary = "也可直接输入 128k / 1.5m 这类写法，保存时按你填的数值生效。",
    )

    SettingsToggle(
        title = "上下文压缩",
        checked = draft.autoCompact,
        onCheckedChange = { onChange(draft.copy(autoCompact = it)) },
    )

    val percentOptions = if (draft.autoCompact) {
        (COMPACT_PERCENT_MIN..COMPACT_PERCENT_MAX step 5).toList()
    } else {
        emptyList()
    }
    SettingsNumber(
        title = "自动压缩上限（50-100%，安全缓冲优先）",
        value = draft.autoCompactPercent,
        options = percentOptions,
        onValueChange = { onChange(draft.copy(autoCompactPercent = it)) },
        summary = if (draft.autoCompact) null else "已关闭自动压缩，此项不生效。",
    )

    ProjectPathField(draft, onChange)
}

/** 项目目录是自由文本，走 `BasicComponent` 的 `bottomAction` 槽位放输入框。 */
@Composable
private fun ProjectPathField(draft: SettingsDraft, onChange: (SettingsDraft) -> Unit) {
    var text by remember(draft.projectPath) { mutableStateOf(draft.projectPath) }

    SettingsTextField(
        title = "项目目录",
        value = text,
        onValueChange = {
            text = it
            // 失焦前就同步回 draft，避免用户点「保存」时漏掉最后一次输入
            onChange(draft.copy(projectPath = it.trim()))
        },
        summary = "项目路径与上下文历史一一绑定；保存新路径后会自动显示该项目可恢复的历史记录。",
    )
}

@Composable
private fun ExtensionsPage(onNavigate: (String) -> Unit) {
    SettingsGroupHeader("扩展功能")
    SettingsEntry(
        title = "Model Context Protocol（MCP）",
        valueText = "MCP 服务器配置",
        onClick = { onNavigate("mcp") },
    )
    SettingsEntry(
        title = "无需重编译的界面调整",
        valueText = "运行时 UI 画布",
        onClick = { onNavigate("canvas") },
    )
    SettingsEntry(
        title = "其他设置",
        valueText = "自定义头部提示词",
        onClick = { onNavigate("other") },
    )
}

// ------------------------------------------------------------------ 工具

/** `Color` → `#RRGGBB`。 */
private fun hexOf(color: Color): String = String.format("#%06X", color.toArgb() and 0xFFFFFF)
