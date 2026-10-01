@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)

package com.zhizhu.zhicode.compose.ui.debug

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.data.Clipboard
import com.zhizhu.zhicode.compose.model.AgentTask
import com.zhizhu.zhicode.compose.model.ChatItem
import com.zhizhu.zhicode.compose.model.ChatKind
import com.zhizhu.zhicode.compose.model.EffortLevel
import com.zhizhu.zhicode.compose.model.PermissionMode
import com.zhizhu.zhicode.compose.model.TaskState
import com.zhizhu.zhicode.compose.model.ToolActivity
import com.zhizhu.zhicode.compose.model.ToolKind
import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import com.zhizhu.zhicode.compose.ui.Glass
import com.zhizhu.zhicode.compose.ui.ZhiAnchoredActionMenu
import com.zhizhu.zhicode.compose.ui.ZhiChip
import com.zhizhu.zhicode.compose.ui.ZhiFilledIconButton
import com.zhizhu.zhicode.compose.ui.ZhiHorizontalDivider
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIconDropdownMenu
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiIndeterminateBar
import com.zhizhu.zhicode.compose.ui.ZhiMenuItem
import com.zhizhu.zhicode.compose.ui.ZhiSectionLabel
import com.zhizhu.zhicode.compose.ui.ZhiSegmentedTabs
import com.zhizhu.zhicode.compose.ui.ZhiSmallPill
import com.zhizhu.zhicode.compose.ui.ZhiTextDropdownChip
import com.zhizhu.zhicode.compose.ui.ZhiUsageBar
import com.zhizhu.zhicode.compose.ui.ZhiVerticalDivider
import com.zhizhu.zhicode.compose.ui.chat.AgentProgressCard
import com.zhizhu.zhicode.compose.ui.chat.AssistantCard
import com.zhizhu.zhicode.compose.ui.chat.EmptyState
import com.zhizhu.zhicode.compose.ui.chat.ErrorCard
import com.zhizhu.zhicode.compose.ui.chat.InfoCard
import com.zhizhu.zhicode.compose.ui.chat.ToolGroupCard
import com.zhizhu.zhicode.compose.ui.chat.UserBubble
import com.zhizhu.zhicode.compose.ui.composer.Composer
import com.zhizhu.zhicode.compose.ui.rememberFingerTracker
import com.zhizhu.zhicode.compose.ui.settings.SettingsChoice
import com.zhizhu.zhicode.compose.ui.settings.SettingsEntry
import com.zhizhu.zhicode.compose.ui.settings.SettingsFootnote
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsNumber
import com.zhizhu.zhicode.compose.ui.settings.SettingsReadOnly
import com.zhizhu.zhicode.compose.ui.settings.SettingsTextField
import com.zhizhu.zhicode.compose.ui.settings.SettingsToggle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * **UI 调试页**（仅 debug 构建可见，入口在设置页「扩展」组）。
 *
 * ## 它解决什么问题
 *
 * 界面问题里最难定位的一类是「看起来不对」，而验证它需要**同时**看到同一个组件在各种
 * 状态下的样子：一条工具卡在"运行中 / 成功 / 失败 / 等待授权"四态下高度差别很大，一次
 * 真的任务只会出现其中一态；同理还有流式回复、思考折叠、上下文脚注、错误卡、空状态。
 * 靠真跑一遍任务去凑齐这些状态既慢又不可复现。
 *
 * 所以这一页把**本工程真正在用的组件**按类别铺开，并用**固定的样例数据**把每种状态都
 * 摆一份：颜色/字阶/圆角/图标 → Miuix 基础组件 → 设置行（preference 全套）→ 对话流
 * 卡片 → 任务卡 → 输入器 → 面板与 chips → 各浮层入口 → 当前 state 快照。
 *
 * ## 它是**可互动的**（不是静态画廊）
 *
 * - 「对话流」是一份**可变的样例数据**：思考可展开、工具行可展开看输出、工具组可整体
 *   折叠；按钮能追加用户消息、追加一段"流式 → 完成"的回复、起一个**正在运行**的工具
 *   （秒表会真的走）并让它成功或失败。
 * - 每一条消息、每一张图片都**长按弹出动作菜单**（用的是生产组件
 *   `ZhiAnchoredActionMenu`，从手指位置长出来），选「复制」即写入剪贴板并弹
 *   **吐司**提示；图片项复制的是**图片信息**（文件名 / 尺寸 / 体积 / 类型）。
 * - 底部有「交互日志」：每次点击/长按/复制都留一行，于是"我点了它到底有没有响应"
 *   不用靠猜。
 * - 输入器与「浮层入口」接的是**真实 ViewModel**：输入、斜杠面板、页脚下拉、发送、
 *   打开 API 配置/MCP/技能/角色卡/记忆/模型选择/环境自检/附加文件都是真路径。
 * - 颜色/字阶/圆角/状态快照读的是**当前主题与当前 state**，切深浅色会跟着变。
 *
 * ## 纪律
 *
 * - 这里**不新造样式**：视觉一律取自 `Zhi*` 令牌与 Miuix 组件本身，颜色/字号/圆角
 *   必须走 `MiuixTheme.colorScheme` / `ZhiTextScale` / `ZhiRadius` —— 这一页的意义
 *   就是"看到的即真实组件"，一旦自己写死数值，它就开始骗人了（由
 *   `UiDebugPageStructureTest` 守着）。
 * - 输入框走 `ZhiTextField`（工程唯一转发点，见 `TextFieldConventionTest`）。
 * - 对话流与任务卡直接调用**生产组件**，不是仿制版。
 * - 未收纳官方组件（NavigationBar / NavigationRail / BreadcrumbBar / SearchBar /
 *   ColorPicker / PullToRefresh / Snackbar / Tooltip / NumberPicker 等）：本工程没有
 *   用到它们，摆出来只会增加维护面而没有调试价值。
 *
 * ## 入口与返回
 *
 * 作为整页压在 `NavDisplay` 的栈上（`AppScaffold` 的 `AppKey.UiDebug`），所以返回键与
 * 边缘滑动返回都走官方导航运行时；从设置页进来时设置主页会留在栈底，返回即回到它。
 */
@Composable
fun UiDebugPage(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    glass: Glass,
    onBack: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val listState = rememberLazyListState()
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "UI 调试",
                largeTitle = "UI 调试",
                scrollBehavior = topAppBarScrollBehavior,
                color = scheme.surface,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = scheme.onBackground,
                        )
                    }
                },
                actions = {
                    // 每进一次都从干净样例开始：上一次调试留下的"半运行工具"不该带到下一次。
                    TextButton(text = "重置", onClick = { viewModel.requestUiDebugReset() })
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
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
            ) {
                item(key = "palette") { PaletteSection() }
                item(key = "typography") { TypographySection() }
                item(key = "radiusIcons") { RadiusAndIconSection() }
                item(key = "basics") { MiuixBasicsSection() }
                item(key = "preference") { PreferenceSection() }
                item(key = "appRows") { AppSettingsRowsSection() }
                item(key = "debugOverlay") { DebugOverlaySection(state, viewModel) }
                item(key = "markdown") { MarkdownSection() }
                item(key = "conversation") { ConversationSection(state.uiDebugResetToken) }
                item(key = "media") { MediaSection(state.uiDebugResetToken) }
                item(key = "workspace") { WorkspaceSection(state, viewModel) }
                item(key = "tasks") { TaskCardSection() }
                item(key = "composer") { ComposerSection(state, viewModel, glass) }
                item(key = "chrome") { ChromeSection() }
                item(key = "overlays") { OverlayEntrySection(state, viewModel) }
                item(key = "state") { StateSnapshotSection(state) }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
            )
        }
    }
}

// ------------------------------------------------------------------ 分区外壳

/** 每个调试分区的标题：沿用设置页的组标题（SmallTitle），保持同一层级语言。 */
@Composable
private fun DebugSection(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MiuixTheme.colorScheme
    SettingsGroup(title) {
        Column {
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Caption,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                )
            }
            content()
        }
    }
}

// ------------------------------------------------------------------ 剪贴板 + 吐司

/**
 * 复制到剪贴板并弹**吐司**。
 *
 * 为什么不复用 `WorkspaceViewModel.copyText`：那条路径在 Android 13+ 上**刻意不弹**
 * 自己的提示（系统已经在复制时弹了"已复制"，再弹一次是重复打扰，见 `data/Clipboard.kt`）。
 * 但调试页的用途恰恰是"给我一个看得见的反馈"——点了有没有生效必须当场看见，
 * 所以这里显式弹吐司，并如实区分成功与失败（空内容/剪贴板不可用时不能谎报成功）。
 */
