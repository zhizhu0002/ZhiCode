package com.zhizhu.zhicode.compose.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.zhizhu.zhicode.compose.model.COMPACT_PERCENT_MAX
import com.zhizhu.zhicode.compose.model.COMPACT_PERCENT_MIN
import com.zhizhu.zhicode.compose.model.CONTEXT_WINDOW_PRESETS
import com.zhizhu.zhicode.compose.model.EffortLevel
import com.zhizhu.zhicode.compose.model.PermissionMode
import com.zhizhu.zhicode.compose.model.SettingsDraft
import com.zhizhu.zhicode.compose.model.ThemeMode
import com.zhizhu.zhicode.compose.model.WEB_RESULTS_MAX
import com.zhizhu.zhicode.compose.model.WEB_RESULTS_MIN
import com.zhizhu.zhicode.compose.model.WEB_TIMEOUT_MAX_SEC
import com.zhizhu.zhicode.compose.model.WEB_TIMEOUT_MIN_SEC
import com.zhizhu.zhicode.compose.model.WebSearchProvider
import com.zhizhu.zhicode.compose.model.formatTokenCountShort
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideInsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideOutsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiDialogWidth
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 蜘蛛 设置页：标题 + × / 分类 Tab / 设置行 / 取消·保存。
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
        maxWidth = ZhiDialogWidth.Wide,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        if (draft == null) return@OverlayDialog

        DialogShell(
            title = "ZhiCode 设置",
            titleAction = {
                IconButton(onClick = { onDismiss() }) {
                    Icon(
                        imageVector = ZhiIcons.close,
                        contentDescription = "关闭设置",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
            actions = {
                TextButton(text = "取消", onClick = onDismiss)
                PrimaryButton(
                    text = "保存",
                    onClick = onSave,
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            // 单页分组：不再用 Tab 分页 —— 六个分类的内容都不长，
            // 分组标题（SmallTitle）给出的结构已经足够扫读，
            // 少一层 Tab 交互、少一次"找不到设置在哪一页"的来回。
            AllSettingsPages(draft, onChange, onNavigate)
        }
    }
}

// ------------------------------------------------------------------ 各分类页

/** 主题模式的显示文案对齐参考图的「夜间模式 / 白天模式」。 */
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "白天模式"
    ThemeMode.DARK -> "夜间模式"
}

/**
 * 分组 = SmallTitle 标题 + 圆角 Card，间距照官方 SettingsPage：
 * 标题与卡片各自水平 12dp，卡片之间 12dp 垂直间隔。
 */
@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    SettingsGroupHeader(title, modifier = Modifier.padding(horizontal = 12.dp))
    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier.padding(horizontal = 12.dp),
        cornerRadius = ZhiRadius.card,
        insideMargin = PaddingValues(0.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun AllSettingsPages(
    draft: SettingsDraft,
    onChange: (SettingsDraft) -> Unit,
    onNavigate: (String) -> Unit,
) {
    SettingsGroup("外观") { AppearancePage(draft, onChange) }
    SettingsGroup("模型与权限") { ModelPermissionPage(draft, onChange, onNavigate) }
    SettingsGroup("Agent 与安全") { AgentSecurityPage(draft, onChange) }
    SettingsGroup("联网") { NetworkPage(draft, onChange) }
    SettingsGroup("上下文与项目") { ContextProjectPage(draft, onChange) }
    SettingsGroup("扩展功能") { ExtensionsPage(onNavigate) }
}

@Composable
private fun AppearancePage(draft: SettingsDraft, onChange: (SettingsDraft) -> Unit) {
    val scheme = MiuixTheme.colorScheme

    SettingsChoice(
        title = "主题模式",
        options = ThemeMode.entries.map(::themeLabel),
        selectedIndex = ThemeMode.entries.indexOf(draft.themeMode),
        onSelect = { onChange(draft.copy(themeMode = ThemeMode.entries[it])) },
        // summary 按官方 demo 惯例保持一句话：BasicComponent 的布局给行尾
        // 取值/箭头最多 60% 行宽，长说明折行后必与右侧取值挤压。
        summary = "右上角 ☼/☾ 可快速切换",
    )


}

@Composable
private fun ModelPermissionPage(
    draft: SettingsDraft,
    onChange: (SettingsDraft) -> Unit,
    onNavigate: (String) -> Unit,
) {
    SettingsEntry(
        title = "API 配置记录",
        valueText = "${draft.profileName} · ${draft.modelLabel}",
        summary = "密钥不会在设置页回填，点模型按钮可自动获取",
        onClick = { onNavigate("apiProfiles") },
    )

    SettingsChoice(
        title = "视觉图片输入（Vision）",
        options = listOf("开启（发送图片给模型）", "关闭（仅保留本地预览）"),
        selectedIndex = if (draft.visionEnabled) 0 else 1,
        onSelect = { onChange(draft.copy(visionEnabled = it == 0)) },
        summary = "模型不支持 vision 时请关闭",
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
    SettingsToggle(
        title = "Agent 沙箱全权调试",
        checked = draft.sandboxAgentFullAccess,
        onCheckedChange = { onChange(draft.copy(sandboxAgentFullAccess = it)) },
        summary = "允许容器内安装、UI 操作与 Frida；不含真机 host 与 Root",
        warn = true,
    )

    SettingsToggle(
        title = "Agent Root 权限",
        checked = draft.rootExecutionEnabled,
        onCheckedChange = { onChange(draft.copy(rootExecutionEnabled = it)) },
        summary = "高风险：需要 Magisk/KernelSU 授权；普通模式仍逐次确认",
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

    SettingsChoice(
        title = "上下文窗口",
        options = windowOptions.map(::formatTokenCountShort),
        selectedIndex = windowOptions.indexOf(draft.contextWindow),
        onSelect = { onChange(draft.copy(contextWindow = windowOptions[it])) },
        summary = "支持 128k / 1.5m 写法",
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
    CustomSystemPromptField(draft, onChange)
}

/**
 * 自定义头部提示词。
 *
 * 这一项**已经真的生效**：`WorkspaceViewModel.engineOverrides()` 会把它塞进
 * `SessionConfig.customSystemPrompt`，每次 `startTurn` 前重新 `configure`，
 * 所以它随下一次完整任务一起发给模型。
 *
 * 因此提示语必须如实说明两件事，否则用户会误判它的权限：
 * 一是它**不能**覆盖安全规则（权限模式、工具白名单、Root 限制由引擎侧强制）；
 * 二是它**只从下一轮生效**（当前这轮已经在跑，改了对它没用）。
 * 另外要提醒不要在这里写密钥——这段文本会原样进入请求。
 */
@Composable
private fun CustomSystemPromptField(draft: SettingsDraft, onChange: (SettingsDraft) -> Unit) {
    SettingsTextField(
        title = "自定义头部提示词",
        value = draft.customSystemPrompt,
        onValueChange = { onChange(draft.copy(customSystemPrompt = it)) },
        summary = "随请求发送，下一任务生效；不能覆盖安全规则，勿填密钥。" +
            if (draft.customSystemPrompt.isBlank()) "" else "当前 ${draft.customSystemPrompt.length} 字。",
        singleLine = false,
        minLines = 5,
    )
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
        summary = "与上下文历史一一绑定，保存后可恢复该项目记录",
    )
}

/**
 * 扩展功能。
 *
 * 这里只列**已经能用**的入口。原版还有两项（运行时 UI 画布、其他设置），当前处理如下：
 *
 * - 「运行时 UI 画布」：**画布的存储与工具是齐的**——`com.zhizhu.zhicode.UiCanvasStore`、
 *   `UiCanvasController`、以及已注册进 `ToolRegistry` 的 `ui_canvas` 工具都在，
 *   Agent 调用它也会正常落盘一份有界操作文档。缺的是**消费端**：
 *   `UiCanvasController.apply(View root, JSONObject document)` 是按稳定节点 id
 *   在 **Android View 树**上找 View 再改属性；Compose 这边没有这套 id，
 *   也没有任何代码读那份文档。所以现在点这个入口只会打开一个空壳面板，
 *   不如先不放入口。要接的话工作量在"给 Compose 界面定义稳定槽位 id
 *   并把文档作用上去"，不是重写存储。
 * - 「其他设置」：那一项原本只是「自定义头部提示词」的另一个入口。现在提示词输入框
 *   已经并入「上下文与项目」分类，同一个字段不需要两条路径，入口撤销。
 */
@Composable
private fun ExtensionsPage(onNavigate: (String) -> Unit) {
    SettingsEntry(
        title = "Model Context Protocol（MCP）",
        valueText = "MCP 服务器配置",
        onClick = { onNavigate("mcp") },
    )
}

// ------------------------------------------------------------------ 工具

/** `Color` → `#RRGGBB`。 */
private fun hexOf(color: Color): String = String.format("#%06X", color.toArgb() and 0xFFFFFF)
