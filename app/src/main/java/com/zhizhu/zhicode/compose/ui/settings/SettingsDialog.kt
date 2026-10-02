@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)

package com.zhizhu.zhicode.compose.ui.settings

import androidx.compose.foundation.layout.Column
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.COMPACT_PERCENT_MAX
import com.zhizhu.zhicode.compose.model.COMPACT_PERCENT_MIN
import com.zhizhu.zhicode.compose.model.EffortLevel
import com.zhizhu.zhicode.compose.model.PermissionMode
import com.zhizhu.zhicode.compose.model.SettingsDraft
import com.zhizhu.zhicode.compose.model.ThemeMode
import com.zhizhu.zhicode.compose.model.WEB_RESULTS_MAX
import com.zhizhu.zhicode.compose.model.WEB_RESULTS_MIN
import com.zhizhu.zhicode.compose.model.WEB_TIMEOUT_MAX_SEC
import com.zhizhu.zhicode.compose.model.WEB_TIMEOUT_MIN_SEC
import com.zhizhu.zhicode.compose.model.formatTokenCountShort
import com.zhizhu.zhicode.compose.model.parseTokenCount
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 蜘蛛 设置页：标题 + 返回 / 分组设置行。
 *
 * ## 没有「保存」按钮：改动即时生效
 *
 * 原来这里有一对「取消 / 保存」，draft 只在点保存时才写回 state。实际上「保存」
 * 除了把值搬进内存 state 之外什么都没做（不落盘、不下发引擎），却让每次改一项都要
 * 先把页面拉到底点一下右上角 —— 所以改成**自动保存**：任一改动立刻写回 state，
 * 返回只是关页面。
 *
 * draft 仍然保留，但语义变了：它不再是一份“待提交副本”，而是**这一屏的显示源**
 * （见 `AppScaffold` 的 `settingsUi = state.settingsDraft ?: lastSettingsDraft`），
 * 也承载「项目目录留空 = 不改动」这类字段级语义。
 *
 * ## 设置行不是手写的
 *
 * 页面骨架（弹窗外壳、分组、标题）用 Miuix 组件搭；
 * 每一条设置项走 `ui/settings/SettingsRows.kt`，那里全部转发到 `miuix-preference`
 * 的 `*Preference` 组件（见该文件表格）。本文件只负责**把 SettingsDraft 的字段
 * 接到那些组件上**，不再自己画值框、下拉和滚轮。
 *
 * ## 容器为什么是 `OverlayDialog`
 *
 * `WindowDialog` 创建**独立 Android Window**，`Scaffold` 提供的 `popupHost` 不会被它继承，
 * 于是 `Overlay*` 系列（本页所有选择器）在里面不可靠，而且主窗口的 `LayerBackdrop`
 * 也采不到它的背景。`OverlayDialog` 参数集与它一致，是 drop-in 替换。
 */
