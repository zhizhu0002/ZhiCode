package com.zhizhu.zhicode.compose.ui
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.theme.ZhiSpace

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.compositeOver
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
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.FloatingActionButton
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
 * 底部悬浮层（任务卡 / 反馈条 / 输入器）的横向外边距。
 *
 * ## 为什么要收成一个函数
 *
 * 这三块是**竖着叠在一起的同一列**，左右必须严格对齐 —— 差 1dp 都会在那条中缝上
 * 看得出来。原先这个表达式在三个文件里各写了一遍（原文
 * `if (wide) 24.dp else 12.dp`）。三份拷贝意味着"改一处忘两处"迟早发生，
 * 而一旦发生，表现是"两块宽度对不齐"这种**只有肉眼能发现**的问题：
 * 编译通过、单测全绿、跑起来也不崩。
 *
 * 收成一个函数之后，宽度一致就是**结构性**保证，而不是靠三处记得同步。
 *
 * [wide] 是横屏/大屏档，比手机竖屏多留一段边距。
 */
internal fun floatingHorizontalInset(wide: Boolean): Dp = if (wide) 24.dp else 12.dp

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
 * [square] 为 true 时改成**方角**，发送键即用这个形态。
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
                imageVector = icon,
                contentDescription = description,
                tint = foreground,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * 右下角的悬浮操作按钮。
 *
 * 转发到 Miuix 的 [FloatingActionButton]，**不改它的默认值**：圆形、`primary` 底色、
 * 带阴影 —— 这就是 HyperOS 的原生样子。以前这类入口在本工程里是顶栏的一个 TextButton
 * （「新建」），列表长了以后要滑到顶部才够得着；FAB 是悬浮的，滚动时一直可用。
 *
 * 放在 Common.kt 而不是直接在各页 import：这样"用 Miuix 原生 FAB"只有一处声明，
 * 后来的人要改尺寸/颜色时也只有一个地方要动。
 */
@Composable
fun ZhiFloatingActionButton(
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = ZhiIcons.attach,
    containerColor: Color = Color.Unspecified,
) {
    val scheme = MiuixTheme.colorScheme
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = if (containerColor == Color.Unspecified) scheme.primary else containerColor,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = scheme.onPrimary,
        )
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

/**
 * 加载指示器。转发到 Miuix **[InfiniteProgressIndicator]**：一圈细环 + 一个绕行的点。
 *
 * ## 为什么不用 `CircularProgressIndicator`
 *
 * 那个先是在这里用的，换掉是因为观感：它的自转态是"弧长还会变的一条弧"，
 * 在**居中放大显示**时显得笨重；而 Miuix 这个轨道点式的更轻、更像 HyperOS 里
 * 那种"正在转圈加载"的观感（同一个包里两个都提供，官方示例两种都用）。
 * 换回前者只改这一个函数。
 *
 * ## 尺寸与颜色
 *
 * Miuix 的默认值是 `size = 20.dp, strokeWidth = 2.dp, orbitingDotSize = 2.dp`，
 * `color` 默认是**写死的 `Color.Gray`**。20dp 是给"行内小图标位"用的，
 * 放在面板中间太小、而且灰得看不出是加载中，所以：
 * - 尺寸放大到 [LoadingIndicatorSize]（这个函数只有"页面中央等待"这一种用法）；
 * - 颜色取主题 `primary`，深浅色各自成立，不写死。
 *
 * ## 只有"不确定进度"这一种形态
 *
 * 本工程所有等待都是"不知道还要多久"（拉模型目录、装运行时、跑命令）。
 * 画一条走到 80% 就停住的确定进度条是**假精确**，比一个诚实的转圈更糟。
 * 哪天真有可量化的进度，再加参数。
 */
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

/**
 * 加载指示器的直径。
 *
 * 40dp：是 Miuix 默认值（20dp）的两倍。默认那个是给行内小图标位准备的，
 * 放在页面/面板正中央等待时太小、与旁边的文字不成比例。
 */
private val LoadingIndicatorSize = 40.dp

/**
 * 分组小标题（"项目历史 · xxx"、"工作区"）。转发到 Miuix [SmallTitle]。
 *
 * [textColor] / [insideMargin] 两个可选参数是为**底部 Sheet 里的分区**加的：
 * - 标题有时要随状态换色（例如模型面板的"已获取 2 个模型"在失败时要变灰）；
 * - Miuix `SmallTitle` 默认内边距是 `PaddingValues(28.dp, 8.dp)`，而 sheet 自身
 *   已经有 24dp 横向内边距，两者相加会让标题比卡片缩进 52dp。Miuix 官方
 *   `BottomSheetSection` 示例在 sheet 里显式覆写成 `PaddingValues(16.dp, 8.dp)`，
 *   这里跟随它 —— 标题比卡片左边缘多缩进 16dp，是官方的层级观感。
 *
 * 两个参数都有默认值，现有调用点不受影响。
 */
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