private fun copyWithToast(context: android.content.Context, label: String, text: String, toast: String) {
    val ok = Clipboard.copy(context, label, text)
    val message = if (ok) toast else "复制失败：内容为空或剪贴板不可用"
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/** 一条消息的可见文本：与生产路径（`WorkspaceViewModel.copyMessage`）取同一份内容。 */
private fun copyTextOf(item: ChatItem): String = when (item.kind) {
    ChatKind.ASSISTANT, ChatKind.ERROR -> item.body.ifBlank { item.thinking }
    ChatKind.TOOL_GROUP -> item.tools.joinToString("\n") { tool ->
        listOfNotNull(
            tool.displayName.takeIf { it.isNotBlank() },
            tool.summary.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    }
    else -> item.body
}

/** 工具组的完整输出（含各工具的运行详情），供"复制全部输出"用。 */
private fun toolsTextOf(item: ChatItem): String = item.tools.joinToString("\n\n") { tool ->
    buildString {
        append(tool.displayName)
        if (tool.summary.isNotBlank()) append("  ").append(tool.summary)
        append('\n')
        append(
            when {
                tool.awaitingPermission -> "等待授权"
                !tool.completed -> "运行中"
                tool.failed -> "失败${tool.exitCode?.let { "（退出码 $it）" } ?: ""}"
                else -> "完成"
            },
        )
        if (tool.additions > 0 || tool.deletions > 0) {
            append("  +${tool.additions} −${tool.deletions}")
        }
        if (tool.output.isNotBlank()) append('\n').append(tool.output)
    }
}

// ------------------------------------------------------------------ 样例对话数据

/** 样例对话流里的一项：真实消息，或一张图片（图片是消息里最常见的"非文本内容"）。 */
private sealed interface DebugItem {
    val id: String

    /** 用生产组件渲染的一条消息。 */
    data class Msg(override val id: String, val item: ChatItem) : DebugItem

    /** 一张附件图片：渲染成"图片 + 说明"，长按可复制**图片信息**。 */
    data class Image(
        override val id: String,
        val name: String,
        val dimensions: String,
        val size: String,
        val mime: String,
        val caption: String,
        val expanded: Boolean = false,
    ) : DebugItem {
        /** 长按「复制图片信息」写入的内容。 */
        fun info(): String = "$name · $dimensions · $size · $mime"
    }
}

/**
 * 可变的样例会话 + 交互日志。
 *
 * 用 `mutableStateListOf` 而不是 `List`：这一页的价值一半在"点了有反应"，
 * 而不可变列表每次改动都要重建整份数据结构，反而更容易写错。
 */
private class DebugConversation {
    val items = mutableStateListOf<DebugItem>()
    val log = mutableStateListOf<String>()
    private var seq = 0

    init {
        reset()
    }

    /** 重置为出厂样例（按钮与顶栏都用它）。 */
    fun reset() {
        seq = 0
        items.clear()
        items.addAll(initialSamples())
        log.clear()
        note("样例对话已重置（${items.size} 项）")
    }

    /** 记一行交互日志，最多留 10 行。 */
    fun note(text: String) {
        log.add(0, text)
        while (log.size > 10) log.removeAt(log.lastIndex)
    }

    fun nextId(prefix: String): String = "$prefix-${++seq}"

    // ---- 按 id 改一条消息 ----

    private fun updateMsg(id: String, transform: (ChatItem) -> ChatItem) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = items[index]
        if (current is DebugItem.Msg) items[index] = current.copy(item = transform(current.item))
    }

    private fun updateImage(id: String, transform: (DebugItem.Image) -> DebugItem.Image) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = items[index]
        if (current is DebugItem.Image) items[index] = transform(current)
    }

    // ---- 交互：折叠/展开 ----

    fun toggleThinking(id: String) {
        updateMsg(id) { it.copy(thinkingExpanded = !it.thinkingExpanded) }
        note("切换思考内容 · $id")
    }

    fun toggleTool(msgId: String, toolId: String) {
        // 与生产分支一致：只有"已完成且有输出"的工具才有可展开的内容。
        updateMsg(msgId) { item ->
            item.copy(
                tools = item.tools.map { tool ->
                    if (tool.id == toolId && tool.completed && tool.output.isNotBlank()) {
                        tool.copy(expanded = !tool.expanded)
                    } else {
                        tool
                    }
                },
            )
        }
        note("展开/折叠工具输出 · $toolId")
    }

    fun toggleGroup(msgId: String, expanded: Boolean) {
        updateMsg(msgId) { item ->
            item.copy(
                groupCompleted = expanded,
                tools = item.tools.map { tool ->
                    if (tool.completed && tool.output.isNotBlank()) tool.copy(expanded = expanded) else tool
                },
            )
        }
        note(if (expanded) "展开全部工具输出" else "折叠全部工具输出")
    }

    fun toggleImage(id: String) {
        updateImage(id) { it.copy(expanded = !it.expanded) }
        note("切换图片大小 · $id")
    }

    // ---- 交互：追加与推进 ----

    fun appendUser(text: String) {
        val id = nextId("dbg-user")
        items.add(
            DebugItem.Msg(
                id = id,
                item = ChatItem(id = id, kind = ChatKind.USER, body = text),
            ),
        )
        note("追加用户消息 · ${text.take(12)}…")
    }

    /** 追加一段**流式**回复，返回它的 id（调用方稍后把它置为完成）。 */
    fun appendStreamingAssistant(): String {
        val id = nextId("dbg-assistant")
        items.add(
            DebugItem.Msg(
                id = id,
                item = ChatItem(
                    id = id,
                    kind = ChatKind.ASSISTANT,
                    body = "正在读取 `app/src/main/java/com/zhizhu/zhicode/compose/ui/Animations.kt`",
                    streaming = true,
                ),
            ),
        )
        note("追加流式回复 · $id")
        return id
    }

    /** 流式结束：正文补全、去掉 streaming 标记。 */
    fun finishStreaming(id: String) {
        updateMsg(id) {
            it.copy(
                streaming = false,
                body = it.body + "\n\n已按官方曲线改完；`bash test-source-no-build.sh` 全部通过。",
                contextTokens = 21_600,
                contextWindow = 200_000,
            )
        }
        note("流式回复完成 · $id")
    }

    /**
     * 起一个**正在运行**的工具：追加到最后一个工具组上（没有就新建一个）。
     * 运行中的行会让秒表真的走起来（见 [tickRunning]）。
     */
    fun startTool(displayName: String, summary: String, kind: ToolKind) {
        val tool = ToolActivity(
            id = nextId("tool"),
            toolName = displayName,
            displayName = displayName,
            summary = summary,
            completed = false,
            kind = kind,
        )
        val groupId = lastGroupId()
        if (groupId == null) {
            val id = nextId("dbg-tools")
            items.add(
                DebugItem.Msg(
                    id = id,
                    item = ChatItem(
                        id = id,
                        kind = ChatKind.TOOL_GROUP,
                        groupLabel = "执行 1 项",
                        tools = listOf(tool),
                    ),
                ),
            )
        } else {
            updateMsg(groupId) { item ->
                item.copy(
                    tools = item.tools + tool,
                    groupCompleted = false,
                    groupLabel = "执行 ${item.tools.size + 1} 项",
                )
            }
        }
        note("起工具 · $displayName $summary")
    }

    /**
     * 逐条改写消息项（图片项原样保留）。
     *
     * 刻意不用 `MutableList.replaceAll`：它是 `java.util.List` 的默认方法，在
     * `SnapshotStateList` 上虽然能跑，但回调里无法"跳过不写"（每次都会写回，
     * 于是没有变化也会触发重组），而且语义上容易与 `removeAll` 那类混淆。
     * 只留这一条按索引写回的路径，返回真正改动的条数。
     *
     * [transform] 返回 `null` 表示这一条无需改动。
     */
    private inline fun updateMsgs(transform: (ChatItem) -> ChatItem?): Int {
        var changed = 0
        for (index in items.indices) {
            val entry = items[index]
            if (entry is DebugItem.Msg) {
                val updated = transform(entry.item) ?: continue
                items[index] = entry.copy(item = updated)
                changed++
            }
        }
        return changed
    }

    /** 让当前**所有**运行中的工具成功完成，并补上输出。 */
    fun completeRunning() {
        val touched = mutableListOf<String>()
        updateMsgs { item ->
            var any = false
            val tools = item.tools.map { tool ->
                if (tool.completed || tool.awaitingPermission) {
                    tool
                } else {
                    any = true
                    touched.add(tool.displayName)
                    tool.copy(
                        completed = true,
                        output = "(调试样例) ${tool.summary.ifBlank { tool.displayName }} 执行成功",
                    )
                }
            }
            if (any) item.copy(tools = tools, groupCompleted = true) else null
        }
        if (touched.isEmpty()) note("没有运行中的工具可完成") else note("完成工具 · ${touched.joinToString("/")}")
    }

    /** 让当前所有运行中的工具**失败**（带退出码与错误输出）。 */
    fun failRunning() {
        val touched = mutableListOf<String>()
        updateMsgs { item ->
            var any = false
            val tools = item.tools.map { tool ->
                if (tool.completed || tool.awaitingPermission) {
                    tool
                } else {
                    any = true
                    touched.add(tool.displayName)
                    tool.copy(
                        completed = true,
                        failed = true,
                        exitCode = 1,
                        output = "FAIL UiDebugPageStructureTest\n  AssertionError: 组件未铺开",
                    )
                }
            }
            if (any) item.copy(tools = tools, groupCompleted = true) else null
        }
        if (touched.isEmpty()) note("没有运行中的工具可失败") else note("工具失败 · ${touched.joinToString("/")}")
    }

    /** 等待授权 → 授权通过（演示"等待授权"这一态怎么消失）。 */
    fun approveAwaiting() {
        val changed = updateMsgs { item ->
            var any = false
            val tools = item.tools.map { tool ->
                if (tool.awaitingPermission) {
                    any = true
                    tool.copy(awaitingPermission = false, completed = true, output = "(调试样例) 已授权并执行完成")
                } else {
                    tool
                }
            }
            if (any) item.copy(tools = tools) else null
        }
        note(if (changed > 0) "等待授权的工具已放行" else "没有等待授权的工具")
    }

    /** 是否有工具正在运行（用来决定秒表要不要跑）。 */
    fun hasRunning(): Boolean = items.any { entry ->
        entry is DebugItem.Msg && entry.item.tools.any { !it.completed && !it.awaitingPermission }
    }

    /**
     * 推进运行中工具的秒表。
     *
     * 生产代码里 `elapsedMs` 由引擎推；这里由调试页自己每 [TICK_MILLIS] 加一点，
     * 于是"运行中 · 0.3s"这行字会真的在走 —— 静止的假数据看不出
     * 「运行中」和「卡住了」的区别。
     *
     * @return 是否还有运行中的工具（false 时调用方应停下循环）。
     */
    fun tickRunning(): Boolean = updateMsgs { item ->
        var any = false
        val tools = item.tools.map { tool ->
            if (tool.completed || tool.awaitingPermission) {
                tool
            } else {
                any = true
                tool.copy(elapsedMs = tool.elapsedMs + TICK_MILLIS)
            }
        }
        if (any) item.copy(tools = tools) else null
    } > 0

    private fun lastGroupId(): String? =
        items.lastOrNull { it is DebugItem.Msg && it.item.kind == ChatKind.TOOL_GROUP }?.id

    companion object {
        /** 秒表步长：与生产里引擎推 `elapsedMs` 的频率同一个量级。 */
        const val TICK_MILLIS = 150L
    }
}