@Composable
fun SettingsDialog(
    draft: SettingsDraft?,
    onChange: (SettingsDraft) -> Unit,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    // 整页设置，骨架全部用 Miuix 原生组件，照官方 example 的 SettingsPage 模式：
    // Scaffold + SmallTopAppBar(MiuixScrollBehavior) + LazyColumn(overScroll+nestedScroll)。
    // 返回键 = MiuixIcons.Back + 原生 IconButton。顶栏**没有动作按钮**：改动即时生效，
    // 没有需要用户再确认一次的东西（见文件顶部「没有保存按钮」一节）。
    if (draft == null) return

    val scheme = MiuixTheme.colorScheme
    val listState = rememberLazyListState()
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            // rikkahub 用 LargeFlexibleTopAppBar（大标题 + 滚动折叠）；
            // Miuix 对应组件 = TopAppBar（自带 largeTitle 折叠行为）。
            TopAppBar(
                title = "设置",
                largeTitle = "设置",
                scrollBehavior = topAppBarScrollBehavior,
                color = scheme.surface,
                navigationIcon = {
                    // 官方 BackNavigationIcon 同款：原生 IconButton + MiuixIcons.Back
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = MiuixIcons.Back,
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
                item(key = "settingsBody") {
                    AllSettingsPages(draft, onChange, onNavigate)
                }
            }
            // Miuix 原生滚动条
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
            )
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

@Composable
private fun AllSettingsPages(
    draft: SettingsDraft,
    onChange: (SettingsDraft) -> Unit,
    onNavigate: (String) -> Unit,
) {
    // rikkahub 式 hub：主页主要是「导航行（图标+标题+副标题+箭头）」，
    // 少数高频即时项（主题模式）内联；细节全部下放到二级页与原分组。
    // 顺序按使用频率：模型最先，扩展收尾。
    // rikkahub 的分组顺序：通用最先（主题/联网这类看一眼就走的），模型服务其次，
    // 扩展收尾。组名也从功能视角改成 rikkahub 的叫法。
    SettingsGroup("通用") { GeneralPage(draft, onChange, onNavigate) }
    SettingsGroup("模型与服务") { ModelServicePage(draft, onChange, onNavigate) }
    SettingsGroup("上下文与项目") { ContextProjectPage(draft, onChange) }
    SettingsGroup("Agent 与安全") { AgentSecurityPage(draft, onChange) }
    SettingsGroup("扩展") { ExtensionsPage(onNavigate) }
}

/** 通用组：主题模式 + 联网搜索（rikkahub 的 generalSettings / search 合并）。 */
@Composable
private fun GeneralPage(
    draft: SettingsDraft,
    onChange: (SettingsDraft) -> Unit,
    onNavigate: (String) -> Unit,
) {
    SettingsChoice(
        title = "主题模式",
        options = ThemeMode.entries.map(::themeLabel),
        selectedIndex = ThemeMode.entries.indexOf(draft.themeMode),
        onSelect = { onChange(draft.copy(themeMode = ThemeMode.entries[it])) },
        // 不写「右上角 ☼/☾ 可快速切换」——顶栏早就没有这个快捷键了，
        // 指向不存在入口的说明比没有说明更糟。
    )
    NetworkPage(draft, onChange, onNavigate)
}

@Composable
private fun ModelServicePage(
    draft: SettingsDraft,
    onChange: (SettingsDraft) -> Unit,
    onNavigate: (String) -> Unit,
) {
    // rikkahub hub 的「当前值行」：标题 + 当前值当副标题 + 行尾箭头，
    // 细节进二级页。原先这里用 SettingsEntry 且写了一句「密钥不会回填」的长说明，
    // 该说明在二级页里已有，这里按 hub 惯例收成一行当前值。
    // 不带行首图标：这一屏只有它一个入口有图标，会显得像另一种优先级；
    // 而且那个图标是自绘的文件夹，与同屏 Miuix 图标不是一套。
    SettingsEntry(
        title = "API 配置",
        valueText = "${draft.profileName} · ${draft.modelLabel}",
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

    // 输入法：两种输入类型各有代价，只能让用户按自己机型选（默认关 = 正常输入法）。
    SettingsToggle(
        title = "终端字符模式输入",
        checked = draft.terminalCharMode,
        onCheckedChange = { onChange(draft.copy(terminalCharMode = it)) },
        summary = "若终端里调出的不是你常用的输入法（而是安全/密码键盘），保持关闭；" +
            "若个别机型在终端里切换输入法后状态残留，再开启。",
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
    )

    // 关闭联网搜索时后续配置全部折叠（官方 SettingsPage 的 AnimatedVisibility 模式）
    androidx.compose.animation.AnimatedVisibility(visible = draft.webSearchEnabled) {
        Column {
            // 搜索服务已改成**独立整页**（RikkaHub 形态：多服务列表 + 每服务专属选项）。
            // 这里只留入口 —— 原先是一排「类型下拉 + 一个 Key 框 + 实例地址框」，
            // 只能配一个服务，而这一页要能配多个、每个还有自己的 depth/topic/语言。
            // 入口行与 API 配置记录同一个形态（都是"配置对象列表"）。
            BasicComponent(
                title = "搜索服务",
                titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
                summary = "添加 Tavily / Exa / Brave / Perplexity / SearXNG 等；点一个设为当前使用",
                summaryColor = BasicComponentDefaults.summaryColor(
                    color = scheme.onSurfaceVariantSummary,
                ),
                onClick = { onNavigate("searchServices") },
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            )

            SettingsIntField(
                title = "默认搜索结果数（1-50）",
                value = draft.webSearchMaxResults,
                min = WEB_RESULTS_MIN,
                max = WEB_RESULTS_MAX,
                parse = { it.trim().toIntOrNull() },
                onValueChange = { onChange(draft.copy(webSearchMaxResults = it)) },
                summary = "服务未单独指定条数时用它",
            )

            SettingsIntField(
                title = "联网超时（秒）",
                value = draft.webSearchTimeoutSec,
                min = WEB_TIMEOUT_MIN_SEC,
                max = WEB_TIMEOUT_MAX_SEC,
                parse = { it.trim().toIntOrNull() },
                onValueChange = { onChange(draft.copy(webSearchTimeoutSec = it)) },
                summary = "支持 ${WEB_TIMEOUT_MIN_SEC}-${WEB_TIMEOUT_MAX_SEC} 秒",
            )
        }
    }
}

@Composable
private fun ContextProjectPage(draft: SettingsDraft, onChange: (SettingsDraft) -> Unit) {
    SettingsIntField(
        title = "上下文窗口",
        value = draft.contextWindow,
        // 上下文窗口不设人为上限：给多少就是多少，超出实际支持量由引擎侧处理。
        // `parseTokenCount` 已经挡掉了非正数与解析失败，这里只兜住上下界。
        min = 1,
        max = Int.MAX_VALUE,
        parse = ::parseTokenCount,
        // 显示成 `200k` 而不是 `200000`：写作写法与标题里提示的 `128k / 1.5m` 一致。
        format = ::formatTokenCountShort,
        onValueChange = { onChange(draft.copy(contextWindow = it)) },
        summary = "支持 128k / 1.5m 写法",
    )

    SettingsToggle(
        title = "上下文压缩",
        checked = draft.autoCompact,
        onCheckedChange = { onChange(draft.copy(autoCompact = it)) },
    )

    androidx.compose.animation.AnimatedVisibility(visible = draft.autoCompact) {
        SettingsIntField(
            title = "自动压缩上限（50-100%，安全缓冲优先）",
            value = draft.autoCompactPercent,
            min = COMPACT_PERCENT_MIN,
            max = COMPACT_PERCENT_MAX,
            parse = { it.trim().toIntOrNull() },
            onValueChange = { onChange(draft.copy(autoCompactPercent = it)) },
        )
    }

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
    // rikkahub 式入口行：图标 + 标题 + 副标题；Miuix 用 ArrowPreference 的 startAction。
    // 注意：这里不放「API 配置」——那条带当前生效值的入口留在上面的
    // 「模型与对话」组里，同一目标只保留一条路径（hub 的规矩）。
    SettingsIconEntry(
        title = "Model Context Protocol（MCP）",
        summary = "MCP 服务器配置",
        icon = ZhiIcons.sandbox,
        onClick = { onNavigate("mcp") },
    )
    SettingsIconEntry(
        title = "Skill 管理器",
        summary = "项目级与用户级技能模板",
        icon = ZhiIcons.skill,
        onClick = { onNavigate("skills") },
    )
    SettingsIconEntry(
        title = "自定义角色卡",
        summary = "可切换的多份人设指令",
        icon = ZhiIcons.roleCard,
        onClick = { onNavigate("roleCards") },
    )
    // 「关于」行已删：onClick 是空的，点了没反应——rikkahub 的 About 页有
    // 版本/开源许可/检查更新等真实内容，我们暂时没有可放的，空壳不如没有。
    SettingsIconEntry(
        title = "记忆文件 · ZhiCode.md",
        summary = "项目级与用户级说明文件",
        icon = ZhiIcons.edit,
        onClick = { onNavigate("memory") },
    )

    // 仅 debug 构建：把工程真正在用的组件按类别铺开（含对话流/任务卡/输入器），
    // 用来复现"看起来不对"这类问题 —— 详见 ui/debug/UiDebugPage.kt。
    if (com.zhizhu.zhicode.compose.BuildConfig.DEBUG) {
        SettingsIconEntry(
            title = "UI 调试",
            summary = "铺开全部组件与状态（仅 debug 构建）",
            icon = ZhiIcons.floatingBall,
            onClick = { onNavigate("uiDebug") },
        )
    }
}

/** rikkahub 式导航行：图标 + 标题 + 副标题 + 箭头（Miuix ArrowPreference）。 */
@Composable
private fun SettingsIconEntry(
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    ArrowPreference(
        title = title,
        summary = summary,
        startAction = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.onBackground,
            )
        },
        onClick = onClick,
    )
}

// ------------------------------------------------------------------ 工具

