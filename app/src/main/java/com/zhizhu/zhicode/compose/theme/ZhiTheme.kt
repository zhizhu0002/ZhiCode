package com.zhizhu.zhicode.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 当前是否深色主题。
 *
 * 由 `ZhiCodeApp` 提供。存在的理由是 [ZhiColors] 里的层级色必须知道深浅，
 * 而它不能去读 `MiuixTheme.colorScheme.background`（那个值已被本工程覆盖，会形成循环）。
 */
val LocalIqDark = compositionLocalOf { false }

/**
 * 全局统一圆角。
 *
 * 之前每个文件各自写 `RoundedCornerShape(7.dp/8.dp/9.dp/11.dp/13.dp/16.dp…)`，
 * 一屏里能出现十种不同的圆角，看起来就是"没设计过"。现在收敛成四级：
 *
 *  · [floating] 悬浮层（顶栏 / 侧栏抽屉 / 输入器 / 任务卡）
 *  · [card]     卡片、面板、弹窗、列表行 —— 绝大多数容器
 *  · [inner]    卡片内部再嵌一层的小块（代码块、详情框、分段控件的选中胶囊）
 *  · [square]   近乎方角，只给用户明确要求"方形"的输入器
 *
 * 正圆（图标按钮、chip pill）不在这里：它们按高度取 `height / 2`，
 * 写死半径在高度变化时就不再是正圆了。
 */
object ZhiRadius {
    val floating = 20.dp

    /**
     * 文字按钮（`Button` / `TextButton`）。
     *
     * **不能**沿用 [floating]（20dp）：Miuix 按钮的标准高度是
     * `ButtonDefaults.MinHeight` = **40dp**，20dp 半径恰好等于高度的一半，
     * 于是按钮圆成一个**胶囊**。Miuix 自己给按钮的圆角是
     * `ButtonDefaults.CornerRadius` = **16dp**（16/40 = 0.4），是不顶满的圆角矩形。
     *
     * 注：[floating] 给**面板/卡片**用（顶栏下半圆角、悬浮输入器、悬浮任务卡）没问题，
     * 那些容器高度远大于 40dp，20dp 不到半高。
     *
     * 图标按钮不用这个值：Miuix `IconButtonDefaults.CornerRadius` = 40dp 配
     * MinHeight 40dp，也就是**正圆**，见 `ZhiFilledIconButton`。
     */
    val button = 12.dp

    val card = 14.dp
    val inner = 10.dp
    val square = 4.dp
}

/** 布局尺度常量，取自原 IQ Code 的 dp() 用量。 */
object Dimens {
    val screenPadding = 12.dp
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

/**
 * 语义色：Miuix 原生色板里没有 diff 用的绿/红/琥珀，这里补上。
 *
 * ## 为什么是 `@Composable` 函数而不是 `val`
 *
 * 这些色**必须**随深浅色切换，否则浅色模式直接坏掉：同一个 `#34C759` 放在
 * 深色底上是合适的亮绿，放到白底上对比度只有约 1.9:1（几乎看不清）。
 * 所以每个色给一对值（深色底用亮色、浅色底用暗色），并在取值时读一次
 * `MiuixTheme.colorScheme.background` 的亮度来决定用哪一边。
 *
 * 这样调用点不需要各自透传 `isDark`：直接写 `ZhiColors.green()` 即可，
 * 深/浅由当前主题决定。写成函数而不是 `val` 是因为 Kotlin 不允许同名的
 * 属性与函数共存，而 `val` 又无法在取值时读 CompositionLocal。
 */
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

    /**
     * 当前是否深色主题。
     *
     * **不能**用 `MiuixTheme.colorScheme.background.luminance()` 判定：
     * 本工程会把主题的 `background` 覆盖成 [backdrop]，那样就形成了循环依赖 ——
     * 在 `darkColorScheme` 下求值时会读到 Miuix 的*默认*（浅色）方案，
     * 于是深色模式整套层级色都取到浅色分支（面板变白、卡片变浅灰）。
     * 所以改由 [LocalIqDark] 显式提供。
     */
    @Composable
    @ReadOnlyComposable
    fun isDark(): Boolean = LocalIqDark.current

    /** 成功 / 新增（diff 的 `+`、工具完成的 ✓、运行环境就绪）。 */
    @Composable
    fun green(): Color = if (isDark()) greenOnDark else greenOnLight

    /** 失败 / 删除（diff 的 `−`、工具失败、错误）。 */
    @Composable
    fun red(): Color = if (isDark()) redOnDark else redOnLight

    /** 提醒（风险说明、计划批准提示）。 */
    @Composable
    fun amber(): Color = if (isDark()) amberOnDark else amberOnLight

    @Composable
    fun greenContainer(): Color = if (isDark()) greenContainerDark else greenContainerLight

    @Composable
    fun redContainer(): Color = if (isDark()) redContainerDark else redContainerLight

    // ---------------------------------------------------------------- 层级色

    /**
     * 主背板（窗口最底层整片颜色）。
     *
     * 深色 `#242424`（比纯黑亮一点，让纯黑的板块能"退到后面"）；
     * 浅色 `#EDEDED`。
     *
     * 与 [panelSurface] 的关系是：**背板退后、板块站出来**。
     * 深色下板块是纯黑，所以背板必须比它亮，否则两者糊成一片。
     */
    @Composable
    fun backdrop(): Color = if (isDark()) DarkBackdrop else PanelLight

    /** 侧栏 / 面板底色：深色**纯黑**、浅色**纯白**。 */
    @Composable
    fun panelSurface(): Color = if (isDark()) Color.Black else Color.White

    /**
     * 面板里的卡片底。
     *
     * 深色：比纯黑面板**亮**一档（`#2A2A2A`），否则黑底黑卡完全看不见；
     * 浅色：比纯白面板**暗**一档（`#F8F8F8`）。
     */
    @Composable
    fun cardSurface(): Color = if (isDark()) DarkCard else CardLight

    /** 卡片内部再嵌一层的小块（代码块、diff 块、详情框）。 */
    @Composable
    fun cardInnerSurface(): Color =
        if (isDark()) MiuixTheme.colorScheme.surfaceContainerHighest else CardInnerLight

    private val DarkBackdrop = Color(0xFF242424)
    private val DarkCard = Color(0xFF2E2E2E)
    private val PanelLight = Color(0xFFEDEDED)
    private val CardLight = Color(0xFFF8F8F8)
    private val CardInnerLight = Color(0xFFF0F0F0)
}
