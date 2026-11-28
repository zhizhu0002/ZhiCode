package com.zhizhu.zhicode.compose.ui

import com.zhizhu.zhicode.compose.data.BottomBarStyle
import com.zhizhu.zhicode.compose.theme.LocalThemeSettings
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.theme.ZhiSpacing
import com.zhizhu.zhicode.compose.theme.ZhiSpace

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.zhizhu.zhicode.compose.ui.effects.ZhiSquircle
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DividerDefaults
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldColors
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType


/** 顶栏 chip 的固定高度；胶囊半径由它推出来。 */
private val ChipHeight = 26.dp

/** 输入器底部小 pill 的固定高度。 */
private val PillHeight = 24.dp

internal fun floatingHorizontalInset(wide: Boolean): Dp =
    if (wide) ZhiSpacing.pageMarginWide else ZhiSpacing.pageMargin

/** 底部悬浮层**共用的外壳**：任务卡、反馈条、输入器都从这一个组件出。 */
@Composable
internal fun FloatingBottomShell(
    wide: Boolean,
    glass: Glass,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    // 「悬浮底栏」开关：关闭时贴满宽度、去圆角与阴影 —— 回到传统底部栏的观感。
    // 它不是样式偏好而是**可读性**选择：屏幕小时悬浮内缩会吃掉两侧各 12dp，
    // 而长命令恰好最需要横向空间。
    val floating = LocalThemeSettings.current.floatingBar
    // iOS 风格：悬浮时把圆角拉到全圆（胶囊），而不是 Miuix 的中等圆角。
    // 这是两种不同的设计语言 —— iOS 的输入条是一条胶囊，Miuix 是一张卡片。
    // 只在**真的悬浮**时生效：贴边形态没有"形态"可谈（见 BottomBarStyle 的说明）。
    val iosStyle = floating &&
        LocalThemeSettings.current.bottomBarStyleValue == BottomBarStyle.IOS
    // 圆角走 squircle（连续曲率），与 Miuix 自带的卡片、以及这里的三块悬浮层
    // 保持同一种轮廓语言。同一个 `shape` 对象会同时喂给
    // `textureBlur(shape=…)`、`border(shape=…)` 与 `FloatingToolbar` 的底色 ——
    // 所以必须是**同一个实例**，否则角上会出现两套轮廓的重影。
    val shape = ZhiSquircle(
        when {
            iosStyle -> 28.dp
            floating -> ZhiRadius.floating
            else -> 0.dp
        },
    )
    val inset = if (floating) floatingHorizontalInset(wide) else 0.dp
    FloatingToolbar(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding)
            // 横向内缩在**布局层**，卡片贴住布局框 —— 与之前发送栏一致。
            .padding(horizontal = inset)
            .then(if (floating) glass.blur(Modifier, shape) else Modifier)
            // 玻璃边缘的高光 + 阴影共同给出悬浮层与背景的距离；浅色不能只靠
            // 白色底，因为那会回到"一块平白卡片"，也正是 blur 看不见的原因。
            .then(
                if (floating) {
                    Modifier.border(width = 1.dp, color = glass.edgeHighlight(), shape = shape)
                } else {
                    Modifier
                },
            ),
        color = glass.surfaceColor(if (ZhiColors.isDark()) scheme.surfaceContainer else ZhiColors.panelSurface()),
        cornerRadius = if (floating) ZhiRadius.floating else 0.dp,
        outSidePadding = PaddingValues(0.dp),
        shadowElevation = if (floating) 12.dp else 0.dp,
        showDivider = !floating,
        content = content,
    )
}