/**
 * 出厂样例：把每种状态都摆一份。
 *
 * 覆盖：用户文本、用户附图片、带思考与上下文脚注的回复、流式回复、工具组
 * （搜索已完成 / 读取已展开 / 编辑**运行中** / 命令**失败** / 命令**等待授权**）、
 * 错误卡、提示卡。
 */
private fun initialSamples(): List<DebugItem> = listOf(
    DebugItem.Msg(
        id = "dbg-user-0",
        item = ChatItem(
            id = "dbg-user-0",
            kind = ChatKind.USER,
            body = "把设置页的动画都改成 miuix 官方的曲线，顺便看一下长文本气泡在窄屏上的折行。",
        ),
    ),
    DebugItem.Image(
        id = "dbg-image-0",
        name = "IMG_20261001_221500.jpg",
        dimensions = "1080×2400",
        size = "2.4 MB",
        mime = "image/jpeg",
        caption = "顺便看下这张截图里输入器页脚三个 pill 的间距",
    ),
    DebugItem.Msg(
        id = "dbg-assistant-0",
        item = ChatItem(
            id = "dbg-assistant-0",
            kind = ChatKind.ASSISTANT,
            title = "ZhiCode",
            body = """
                已经按官方曲线改完了。要点：

                - 淡入 `tween(300, SinOutEasing)`，淡出 `tween(150, SinOutEasing)`
                - 位移退出 `tween(200, DecelerateEasing(1.5f))`
                - 数字/进度用 `folmeSpring(1.0, 0.3)`

                ```kotlin
                val fadeOutSpec = tween(150, easing = SinOutEasing)
                ```
            """.trimIndent(),
            thinking = "先确认哪些调用点还在用自拟的 280ms/EaseOutCubic，再逐处替换；" +
                "换完必须重跑守卫，否则可能悄悄放宽了断言。",
            thinkingExpanded = false,
            processSteps = listOf("读取 Animations.kt", "替换 12 处调用点", "编译校验"),
            contextTokens = 18_400,
            contextWindow = 200_000,
        ),
    ),
    DebugItem.Msg(
        id = "dbg-assistant-stream",
        item = ChatItem(
            id = "dbg-assistant-stream",
            kind = ChatKind.ASSISTANT,
            body = "正在读取 `app/src/main/java/com/zhizhu/zhicode/compose/ui/AppScaffold.kt`",
            streaming = true,
        ),
    ),
    DebugItem.Msg(
        id = "dbg-tools-0",
        item = ChatItem(
            id = "dbg-tools-0",
            kind = ChatKind.TOOL_GROUP,
            groupLabel = "搜索 1 个模式 · 读取 2 个文件 · 编辑 2 个文件 · 执行 2 条命令",
            groupCompleted = false,
            tools = listOf(
                ToolActivity(
                    id = "t-search",
                    toolName = "grep",
                    displayName = "搜索",
                    summary = "ZhiMotion\\.",
                    completed = true,
                    elapsedMs = 410,
                    kind = ToolKind.SEARCH,
                    output = "app/.../ui/Composer.kt:12\napp/.../ui/AppScaffold.kt:170\napp/.../ui/Sidebar.kt:233",
                ),
                ToolActivity(
                    id = "t-read",
                    toolName = "read",
                    displayName = "读取",
                    summary = "Animations.kt",
                    completed = true,
                    elapsedMs = 320,
                    kind = ToolKind.READ,
                    // 已展开：展示"展开后的全量输出"这一态
                    expanded = true,
                    output = "1  object ZhiMotion {\n2      val fadeInSpec = tween(300, easing = SinOutEasing)\n" +
                        "3      val fadeOutSpec = tween(150, easing = SinOutEasing)\n4  }",
                ),
                ToolActivity(
                    id = "t-edit",
                    toolName = "edit",
                    displayName = "编辑",
                    summary = "AppScaffold.kt",
                    completed = true,
                    elapsedMs = 860,
                    additions = 12,
                    deletions = 3,
                    kind = ToolKind.EDIT,
                    output = "app/.../ui/AppScaffold.kt: 12 行新增，3 行删除",
                ),
                ToolActivity(
                    id = "t-edit-running",
                    toolName = "edit",
                    displayName = "编辑",
                    summary = "SettingsDialog.kt",
                    // 运行中：行尾没有 chevron，底部一行「运行中 · 1.2s」会随秒表走
                    completed = false,
                    elapsedMs = 1200,
                    kind = ToolKind.EDIT,
                ),
                ToolActivity(
                    id = "t-bash-failed",
                    toolName = "bash",
                    displayName = "执行",
                    summary = "bash test-source-no-build.sh",
                    completed = true,
                    failed = true,
                    exitCode = 1,
                    elapsedMs = 4_200,
                    kind = ToolKind.COMMAND,
                    output = "FAIL UiDebugPageStructureTest\n  AssertionError: UI 调试页必须调用生产组件 EmptyState(",
                ),
                ToolActivity(
                    id = "t-bash-awaiting",
                    toolName = "bash",
                    displayName = "执行",
                    summary = "pkg install -y openjdk-21",
                    // 等待授权：底部一行用强调色写「等待授权…」，没有 chevron
                    completed = false,
                    awaitingPermission = true,
                    kind = ToolKind.COMMAND,
                ),
            ),
        ),
    ),
    DebugItem.Msg(
        id = "dbg-error-0",
        item = ChatItem(
            id = "dbg-error-0",
            kind = ChatKind.ERROR,
            title = "编译失败",
            body = "`Unresolved reference 'ZhiIcons'`（SettingsDialog.kt:406）—— 需要补 import。",
        ),
    ),
    DebugItem.Msg(
        id = "dbg-info-0",
        item = ChatItem(
            id = "dbg-info-0",
            kind = ChatKind.INFO,
            title = "提示",
            body = "已切换到「项目路径与会话」面板。",
        ),
    ),
)

// ------------------------------------------------------------------ 设计令牌

/** 语义色板：每格 = 色块 + 令牌名 + 十六进制值。 */
@Composable
private fun PaletteSection() {
    val scheme = MiuixTheme.colorScheme
    // 只用工程**实际引用过**的令牌：色板里出现没人用的颜色，会引导后来者去用它。
    val tokens = listOf(
        "background" to scheme.background,
        "surface" to scheme.surface,
        "surfaceContainer" to scheme.surfaceContainer,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh,
        "surfaceContainerHighest" to scheme.surfaceContainerHighest,
        "primary" to scheme.primary,
        "onPrimary" to scheme.onPrimary,
        "onBackground" to scheme.onBackground,
        "onSurface" to scheme.onSurface,
        "onBackgroundVariant" to scheme.onBackgroundVariant,
        "onSurfaceContainerHigh" to scheme.onSurfaceContainerHigh,
        "onSurfaceVariantSummary" to scheme.onSurfaceVariantSummary,
        "dividerLine" to scheme.dividerLine,
        "error" to scheme.error,
        "errorContainer" to scheme.errorContainer,
    )
    // 补上应用自己的层级色与语义色（Miuix 色板里没有 diff 绿/红/琥珀）。
    val appTokens = listOf(
        "backdrop" to ZhiColors.backdrop(),
        "panelSurface" to ZhiColors.panelSurface(),
        "cardSurface" to ZhiColors.cardSurface(),
        "cardInnerSurface" to ZhiColors.cardInnerSurface(),
        "green" to ZhiColors.green(),
        "red" to ZhiColors.red(),
        "amber" to ZhiColors.amber(),
    )

    DebugSection("设计令牌 · 颜色", "背景/表面层级与应用语义色；十六进制为当前主题下的实际值") {
        SwatchGrid(tokens)
        ZhiSectionLabel(
            text = "应用层级 / 语义色",
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp),
        )
        SwatchGrid(appTokens)
    }
}

