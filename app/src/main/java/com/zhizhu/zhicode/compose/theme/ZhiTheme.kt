package com.zhizhu.zhicode.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** 当前是否深色主题。 */
val LocalZhiDark = compositionLocalOf { false }

/** 全局统一圆角。 */
object ZhiRadius {
    val floating = MochiBaseline.cornerLargeIncreased

    /** 文字按钮（`Button` / `TextButton`）—— 12dp，等于 Mochi 的 `medium`。 */
    val button = MochiBaseline.cornerMedium

    /** 卡片、面板、弹窗、列表行 —— **16dp**，等于 Mochi 的 `large`。 */
    val card = MochiBaseline.cornerLarge

    val inner = MochiBaseline.cornerMedium

    val square = MochiBaseline.cornerExtraSmall

    val actionKey = MochiBaseline.cornerSmall
}

object ZhiSpacing {

    /** 页面级水平边距（手机单手场景）。 */
    val pageMargin = 16.dp

    /** 页面级水平边距（折叠屏 / 平板，双手持或平放）。 */
    val pageMarginWide = 24.dp

    /** 最小触控热区边长（Android 无障碍规范 48dp）。 */
    val minTouchTarget = 48.dp

    /** 密集工具行里的触控热区下限。 */
    val compactTouchTarget = 40.dp

    /** 相邻可点元素之间的最小间距（规范要求 ≥8dp，否则会误触）。 */
    val touchGap = 8.dp

    /** 页面内一级区块之间的距离。 */
    val sectionGap = 12.dp

    /** 行与行之间、以及行内元素之间的距离。 */
    val rowGap = 8.dp
}

/** 布局尺度常量，取自上游原版 IQ Code 的 dp() 用量。 */
object Dimens {
    /** 页面级水平边距。 */
    val screenPadding = ZhiSpacing.pageMargin

    val topBarHeight = 48.dp

    // 圆角统一走 ZhiRadius，这里只保留别名的转发，避免两份数字各改各的
    val composerRadius = ZhiRadius.square
    val cardRadius = ZhiRadius.card
    val chipRadius = ZhiRadius.card
    val pillRadius = ZhiRadius.card

    val rowHeight = 40.dp
    val tabRowHeight = 40.dp
    val bubbleMaxWidthFraction = 0.82f
}

object ZhiColors {
    // ---- 深色底上的取值（亮色系） ----
    private val greenOnDark = Color(0xFF34C759)
    private val redOnDark = Color(0xFFFF453A)
    private val amberOnDark = Color(0xFFE8A33D)

    // ---- 浅色底上的取值（暗色系，保证在白底上对比度 ≥4.5:1） ----
    private val greenOnLight = Color(0xFF1B7F36)
    private val redOnLight = Color(0xFFC62828)
    private val amberOnLight = Color(0xFF9A6100)

    private val greenContainerDark = Color(0xFF12341F)
    private val greenContainerLight = Color(0xFFDCF2E3)
    private val redContainerDark = Color(0xFF3A1A1C)
    private val redContainerLight = Color(0xFFFBE0E0)

    private val WarnFillLight = Color(0xFFF8F1E6)
    private val WarnFillDark = Color(0xFF433B2F)
    private val WarnTextLight = Color(0xFF8A5A00)
    private val WarnTextDark = Color(0xFFF0B860)

    /** 当前是否深色主题。 */
    @Composable
    @ReadOnlyComposable
    fun isDark(): Boolean = LocalZhiDark.current

    /** 成功 / 新增（diff 的 `+`、工具完成的 ✓、运行环境就绪）。 */
    @Composable
    fun green(): Color = if (isDark()) greenOnDark else greenOnLight

    /** 失败 / 删除（diff 的 `−`、工具失败、错误）。 */
    @Composable
    fun red(): Color = if (isDark()) redOnDark else redOnLight

    /** 提醒（风险说明、计划批准提示）。 */
    @Composable
    fun amber(): Color = if (isDark()) amberOnDark else amberOnLight

    /** 警示通知条的底色（淡琥珀实色，见 [WarnFillLight] 的说明）。 */
    @Composable
    fun warnFill(): Color = if (isDark()) WarnFillDark else WarnFillLight