/** 顶栏/侧栏/工具行使用的图标按钮。 */
@Composable
fun ZhiIconButton(
    icon: Painter,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    iconSize: Dp = 20.dp,
    background: Color = Color.Transparent,
    enabled: Boolean = true,
    compact: Dp? = null,
) {
    val scheme = MiuixTheme.colorScheme
    // `onBackgroundVariant` 在浅色 Miuix 色板中是较弱的辅助色，作为通用
    // 图标默认色会让菜单、返回和设置等可操作图标显得发灰；主文字色在深浅
    // 主题中都保持足够对比度。明确传入的 tint（包括禁用态）仍优先保留。
    val color = if (tint == Color.Unspecified) scheme.onSurface else tint
    val content: @Composable () -> Unit = {
        Icon(
            painter = icon,
            contentDescription = description,
            tint = color,
            modifier = Modifier.size(iconSize),
        )
    }
    if (compact == null) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            backgroundColor = background,
            // 触控热区兜底（规范 48dp）。Miuix 自己的 `IconButtonDefaults` 是 40dp ——
            // 库默认值不满足无障碍规范，所以要在这里显式抬。
            //
            // `defaultMinSize` 会被**父约束夹住**：放进 36/40dp 高的行里时会自动
            // 退到该行能给出的最大高度，不会把行撑破。所以无需在每个调用点分支。
            minWidth = ZhiSpacing.minTouchTarget,
            minHeight = ZhiSpacing.minTouchTarget,
            content = content,
        )
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            backgroundColor = background,
            cornerRadius = compact / 2,
            // 视觉仍是 `compact` 方形，但触控盒至少 48dp —— 同样受父约束夹取：
            // 顶栏 48dp 能容下，30dp 的密集工具行会退到行高。
            minHeight = if (compact > ZhiSpacing.minTouchTarget) compact else ZhiSpacing.minTouchTarget,
            minWidth = if (compact > ZhiSpacing.minTouchTarget) compact else ZhiSpacing.minTouchTarget,
            content = content,
        )
    }
}