@Composable
private fun SwatchGrid(tokens: List<Pair<String, Color>>) {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 手写 3 列网格：FlowRow 仍是实验 API，为一个色板引入 opt-in 不值得。
        tokens.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, color) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(ZhiRadius.inner),
                            color = color,
                            contentColor = scheme.onBackground,
                            content = {},
                        )
                        Text(
                            text = name,
                            fontSize = ZhiTextScale.Micro,
                            color = scheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                        Text(
                            text = hexOf(color),
                            fontSize = ZhiTextScale.Micro,
                            color = scheme.onBackgroundVariant,
                        )
                    }
                }
                // 末行不足 3 个时补占位，避免最后几格被拉宽
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** 颜色 → `#RRGGBB`（色板里的令牌都是不透明的，透明度不进字串）。 */
private fun hexOf(color: Color): String = String.format(
    "#%02X%02X%02X",
    (color.red * 255).toInt(),
    (color.green * 255).toInt(),
    (color.blue * 255).toInt(),
)

/** 字阶：9 档全部铺一遍，标签写档位名与 sp 值。 */
@Composable
private fun TypographySection() {
    val scheme = MiuixTheme.colorScheme
    val steps = listOf(
        "Title" to ZhiTextScale.Title,
        "TitleSmall" to ZhiTextScale.TitleSmall,
        "Heading" to ZhiTextScale.Heading,
        "Subheading" to ZhiTextScale.Subheading,
        "Body" to ZhiTextScale.Body,
        "BodySmall" to ZhiTextScale.BodySmall,
        "Caption" to ZhiTextScale.Caption,
        "Footnote" to ZhiTextScale.Footnote,
        "Micro" to ZhiTextScale.Micro,
    )
    DebugSection("设计令牌 · 字阶", "ZhiTextScale 的 9 档；正文样式由主题 textStyles 接管") {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            steps.forEach { (name, size) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${name} · ${size.value}sp",
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = ZhiTextScale.Micro,
                        modifier = Modifier.width(120.dp),
                    )
                    Text(text = "蜘蛛 ZhiCode 示例 Aa 123", fontSize = size)
                }
            }
        }
    }
}

/** 圆角令牌 + 全部图标。 */
@Composable
private fun RadiusAndIconSection() {
    val scheme = MiuixTheme.colorScheme
    val radii = listOf(
        "floating 20" to ZhiRadius.floating,
        "card 14" to ZhiRadius.card,
        "button 12" to ZhiRadius.button,
        "inner 10" to ZhiRadius.inner,
        "square 4" to ZhiRadius.square,
    )
    val icons = listOf(
        "menu" to ZhiIcons.menu,
        "settings" to ZhiIcons.settings,
        "newSession" to ZhiIcons.newSession,
        "projectPath" to ZhiIcons.projectPath,
        "skill" to ZhiIcons.skill,
        "roleCard" to ZhiIcons.roleCard,
        "sandbox" to ZhiIcons.sandbox,
        "runtime" to ZhiIcons.runtime,
        "attach" to ZhiIcons.attach,
        "send" to ZhiIcons.send,
        "stop" to ZhiIcons.stop,
        "close" to ZhiIcons.close,
        "edit" to ZhiIcons.edit,
        "collapse" to ZhiIcons.collapse,
        "expand" to ZhiIcons.expand,
        "refresh" to ZhiIcons.refresh,
        "info" to ZhiIcons.info,
        "more" to ZhiIcons.more,
        "chevronDown" to ZhiIcons.chevronDown,
        "done" to ZhiIcons.done,
        "failed" to ZhiIcons.failed,
        "pending" to ZhiIcons.pending,
        "awaiting" to ZhiIcons.awaiting,
        "file" to ZhiIcons.file,
    )

    DebugSection("设计令牌 · 圆角与图标", "圆角取自 ZhiRadius；图标是 ZhiIcons 全集") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            radii.forEach { (_, radius) ->
                Surface(
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(radius),
                    color = scheme.surfaceContainerHigh,
                    contentColor = scheme.onBackground,
                    content = {},
                )
            }
        }
        Text(
            text = radii.joinToString("   ") { it.first },
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Micro,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            icons.chunked(6).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    row.forEach { (_, icon) ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = scheme.onBackground,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    repeat(6 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 官方基础组件

/** Miuix 基础组件全家桶（本工程用到的那一档）。全部可点/可拖。 */
@Composable
private fun MiuixBasicsSection() {
    val scheme = MiuixTheme.colorScheme
    val context = LocalContext.current
    var switchA by remember { mutableStateOf(true) }
    var switchB by remember { mutableStateOf(false) }
    var checkA by remember { mutableStateOf(true) }
    var checkB by remember { mutableStateOf(false) }
    var radio by remember { mutableStateOf(0) }
    var slider by remember { mutableStateOf(0.4f) }
    var lastTap by remember { mutableStateOf("（还没点过）") }

    DebugSection("Miuix 组件 · 基础", "按钮 / 卡片 / 选择控件 / 进度 —— 全部官方默认尺寸，点了都有反应") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { lastTap = "Button · 主按钮" },
                    content = { Text("主按钮") },
                )
                Button(
                    onClick = {},
                    enabled = false,
                    content = { Text("禁用") },
                )
                TextButton(text = "文字按钮", onClick = { lastTap = "TextButton" })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ZhiIconButton(
                    icon = ZhiIcons.close,
                    description = "默认圆钮",
                    onClick = { lastTap = "ZhiIconButton · 默认" },
                )
                ZhiIconButton(
                    icon = ZhiIcons.edit,
                    description = "紧凑方钮",
                    onClick = { lastTap = "ZhiIconButton · compact 28dp" },
                    compact = 28.dp,
                )
                ZhiFilledIconButton(
                    icon = ZhiIcons.send,
                    description = "实心方角",
                    onClick = { lastTap = "ZhiFilledIconButton · 方角" },
                    containerColor = scheme.primary,
                    square = true,
                )
                ZhiFilledIconButton(
                    icon = ZhiIcons.stop,
                    description = "实心圆",
                    onClick = { lastTap = "ZhiFilledIconButton · 圆" },
                    containerColor = scheme.error,
                    contentColor = scheme.onBackground,
                )
                Badge { Text("9+") }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { lastTap = "Card" },
                cornerRadius = ZhiRadius.card,
                insideMargin = PaddingValues(12.dp),
                colors = CardDefaults.defaultColors(
                    color = ZhiColors.cardSurface(),
                    contentColor = scheme.onSurface,
                ),
            ) {
                Text(text = "Card（ZhiRadius.card）· 可点", fontSize = ZhiTextScale.Body)
                HorizontalPairDivider()
                Text(
                    text = "Card 的按压反馈与 squircle 圆角由 Miuix 负责。",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Caption,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(checked = switchA, onCheckedChange = { switchA = it })
                Switch(checked = switchB, onCheckedChange = { switchB = it }, enabled = false)
                Checkbox(state = ToggleableState(checkA), onClick = { checkA = !checkA })
                Checkbox(state = ToggleableState(checkB), onClick = { checkB = !checkB })
                RadioButton(selected = radio == 0, onClick = { radio = 0 })
                RadioButton(selected = radio == 1, onClick = { radio = 1 })
                RadioButton(selected = false, onClick = {})
            }
            Slider(value = slider, onValueChange = { slider = it })
            LinearProgressIndicator(progress = slider, modifier = Modifier.fillMaxWidth())
            LinearProgressIndicator(progress = null, modifier = Modifier.fillMaxWidth())
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(progress = slider, size = 28.dp)
                CircularProgressIndicator(progress = null, size = 28.dp)
                ZhiUsageBar(fraction = slider, modifier = Modifier.weight(1f))
            }
            ZhiIndeterminateBar()
            FloatingToolbar(
                modifier = Modifier.fillMaxWidth(),
                color = scheme.surfaceContainer,
                cornerRadius = ZhiRadius.floating,
                shadowElevation = 8.dp,
                showDivider = false,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = "FloatingToolbar", fontSize = ZhiTextScale.BodySmall)
                    Spacer(modifier = Modifier.weight(1f))
                    ZhiChip(
                        label = "点我复制 chip 文本",
                        onClick = { copyWithToast(context, "chip", "chip", "已复制") },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "最近一次点击：$lastTap",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Caption,
                )
            }
        }
    }
}

/** 卡内分隔线（Miuix 的细分线）。 */
@Composable
private fun HorizontalPairDivider() {
    Spacer(modifier = Modifier.height(6.dp))
    ZhiHorizontalDivider(modifier = Modifier.fillMaxWidth())
    Spacer(modifier = Modifier.height(6.dp))
}

// ------------------------------------------------------------------ 设置行

