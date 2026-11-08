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

/**
 * 当前是否深色主题。
 *
 * 由 `ZhiCodeApp` 提供。存在的理由是 [ZhiColors] 里的层级色必须知道深浅，
 * 而它不能去读 `MiuixTheme.colorScheme.background`（那个值已被本工程覆盖，会形成循环）。
 */
val LocalZhiDark = compositionLocalOf { false }

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
    val floating = MochiBaseline.cornerLargeIncreased

    /**
     * 文字按钮（`Button` / `TextButton`）—— 12dp，等于 Mochi 的 `medium`。
     *
     * 不能沿用 [floating]（20dp）：按钮高度 48dp 时 20dp 半径已接近半高、偏胶囊；
     * 12 / 48 = 0.25 是不顶满的圆角矩形。
     *
     * 图标按钮不用这个值：图标按钮是正圆，见 `ZhiFilledIconButton`。
     */
    val button = MochiBaseline.cornerMedium

    /**
     * 卡片、面板、弹窗、列表行 —— **16dp**，等于 Mochi 的 `large`。
     *
     * 这是全应用**唯一**的卡片圆角来源：`ZhiMetrics.card`、`MochiSkin` 的 `large`、
     * 以及各处 `cornerRadius = ZhiRadius.card` 都指向这里，改这一个值即全局生效。
     *
     * 沿革：14dp → 28dp（照抄 Mochi `extraLarge`）→ **16dp**。
     * 28dp 的问题：它比 Miuix 自己的 `CardDefaults.CornerRadius`（16dp）大得多，
     * 于是同一个应用里并存两种卡片圆角 —— 设置页 28、弹窗与下拉 16，摆在一起
     * 像两套设计。统一到 16 之后与 Miuix 原生组件天然一致。
     */
    val card = MochiBaseline.cornerLarge

    /**
     * 卡片内部再嵌一层的小块 —— **12dp**。
     *
     * ⚠️ 这里**不能**再用同心公式 `card − rowTextInset`：card 降到 16 之后
     * `16 − 16 = 0`，内嵌块会变成直角，比外卡片还硬。所以退回显式的 `medium` 档。
     * 12 与 16 只差 4dp，视觉上仍是"里层比外层略方一点"的正常嵌套关系。
     */
    val inner = MochiBaseline.cornerMedium

    val square = MochiBaseline.cornerExtraSmall

    /**
     * 输入器底部「发送 / 停止」这类 30dp 小方键的圆角。
     *
     * ## 为什么要单独一个值
     *
     * 方角的观感取决于**圆角/边长这个比例**，不是圆角的绝对值：
     *
     * | 圆角 | 除以 30dp 边长 | 观感 |
     * | --- | --- | --- |
     * | [square] 4dp | 0.13 | 硬邦邦的方块（试过，用户反馈"太方了"） |
     * | 8dp（本值） | 0.27 | 圆角方键，有圆润感但明确是"方"的 |
     * | [inner] 10dp | 0.33 | 已经偏胶囊（配 30dp 时用户反馈"两个键像一块被劈成两半"） |
     *
     * 同一个 10dp 放在 40dp 的按钮上只有 0.25、还算方，所以**不是** [inner] 变了，
     * 是按钮变小之后必须跟着换档。写死一个绝对值给两种尺寸用，必然有一边不对 ——
     * 这也是为什么它值得单独一个 token，而不是继续复用 [inner] 或 [square]。
     */
    val actionKey = MochiBaseline.cornerSmall
}

/**
 * 间距与触控热区令牌（对齐 Miuix / HyperOS 的自适应设计基准）。
 *
 * ## 为什么单独一个对象，而不是继续散在 `padding(horizontal = 12.dp)` 里
 *
 * 改版前页面级水平边距是 **12dp 与 16dp 混用**（设置页 16、关于页 12、
 * 许可页 18…）。同一个应用里三种页面边距，翻页时左右边缘会"跳"——
 * 这是最容易被看出来、又最容易被归因成"没对齐"的一类问题。
 *
 * HYPEROS 的自适应设计基准给手机单手场景的防误触边距是 **16px**（≈16dp），
 * 所以页面级统一取 16dp，折叠屏/平板再按 [pageMarginWide] 放宽。
 *
 * ## 边界：这**不是**组件内部内边距的替代品
 *
 * Miuix 的 `Card` / `Preference` / `BasicComponent` 内部有自己的 padding，
 * 规范明确要求**不要**去覆盖它们。所以本对象只管"页面与屏幕边缘之间"、
 * "页面内一级区块之间"这两类间距；卡片内部的 12dp 不动。
 */