/**
 * 通知条的语义档。
 *
 * - [INFO]   中性提示（灰底）
 * - [WARN]   警告/注意（琥珀底）—— 对话流里斜杠命令输出、计划审批结果那一类
 * - [ERROR]  错误（红底）
 */
enum class ZhiNoticeTone { INFO, WARN, ERROR }

/**
 * 通知条（一条贴边的横幅）：整宽、**纯色底 + 同色系文字、没有阴影**。
 *
 * ## 为什么要有这个分量
 *
 * 同一件事（"出错了"）在这份工程里原来有**三种长相**：
 *
 * - 沙箱页 `ErrorDetail`：一张 `surfaceContainer` 的 **Card**（深灰）配红字 ——
 *   看起来像一张普通卡片，只是里面的字恰好是红的；红色只出现在文字上，
 *   颜色面积太小，一眼扫过去不觉得"这是出事了"。
 * - 对话流 `ErrorCard`：左边一根 3dp 红竖条 + 红标题 + 正文。
 * - 对话流 `MessageBar` 的错误档：红底红字，但是一个**带投影的浮动工具栏**。
 *
 * 三者讲同一件事却长得不一样。现在统一成这一种**通知条**：
 * 整宽横幅、纯色底、无阴影，改配色只需要改这一处。
 *
 * ## 几何为什么是「小圆角 + 紧内边距」
 *
 * 圆角用 [ZhiRadius.inner]（10dp）而不是卡片的 14dp：通知条比卡片矮得多
 * （两三行文字），14dp 圆角配这点高度会显得"泡"起来。10dp 配 40dp 左右的高度
 * 观感是"有圆润感的横条"，这是参考图里那一条的样子。
 *
 * 内边距比卡片紧一档（竖直 8dp）：它是一条**通知**，不是一张内容卡，
 * 不该有卡片那种舒展的留白。
 *
 * ## 用 `errorContainer` / `error` 这一对，而不是 `error` / `onError*`
 *
 * `errorContainer` 在深色档是**暗红底**，`error` 是它上面那个**亮红字**；
 * 浅色档反过来（浅红底 + 深红字）。两端都是"底色的对比色"，
 * 所以不用按深浅色分叉。`onErrorContainer` 只有浅色档才对得上。
 *
 * @param text 正文。可以是多行（后端错误会带完整的启动阶段）。
 * @param title 可选标题（"错误"、"提示"）。空则不占一行。
 * @param tone 决定底色与文字色，见 [ZhiNoticeTone]。
 * @param content 正文的**自定义渲染**，给了它就不画 [text]。
 *
 *   ⚠️ 这个槽**刻意不把颜色传出去**：对话流的错误/提示正文要过 Markdown，
 *   而 Markdown 里每类块（标题/引用/代码/表格）都有自己的主题色。
 *   传一个"统一正文色"进去只会让调用方以为能覆盖，实际覆盖不了（代码块自带底色）。
 *   标题仍然由这里的 [titleColor] 统一着成该档的主色 —— 那才是"这是错误"的信号。
 */
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
        ZhiNoticeTone.ERROR -> scheme.error
        ZhiNoticeTone.WARN -> ZhiColors.amber()
        ZhiNoticeTone.INFO -> scheme.onSurfaceVariantSummary
    }
    val bodyColor = when (tone) {
        ZhiNoticeTone.ERROR -> scheme.error
        ZhiNoticeTone.WARN -> scheme.onSurface
        ZhiNoticeTone.INFO -> scheme.onSurfaceVariantSummary
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        // 见上面「几何」那段：通知条比卡片矮，圆角跟着小一档。
        shape = RoundedCornerShape(ZhiRadius.inner),
        color = when (tone) {
            ZhiNoticeTone.ERROR -> scheme.errorContainer
            // 琥珀在主题里没有对应的容器色，用「琥珀按低透明度铺在表面色上」——
            // 这样深浅色两档都成立，也不写死一个只在深色下对的十六进制值。
            ZhiNoticeTone.WARN -> ZhiColors.amber().copy(alpha = 0.18f).compositeOver(scheme.surface)
            ZhiNoticeTone.INFO -> scheme.surfaceContainer
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = ZhiSpace.m, vertical = ZhiSpace.s)) {
            if (!title.isNullOrEmpty()) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = ZhiTextScale.Caption,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (content != null) {
                content()
            } else if (text.isNotEmpty()) {
                Text(
                    text = text,
                    color = bodyColor,
                    fontSize = ZhiTextScale.Footnote,
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
