@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)

package com.zhizhu.zhicode.compose.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.AgentTask
import com.zhizhu.zhicode.compose.model.ChatItem
import com.zhizhu.zhicode.compose.model.ChatKind
import com.zhizhu.zhicode.compose.model.TaskState
import com.zhizhu.zhicode.compose.model.ToolActivity
import com.zhizhu.zhicode.compose.model.ToolKind
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import com.zhizhu.zhicode.compose.ui.Glass
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
import com.zhizhu.zhicode.compose.ui.settings.SettingsChoice
import com.zhizhu.zhicode.compose.ui.settings.SettingsEntry
import com.zhizhu.zhicode.compose.ui.settings.SettingsFootnote
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsNumber
import com.zhizhu.zhicode.compose.ui.settings.SettingsReadOnly
import com.zhizhu.zhicode.compose.ui.settings.SettingsTextField
import com.zhizhu.zhicode.compose.ui.settings.SettingsToggle
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
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
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * **UI 调试页**（仅 debug 构建可见，入口在设置页「扩展」组）。
 *
 * ## 它解决什么问题
 *
 * 界面问题里最难定位的一类是「看起来不对」，而验证它需要**同时**看到同一个组件在各种
 * 状态下的样子：一条工具卡在"进行中 / 成功 / 失败 / 等待授权"四态下高度差别很大，一次
 * 真的任务只会出现其中一态；同理还有流式回复、思考折叠、上下文脚注、错误卡、空状态。
 * 靠真跑一遍任务去凑齐这些状态既慢又不可复现。
 *
 * 所以这一页把**本工程真正在用的组件**按类别铺开，并用**固定的样例数据**把每种状态都
 * 摆一份：颜色/字阶/圆角/图标 → Miuix 基础组件 → 设置行（preference 全套）→ 对话流
 * 卡片 → 任务卡 → 输入器 → 面板与 chips → 各浮层入口 → 当前 state 快照。
 *
 * ## 纪律
 *
 * - 这里**不新造样式**：视觉一律取自 `Zhi*` 令牌与 Miuix 组件本身，颜色/字号/圆角
 *   必须走 `MiuixTheme.colorScheme` / `ZhiTextScale` / `ZhiRadius` —— 这一页的意义
 *   就是"看到的即真实组件"，一旦自己写死数值，它就开始骗人了。
 * - 输入框走 `ZhiTextField`（工程唯一转发点，见 `TextFieldConventionTest`）。
 * - 对话流与任务卡直接调用**生产组件**（`ChatList` 里的那几个），不是仿制版。
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
                item(key = "conversation") { ConversationSection() }
                item(key = "tasks") { TaskCardSection() }
                item(key = "composer") { ComposerSection(state, viewModel, glass) }
                item(key = "chrome") { ChromeSection() }
                item(key = "overlays") { OverlayEntrySection(viewModel) }
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

/** Miuix 基础组件全家桶（本工程用到的那一档）。 */
@Composable
private fun MiuixBasicsSection() {
    val scheme = MiuixTheme.colorScheme
    var switchA by remember { mutableStateOf(true) }
    var switchB by remember { mutableStateOf(false) }
    var checkA by remember { mutableStateOf(true) }
    var checkB by remember { mutableStateOf(false) }
    var radio by remember { mutableStateOf(0) }
    var slider by remember { mutableStateOf(0.4f) }

    DebugSection("Miuix 组件 · 基础", "按钮 / 卡片 / 选择控件 / 进度 —— 全部官方默认尺寸") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {}, content = { Text("主按钮") })
                Button(onClick = {}, enabled = false, content = { Text("禁用") })
                TextButton(text = "文字按钮", onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ZhiIconButton(icon = ZhiIcons.close, description = "默认圆钮", onClick = {})
                ZhiIconButton(icon = ZhiIcons.edit, description = "紧凑方钮", onClick = {}, compact = 28.dp)
                ZhiFilledIconButton(
                    icon = ZhiIcons.send,
                    description = "实心方角",
                    onClick = {},
                    containerColor = scheme.primary,
                    square = true,
                )
                ZhiFilledIconButton(
                    icon = ZhiIcons.stop,
                    description = "实心圆",
                    onClick = {},
                    containerColor = scheme.error,
                    contentColor = scheme.onBackground,
                )
                Badge { Text("9+") }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = ZhiRadius.card,
                insideMargin = PaddingValues(12.dp),
                colors = CardDefaults.defaultColors(
                    color = ZhiColors.cardSurface(),
                    contentColor = scheme.onSurface,
                ),
            ) {
                Text(text = "Card（ZhiRadius.card）", fontSize = ZhiTextScale.Body)
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
                    ZhiChip(label = "chip")
                }
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
            summary = "Miuix 的 bottomAction 槽位",
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

// ------------------------------------------------------------------ 对话流

/** 对话流的**生产组件** + 覆盖每种状态的样例数据。 */
@Composable
private fun ConversationSection() {
    DebugSection(
        title = "对话流（生产组件 + 样例数据）",
        subtitle = "直接调用 ChatList 里的组件；长按动作菜单、思考折叠、上下文脚注都可点",
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            ConversationSamples()
        }
    }
}