/** 实心图标按钮（发送 / 停止）。 */
@Composable
fun ZhiFilledIconButton(
    icon: Painter? = null,
    description: String,
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
    enabled: Boolean = true,
    size: Dp = 34.dp,
    square: Boolean = false,
    glyph: String? = null,
    glyphSize: TextUnit = MiuixTheme.textStyles.title2.fontSize,
    /** 前景色。默认 `onPrimary`（配 `primary` 容器）；配 `error` 容器时要传 `onError`。 */
    contentColor: Color = Color.Unspecified,
) {
    val foreground = if (contentColor == Color.Unspecified) {
        MiuixTheme.colorScheme.onPrimary
    } else {
        contentColor
    }
    IconButton(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
        backgroundColor = containerColor,
        // ⚠️ 方角用的是 `ZhiRadius.actionKey`（8dp），**不是** `ZhiRadius.square`（4dp），
        // 也不是 `ZhiRadius.inner`（10dp）。两个都试过：
        // 4dp 在 30dp 的键上是个硬邦邦的方块（"太方了"），
        // 10dp 又已经偏胶囊、两个键并排像"一块被劈成两半"。
        //
        // 根因是**方角的观感取决于「圆角/边长」比例**，而不是圆角的绝对值：
        // 同一个 10dp 放在 40dp 的按钮上只有 0.25、还算方，放到 30dp 上就是 0.33。
        // 所以按钮变小必须跟着换档，不能沿用同一个绝对值。详见 ZhiRadius.actionKey。
        cornerRadius = if (square) ZhiRadius.actionKey else size / 2,
        minHeight = size,
        minWidth = size,
    ) {
        if (glyph != null) {
            Text(
                text = glyph,
                color = foreground,
                fontSize = glyphSize,
                fontWeight = FontWeight.Medium,
            )
        } else if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = description,
                tint = foreground,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** 右下角的悬浮操作按钮。 */
@Composable
fun ZhiFloatingActionButton(
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    icon: Painter = ZhiIcons.attach,
    containerColor: Color = Color.Unspecified,
) {
    val scheme = MiuixTheme.colorScheme
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = if (containerColor == Color.Unspecified) scheme.primary else containerColor,
    ) {
        Icon(
            painter = icon,
            contentDescription = description,
            tint = scheme.onPrimary,
        )
    }
}

/** 顶栏里的胶囊 chip（上下文用量、模型名、设备时间）。 */
@Composable
fun ZhiChip(
    label: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    maxLines: Int = 1,
    fontSize: TextUnit = MiuixTheme.textStyles.body2.fontSize,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    val scheme = MiuixTheme.colorScheme
    // 显式传入的颜色优先，用于上下文用量这类需要按阈值变色的 chip
    val targetBackground = when {
        containerColor != Color.Unspecified -> containerColor
        active -> scheme.primary
        else -> scheme.surfaceContainer
    }
    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = ZhiMotion.colorSpec,
        label = "chipBackground",
    )
    val foreground = when {
        contentColor != Color.Unspecified -> contentColor
        active -> scheme.onPrimary
        else -> scheme.onSurface
    }
    ZhiPillSurface(
        onClick = onClick,
        modifier = modifier.height(ChipHeight),
        color = background,
        contentColor = foreground,
        // 胶囊半径 = 高度的一半，不写进 ZhiRadius：高度一改，写死的半径就不再是胶囊
        cornerRadius = ChipHeight / 2,
    ) {
        Text(
            text = label,
            fontSize = fontSize,
            maxLines = maxLines,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/** 输入器底部的小 pill（权限 / 推理 / 模型）。同样是 [Surface] 的薄封装。 */
@Composable
fun ZhiSmallPill(
    label: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    marquee: Boolean = false,
) {
    val scheme = MiuixTheme.colorScheme
    val foreground by animateColorAsState(
        targetValue = if (highlighted) scheme.primary else scheme.onSurface,
        animationSpec = ZhiMotion.colorSpec,
        label = "pillForeground",
    )
    ZhiPillSurface(
        onClick = onClick,
        modifier = modifier.height(PillHeight),
        color = if (highlighted) scheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        contentColor = foreground,
        cornerRadius = PillHeight / 2,
    ) {
        Text(
            text = label,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            maxLines = 1,
            overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = if (marquee) 2.dp else 8.dp)
                .then(
                    if (marquee) {
                        Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 800,
                            repeatDelayMillis = 1000,
                            velocity = 36.dp,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

@Composable
private fun ZhiPillSurface(
    onClick: (() -> Unit)?,
    modifier: Modifier,
    color: Color,
    contentColor: Color,
    cornerRadius: Dp,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.defaultColors(color = color, contentColor = contentColor)
    val slot: @Composable ColumnScope.() -> Unit = {
        // 用 fillMaxHeight 而不是 fillMaxSize：高度由调用方固定，
        // 宽度应随文字内容走。否则配合 widthIn 时会被撑满整行剩余宽度。
        Box(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            cornerRadius = cornerRadius,
            insideMargin = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = colors,
            pressFeedbackType = PressFeedbackType.Sink,
            content = slot,
        )
    } else {
        Card(
            modifier = modifier,
            cornerRadius = cornerRadius,
            insideMargin = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = colors,
            content = slot,
        )
    }
}

@Composable
fun ZhiLoadingIndicator(modifier: Modifier = Modifier, size: Dp = LoadingIndicatorSize) {
    InfiniteProgressIndicator(
        modifier = modifier,
        color = MiuixTheme.colorScheme.primary,
        size = size,
        // 环与点都跟着 size 放大，比例与 Miuix 默认值一致（20 : 2 : 2 → 40 : 4 : 4）。
        strokeWidth = size / 10,
        orbitingDotSize = size / 10,
    )
}

/** 加载指示器的直径。 */
private val LoadingIndicatorSize = 40.dp

/** 分组小标题（"项目历史 · xxx"、"工作区"）。 */
@Composable
fun ZhiSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    insideMargin: PaddingValues? = null,
) {
    // insideMargin 传 null 时不传该参数，走 Miuix 自己的默认值 —— 不用 "0dp 表示默认"
    // 这种哨兵值，否则哪天想把某个位置的内边距显式清零就没法表达。
    if (insideMargin == null) {
        SmallTitle(text = text, modifier = modifier, textColor = textColor)
    } else {
        SmallTitle(text = text, modifier = modifier, textColor = textColor, insideMargin = insideMargin)
    }
}

/** `matchWidth` 时使用足够大的宽度上限，让 Miuix 按可用宽度计算真正的等分 Tab。 */
private val TabsMatchWidthLimit = 1000.dp

@Composable
fun ZhiSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    matchWidth: Boolean = false,
) {
    if (tabs.isEmpty()) return
    val safeSelectedIndex = selectedIndex.coerceIn(0, tabs.lastIndex)
    // 只向 Miuix 转发合法下标；调用方若在列表更新后暂时拿到旧下标，
    // 不应把越界值传入组件或触发一次无效的回调。
    val selectTab = rememberUpdatedState(onSelect)
    // TabRow 自己拥有横向懒列表；把 state 稳定地留在 wrapper 内，避免每次上层
    // 流式状态变化都重建横向滚动位置，也让选中项自动保持在可见区。
    val tabListState = rememberLazyListState()
    // Apply the caller's width constraint outside the Miuix row. TabRowWithContour
    // starts with fillMaxWidth internally; passing `width(160.dp)` directly to it
    // can therefore let the internal fill win and consume the whole SettingsGroup row,
    // leaving the label with a one-glyph column (the NavigationBar screenshot bug).
    Box(modifier = modifier) {
        TabRowWithContour(
            tabs = tabs,
            selectedTabIndex = safeSelectedIndex,
            onTabSelected = { index ->
                if (index in tabs.indices && index != safeSelectedIndex) {
                    selectTab.value(index)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            listState = tabListState,
            itemSpacing = 4.dp,
            minWidth = if (matchWidth) 0.dp else TabRowDefaults.TabRowWithContourMinWidth,
            maxWidth = if (matchWidth) TabsMatchWidthLimit else TabRowDefaults.TabRowWithContourMaxWidth,
        )
    }
}

/** 下拉菜单里的一项。 */
data class ZhiMenuItem(
    val text: String,
    /** 条目下方的说明文字。 */
    val summary: String? = null,
    /** 条目左侧的图标。传 null 就不显示。 */
    val icon: Painter? = null,
    /** 单选场景：当前项会在弹出列表里打勾。 */
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun ZhiIconDropdownMenu(
    items: List<ZhiMenuItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 34.dp,
    minWidth: Dp = 34.dp,
    cornerRadius: Dp = minHeight / 2,
    backgroundColor: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val entry = remember(items, scheme.primary) {
        DropdownEntry(
            items = items.map { item ->
                // 先取出成局部 val：`item.icon` 是可空字段，直接进 lambda 无法智能转换。
                val icon = item.icon
                val iconSlot: (@Composable (Modifier) -> Unit)? =
                    if (icon == null) {
                        null
                    } else {
                        { m: Modifier ->
                            Icon(
                                painter = icon,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = m,
                            )
                        }
                    }
                DropdownItem(
                    text = item.text,
                    summary = item.summary,
                    selected = item.selected,
                    onClick = item.onClick,
                    icon = iconSlot,
                )
            },
        )
    }
    // 打开菜单时先收起输入法：App 是 edge-to-edge，窗口不随键盘缩小，而 Miuix
    // 浮层的定位（0.9.4 的 rememberListPopupLayoutInfo）只扣状态栏/导航栏、
    // **不认识 IME** —— 键盘开着时菜单会整个落在键盘底下（用户实测"显示在下层"）。
    // 收起键盘后锚点随 composer 上移，浮层跟着锚点走（ListPopupLayout 持续跟踪
    // onGloballyPositioned），这也是原版 dialog 式菜单的天然行为。
    val keyboard = LocalSoftwareKeyboardController.current
    OverlayIconDropdownMenu(
        entry = entry,
        modifier = modifier,
        enabled = enabled,
        minHeight = minHeight,
        minWidth = minWidth,
        cornerRadius = cornerRadius,
        backgroundColor = backgroundColor,
        onExpandedChange = { expanded -> if (expanded) keyboard?.hide() },
        content = content,
    )
}

@Composable
fun ZhiTextDropdownChip(
    label: String,
    items: List<ZhiMenuItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MiuixTheme.colorScheme
    ZhiIconDropdownMenu(
        items = items,
        modifier = modifier,
        enabled = enabled,
        minHeight = PillHeight,
        // 宽度交给外层的 weight(1f)：这里放开最小宽，否则三列会被 Miuix 默认的
        // 40dp 下限顶出去
        minWidth = 0.dp,
        cornerRadius = PillHeight / 2,
        // 透明底：底色是输入器那块 FloatingToolbar，chip 自己不该再画一层
        backgroundColor = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                painter = ZhiIcons.collapse,
                contentDescription = null,
                tint = scheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 2.dp).size(11.dp),
            )
        }
    }
}


@Composable
fun ZhiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    useLabelAsPlaceholder: Boolean = true,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    /** 默认 **false**，与 Miuix 原默认值一致。 */
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    colors: TextFieldColors = TextFieldDefaults.textFieldColors(),
    insideMargin: DpSize = TextFieldDefaults.InsideMargin,
    cornerRadius: Dp = TextFieldDefaults.CornerRadius,
    /** 传 null 用主题的正文样式（只补一个可见的文字色）。 */
    textStyle: TextStyle? = null,
    /** 光标颜色。 */
    cursorBrush: Brush = SolidColor(MiuixTheme.colorScheme.primary),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    interactionSource: MutableInteractionSource? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val scheme = MiuixTheme.colorScheme
    val fieldState = remember { TextFieldState(value) }
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    // 外部改值：只在真不一致时写回，并把光标放到末尾。
    // 少了这一步，点列表填值之后光标会留在旧位置，用户接着输入就插错地方
    // ——即上面第 2 个缺陷的来源。
    LaunchedEffect(value) {
        if (fieldState.text.toString() != value) fieldState.setTextAndPlaceCursorAtEnd(value)
    }

    // 内部改值回传给状态层：跳过首次订阅发射与和外部值一致的回声，
    // 避免表单一打开就触发 onValueChange("") 把 touched 全部置真（满屏误飘红）。
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }
            .collect { text ->
                if (text != latestValue) latestOnValueChange(text)
            }
    }

    val base = textStyle ?: MiuixTheme.textStyles.main
    TextField(
        state = fieldState,
        modifier = modifier,
        insideMargin = insideMargin,
        colors = colors,
        cornerRadius = cornerRadius,
        label = label,
        useLabelAsPlaceholder = useLabelAsPlaceholder,
        enabled = enabled,
        readOnly = readOnly,
        // takeOrElse：调用点若显式指定了颜色就尊重它，否则补一个在深浅两套里
        // 都看得清的文字色。
        textStyle = base.copy(color = base.color.takeOrElse { scheme.onSurface }),
        keyboardOptions = keyboardOptions,
        lineLimits = if (singleLine) {
            TextFieldLineLimits.SingleLine
        } else {
            TextFieldLineLimits.MultiLine(minHeightInLines = minLines, maxHeightInLines = maxLines)
        },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        interactionSource = interactionSource,
        cursorBrush = cursorBrush,
    )
}

@Composable
fun ZhiTextField(
    state: androidx.compose.foundation.text.input.TextFieldState,
    modifier: Modifier = Modifier,
    label: String = "",
    useLabelAsPlaceholder: Boolean = true,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    colors: TextFieldColors = TextFieldDefaults.textFieldColors(),
    insideMargin: DpSize = TextFieldDefaults.InsideMargin,
    cornerRadius: Dp = TextFieldDefaults.CornerRadius,
    textStyle: TextStyle? = null,
    cursorBrush: Brush = SolidColor(MiuixTheme.colorScheme.primary),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    interactionSource: MutableInteractionSource? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val scheme = MiuixTheme.colorScheme
    val base = textStyle ?: MiuixTheme.textStyles.main
    TextField(
        state = state,
        modifier = modifier,
        insideMargin = insideMargin,
        colors = colors,
        cornerRadius = cornerRadius,
        label = label,
        useLabelAsPlaceholder = useLabelAsPlaceholder,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = base.copy(color = base.color.takeOrElse { scheme.onSurface }),
        keyboardOptions = keyboardOptions,
        lineLimits = if (singleLine) {
            TextFieldLineLimits.SingleLine
        } else {
            TextFieldLineLimits.MultiLine(minHeightInLines = minLines, maxHeightInLines = maxLines)
        },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        interactionSource = interactionSource,
        cursorBrush = cursorBrush,
    )
}

/** Miuix 复选设置行的稳定转发。 */
@Composable
fun ZhiCheckboxPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    startAction: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    CheckboxPreference(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        startAction = startAction,
        checkboxLocation = CheckboxLocation.End,
        enabled = enabled,
    )
}

/** 表单校验提示（表单里唯一该用的错误行）。 */
@Composable
fun ZhiFieldError(message: String?, touched: Boolean = true) {
    if (message == null || !touched) return
    Text(
        text = message,
        color = MiuixTheme.colorScheme.error,
        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
    )
}

fun Modifier.zhiObservePointer(onPosition: (Offset) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                // Initial：先于子组件拿到事件；不消费，于是子组件照常处理。
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.lastOrNull() ?: continue
                onPosition(change.position)
            }
        }
    }