object ZhiSpacing {

    /** 页面级水平边距（手机单手场景）。 */
    val pageMargin = 16.dp

    /** 页面级水平边距（折叠屏 / 平板，双手持或平放）。 */
    val pageMarginWide = 24.dp

    /**
     * 最小触控热区边长（Android 无障碍规范 48dp）。
     *
     * Miuix `IconButtonDefaults` 实测是 `MinWidth = MinHeight = 40dp` ——
     * 也就是**库的默认值本身就不满足 48dp**。所以凡是本工程自己接的
     * 图标按钮 / 行内可点区域，都要显式抬到这个值，而不是依赖库默认。
     */
    val minTouchTarget = 48.dp

    /**
     * 密集工具行里的触控热区下限。
     *
     * 顶栏总高 48dp，里面再塞 48dp 的按钮盒会把图标挤到贴边；这里退一档到 40dp
     * （与 Miuix 默认一致）。这一档**只允许**出现在高度受限的栏内，
     * 且必须与相邻可点元素保持 ≥8dp 间距。
     */
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
    /**
     * 页面级水平边距。
     *
     * 值转发自 [ZhiSpacing.pageMargin]（16dp）：旧值 12dp 是上游原版的用量，
     * 按设计基准（手机单手防误触 16dp）抬了一档。留着这个别名是因为它被当作
     * "页面边距"读了很多年，改名反而会让调用点对不上号。
     */
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
     * 所以改由 [LocalZhiDark] 显式提供。
     */
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

    @Composable
    fun greenContainer(): Color = if (isDark()) greenContainerDark else greenContainerLight

    @Composable
    fun redContainer(): Color = if (isDark()) redContainerDark else redContainerLight

    // ---------------------------------------------------------------- 层级色

    /**
     * 主背板（窗口最底层整片颜色）。
     *
     * 深色 `#28282A`（比面板略亮，保留 HyperOS 的柔和层次）；
     * 浅色 `#F1F1F3`。
     *
     * 与 [panelSurface] 的关系是：**背板退后、板块站出来**。
     * 背板只负责托住工作区，不与内容卡片争夺注意力。
     */
    @Composable
    fun backdrop(): Color = when {
        // Miuix 的普通深色背板不是纯黑；Sheet、页面缝隙和模糊背板
        // 都依赖这一层与 surface/container 拉开层级。
        isDark() -> DarkBackdrop
        else -> PanelLight
    }

    /**
     * 侧栏 / 二级页面（设置、MCP、技能…）的底色。
     *
     * 深色下用 [PanelDark]（`#1C1C1E`）而**不是**纯黑 —— 这是 Miuix 的层级规矩：
     * 背板（[backdrop]）最暗、面板亮一档、卡片再亮一档。三级拉开了，
     * 分组边界才会自然出现。
     *
     * ⚠️ 曾经这里被改成 `Color.Black`，结果是设置页、MCP 页这类二级页面整块发黑，
     * 与顶部 `SmallTopAppBar` 的 `surface` 之间出现一道明显断层，卡片也闷在背景里。
     * 纯黑只用于 **AMOLED**（见 [backdrop]）与**侧栏抽屉**这种刻意的产品选择。
     */
    @Composable
    fun panelSurface(): Color = when {
        isDark() -> PanelDark
        else -> Color.White
    }

    /**
     * 面板里的卡片底。
     *
     * 深色：比柔黑面板**亮**一档（`#323235`），让分组边界自然出现；
     * 浅色：比纯白面板**暗**一档（`#FAFAFC`）。
     */
    @Composable
    fun cardSurface(): Color = when {
        isDark() -> DarkCard
        else -> CardLight
    }