/** `miuix-preference` 的整套设置行（含本工程暂未使用的两种，便于比较）。 */
@Composable
private fun PreferenceSection() {
    val scheme = MiuixTheme.colorScheme
    var sw by remember { mutableStateOf(true) }
    var cb by remember { mutableStateOf(false) }
    var radio by remember { mutableStateOf(0) }
    var drop by remember { mutableStateOf(1) }
    var spin by remember { mutableStateOf(2) }
    var slider by remember { mutableStateOf(0.35f) }

    DebugSection("Miuix 组件 · 设置行（preference）", "这一层就是设置页每一行的样子；行间不画分隔线") {
        SwitchPreference(
            checked = sw,
            onCheckedChange = { sw = it },
            title = "SwitchPreference",
            summary = "整行可点，右侧滑块",
        )
        CheckboxPreference(
            checked = cb,
            onCheckedChange = { cb = it },
            title = "CheckboxPreference",
            summary = "勾选框在行首（本工程暂未使用）",
        )
        RadioButtonPreference(
            title = "RadioButtonPreference · A",
            selected = radio == 0,
            onClick = { radio = 0 },
            summary = "单选行（本工程暂未使用）",
        )
        RadioButtonPreference(
            title = "RadioButtonPreference · B",
            selected = radio == 1,
            onClick = { radio = 1 },
        )
        ArrowPreference(
            title = "ArrowPreference",
            summary = "行尾自带 ›，入口型设置项",
            startAction = {
                Icon(
                    imageVector = ZhiIcons.projectPath,
                    contentDescription = null,
                    tint = scheme.onBackground,
                )
            },
            onClick = {},
        )
        OverlayDropdownPreference(
            items = listOf("选项一", "选项二", "选项三"),
            selectedIndex = drop,
            title = "OverlayDropdownPreference",
            summary = "点整行弹出选择列表",
            onSelectedIndexChange = { drop = it },
        )
        OverlaySpinnerPreference(
            items = (1..6).map { index ->
                DropdownItem(
                    text = "$index",
                    selected = index - 1 == spin,
                    onClick = { spin = index - 1 },
                )
            },
            selectedIndex = spin,
            title = "OverlaySpinnerPreference",
            summary = "滚轮数字选择",
            maxHeight = 200.dp,
        )
        SliderPreference(
            value = slider,
            onValueChange = { slider = it },
            title = "SliderPreference",
            summary = "行内滑杆（本工程暂未使用）",
            valueText = "${(slider * 100).toInt()}%",
        )
        var inlineText by remember { mutableStateOf("行内输入框") }
        SettingsTextField(
            title = "行内输入（ZhiTextField）",
            value = inlineText,
            onValueChange = { inlineText = it },
            summary = "Miuix 的 bottomAction 槽位；光标落在末尾，不是开头",
        )
        SmallTitle(text = "非 preference 的纯展示行", modifier = Modifier.padding(start = 12.dp, top = 10.dp))
        SettingsReadOnly(title = "只读行 · 色值", valueText = "#34C759", swatch = ZhiColors.green())
        SettingsFootnote("脚注行：不占标题、只有一句说明。")
    }
}

/** 本工程自己的设置行封装（`ui/settings/SettingsRows.kt` 的 6 个入口）。 */
@Composable
private fun AppSettingsRowsSection() {
    var toggle by remember { mutableStateOf(true) }
    var choice by remember { mutableStateOf(0) }
    var number by remember { mutableStateOf(3) }

    DebugSection("设置行 · 工程封装", "ZhiCode 在 Miuix 之上只做语义翻译，尺寸/配色全是官方默认") {
        SettingsToggle(
            title = "SettingsToggle",
            checked = toggle,
            onCheckedChange = { toggle = it },
            summary = "普通说明",
        )
        SettingsToggle(
            title = "风险提示（warn）",
            checked = false,
            onCheckedChange = {},
            summary = "琥珀色说明，用于 Root / 沙箱全权这类高风险项",
            warn = true,
        )
        SettingsChoice(
            title = "SettingsChoice",
            options = listOf("第一个", "第二个", "第三个"),
            selectedIndex = choice,
            onSelect = { choice = it },
            summary = "枚举型",
        )
        SettingsNumber(
            title = "SettingsNumber",
            value = number,
            options = (1..10).toList(),
            onValueChange = { number = it },
            summary = "滚轮数字型",
        )
        SettingsEntry(title = "SettingsEntry", valueText = "入口", onClick = {})
    }
}

// ------------------------------------------------------------------ 全局调试浮层开关

/**
 * 全局调试浮层的开关与说明。
 *
 * 这一项与其他分区不同：它改的是**整个应用**的样子（打开后工作区右上角会多出仪表盘），
 * 所以放在最前面，并且把"打开后会发生什么"写清楚。
 */
@Composable
private fun DebugOverlaySection(state: WorkspaceUiState, viewModel: WorkspaceViewModel) {
    DebugSection(
        title = "全局调试浮层",
        subtitle = "打开后工作区右上角出现仪表盘：面板/工具/输入器状态 + 输入的 Markdown 实时预览",
    ) {
        if (com.zhizhu.zhicode.compose.BuildConfig.DEBUG) {
            SettingsToggle(
                title = "启用调试浮层",
                checked = state.debugOverlayEnabled,
                onCheckedChange = { viewModel.setDebugOverlayEnabled(it) },
                summary = "边用边看：输入框里打的字会立刻用对话流渲染器画一遍；工具调用逐条列出状态。"
                    + "返回工作区即可看到（浮层只在工作区显示，不挡设置页）。",
                warn = true,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingsFootnote(
                "浮层有「展开 / 收起」两态：收起后是贴右缘的窄药丸，只显示 面板 · 消息数 · 输入字数 · 运行中工具数。"
            )
            SettingsFootnote(
                "它不持久化：重启应用后回到关闭。避免某次调试忘了关，下次打开以为界面坏了。"
            )
        }
    }
}

// ------------------------------------------------------------------ Markdown 全语法

/**
 * 对话流的 Markdown **全语法**样例。
 *
 * 每一组都是「说明 + 源码 + 渲染结果」三样并排给出：源码用等宽卡、渲染结果用
 * **生产的助手气泡** [AssistantCard]。这样一屏就能回答两个问题：
 * "这段语法渲染成什么样" 与 "它为什么渲染成这样"（源码就在上面）。
 *
 * 语法清单来自 `MarkdownParse.kt` 的文件头（块级 10 项、行内 12 项），这里逐项落地；
 * 最后一组专门放**病态/边界输入**（未闭合围栏与强调、超深列表、引用里放代码块、
 * 缺分隔行的表格…），因为这一类最容易在改动解析器时悄悄退化。
 */
@Composable
private fun MarkdownSection() {
    DebugSection(
        title = "对话流 · Markdown 全语法",
        subtitle = "每组给出 源码 → 渲染 对照；渲染走的是生产组件 AssistantCard（与真实回复同一套）",
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            MARKDOWN_SAMPLES.forEach { sample ->
                MarkdownSampleBlock(sample)
            }
            SettingsFootnote(
                "有意不支持的（遇到时按普通文本原样显示，不会丢字符）：内联 HTML、脚注、" +
                    "定义列表、引用式链接 [x][1]。",
            )
        }
    }
}

/** 一组 Markdown 样例：说明 + 源码 + 渲染。 */
private data class MarkdownSample(val title: String, val note: String, val source: String)

@Composable
private fun MarkdownSampleBlock(sample: MarkdownSample) {
    val scheme = MiuixTheme.colorScheme
    var showSource by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(
            text = sample.title,
            fontSize = ZhiTextScale.BodySmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = sample.note,
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Micro,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                text = if (showSource) "隐藏源码" else "查看源码（${sample.source.length} 字）",
                onClick = { showSource = !showSource },
            )
        }
        if (showSource) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = ZhiRadius.inner,
                insideMargin = PaddingValues(10.dp),
                colors = CardDefaults.defaultColors(
                    color = ZhiColors.cardInnerSurface(),
                    contentColor = scheme.onSurfaceVariantSummary,
                ),
            ) {
                Text(
                    text = sample.source,
                    fontSize = ZhiTextScale.Footnote,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        // 渲染结果放在生产气泡里：与真实回复完全同一条渲染路径。
        AssistantCard(
            item = ChatItem(
                id = "md-${sample.title}",
                kind = ChatKind.ASSISTANT,
                title = "ZhiCode",
                body = sample.source,
            ),
            onToggleThinking = {},
            onLongPress = {},
        )
    }
}

/**
 * 样例正文。
 *
 * ⚠️ 这些是**内容里含 Markdown 的 Kotlin 原始字符串**，所以：
 * 反引号可以直接写（原始字符串的定界符是三个引号，不是反引号），
 * 而 `$` 必须避开 —— 需要字面量美元符时写 `${'$'}`。
 */