/** 手指位置的**普通**持有对象（不是 `State`，理由见 [zhiObservePointer]）。 */
class FingerPosition {
    /** 最近一次指针事件的位置；从未收到事件时为 `Offset.Unspecified`。 */
    var value: Offset = Offset.Unspecified
        internal set

    internal fun record(position: Offset) {
        value = position
    }
}

/** 长按动作菜单用的**手指位置追踪器**：[modifier] 挂到条目上，[offset] 在长按 */
class FingerTracker internal constructor(
    private val holder: FingerPosition,
    private val density: Density,
    /** 观察者修饰符，由调用点挂到被长按的那一项上。 */
    val modifier: Modifier,
) {
    /** 手指位置（相对挂载的那一项）；从未收到指针事件时为 null。 */
    fun offset(): DpOffset? {
        val position = holder.value
        if (position == Offset.Unspecified) return null
        return with(density) { DpOffset(position.x.toDp(), position.y.toDp()) }
    }
}

/** 记住一个 [FingerTracker]。把它的 [FingerTracker.modifier] 挂到条目上即可。 */
@Composable
fun rememberFingerTracker(): FingerTracker {
    val density = LocalDensity.current
    val holder = remember { FingerPosition() }
    val observer = remember {
        Modifier.zhiObservePointer { position -> holder.record(position) }
    }
    return remember(density, observer) { FingerTracker(holder, density, observer) }
}