@Composable
private fun ColumnScope.ConversationSamples() {
    var thinkingExpanded by remember { mutableStateOf(false) }

    UserBubble(
        item = ChatItem(
            id = "dbg-user-1",
            kind = ChatKind.USER,
            body = "把设置页的动画都改成 miuix 官方的曲线，顺便看一下长文本气泡在窄屏上的折行。",
        ),
        onLongPress = {},
    )
    AssistantCard(
        item = ChatItem(
            id = "dbg-assistant-1",
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
            thinking = "先确认哪些调用点还在用自拟的 280ms/EaseOutCubic，再逐处替换。",
            thinkingExpanded = thinkingExpanded,
            processSteps = listOf("读取 Animations.kt", "替换调用点", "编译校验"),
            contextTokens = 18_400,
            contextWindow = 200_000,
        ),
        onToggleThinking = { thinkingExpanded = !thinkingExpanded },
        onLongPress = {},
    )
    // 流式中的状态（尾字带光标 + 「正在输入」提示）
    AssistantCard(
        item = ChatItem(
            id = "dbg-assistant-2",
            kind = ChatKind.ASSISTANT,
            body = "正在读取 `app/src/main/java/com/zhizhu/zhicode/compose/ui/Animations.kt`",
            streaming = true,
        ),
        onToggleThinking = {},
        onLongPress = {},
    )
    ToolGroupCard(
        item = ChatItem(
            id = "dbg-tools",
            kind = ChatKind.TOOL_GROUP,
            groupLabel = "搜索 2 个模式 · 读取 3 个文件 · 执行 1 条命令",
            groupCompleted = false,
            tools = listOf(
                ToolActivity(
                    id = "t1",
                    toolName = "grep",
                    displayName = "搜索",
                    summary = "ZhiMotion\\.",
                    completed = true,
                    kind = ToolKind.SEARCH,
                ),
                ToolActivity(
                    id = "t2",
                    toolName = "read",
                    displayName = "读取",
                    summary = "Animations.kt",
                    completed = true,
                    kind = ToolKind.READ,
                    elapsedMs = 320,
                ),
                ToolActivity(
                    id = "t3",
                    toolName = "edit",
                    displayName = "编辑",
                    summary = "AppScaffold.kt",
                    completed = false,
                    kind = ToolKind.EDIT,
                    additions = 12,
                    deletions = 3,
                ),
                ToolActivity(
                    id = "t4",
                    toolName = "bash",
                    displayName = "执行",
                    summary = "bash test-source-no-build.sh",
                    completed = true,
                    failed = true,
                    exitCode = 1,
                    kind = ToolKind.COMMAND,
                    output = "FAIL LayoutConsistencyTest",
                ),
                ToolActivity(
                    id = "t5",
                    toolName = "bash",
                    displayName = "执行",
                    summary = "pkg install -y openjdk-21",
                    awaitingPermission = true,
                    kind = ToolKind.COMMAND,
                ),
            ),
        ),
        onToggleTool = {},
        onToggleGroup = {},
        onActions = {},
    )
    ErrorCard(
        item = ChatItem(
            id = "dbg-error",
            kind = ChatKind.ERROR,
            title = "编译失败",
            body = "`Unresolved reference 'ZhiIcons'`（SettingsDialog.kt:406）—— 需要补 import。",
        ),
    )
    InfoCard(
        item = ChatItem(
            id = "dbg-info",
            kind = ChatKind.INFO,
            title = "提示",
            body = "已切换到「项目路径与会话」面板。",
        ),
    )
    Text(
        text = "空状态（对话流无内容时）",
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = ZhiTextScale.Caption,
        modifier = Modifier.padding(top = 8.dp),
    )
    // 空状态是整屏居中的组件，这里给它一个受限高度，免得在画廊里吃掉大半屏。
    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        EmptyState()
    }
}

// ------------------------------------------------------------------ 任务卡

@Composable
private fun TaskCardSection() {
    val tasks = listOf(
        AgentTask(title = "读取工程结构", detail = "- 扫描 `app/src/main`\n- 统计模块", state = TaskState.DONE),
        AgentTask(title = "替换动效令牌", detail = "把 12 个文件收口到 Animations.kt", state = TaskState.RUNNING),
        AgentTask(title = "编译并跑守卫", state = TaskState.PENDING),
    )
    DebugSection("Agent 任务卡", "悬浮态是对话页底部那张卡（embedded 形态由 FloatingAgentStatus 提供）") {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            AgentProgressCard(
                status = "正在替换动效令牌…",
                tasks = tasks,
                onExpand = {},
                maxTasks = 3,
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
    var tab by remember { mutableStateOf(0) }
    var dropdown by remember { mutableStateOf(false) }

    DebugSection("面板 · chips · 分段控件", "顶栏与输入器页脚用到的这些小组件") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ZhiSectionLabel(text = "ZhiSectionLabel（分组小标题）")
            ZhiSegmentedTabs(
                tabs = listOf("对话", "终端", "文件"),
                selectedIndex = tab,
                onSelect = { tab = it },
                matchWidth = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ZhiChip(label = "默认 chip")
                ZhiChip(label = "选中 chip", active = true)
                ZhiChip(label = "带色 chip", containerColor = ZhiColors.amber(), contentColor = scheme.onBackground)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ZhiSmallPill(label = "普通 pill")
                ZhiSmallPill(label = "高亮 pill", highlighted = true, onClick = {})
                ZhiTextDropdownChip(
                    label = "权限：每次询问",
                    items = listOf(
                        ZhiMenuItem(text = "每次询问", selected = true, onClick = {}),
                        ZhiMenuItem(text = "自动编辑", onClick = {}),
                    ),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZhiIconDropdownMenu(
                    items = listOf(
                        ZhiMenuItem(text = "附加项目文件", summary = "搜索并引用文件", icon = ZhiIcons.file, onClick = {}),
                        ZhiMenuItem(text = "打开技能", icon = ZhiIcons.skill, onClick = {}),
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
                ZhiIconButton(icon = ZhiIcons.more, description = "独立图标按钮", onClick = { dropdown = !dropdown })
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (dropdown) "（下拉菜单已在上面展开）" else "点＋看下拉菜单",
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
private fun OverlayEntrySection(viewModel: WorkspaceViewModel) {
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
        }
    }
}
