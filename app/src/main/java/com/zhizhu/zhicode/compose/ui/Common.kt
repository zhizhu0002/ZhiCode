package com.zhizhu.zhicode.compose.ui
import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import com.zhizhu.zhicode.compose.theme.ZhiRadius

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.Color
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 本文件是 Miuix 组件的**唯一转发层**。
 *
 * 这里曾经是一堆手写的 `Box + background + clip(RoundedCornerShape)`，
 * 现已全部换成 Miuix 自己的组件，好处是按压反馈、涟漪、圆角(squircle)、
 * 主题色解析都由 Miuix 负责，和 HyperOS 原生观感一致。
 *
 * 保留 `Zhi*` 包装只是为了给调用点一个稳定的签名，内部不再有自绘逻辑。
 */

/** 顶栏 chip 的固定高度；胶囊半径由它推出来。 */
private val ChipHeight = 26.dp

/** 输入器底部小 pill 的固定高度。 */
private val PillHeight = 24.dp

/**
 * 顶栏/侧栏/工具行使用的图标按钮。转发到 Miuix [IconButton]（自带涟漪与按压反馈）。
 *
 * [compact] 非空时把按钮压成该边长的方形。**必须**在窄行里显式传：
 * Miuix `IconButtonDefaults` 的最小尺寸是 40dp 且默认正圆
 * （实测字节码：`MinWidth = MinHeight = CornerRadius = 40`），
 * 直接用在 25dp 高的工具行上会把整行撑高。
 */
@Composable
fun ZhiIconButton(
    icon: ImageVector,
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
    val color = if (tint == Color.Unspecified) scheme.onBackgroundVariant else tint
    val content: @Composable () -> Unit = {
        Icon(
            imageVector = icon,
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
            content = content,
        )
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            backgroundColor = background,
            cornerRadius = compact / 2,
            minHeight = compact,
            minWidth = compact,
            content = content,
        )
    }
}

/**
 * 实心图标按钮（发送 / 停止）。
 * 转发到 Miuix [IconButton]，用 `cornerRadius = 半径` 得到正圆；
 * [square] 为 true 时改成**方角**（圆角 10dp），发送键即用这个形态。
 * `enabled` 的禁用态由 Miuix 自己处理，不再手写 alpha。
 *
 * [glyph] 非空时改用**文字字形**而不是矢量图标：Miuix 图标库没有纯右箭头，
 * 而原版 蜘蛛 的发送键本来就是文字字形（`text("↑")`），所以 `→` 走这里。
 */