@Composable
fun ZhiAnchoredActionMenu(
    /** 亮着就弹、灭掉就退。**不要**用 `if` 包住整个组件，见上面的说明。 */
    open: Boolean,
    labels: List<String>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** 手指位置（相对被长按的那一项）。为空则退回「贴条目锚定」。 */
    fingerOffset: DpOffset? = null,
    renderInRootScaffold: Boolean = true,
) {
    if (labels.isEmpty()) return
    // 每次重组重建即可：OverlayDropdownPopup 内部用 rememberUpdatedState(entries)
    // 持有它，所以不会因为新实例而丢状态；反过来若用 remember 缓存，
    // onClick 里捕获的 onSelect 就有过期风险（这个回调会随重组变化）。
    val entries = listOf(
        DropdownEntry(
            items = labels.mapIndexed { index, label ->
                DropdownItem(text = label, onClick = { onSelect(index) })
            },
        ),
    )
    // 锚点跟着手指走：位移加在**这一层**（弹层取的就是它的父坐标）。
    val anchorModifier = if (fingerOffset == null) {
        Modifier
    } else {
        Modifier.absoluteOffset(x = fingerOffset.x, y = fingerOffset.y)
    }
    Box(modifier.then(anchorModifier)) {
        OverlayDropdownPopup(
            entries = entries,
            show = open,
            onDismiss = onDismiss,
            onDismissFinished = {},
            maxHeight = null,
            dropdownColors = DropdownDefaults.dropdownColors(),
            renderInRootScaffold = renderInRootScaffold,
            collapseOnSelection = true,
        )
    }
}