    /** 卡片内部再嵌一层的小块（代码块、diff 块、详情框）。 */
    @Composable
    fun cardInnerSurface(): Color =
        if (isDark()) MiuixTheme.colorScheme.surfaceContainerHighest else CardInnerLight

    // ------------------------------------------------------- 行首图标底色（Plate）
    //
    // 设置页每一行的行首是一块彩色方块（借小米「设置」的观感，见
    // `ui/settings/SettingsIconPlate.kt`）。这六个值原来写在那**UI 文件**里，
    // 是当时唯一一处"主题色散落在组件里"的地方 —— 按规范，字面色值只应存在于
    // 主题层，调用方只认语义名。
    //
    // ⚠️ 深浅色共用一套：彩色方块在深色底上本来就是亮点，小米也这么做。
    // ⚠️ 新增颜色必须与白色字形的对比度 ≥3:1（方块里的字形是纯白）。
    //
    // 它们是**常量**而不是 `@Composable` 函数（与上面的层级色不同）：plate 色不随
    // 深浅变化，所以不需要读 CompositionLocal；写成常量才能在非组合上下文
    // （数据类默认值、预览）里直接用。
    val plateBlue = Color(0xFF3182FF)
    val plateGreen = Color(0xFF1B9C48)
    val plateOrange = Color(0xFFD97500)
    val plateRed = Color(0xFFF2453D)
    val platePurple = Color(0xFF7A5AF8)
    val plateGray = Color(0xFF8A9099)
    /**
     * **终端井**：工具输出、diff、运行中的实时输出都画在它上面。
     *
     * 判据只有一条（用户原话：「底色比其他地方要黑的就是我说的预览框」）：
     * **它必须比页面底色更暗**。IQ Code 反编译版（`~/.iqcode/.../MainActivity.java`
     * 的 `TERMINAL_BG`）三套调色板都遵守这条：
     *
     * | 调色板 | 页面 `BG` | 终端井 `TERMINAL_BG` |
     * | --- | --- | --- |
     * | 暖黑（默认深色） | `#12110F` | `#0A0B0C` ← 更暗 |
     * | 霓虹 | `#060D1C` | `#040914` ← 更暗 |
     * | 亮色 | `#F7F6F2` | `#F1EFE8` ← 更暗 |
     *
     * 而不是像 [cardInnerSurface] 那样"深色下比卡片亮一档"（Miuix 的
     * `surfaceContainerHighest`）—— 那是**卡片**的层级语义，方向恰好相反。
     * 方向不是配色偏好：井表示"这是程序吐出来的原始字节"，卡片表示"这是应用渲染的内容"。
     *
     * 取值按同一关系落到本工程的页面底色上（深 [backdrop] `#242424` / 浅 `#EDEDED`）：
     * 深色直接用 IQ Code 的 `#0A0B0C`；浅色取 `#E7E5DE` —— 与 IQ Code 亮色里
     * "比页面低 6/7/10" 的差值同量级。
     */
    @Composable
    fun terminalSurface(): Color = if (isDark()) TerminalDark else TerminalLight

    private val TerminalDark = Color(0xFF0A0B0C)
    private val TerminalLight = Color(0xFFE7E5DE)

    /*
     * 深色三级色阶 —— 数值全部取自 Miuix `darkColorScheme`（
     * `miuix-ui/.../theme/Colors.kt:451`），不是自己调的。
     *
     * | 层 | Miuix 槽位 | 值 |
     * | --- | --- | --- |
     * | 页面背板 | `surface`（`Scaffold.containerColor` 的默认值） | **纯黑 `#000000`** |
     * | 面板（侧栏 / 二级页底） | `background` | `#242424` |
     * | 卡片 / 分组 | `surfaceContainerHighest` | `#2D2D2D` |
     *
     * ⚠️ 这里曾经把背板写成 `#28282A`、把面板压到 `#1C1C1E`，理由是"纯黑太像
     * AMOLED"。但 Miuix 的深色页面**本来就是纯黑**：`Scaffold` 不给
     * `containerColor` 时用 `colorScheme.surface`，而深色 `surface = Color.Black`。
     * 真正的层级感来自"纯黑页面 + `#242424` 卡片"，而不是把页面提亮 ——
     * 把页面提亮到 `#28282A` 之后，页面与卡片只差一档，反而糊成一片。
     *
     * 浮层不受影响：`BottomSheetDefaults.backgroundColor()` 与
     * `OverlayDialog` 取的是 `background`（`#242424`），不是 `surface`。
     */
    private val DarkBackdrop = Color.Black
    private val PanelDark = Color(0xFF242424)
    private val DarkCard = Color(0xFF2D2D2D)