    /** 警示通知条上的文字色（与 [warnFill] 成对，对比度 ≥5:1）。 */
    @Composable
    fun warnText(): Color = if (isDark()) WarnTextDark else WarnTextLight

    @Composable
    fun greenContainer(): Color = if (isDark()) greenContainerDark else greenContainerLight

    @Composable
    fun redContainer(): Color = if (isDark()) redContainerDark else redContainerLight

    // ---------------------------------------------------------------- 层级色

    /** 主背板（窗口最底层整片颜色）。 */
    @Composable
    fun backdrop(): Color = when {
        // Miuix 的普通深色背板不是纯黑；Sheet、页面缝隙和模糊背板
        // 都依赖这一层与 surface/container 拉开层级。
        isDark() -> DarkBackdrop
        else -> PanelLight
    }

    @Composable
    fun panelSurface(): Color = when {
        isDark() -> PanelDark
        else -> Color.White
    }

    /** 面板里的卡片底。 */
    @Composable
    fun cardSurface(): Color = when {
        isDark() -> DarkCard
        else -> CardLight
    }

    /** 卡片内部再嵌一层的小块（代码块、diff 块、详情框）。 */
    @Composable
    fun cardInnerSurface(): Color =
        if (isDark()) MiuixTheme.colorScheme.surfaceContainerHighest else CardInnerLight

    val plateBlue = Color(0xFF3182FF)
    val plateGreen = Color(0xFF1B9C48)
    val plateOrange = Color(0xFFD97500)
    val plateRed = Color(0xFFF2453D)
    val platePurple = Color(0xFF7A5AF8)
    val plateGray = Color(0xFF8A9099)
    @Composable
    fun terminalSurface(): Color = if (isDark()) TerminalDark else TerminalLight

    private val TerminalDark = Color(0xFF17171A)
    private val TerminalLight = Color(0xFFEAEAEE)

    private val DarkBackdrop = Color.Black
    private val PanelDark = Color(0xFF242424)
    private val DarkCard = Color(0xFF2D2D2D)


    /** 浅色中性阶。 */
    internal val PanelLight = Color(0xFFF1F1F3)
    internal val CardLight = Color(0xFFFAFAFC)
    internal val CardInnerLight = Color(0xFFF7F7F8)

    /** 浅色的次级填充（对应 Miuix 的 `secondary` / `secondaryVariant` / `secondaryContainer`）。 */
    internal val SoftFillLight = Color(0xFFF2F2F4)

    /** 浅色的弱化文字 / 图标色（对应 Miuix 的 `onBackgroundVariant`）。 */
    internal val MutedLight = Color(0xFF6E6E76)

    internal val MutedDark = Color(0xFF9A9AA2)

    internal val SummaryDark = Color(0xFF9E9EA6)

    /** 深色下**容器/选中底上**的正文色（对应 Miuix 的 `onSecondaryContainer` 与 */
    internal val SecondaryDark = Color(0xFFB0B0B8)
}

object ZhiOverlayPalette {

    /** 当前主题下的一组 ARGB。 */
    class Snapshot(
        /** 面板/对话框的底色。 */
        @JvmField val surface: Int,
        /** 压在 [surface] 上的主文字色（对比度由 Miuix 的主题保证）。 */
        @JvmField val onSurface: Int,
        /** 副文字色（说明、次要信息）。 */
        @JvmField val muted: Int,
        /** 强调色（品牌名、可点动作）。 */
        @JvmField val accent: Int,
        /** 危险动作色（「停止」）。 */
        @JvmField val danger: Int,
        /** 底色是否为浅色。系统栏图标明暗据此决定。 */
        @JvmField val light: Boolean,
    )

    /** 解析当前主题。 */
    @JvmStatic
    fun resolve(context: android.content.Context?): Snapshot {
        val dark = if (context == null) {
            true
        } else {
            ZhiThemeMode.resolve(ZhiThemeMode.stored(context), ZhiThemeMode.systemDark(context))
        }
        val scheme = if (dark) darkColorScheme() else lightColorScheme()
        return Snapshot(
            surface = scheme.surfaceContainer.toArgb(),
            onSurface = scheme.onSurfaceContainer.toArgb(),
            muted = scheme.onSurfaceVariantSummary.toArgb(),
            accent = scheme.primary.toArgb(),
            danger = scheme.error.toArgb(),
            light = !dark,
        )
    }
}