@Composable
fun ZhiFilledIconButton(
    icon: ImageVector? = null,
    description: String,
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
    enabled: Boolean = true,
    size: Dp = 34.dp,
    square: Boolean = false,
    glyph: String? = null,
    glyphSize: TextUnit = ZhiTextScale.TitleSmall,
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
        cornerRadius = if (square) ZhiRadius.inner else size / 2,
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
                imageVector = icon,
                contentDescription = description,
                tint = foreground,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * 顶栏里的胶囊 chip（上下文用量、模型名、设备时间）。
 * Miuix 没有 Chip 组件，用带 `onClick` 的 [Surface] 代替——
 * 这样按压反馈是 Miuix 原生的，而不是之前手写的缩放。
 */
@Composable
fun ZhiChip(
    label: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    maxLines: Int = 1,
    fontSize: TextUnit = ZhiTextScale.Caption,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    val scheme = MiuixTheme.colorScheme
    // 显式传入的颜色优先，用于上下文用量这类需要按阈值变色的 chip
    val targetBackground = when {
        containerColor != Color.Unspecified -> containerColor
        active -> scheme.primary
        else -> scheme.surfaceContainerHigh
    }
    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = ZhiMotion.colorSpec,
        label = "chipBackground",
    )
    val foreground = when {
        contentColor != Color.Unspecified -> contentColor
        active -> scheme.onPrimary
        else -> scheme.onSurfaceVariantSummary
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
) {
    val scheme = MiuixTheme.colorScheme
    val foreground by animateColorAsState(
        targetValue = if (highlighted) scheme.primary else scheme.onSurfaceVariantSummary,
        animationSpec = ZhiMotion.colorSpec,
        label = "pillForeground",
    )
    ZhiPillSurface(
        onClick = onClick,
        modifier = modifier.height(PillHeight),
        color = Color.Transparent,
        contentColor = foreground,
        cornerRadius = PillHeight / 2,
    ) {
        Text(
            text = label,
            fontSize = ZhiTextScale.Footnote,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/**
 * chip / pill 共用的 Miuix [Card] 底座：内容居中，颜色由外层决定。
 *
 * 为什么用 `Card` 而不是 `Surface`：Miuix 的 squircle 圆角与按压反馈（Sink）只在
 * `Card` / `Button` / `IconButton` / `FloatingToolbar` 上生效，`Surface` 只吃标准
 * `RoundedCornerShape`。chip 是个经常被点的元素，走 `Card` 才能拿到原生手感。
 */
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

/** 分组小标题（"项目历史 · xxx"、"工作区"）。转发到 Miuix [SmallTitle]。 */
@Composable
fun ZhiSectionLabel(text: String, modifier: Modifier = Modifier) {
    SmallTitle(
        text = text,
        modifier = modifier,
        textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/**
 * `matchWidth` 时用的宽度上限（S3 重构：从 1000dp 收敛到 252dp）。
 *
 * Miuix 宽度算法是 `(可用宽 − (n−1)×间距) / n` 再 `coerceIn(minWidth, maxWidth)`。
 * 4 项等分在 450dpi 手机上约 100dp/项，252dp 足够表达"等分"语义
 * 又不会像 1000dp 那样完全架空官方 84dp 上限的设计意图。
 */
private val TabsMatchWidthLimit = 252.dp

/**
 * 分段按钮组。转发到 Miuix **[TabRowWithContour]** —— `TabRow` 的带轮廓变体。
 *
 * ## 为什么用带轮廓的那个
 *
 * Miuix 的 `TabRow` 有两个变体（官方文档「页面导航 → TabRow」）：
 *  - `TabRow`：各项各自一个圆角块，未选中项**也**有可见描边，像一排独立按钮；
 *  - `TabRowWithContour`：整行一个外轮廓 + 内部一条底轨，只有选中项凸起一个圆角块。
 *
 * 后者才是 HyperOS 里那种「一格一格」的分段控件观感，也是顶栏工作区标签与
 * 设置页分类标签想要的形态。
 *
 * ## 两个变体的默认尺寸不同，换用时要一起改
 *
 * | | `TabRow` | `TabRowWithContour` |
 * |---|---|---|
 * | 高 / 圆角 | 42 / 12 | **45 / 8** |
 * | min / max 宽 | 76 / 98 | **62 / 84** |
 * | itemSpacing | 9 | **5** |
 *
 * 高度差 3dp：外层的固定高度常量（如 `WorkspaceTabRowHeight`）必须跟着给到 45dp，
 * 否则轮廓会被裁掉。
 *
 * ## `matchWidth`
 *
 * 置 `true` 时把 min/max 宽放开，让 n 项精确等分整行宽度并填满；置 `false`
 * 时沿用 Miuix 默认区间，项数超出一屏就横向滚动。
 */
@Composable
fun ZhiSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    matchWidth: Boolean = false,
) {
    TabRowWithContour(
        tabs = tabs,
        selectedTabIndex = selectedIndex,
        onTabSelected = onSelect,
        modifier = modifier,
        minWidth = if (matchWidth) 0.dp else TabRowDefaults.TabRowWithContourMinWidth,
        maxWidth = if (matchWidth) TabsMatchWidthLimit else TabRowDefaults.TabRowWithContourMaxWidth,
    )
}

/**
 * 下拉菜单里的一项。
 *
 * 不直接把 Miuix 的 `DropdownItem` 暴露给界面代码：那样每个调用点都要认识
 * `DropdownEntry` / `DropdownItem` 两个类，将来 Miuix 改签名会波及一片。
 * 这里只留界面真正需要表达的三样东西。
 */
data class ZhiMenuItem(
    val text: String,
    /** 条目下方的说明文字。 */
    val summary: String? = null,
    /** 条目左侧的图标。传 null 就不显示。 */
    val icon: ImageVector? = null,
    /** 单选场景：当前项会在弹出列表里打勾。 */
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * 触发按钮 + 展开的动作菜单。转发到 Miuix **`OverlayIconDropdownMenu`**。
 *
 * ## 为什么两个 chip 也用它，而不用 `OverlayDropdownPreference`
 *
 * 后者是「设置行」形态：内部是 `BasicComponent` + `Button`，字号取主题的
 * `body1`（**16sp**）、按钮最小高 `ButtonDefaults.MinHeight`（40dp）。
 * 而输入器页脚那一行只有 34dp（同类元素 `ZhiSmallPill` 是 10sp / 28dp），
 * 放在一起就是"格格不入"：字大一截、高度溢出、标签被挤到折行。
 *
 * 官方文档对 `content` 的说明是「按钮内显示的图标（**或其他可组合内容**）」，
 * 所以这里把它当"任意触发内容"用：`+` 传一个图标，两个 chip 传
 * 与 `ZhiSmallPill` 完全同款的文字 + 折叠箭头。这样三者在同一行里尺寸一致。
 *
 * ⚠️ **必须位于 Miuix `Scaffold` 内** —— `Overlay*` 系列靠 Scaffold 提供的
 * `MiuixPopupHost` 渲染弹出内容（官方文档「使用前提」）。本工程根部就是它。
 *
 * 默认尺寸给 34dp：Miuix `IconButtonDefaults` 是 40dp 正圆，
 * 而输入器页脚那一行只有 34dp 高，用默认值会把行撑高。
 */
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
    val entry = DropdownEntry(
        items = items.map { item ->
            // 先取出成局部 val：`item.icon` 是可空字段，直接进 lambda 无法智能转换。
            val icon = item.icon
            val iconSlot: (@Composable (Modifier) -> Unit)? =
                if (icon == null) {
                    null
                } else {
                    { m: Modifier ->
                        Icon(
                            imageVector = icon,
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
    OverlayIconDropdownMenu(
        entry = entry,
        modifier = modifier,
        enabled = enabled,
        minHeight = minHeight,
        minWidth = minWidth,
        cornerRadius = cornerRadius,
        backgroundColor = backgroundColor,
        content = content,
    )
}

/**
 * 文字下拉 chip：外观与 [ZhiSmallPill] **完全一致**（10sp / 28dp / 透明底 + 折叠箭头），
 * 点一下展开选项。
 *
 * 用于输入器页脚的权限 / 推理 —— 旁边就是同款的模型药丸，三者必须看起来是一套。
 *
 * 选中即生效，不需要"选完再提交"那一层（对应 [WorkspaceViewModel.setPermissionMode]
 * / `setEffort`）。
 *
 * ⚠️ 别换回 `OverlayDropdownPreference`：它是 16sp / 40dp 的"设置行"，
 * 放到这条 34dp 的页脚里会撑高整行、把标签挤到折行。
 */
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
                fontSize = ZhiTextScale.Footnote,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                imageVector = ZhiIcons.chevronDown,
                contentDescription = null,
                tint = scheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 2.dp).size(11.dp),
            )
        }
    }
}


/**
 * 文本输入框。转发到 Miuix [TextField] 的 **`TextFieldState`** 重载。
 *
 * ## 为什么不能直接用 `TextField(value = 字符串, …)` 那条重载
 *
 * 工程原先 22 处输入框全部走 `value: String` 那条旧重载，它在真机上暴露了两个
 * 缺陷，而且**看起来毫无关联、实则同源**：
 *
 * 1. **输入的字看不见**（和占位符一个色）。Miuix 里输入字的颜色是
 *    `textStyle.color.takeOrElse { LocalContentColor.current }`
 *    （源码 `TextField.kt` 的 `resolvedTextStyle`）。本工程的字阶只改
 *    `fontSize`、不动 color，于是它一路回落到 `LocalContentColor` ——
 *    而 **Miuix `Card` 会用 `colors.contentColor` 覆盖这个局部值**
 *    （`CardKt` 里 `CompositionLocalProvider(LocalContentColor provides
 *    colors.contentColor)`）。弹窗内容区（`Dialogs/RoleCardsOverlay` 那层用的
 *    [com.zhizhu.zhicode.compose.ui.dialogs.DialogShell]），暗色方案里它是
 *    **`#666666`**；占位符 `onSecondaryContainer` 是 `#7C7C7C` ——
 *    在 `#242424` 底上两者分不出来。
 *
 * 2. **光标停在第一个字母前面，输入顺序错乱**（输入 `123` 显示 `231`）。
 *    旧重载内部是 `remember {}`（无 key）建一个 `TextFieldValue`，选区取默认值
 *    `TextRange.Zero`；之后每次外部文本变化只 `copy(text = 新值)`，
 *    **选区原样保留**（实测 Compose `BasicTextFieldKt` 字节码：建值与
 *    `copy$default` 两处的 mask 都是 6，selection 都走默认）。于是光标恒在
 *    索引 0，输入法按「光标在 0」提交，字符就插到了最前面：先 `1`，
 *    再把 `23` 插到 0，得 `231`。
 *
 * 所以改用 `TextFieldState` 重载（Miuix 0.9.4 文档列在首位的现代 API），
 * 并守两条纪律：**外部改值**用 [setTextAndPlaceCursorAtEnd]（光标落末尾，
 * 不是 0）；**回传**走 `snapshotFlow`，不把渲染时机与状态时机绑在一起。
 *
 * 参数只保留各调用点真正用到的那些，其余交给 Miuix 默认值。
 */
@Composable
fun ZhiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    useLabelAsPlaceholder: Boolean = true,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    /**
     * 默认 **false**，与 Miuix 原默认值一致。
     *
     * 不能默认 true：本工程有几处输入框（输入器、反馈、自由输入）**不写**
     * `singleLine` 而是靠 `minLines`/`maxLines` 表达多行，默认 true 会把它们
     * 变成单行。凡是单行的地方，调用点本来就都显式写了 `singleLine = true`。
     */
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    colors: TextFieldColors = TextFieldDefaults.textFieldColors(),
    insideMargin: DpSize = TextFieldDefaults.InsideMargin,
    cornerRadius: Dp = TextFieldDefaults.CornerRadius,
    /** 传 null 用主题的正文样式（只补一个可见的文字色）。 */
    textStyle: TextStyle? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    interactionSource: MutableInteractionSource? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val scheme = MiuixTheme.colorScheme
    val fieldState = remember { TextFieldState(value) }

    // 外部改值：只在真不一致时写回，并把光标放到末尾。
    // 少了这一步，点列表填值之后光标会留在旧位置，用户接着输入就插错地方
    // ——即上面第 2 个缺陷的来源。
    LaunchedEffect(value) {
        if (fieldState.text != value) fieldState.setTextAndPlaceCursorAtEnd(value)
    }

    // 内部改值回传给状态层。snapshotFlow 自身只在值真的变化时发射，
    // 所以外部写回不会被再次回传而打成循环。
    // （`TextFieldState.text` 是 `CharSequence`，这里显式转成 `String`。）
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }.collect { onValueChange(it) }
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
    )
}

/**
 * 手指位置的**只读**观察者。
 *
 * ## 为什么不用「换掉手势」的办法
 *
 * 要让长按菜单从手指处长出来就得拿到手指坐标，而 Miuix `Card` 的 `onLongPress`
 * 不给坐标。直觉做法是把手势换成
 * `detectTapGestures(onLongPress = { offset -> … })` —— 但那会**顶掉**
 * 组件自己的 `combinedClickable`，连带丢掉两样东西：
 *
 * - **点击行为**。侧栏那一条是「点击打开会话 + 长按出菜单」，两者都要在；
 * - **无障碍语义**。长按动作不再是组件声明的，而是一个裸手势。
 *
 * 所以这里走 `PointerEventPass.Initial`：**先于子组件看到事件，但绝不消费**。
 * 事件原封不动继续下传，`Card` 的 `combinedClickable` 完全不受影响。
 *
 * ⚠️ **绝不要在这里调用 `consume()`**。加了就会把点击与长按一起吞掉，
 * 而界面只是"点了没反应"，没有任何编译错误 —— `AnchoredMenuStructureTest`
 * 专门钉住这一条。
 *
 * ## 为什么写普通字段而不是 `State`
 *
 * 回调在**每次指针移动**（含滚动）时都会触发。若写进 `mutableStateOf`，滚动一屏
 * 就要重组几百次。这里只更新一个普通对象，值在长按回调触发时读一次即可 ——
 * 全程零重组。
 */
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

/**
 * 手指位置的**普通**持有对象（不是 `State`，理由见 [zhiObservePointer]）。
 *
 * 长按是「先按下、再等待」，所以触发时读到的是**最后一次指针事件**的位置 ——
 * 对长按来说就是手指按住不放的那个点。
 */
class FingerPosition {
    /** 最近一次指针事件的位置；从未收到事件时为 `Offset.Unspecified`。 */
    var value: Offset = Offset.Unspecified
        internal set

    internal fun record(position: Offset) {
        value = position
    }
}

/**
 * 长按动作菜单用的**手指位置追踪器**：[modifier] 挂到条目上，[offset] 在长按
 * 触发时读出手指位置。
 *
 * 分成两个成员是刻意的：
 * - [modifier] 是**只读观察者**（见 [zhiObservePointer]），只写普通字段，
 *   所以滚动期间不产生重组；
 * - [offset] 在**触发时**才把 px 换算成 `DpOffset`，所以滚动期间连单位换算都没有；
 *   尚未收到过指针事件时返回 null，调用点据此退回「贴条目锚定」。
 */
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

/**
 * **贴住某一项弹出**的动作菜单（长按菜单）。
 *
 * ## 为什么用它，而不是居中对话框
 *
 * 长按一条消息 / 一条会话时，"会弹在屏幕正中"与手指所在的位置完全无关，
 * 而动作又是针对**那一项**的 —— 位置与语义对不上。所以改成 Miuix 的下拉
 * 菜单组件：把它放进被长按那一项的布局里，它就会锚在那一项上弹出；
 * 再配合 [fingerOffset]，就能锚在**手指**上。
 *
 * ## 转发到 `OverlayDropdownPopup`
 *
 * 上游源码（`miuix-preference/.../popup/OverlayDropdownPopup.kt`）对这个组件的
 * 说明里明确写了「Entries without selection state **can be used as action menus**」，
 * 它就是为动作菜单准备的：分组分隔线、按压触感、点击后收起都由它负责。
 *
 * ## `fingerOffset` 是怎么让面板跟手的
 *
 * 面板位置不是我们算的。Miuix 的 `ListPopupLayout` 用一个零尺寸 `Spacer` 的
 * `parentLayoutCoordinates.positionInWindow()` 取**调用处那一层**的窗口坐标
 * 当作锚点边界（核过 v0.9.4 源码；`OverlayDropdownPopup` **没有** popupModifier 参数）。
 * 所以只要给这里的外层 [Box] 加位移，锚点就跟着动、面板自然跟过去，
 * 并且仍然保留库自带的「下方空间不足则翻到上方」与「贴边内收」。
 *
 * 用 `absoluteOffset` 而不是 `offset`：手指位置是从指针事件里读出来的**绝对**像素，
 * 不该在 RTL 布局下被镜像。
 *
 * ⚠️ 偏移为空时**不要**施加 0 位移：那会把锚点拉回条目左上角，反而跑偏。
 * 为空就什么都不加，退回「贴条目锚定」。
 *
 * ⚠️ **`collapseOnSelection` 必须显式传 true**。
 * 它的默认值是 `entries.size <= 1`，而本工程恰好只建**一个** entry
 * （一个 entry 装全部动作），所以默认值这次会等于 true —— 但这是**巧合**：
 * 万一以后按分组拆成两个 entry，默认值就变成 false，表现是"点完不收起"，
 * 而那种 bug 不会编译失败。显式写出来，让意图不依赖入口数量。
 *
 * `renderInRootScaffold = true`（与 Miuix 默认一致）：弹层渲染在最外层
 * Scaffold 的弹出宿主里，所以能在整个屏幕范围内定位与绘制，不受局部裁剪影响。
 *
 * ## 调用点
 *
 * 只应由"被长按的那一项"调用（见 `ChatList` 的每项 Box 与 `Sidebar` 的 `SessionRow`），
 * 这样锚点天然就是那一项，手指偏移也是相对那一项量的，两者不会错位。
 */
@Composable
fun ZhiAnchoredActionMenu(
    labels: List<String>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** 手指位置（相对被长按的那一项）。为空则退回「贴条目锚定」。 */
    fingerOffset: DpOffset? = null,
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
            show = true,
            onDismiss = onDismiss,
            onDismissFinished = {},
            maxHeight = null,
            dropdownColors = DropdownDefaults.dropdownColors(),
            renderInRootScaffold = true,
            collapseOnSelection = true,
        )
    }
}

/**
 * 人类可读的字节数。
 *
 * 用 1000 进制而不是 1024：这是文件管理器的通行做法（也才与「2.4 MB」这种
 * 系统显示的读数对得上）。
 *
 * 放在这里是因为它有三处使用者（文件面板、附加面板、附加后的提示），
 * 之前是 `FilesPane` 里的私有函数，没必要各写一份。
 */
internal fun zhiFormatSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1_000L -> "$bytes B"
    bytes < 1_000_000L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1000f)
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000f)
}

/** 横向分隔线。转发到 Miuix [HorizontalDivider]（默认 0.75dp 的 HyperOS 细分线）。 */
@Composable
fun ZhiHorizontalDivider(modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    if (color == Color.Unspecified) {
        HorizontalDivider(modifier = modifier)
    } else {
        HorizontalDivider(modifier = modifier, color = color)
    }
}

/** 纵向分隔线。转发到 Miuix [VerticalDivider]。 */
@Composable
fun ZhiVerticalDivider(modifier: Modifier = Modifier) {
    VerticalDivider(modifier = modifier)
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