private val MARKDOWN_SAMPLES: List<MarkdownSample> = listOf(
    MarkdownSample(
        title = "① 块级 · 标题 / Setext / 分隔线",
        note = "ATX 1-6 级、Setext 两种写法、三种分隔线标记",
        source = """
            # 一级标题
            ## 二级标题
            ### 三级标题
            #### 四级标题
            ##### 五级标题
            ###### 六级标题

            Setext 一级标题
            ===============

            Setext 二级标题
            ---------------

            普通段落跟在后面，用来说明标题与正文的间距。

            ---

            ***

            ___
        """.trimIndent(),
    ),
    MarkdownSample(
        title = "② 块级 · 代码块",
        note = "反引号围栏与波浪线围栏；info string 记为语言，无语言时为空",
        source = """
            带语言的围栏：

            ```kotlin
            object ZhiMotion {
                val fadeOutSpec = tween(150, easing = SinOutEasing)
            }
            ```

            波浪线围栏（同样支持）：

            ~~~
            no-language fence
              indented line
            ~~~

            行内的 `val x = 1` 与围栏不是一回事。
        """.trimIndent(),
    ),
    MarkdownSample(
        title = "③ 块级 · 列表（无序 / 有序 / 嵌套 / 任务项）",
        note = "三种无序标记、两种有序编号、按缩进嵌套（上限 5 层）、任务项带勾选框",
        source = """
            - 无序项，减号
            - 第二项
              - 嵌套一层
                - 嵌套两层
            - 第三项

            * 星号标记
            + 加号标记

            1. 有序第一项
            2. 有序第二项
            3. 有序第三项

            1) 括号编号第一项
            2) 括号编号第二项

            - [ ] 未完成的待办
            - [x] 已完成的待办
            - [x] 带 `行内代码` 的已完成项
        """.trimIndent(),
    ),
    MarkdownSample(
        title = "④ 块级 · 引用（可嵌套、块中可有块）",
        note = "引用内部递归解析：引用里能再放列表、代码块、标题",
        source = """
            > 第一层引用。
            > > 第二层引用，里面还能继续。
            > 回到第一层。

            > 引用里放列表：
            > - 第一项
            > - 第二项
            >
            > 以及代码块：
            > ```kotlin
            > val inside = true
            > ```
        """.trimIndent(),
    ),
    MarkdownSample(
        title = "⑤ 块级 · 表格",
        note = "对齐分隔行决定是不是表格；行长度不齐时按最长行补齐",
        source = """
            | 列一 | 列二 | 列三 |
            | --- | --- | --- |
            | a | b | c |
            | 长内容比较多的一格 |  | 缺一格 |
            | 只有一列 |

            下面这个缺分隔行，**不是**表格（应原样显示成普通文本）：

            | a | b |
            | c | d |
        """.trimIndent(),
    ),
    MarkdownSample(
        title = "⑥ 行内 · 强调与代码",
        note = "粗体 / 斜体 / 粗斜体 / 删除线（各有两种写法）、行内代码、反斜杠转义",
        source = """
            粗体 **B**、下划线粗体 __B__、斜体 *I*、下划线斜体 _I_、
            粗斜体 ***BI***、删除线 ~~GONE~~。

            行内代码 `TextFieldState` 与 `List<String>`。

            转义：\*这不该是斜体\*、\`这不该是代码\`、\_这不该是斜体\_。
        """.trimIndent(),
    ),
    MarkdownSample(
        title = "⑦ 行内 · 链接与图片",
        note = "标准链接、自动链接 <…>、裸 URL 自动识别、图片按链接渲染（带替代文字）",
        source = """
            标准链接：[ZhiCode 仓库](https://example.com/zhicode)。

            自动链接：<https://example.com/auto>。

            裸地址：https://example.com/bare?x=1 与 https://example.com/bare2 。

            图片：![替代文字](https://example.com/image.png)。

            行内代码里的链接不解析：`[a](b)`。
        """.trimIndent(),
    ),
    MarkdownSample(
        title = "⑧ 行内 · 嵌套组合",
        note = "样式之间可嵌套；代码与链接内部不再解析其它标记（这是刻意的）",
        source = """
            **粗体里有 `代码`**，*斜体里有 [链接](https://example.com)*，
            ~~删除线里有 **粗体**~~，***粗斜体里有 `code`***。

            代码里不解析：`**not bold**`、链接文字里不解析：**[链接](https://example.com)**。

            长行折行检查：这是一段刻意写得很长的句子，用来确认在窄屏上自动折行、不出现横向溢出，也不会把行高算错。
        """.trimIndent(),
    ),
    MarkdownSample(
        title = "⑨ 边界 · 未闭合与病态输入",
        note = "未闭合的围栏与强调、超深缩进列表、深引用 —— 都不能崩，也不能吞字符",
        source = """
            未闭合的围栏（应显示到末尾为止）：

            ```kotlin
            val unclosed = 1

            未闭合的强调：**没有收尾 与 *只有一半。

            深缩进列表（超过 5 层后收敛，不再无限加深）：

            - 第 1 层
                - 第 2 层
                    - 第 3 层
                        - 第 4 层
                            - 第 5 层
                                - 第 6 层（应被收敛）
                                    - 第 7 层

            > 深引用：
            > > > > > 很深的引用层级

            * 星号既可能是列表也可能是斜体：*这一行是斜体* 而下一行是列表
            * 这一行是列表
        """.trimIndent(),
    ),
    MarkdownSample(
        title = "⑩ 边界 · 空与纯空白",
        note = "空串、只有空格与换行 —— 渲染层对这三种输入都必须安全（不崩、不留空壳）",
        source = "\n   \n\n",
    ),
)

// ------------------------------------------------------------------ 对话流（可互动）

/**
 * 对话流：**生产组件 + 可变的样例数据**。
 *
 * 与真实对话页的差别只有数据来源 —— 组件的调用方式、回调语义、折叠规则都照抄
 * `ui/chat/ChatList.kt`，所以在这里看到的排版就是真实排版。
 *
 * 长按任意一条（或图片）弹出的动作菜单用的是生产组件 [ZhiAnchoredActionMenu]，
 * 与真实对话页同一个锚定机制（从手指位置长出来）。
 */
