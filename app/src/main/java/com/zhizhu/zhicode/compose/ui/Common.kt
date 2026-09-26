package com.zhizhu.zhicode.compose.ui
import com.zhizhu.zhicode.compose.theme.ZhiRadius

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
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
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 本文件是 Miuix 组件的**唯一转发层**。
 *
 * 这里曾经是一堆手写的 `Box + background + clip(RoundedCornerShape)`，
 * 现已全部换成 Miuix 自己的组件，好处是按压反馈、涟漪、圆角(squircle)、
 * 主题色解析都由 Miuix 负责，和 HyperOS 原生观感一致。
 *
 * 保留 `Iq*` 包装只是为了给调用点一个稳定的签名，内部不再有自绘逻辑。
 */

/** 顶栏 chip 的固定高度；胶囊半径由它推出来。 */
private val ChipHeight = 30.dp

/** 输入器底部小 pill 的固定高度。 */
private val PillHeight = 28.dp

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
 * 而原版 IQ Code 的发送键本来就是文字字形（`text("↑")`），所以 `→` 走这里。
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
    glyphSize: TextUnit = 17.sp,
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
    fontSize: TextUnit = 11.sp,
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
        animationSpec = tween(ZhiMotion.FAST),
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
        animationSpec = tween(ZhiMotion.FAST),
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
            fontSize = 10.sp,
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
 * `matchWidth` 时用的宽度上限。
 *
 * 取一个远大于任何手机宽度的值，好让「等分」结果不被 Miuix 默认的
 * `TabRowWithContourMaxWidth`（84dp）截断 —— 宽度算法是
 * `(可用宽 − (n−1)×间距) / n` 再 `coerceIn(minWidth, maxWidth)`，
 * 上限越小越会迫使整行溢出、进入横向滚动。
 */
private val TabsMatchWidthLimit = 1000.dp

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