    /**
     * 浅色中性阶。
     *
     * 这几个值**不只本工程自己取**：[ZhiAppTheme] 会拿它们覆写 Miuix 浅色方案里
     * 那几个更脏的灰（`surfaceContainerHigh` / `surfaceContainerHighest` = `#E8E8E8`、
     * `secondary` = `#E6E6E6`、`secondaryVariant` / `secondaryContainer` = `#F0F0F0`）。
     * 所以它们是**浅色模式唯一的中性色来源**，改这里即可全局生效，不要再去各调用点
     * 逐个换色 —— 那样只会得到第二套灰。
     */
    internal val PanelLight = Color(0xFFF1F1F3)
    internal val CardLight = Color(0xFFFAFAFC)
    internal val CardInnerLight = Color(0xFFF7F7F8)

    /** 浅色的次级填充（对应 Miuix 的 `secondary` / `secondaryVariant` / `secondaryContainer`）。 */
    internal val SoftFillLight = Color(0xFFF2F2F4)

    /**
     * 浅色的弱化文字 / 图标色（对应 Miuix 的 `onBackgroundVariant`）。
     *
     * Miuix 浅色给的是 `#8C93B0` —— 一个**带蓝味**的灰。整个浅色方案里其余中性色都是
     * 纯灰，只有它是蓝灰：铺在白底上既显得脏，对比度也只有约 2.9:1。换成中性灰后
     * 对比度约 5.3:1，且与 [PanelLight] / [CardLight] 同一族。
     */
    internal val MutedLight = Color(0xFF6E6E76)
}

/**
 * 给**非 Compose**的界面取色：注入到 guest 里的沙箱控制栏与它的日志面板。
 *
 * ## 为什么需要它
 *
 * 那两处是纯 `View`（`SandboxOverlay`），不是 Compose 树，所以拿不到
 * `MiuixTheme.colorScheme`。它原来自己维护了一套配色 —— 读的是 IQ Code 时代的
 * `ui_theme` 键（`day` / `neon-purple` / `custom` / `classic`），那套键跟现在的应用主题
 * **毫无关系**，于是那个悬浮窗一直是"IQ Code 的颜色"而不是 Miuix 的。
 *
 * 这里把"深浅"（[ZhiThemeMode]）与"Miuix 的语义色"（[darkColorScheme] /
 * [lightColorScheme]）接起来，返回一串 ARGB，颜色**没有第二份来源**。
 *
 * ## 为什么返回 int 而不是 `ColorScheme`
 *
 * `androidx.compose.ui.graphics.Color` 是 `@JvmInline value class`，从 Java 侧看到的是
 * `long`，还得再走名字被 mangle 掉的 `toArgb`。直接给 int 让 Java 调用点一行就能用。
 *
 * ## 为什么可以做在 Compose 之外
 *
 * [darkColorScheme] / [lightColorScheme] 是**普通函数**（带默认参数的色板构造器），
 * 不是 `@Composable`；[ZhiThemeMode.stored] / [ZhiThemeMode.systemDark] 同样不依赖组合。
 * 所以这里不需要任何 Compose 运行时就能求值 —— 这正是 guest 进程里唯一可行的做法。
 */
object ZhiOverlayPalette {

    /**
     * 当前主题下的一组 ARGB。
     *
     * 字段是 `@JvmField`：Java 调用点写 `palette.surface` 而不是 `palette.getSurface()`，
     * 与 `SandboxOverlay` 里那些 `View` 代码的读法一致。
     */
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

    /**
     * 解析当前主题。
     *
     * `context` 只用来读用户选的深浅模式；读不到就跟随系统（[ZhiThemeMode.stored] 的语义），
     * 再读不到就按深色 —— 悬浮窗压在任何 guest 界面上，深色底白字是唯一"在什么背景上都读得清"的选择。
     */
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