@Composable
private fun ConversationSection(resetToken: Int) {
    val feed = remember(resetToken) { DebugConversation() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 哪一条的菜单正开着（同时只开一个）。
    var menuFor by remember { mutableStateOf<String?>(null) }

    // 运行中工具的秒表：有运行中的工具就每 TICK_MILLIS 推一次，没有就整段不进循环。
    val hasRunning = feed.hasRunning()
    LaunchedEffect(hasRunning) {
        while (hasRunning) {
            delay(DebugConversation.TICK_MILLIS)
            feed.tickRunning()
        }
    }

    DebugSection(
        title = "对话流（生产组件 + 可互动样例）",
        subtitle = "思考可展开、工具行可展开看输出、工具组可整体折叠；长按任意一条或图片复制（吐司提示）",
    ) {
        // ---- 控制条：所有按钮都会真的改变下面这份样例数据，并记一行交互日志 ----
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "样例数据控制",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Caption,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "追加用户消息",
                    onClick = { feed.appendUser("（调试）再帮我确认一下这个间距。") },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "追加流式回复",
                    onClick = {
                        val id = feed.appendStreamingAssistant()
                        // 1.6 秒后自动收尾：于是"流式 → 完成"这条路径也能被看到，
                        // 而不必靠人去点第二次。
                        scope.launch {
                            delay(1_600)
                            feed.finishStreaming(id)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "起一个工具（运行中）",
                    onClick = { feed.startTool("读取", "WorkspaceViewModel.kt", ToolKind.READ) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "起一条命令",
                    onClick = { feed.startTool("执行", "./gradlew :app:assembleDebug", ToolKind.COMMAND) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "让运行中的成功",
                    onClick = { feed.completeRunning() },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "让运行中的失败",
                    onClick = { feed.failRunning() },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "放行等待授权",
                    onClick = { feed.approveAwaiting() },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "重置样例对话",
                    onClick = { menuFor = null; feed.reset() },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        ZhiHorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))

        // ---- 样例流本体 ----
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            feed.items.forEach { entry ->
                // 手指位置追踪挂在这一项的 Box 上：于是菜单从手指那一点长出来，
                // 与真实对话页（ChatList）完全一致。
                val finger = rememberFingerTracker()
                var fingerOffset by remember { mutableStateOf<DpOffset?>(null) }
                Box(modifier = Modifier.then(finger.modifier)) {
                    when (entry) {
                        is DebugItem.Msg -> {
                            val item = entry.item
                            when (item.kind) {
                                ChatKind.USER -> UserBubble(item) {
                                    fingerOffset = finger.offset()
                                    menuFor = item.id
                                    feed.note("长按 · 用户消息")
                                }
                                ChatKind.ASSISTANT -> AssistantCard(
                                    item = item,
                                    onToggleThinking = { feed.toggleThinking(item.id) },
                                    onLongPress = {
                                        fingerOffset = finger.offset()
                                        menuFor = item.id
                                        feed.note("长按 · 助手回复")
                                    },
                                )
                                ChatKind.TOOL_GROUP -> ToolGroupCard(
                                    item = item,
                                    onToggleTool = { toolId -> feed.toggleTool(item.id, toolId) },
                                    onToggleGroup = { expanded -> feed.toggleGroup(item.id, expanded) },
                                    onActions = {
                                        fingerOffset = null
                                        menuFor = item.id
                                        feed.note("点开工具组菜单")
                                    },
                                )
                                ChatKind.ERROR -> ErrorCard(item)
                                ChatKind.INFO -> InfoCard(item)
                            }
                        }
                        is DebugItem.Image -> DebugImageBubble(
                            image = entry,
                            onToggleSize = { feed.toggleImage(entry.id) },
                            onLongPress = {
                                fingerOffset = finger.offset()
                                menuFor = entry.id
                                feed.note("长按 · 图片")
                            },
                        )
                    }
                    if (menuFor == entry.id) {
                        ItemActionMenu(
                            entry = entry,
                            fingerOffset = fingerOffset,
                            onDismiss = { menuFor = null },
                            onAction = { label ->
                                menuFor = null
                                handleItemAction(context, entry, label, feed)
                            },
                        )
                    }
                }
            }
            Text(
                text = "空状态（对话流无内容时）",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Caption,
                modifier = Modifier.padding(top = 10.dp),
            )
            // 空状态是整屏居中的组件，这里给它一个受限高度，免得在画廊里吃掉大半屏。
            Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                EmptyState()
            }
        }

        ZhiHorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        InteractionLog(feed.log) { feed.log.clear() }
    }
}

/**
 * 某一条的长按菜单。
 *
 * 选项按消息类型给：文本消息能复制内容/思考，工具组能复制摘要与全量输出，
 * 图片能复制图片信息 —— 「复制」在每一类上都必须有实际内容可复制，
 * 否则用户点了会以为复制坏了。
 */
@Composable
private fun ItemActionMenu(
    entry: DebugItem,
    fingerOffset: DpOffset?,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit,
) {
    val labels = when (entry) {
        is DebugItem.Image -> listOf("复制图片信息", "复制文件名", "复制图片说明")
        is DebugItem.Msg -> when (entry.item.kind) {
            ChatKind.ASSISTANT -> listOf("复制回复", "复制思考内容", "复制全文（含思考）")
            ChatKind.TOOL_GROUP -> listOf("复制工具摘要", "复制全部输出")
            ChatKind.USER -> listOf("复制内容", "再次发送（日志）")
            else -> listOf("复制内容")
        }
    }
    ZhiAnchoredActionMenu(
        labels = labels,
        onSelect = { index -> onAction(labels[index]) },
        onDismiss = onDismiss,
        fingerOffset = fingerOffset,
    )
}

/** 执行菜单动作：复制 + 吐司 + 记日志。 */
private fun handleItemAction(
    context: android.content.Context,
    entry: DebugItem,
    label: String,
    feed: DebugConversation,
) {
    when (entry) {
        is DebugItem.Image -> when (label) {
            "复制图片信息" ->
                copyWithToast(context, "图片信息", entry.info(), "已复制图片信息")
            "复制文件名" ->
                copyWithToast(context, "图片文件名", entry.name, "已复制文件名")
            else ->
                copyWithToast(context, "图片说明", entry.caption, "已复制图片说明")
        }
        is DebugItem.Msg -> when (label) {
            "复制回复" -> copyWithToast(context, "智蛛回复", entry.item.body, "已复制")
            "复制思考内容" -> copyWithToast(context, "思考内容", entry.item.thinking, "已复制")
            "复制全文（含思考）" -> copyWithToast(
                context,
                "智蛛回复（含思考）",
                listOf(entry.item.thinking, entry.item.body).filter { it.isNotBlank() }.joinToString("\n\n"),
                "已复制全文",
            )
            "复制工具摘要" -> copyWithToast(context, "工具摘要", copyTextOf(entry.item), "已复制")
            "复制全部输出" -> copyWithToast(context, "工具输出", toolsTextOf(entry.item), "已复制全部输出")
            "再次发送（日志）" -> feed.note("（调试）再次发送：${entry.item.body.take(12)}…")
            else -> copyWithToast(context, "消息", copyTextOf(entry.item), "已复制")
        }
    }
    feed.note("$label · ${entry.id}")
}

/**
 * 图片气泡。
 *
 * 真实实现里图片附件由输入器带入（`AttachmentReader` 读字节），这里用**纯主题色**画一块
 * 占位图 —— 调试页不引入图片资源（那会让它依赖具体素材），但尺寸/圆角/说明文字的排布
 * 与真实附件卡一致。长按复制的是**图片信息**（文件名 / 尺寸 / 体积 / 类型）。
 */
@Composable
private fun DebugImageBubble(
    image: DebugItem.Image,
    onToggleSize: () -> Unit,
    onLongPress: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Card(
        onClick = onToggleSize,
        onLongPress = onLongPress,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        cornerRadius = ZhiRadius.card,
        insideMargin = PaddingValues(8.dp),
        colors = CardDefaults.defaultColors(
            color = ZhiColors.cardSurface(),
            contentColor = scheme.onSurface,
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 点一下切换比例：验证不同宽高比下的圆角/裁切是否正常
                    .aspectRatio(if (image.expanded) 1f else 4f / 3f)
                    .clip(RoundedCornerShape(ZhiRadius.inner))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                scheme.primary.copy(alpha = 0.35f),
                                scheme.surfaceContainerHighest,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = ZhiIcons.floatingBall,
                        contentDescription = null,
                        tint = scheme.onSurface,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = "示例图片 · 点按切换比例",
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = ZhiTextScale.Micro,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Text(
                text = image.name,
                fontSize = ZhiTextScale.Caption,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = "${image.dimensions} · ${image.size} · ${image.mime}",
                color = scheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Micro,
            )
            Text(
                text = image.caption,
                fontSize = ZhiTextScale.BodySmall,
                modifier = Modifier.padding(top = 3.dp),
            )
            Text(
                text = "长按可复制图片信息",
                color = scheme.primary,
                fontSize = ZhiTextScale.Micro,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** 交互日志：每次点击/长按/复制留一行，并显示当前样例数据规模。 */
@Composable
private fun InteractionLog(log: List<String>, onClear: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "交互日志（最近 ${log.size} 条）",
                color = scheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Caption,
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(text = "清空", onClick = onClear)
        }
        if (log.isEmpty()) {
            Text(
                text = "还没有交互。点上面的按钮或长按任意一条消息试试。",
                color = scheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Caption,
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = ZhiRadius.inner,
                insideMargin = PaddingValues(10.dp),
                colors = CardDefaults.defaultColors(
                    color = ZhiColors.cardInnerSurface(),
                    contentColor = scheme.onSurfaceVariantSummary,
                ),
            ) {
                Column {
                    log.forEach { line ->
                        Text(
                            text = "· $line",
                            fontSize = ZhiTextScale.Footnote,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 媒体

/**
 * 媒体区：图片在**独立分区**里再摆一份，方便直接对比不同比例。
 *
 * 对话流里那张图片是"消息里的图片"（与气泡同宽）；这里额外给一张**大图**与一张
 * **错误/占位**态，用来验证 `aspectRatio` 与圆角裁切在极端比例下是否还正常。
 */
@Composable
private fun MediaSection(resetToken: Int) {
    val context = LocalContext.current
    var menuFor by remember(resetToken) { mutableStateOf<String?>(null) }
    val images = remember(resetToken) {
        listOf(
            DebugItem.Image(
                id = "media-wide",
                name = "screenshot_settings_hub.png",
                dimensions = "1080×2400",
                size = "1.1 MB",
                mime = "image/png",
                caption = "设置主页：分组卡与行间距",
            ),
            DebugItem.Image(
                id = "media-square",
                name = "icon_1024.png",
                dimensions = "1024×1024",
                size = "486 KB",
                mime = "image/png",
                caption = "方形图：验证圆角裁切",
                expanded = true,
            ),
        )
    }

    DebugSection(
        title = "媒体 · 图片（长按复制图片信息）",
        subtitle = "结构与消息里的图片一致；长按弹出动作菜单，复制后弹吐司",
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            images.forEach { image ->
                val finger = rememberFingerTracker()
                var fingerOffset by remember { mutableStateOf<DpOffset?>(null) }
                Box(modifier = Modifier.then(finger.modifier)) {
                    DebugImageBubble(
                        image = image,
                        onToggleSize = {},
                        onLongPress = {
                            fingerOffset = finger.offset()
                            menuFor = image.id
                        },
                    )
                    if (menuFor == image.id) {
                        ZhiAnchoredActionMenu(
                            labels = listOf("复制图片信息", "复制文件名"),
                            onSelect = { index ->
                                menuFor = null
                                if (index == 0) {
                                    copyWithToast(context, "图片信息", image.info(), "已复制图片信息")
                                } else {
                                    copyWithToast(context, "图片文件名", image.name, "已复制文件名")
                                }
                            },
                            onDismiss = { menuFor = null },
                            fingerOffset = fingerOffset,
                        )
                    }
                }
            }
            SettingsFootnote(
                "真实路径：输入器「＋ → 图片」读入字节后由 AttachmentReader 生成附件，" +
                    "这里不引入图片素材，避免调试页依赖具体文件。",
            )
        }
    }
}

// ------------------------------------------------------------------ 工作区（真实状态）

/** 直接操作真实工作区状态：切面板 / 开侧栏 / 开设置 / 切权限与推理。 */
@Composable
private fun WorkspaceSection(state: WorkspaceUiState, viewModel: WorkspaceViewModel) {
    DebugSection(
        title = "工作区状态（真实生效）",
        subtitle = "这些按钮改的是真实 ViewModel 状态，切完返回即可看到工作区已经跟着变了",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "切到对话",
                    onClick = { viewModel.selectTab(WorkspaceTab.CHAT) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "切到终端",
                    onClick = { viewModel.selectTab(WorkspaceTab.TERMINAL) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "切到文件",
                    onClick = { viewModel.selectTab(WorkspaceTab.FILES) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "打开侧栏",
                    onClick = viewModel::openSidebar,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "权限 → 每次询问",
                    onClick = { viewModel.setPermissionMode(PermissionMode.ASK) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "推理 → 自动",
                    onClick = { viewModel.setEffort(EffortLevel.AUTO) },
                    modifier = Modifier.weight(1f),
                )
            }
            SettingsFootnote(
                "当前面板：${state.tab.label} · 侧栏${if (state.sidebarOpen) "已打开" else "关闭"} · " +
                    "权限 ${state.permissionMode.label} · 推理 ${state.effort.label}",
            )
        }
    }
}

// ------------------------------------------------------------------ 任务卡

@Composable
private fun TaskCardSection() {
    var tasks by remember {
        mutableStateOf(
            listOf(
                AgentTask(title = "读取工程结构", detail = "- 扫描 `app/src/main`\n- 统计模块", state = TaskState.DONE),
                AgentTask(title = "替换动效令牌", detail = "把 12 个文件收口到 Animations.kt", state = TaskState.RUNNING),
                AgentTask(title = "编译并跑守卫", state = TaskState.PENDING),
            ),
        )
    }
    DebugSection("Agent 任务卡", "点「推进」可以看到三种状态之间的切换（真实任务里由引擎推）") {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            AgentProgressCard(
                status = tasks.firstOrNull { it.state == TaskState.RUNNING }?.title?.let { "正在执行：$it" }
                    ?: "所有任务已完成",
                tasks = tasks,
                onExpand = {},
                maxTasks = 3,
            )
            TextButton(
                text = "推进任务状态",
                onClick = {
                    // DONE → RUNNING → DONE 的循环推进：卡片的进度条与"已完成/总数"会跟着动。
                    tasks = tasks.map { task ->
                        when (task.state) {
                            TaskState.PENDING -> task.copy(state = TaskState.RUNNING)
                            TaskState.RUNNING -> task.copy(state = TaskState.DONE)
                            TaskState.DONE -> task.copy(state = TaskState.PENDING)
                        }
                    }
                },
            )
        }
    }
}

// ------------------------------------------------------------------ 输入器

/** 输入器：**真实 state + 真实回调**，所以这里是真的能用（斜杠面板、附件、chips、发送）。 */
@Composable
private fun ComposerSection(state: WorkspaceUiState, viewModel: WorkspaceViewModel, glass: Glass) {
    DebugSection(
        title = "输入器（真实组件 + 真实状态）",
        subtitle = "这里的输入、斜杠面板、页脚三个下拉、发送/停止都连着真实 ViewModel",
    ) {
        Composer(
            state = state,
            wide = false,
            glass = glass,
            onTextChange = viewModel::onComposerChange,
            onSend = viewModel::send,
            onStop = viewModel::stop,
            onPickSlash = {},
            onRemoveAttachment = { viewModel.removeAttachment(it.id) },
            onAttachFile = viewModel::openAttachPicker,
            onOpenSkills = viewModel::openSkills,
            onOpenFilesTab = {},
            onPickImage = {},
            onPermissionSelected = viewModel::setPermissionMode,
            onEffortSelected = viewModel::setEffort,
            onModelChip = viewModel::showModelPicker,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ------------------------------------------------------------------ 面板与 chips

@Composable
private fun ChromeSection() {
    val scheme = MiuixTheme.colorScheme
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var lastTap by remember { mutableStateOf("（还没点过）") }

    DebugSection("面板 · chips · 分段控件", "顶栏与输入器页脚用到的这些小组件；点了都会记在下面") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ZhiSectionLabel(text = "ZhiSectionLabel（分组小标题）")
            ZhiSegmentedTabs(
                tabs = listOf("对话", "终端", "文件"),
                selectedIndex = tab,
                onSelect = { tab = it; lastTap = "分段控件 → 第 ${it + 1} 项" },
                matchWidth = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ZhiChip(label = "默认 chip", onClick = { lastTap = "chip · 默认" })
                ZhiChip(label = "选中 chip", active = true, onClick = { lastTap = "chip · 选中" })
                ZhiChip(
                    label = "带色 chip",
                    containerColor = ZhiColors.amber(),
                    contentColor = scheme.onBackground,
                    onClick = { lastTap = "chip · 带色" },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ZhiSmallPill(label = "普通 pill")
                ZhiSmallPill(label = "高亮 pill", highlighted = true, onClick = { lastTap = "pill · 高亮" })
                ZhiTextDropdownChip(
                    label = "权限：每次询问",
                    items = listOf(
                        ZhiMenuItem(text = "每次询问", selected = true, onClick = { lastTap = "下拉 chip · 每次询问" }),
                        ZhiMenuItem(text = "自动编辑", onClick = { lastTap = "下拉 chip · 自动编辑" }),
                    ),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZhiIconDropdownMenu(
                    items = listOf(
                        ZhiMenuItem(
                            text = "附加项目文件",
                            summary = "搜索并引用文件",
                            icon = ZhiIcons.file,
                            onClick = { lastTap = "＋菜单 · 附加项目文件" },
                        ),
                        ZhiMenuItem(
                            text = "打开技能",
                            icon = ZhiIcons.skill,
                            onClick = { lastTap = "＋菜单 · 打开技能" },
                        ),
                    ),
                    content = {
                        Icon(
                            imageVector = ZhiIcons.attach,
                            contentDescription = "加号菜单",
                            tint = scheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
                Spacer(modifier = Modifier.width(10.dp))
                ZhiIconButton(
                    icon = ZhiIcons.refresh,
                    description = "把 chip 文案复制到剪贴板",
                    onClick = { copyWithToast(context, "chip", "chip", "已复制") },
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "最近一次交互：$lastTap",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Caption,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(30.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "纵向分隔线 →", fontSize = ZhiTextScale.Caption)
                Spacer(modifier = Modifier.width(8.dp))
                ZhiVerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ZhiVerticalDivider",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Caption,
                )
            }
        }
    }
}


// ------------------------------------------------------------------ 浮层入口

/** 各浮层的**真实入口**：点了就会按正常路径把那一层压到导航栈上。 */
@Composable
private fun OverlayEntrySection(state: WorkspaceUiState, viewModel: WorkspaceViewModel) {
    val entries = listOf<Pair<String, () -> Unit>>(
        "API 配置记录" to viewModel::openApiConfig,
        "MCP 服务器" to viewModel::openMcpConfig,
        "Skill 管理器" to viewModel::openSkills,
        "自定义角色卡" to viewModel::openRoleCards,
        "记忆文件" to viewModel::openMemory,
        "模型选择" to viewModel::showModelPicker,
        "环境自检" to viewModel::openEnvironment,
        "附加项目文件" to viewModel::openAttachPicker,
        "任务清单" to viewModel::openTaskList,
    )
    // 当前哪一层开着：这些状态本来就在 state 里，直接读出来就能知道"刚才那点有没有生效"。
    val openNow = buildList {
        if (state.apiConfig != null) add("API 配置")
        if (state.mcpConfig != null) add("MCP")
        if (state.skills != null) add("技能")
        if (state.roleCards != null) add("角色卡")
        if (state.memory != null) add("记忆文件")
        if (state.modelPicker != null) add("模型选择")
        if (state.environmentOpen) add("环境自检")
        if (state.attachPickerOpen) add("附加文件")
        if (state.taskListOpen) add("任务清单")
    }
    DebugSection(
        title = "浮层入口",
        subtitle = "这些都是真实入口（走 ViewModel → NavDisplay 页面栈），返回键逐级回退",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, action) ->
                        TextButton(text = label, onClick = action, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
            Text(
                text = if (openNow.isEmpty()) "当前没有浮层打开" else "当前打开：${openNow.joinToString(" / ")}",
                color = MiuixTheme.colorScheme.primary,
                fontSize = ZhiTextScale.Caption,
            )
            SettingsFootnote(
                "权限确认 / 计划审批 / 选择器这三类浮层由真实任务流触发，没有可构造的入口，" +
                    "所以不在这里假造一份——假数据会让人误判真实观感。",
            )
        }
    }
}

// ------------------------------------------------------------------ 状态快照

/** 当前 state 的关键字段一览：调试时不必再去对照代码猜"界面为什么是这样"。 */
@Composable
private fun StateSnapshotSection(state: WorkspaceUiState) {
    val context = LocalContext.current
    val rows = listOf(
        "项目" to "${state.projectName} · ${state.projectPath}",
        "当前面板" to state.tab.label,
        "会话" to "${state.sessions.size} 条（当前 ${state.activeSessionId.ifBlank { "无" }}）",
        "对话条目" to "${state.transcript.size} 条",
        "工作状态" to (state.workingStatus ?: "空闲"),
        "Agent 任务" to "${state.tasks.size} 条（清单${if (state.taskListOpen) "已" else "未"}展开）",
        "权限模式" to state.permissionMode.label,
        "推理强度" to state.effort.label,
        "配置 / 模型" to "${state.profileName} · ${state.modelLabel}" +
            if (state.apiKeyConfigured) "（已配置密钥）" else "（缺密钥）",
        "上下文" to "${state.contextTokens} / ${state.contextWindow}",
        "运行环境" to if (state.runtimeReady) "就绪" else "未就绪（${state.runtimeProgress}% ${state.runtimeMessage}）",
        "输入器" to "${state.composerText.length} 字 · ${state.attachments.size} 附件 · " +
            if (state.composerBusy) "任务运行中" else "空闲",
        "斜杠面板" to (state.slashQuery?.let { "「$it」→ ${state.slashMatches.size} 项" } ?: "收起"),
        "侧栏 / 设置" to "侧栏${if (state.sidebarOpen) "开" else "关"} · " +
            "设置${if (state.settingsOpen) "开" else "关"} · UI 调试开",
    )
    DebugSection("状态快照（只读）", "调试「界面为什么长这样」最先要看的就是这几项") {
        Column {
            rows.forEach { (name, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = name,
                        fontSize = ZhiTextScale.Caption,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.width(88.dp),
                    )
                    Text(
                        text = value,
                        fontSize = ZhiTextScale.Caption,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            TextButton(
                text = "复制整份快照",
                onClick = {
                    copyWithToast(
                        context,
                        "ZhiCode 状态快照",
                        rows.joinToString("\n") { (name, value) -> "$name：$value" },
                        "已复制状态快照",
                    )
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}