/** 人类可读的字节数 —— **已经搬走了**。 */

/** 横向分隔线。 */
@Composable
fun ZhiHorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = Color.Unspecified,
) {
    if (color == Color.Unspecified) {
        HorizontalDivider(modifier = modifier, thickness = thickness)
    } else {
        HorizontalDivider(modifier = modifier, thickness = thickness, color = color)
    }
}

/** 纵向分隔线。转发到 Miuix [VerticalDivider]。参数含义同 [ZhiHorizontalDivider]。 */
@Composable
fun ZhiVerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = Color.Unspecified,
) {
    if (color == Color.Unspecified) {
        VerticalDivider(modifier = modifier, thickness = thickness)
    } else {
        VerticalDivider(modifier = modifier, thickness = thickness, color = color)
    }
}

/** 通知条的语义档。 */
enum class ZhiNoticeTone { INFO, WARN, ERROR }

@Composable
fun ZhiNoticeBar(
    text: String = "",
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: ZhiNoticeTone = ZhiNoticeTone.INFO,
    content: (@Composable () -> Unit)? = null,
) {
    val scheme = MiuixTheme.colorScheme
    val titleColor = when (tone) {
        ZhiNoticeTone.ERROR -> ZhiColors.red()
        ZhiNoticeTone.WARN -> ZhiColors.warnText()
        ZhiNoticeTone.INFO -> scheme.onSurface
    }
    val bodyColor = when (tone) {
        ZhiNoticeTone.ERROR -> ZhiColors.red()
        ZhiNoticeTone.WARN -> ZhiColors.warnText()
        ZhiNoticeTone.INFO -> scheme.onSurfaceVariantSummary
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        // 见上面「几何」那段：通知条比卡片矮，圆角跟着小一档。
        shape = ZhiSquircle(ZhiRadius.inner),
        color = when (tone) {
            ZhiNoticeTone.ERROR -> scheme.errorContainer
            ZhiNoticeTone.WARN -> ZhiColors.warnFill()
            ZhiNoticeTone.INFO -> scheme.surfaceContainer
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = ZhiSpace.m, vertical = ZhiSpace.s)) {
            if (!title.isNullOrEmpty()) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (content != null) {
                content()
            } else if (text.isNotEmpty()) {
                Text(
                    text = text,
                    color = bodyColor,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                )
            }
        }
    }
}

/** 上下文用量细条。转发到 Miuix [LinearProgressIndicator]。 */
@Composable
fun ZhiUsageBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    height: Dp = Dp.Unspecified,
) {
    val scheme = MiuixTheme.colorScheme
    val barColor = if (color == Color.Unspecified) scheme.primary else color
    val background = if (trackColor == Color.Unspecified) scheme.surfaceContainerHighest else trackColor
    val colors = ProgressIndicatorDefaults.progressIndicatorColors(
        foregroundColor = barColor,
        backgroundColor = background,
    )
    if (height == Dp.Unspecified) {
        LinearProgressIndicator(
            modifier = modifier.fillMaxWidth(),
            progress = fraction.coerceIn(0f, 1f),
            colors = colors,
        )
    } else {
        LinearProgressIndicator(
            modifier = modifier.fillMaxWidth(),
            progress = fraction.coerceIn(0f, 1f),
            colors = colors,
            height = height,
        )
    }
}

/** 不确定态进度条（顶栏忙碌扫动条）。转发到 Miuix [LinearProgressIndicator]。 */
@Composable
fun ZhiIndeterminateBar(modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val scheme = MiuixTheme.colorScheme
    val colors = if (color == Color.Unspecified) {
        ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = scheme.primary)
    } else {
        ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = color)
    }
    LinearProgressIndicator(
        modifier = modifier.fillMaxWidth(),
        progress = null,
        colors = colors,
        height = 2.dp,
    )
}
