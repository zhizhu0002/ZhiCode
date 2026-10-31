package com.zhizhu.zhicode.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.data.ThemeSettings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.feice.mochi.shape.MochiCapsule
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiColorScheme
import ui.feice.mochi.theme.MochiShapes
import ui.feice.mochi.theme.MochiTextStyles
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.darkColorScheme
import ui.feice.mochi.theme.defaultShapes
import ui.feice.mochi.theme.defaultTextStyles
import ui.feice.mochi.theme.lightColorScheme

/**
 * 麻薯（Mochi）与 Miuix 并存的**唯一**桥接层。
 *
 * ## 这一层解决什么问题
 *
 * 工程里现在同时挂着两套主题系统：Miuix 的 `MiuixTheme`（现有全部界面）与
 * Mochi 的 `MochiTheme`（新组件的来源）。两边各有自己的
 *
 * - 色板（`Colors` vs `MochiColorScheme`）；
 * - 字阶（`TextStyles` 14 槽 vs `MochiTextStyles` 20 槽）；
 * - 形状（`ZhiRadius` 手写值 vs `MochiShapes` 的 G2 连续圆角九档）。
 *
 * 不桥接的话，同一个界面里两种组件会**各按各的默认值**排版：字号差 1~2sp、
 * 圆角差 2~6dp、行高一个按 1.2em 一个按写死值 —— 这在逐屏对照里就是
 * 「有的卡片圆一点、有的大一号」那种说不清但一眼看得出的不一致。
 *
 * ## 为什么包在 `MiuixTheme` **内层**
 *
 * `ZhiAppTheme` 的顺序是：深浅判定 → 色板 → `CompositionLocalProvider` → `MiuixTheme`。
 * Mochi 的主题再包一层放在 `MiuixTheme` 里面，等于：
 *
 * 1. 现有所有读 `MiuixTheme.colorScheme` 的调用点**一行不用改**；
 * 2. Mochi 组件读到的 `MochiTheme` 也一定是「已按本工程设置算好的」那一份，
 *    而不是它自己的 `isSystemInDarkTheme()` 默认值；
 * 3. Mochi 组件与 Miuix 组件读到的**强调色是同一个**（Mochi 的 `keyColor` 取自
 *    `MiuixTheme.colorScheme.primary`，见 [rememberZhiMochiColors]）。
 *
 * 如果顺序反过来（Mochi 在外、Miuix 在内），`MiuixTheme` 会用自己的一套色板盖掉
 * 外层，两边的明暗就可能在「应用内强制深色 + 系统浅色」这类组合下打架。
 */
@Composable
fun ZhiMochiTheme(theme: ThemeSettings, content: @Composable () -> Unit) {
    val isDark = ZhiThemeMode.rememberCurrentDark(theme.mode)
    MochiTheme(
        colors = rememberZhiMochiColors(theme, isDark),
        textStyles = zhiMochiTextStyles(),
        shapes = zhiMochiShapes(),
        /*
         * 这里**不**传那个 Boolean（MochiTheme 里真实的名字是 `systemBarAppearance`）。
         * 它管的是「由 Mochi 去改系统栏图标的明暗」；而本工程的系统栏明暗已经由
         * `ZhiThemeMode.ApplySystemBars(isDark)` 在 ZhiAppTheme 最开头统一接管。
         * 两边都写最后写入者胜，会出现「切主题瞬间图标闪一下」。所以留给库默认，
         * 只由我们这一处决定。
         */
        content = content,
    )
}

/**
 * 色板：**把 Miuix 的语义色直接灌进 Mochi**，而不是让 Mochi 自己从种子色生成一套。
 *
 * ## 为什么不能只传种子色（这是实测出来的，不是推断）
 *
 * 第一版只把 `MiuixTheme.colorScheme.primary` 当 `keyColor` 传进去，结果是**两边不对齐**：
 *
 * | | 深色模式下的 primary |
 * | --- | --- |
 * | Miuix | 饱和的中蓝（保持品牌色） |
 * | Mochi | 浅紫蓝（Material tone 80 规则：深色下取浅色档） |
 *
 * 在陈列页并排一看就露馅：同样是 `primary` 加 22% 透明度，左边是深蓝、右边是灰紫；
 * Mochi 按钮是浅紫底配白字（对比度 1.9:1，几乎读不清）。根因不是 bug ——
 * **两套体系对 dark primary 的定义本来就相反**：Material 3 要「深色底配浅色主色」，
 * 而本工程的 Miuix 主题保持饱和品牌色。只对齐种子色，输出的一整条色调曲线仍然不同。
 *
 * 所以这里**逐个语义槽位映射**，让两个 `colorScheme` 在观感上等价：
 * 同一个 `primary` 在左边读 `MiuixTheme.colorScheme.primary`、在右边读
 * `MochiTheme.colorScheme.primary`，拿到的是**同一个值**。
 *
 * ## 映射表（左 = Miuix 字段，右 = Mochi 字段）
 *
 * - 强调：`primary` / `onPrimary` / `primaryContainer` / `onPrimaryContainer`
 * - 次强调：`secondary` / `onSecondary` / `secondaryContainer` / `onSecondaryContainer`
 * - 三级：`tertiaryContainer` / `onTertiaryContainer`（Miuix 没有 tertiary 基色，
 *   Mochi 的 `tertiary` 保持它自己从同一颗种子生成的和谐值）
 * - 中性：`background` / `onBackground` / `surface` / `onSurface` / `surfaceVariant`
 * - 变体文字：Mochi `onSurfaceVariant` ← Miuix `onSurfaceVariantSummary`
 *   （两边这一档都是"次要说明文字"，是混排时最容易看出色差的一处）
 * - 容器：`surfaceContainer` / `surfaceContainerHigh` / `surfaceContainerHighest`
 * - 边框与错误：`outline` ← `outline`、`error` / `onError` / `errorContainer` / `onErrorContainer`
 *
 * Mochi 独有的 `surfaceBright` / `surfaceDim` / `inverseSurface` / `surfaceTint` 等
 * **保留生成值**：Miuix 没有对应语义，硬编会造出一个两侧都说不出理由的数。
 *
 * 颜色全部逐槽位跟随 Miuix，所以两边读到的 `primary` / `surface` / `outline` 是**同一个值**。
 */
@Composable
fun rememberZhiMochiColors(theme: ThemeSettings, isDark: Boolean): MochiColorScheme {
    // 必须在 MiuixTheme 之内调用（顺序见文件头说明）：这两个色板就是对齐的来源。
    val miuix = MiuixTheme.colorScheme
    return remember(miuix, isDark) {
        val base = if (isDark) darkColorScheme(miuix.primary) else lightColorScheme(miuix.primary)
        val mapped = base.copy(
            primary = miuix.primary,
            onPrimary = miuix.onPrimary,
            primaryContainer = miuix.primaryContainer,
            onPrimaryContainer = miuix.onPrimaryContainer,
            secondary = miuix.secondary,
            onSecondary = miuix.onSecondary,
            secondaryContainer = miuix.secondaryContainer,
            onSecondaryContainer = miuix.onSecondaryContainer,
            tertiaryContainer = miuix.tertiaryContainer,
            onTertiaryContainer = miuix.onTertiaryContainer,
            background = miuix.background,
            onBackground = miuix.onBackground,
            surface = miuix.surface,
            onSurface = miuix.onSurface,
            surfaceVariant = miuix.surfaceVariant,
            onSurfaceVariant = miuix.onSurfaceVariantSummary,
            surfaceContainer = miuix.surfaceContainer,
            surfaceContainerHigh = miuix.surfaceContainerHigh,
            surfaceContainerHighest = miuix.surfaceContainerHighest,
            outline = miuix.outline,
            error = miuix.error,
            onError = miuix.onError,
            errorContainer = miuix.errorContainer,
            onErrorContainer = miuix.onErrorContainer,
        )
        mapped
    }
}

/**
 * 字阶：把 Miuix 的 14 个槽位**按实测字号**映射到 Mochi 的 20 个槽位。
 *
 * ## 下表不是猜的
 *
 * 两边的默认值都是从字节码里读出来的（`javap -c` 各 `defaultTextStyles()` 的
 * `bipush` / `TextUnit.getSp` 参数）：
 *
 * | Miuix 槽位 | 字号 | → Mochi 槽位 | Mochi 默认 | 本工程取值 |
 * | --- | --- | --- | --- | --- |
 * | title1 | 32 | headlineLarge | 32/40 | 原样 |
 * | title2 | 24 | headlineSmall | 24/32 | 原样 |
 * | title3 | 20 | titleLarge | 22/28 | **压到 20/28** |
 * | title4 | 18 | titleMedium | 16/24 | **抬到 18/24**（见下） |
 * | headline1 | 17 | bodyLarge | 16/24 | 原样（差 1sp 是可接受的档位差） |
 * | headline2 | 16 | bodyLarge | 16/24 | 原样 |
 * | body1 | 16 | bodyLarge | 16/24 | 原样 |
 * | body2 | 14 | bodyMedium | 14/20 | 原样 |
 * | main | 17 | bodyLarge | 16/24 | 原样 |
 * | paragraph | 17 | bodyLarge | 16/24 | 原样 |
 * | button | 17 | labelLarge | 14/20 | **抬到 16/20**（按钮文字不能比正文小） |
 * | subtitle | 14 Bold | titleSmall | 14/20 | 原样（Bold 由调用点给） |
 * | footnote1 | 13 | labelMedium | 12/16 | **抬到 13/18** |
 * | footnote2 | 11 | labelSmall | 11/16 | 原样 |
 *
 * ## 只改 4 个槽位，其余原样
 *
 * 改动的每一档都能回答「为什么」：20 与 18 是 Miuix 有、Mochi 没有的中间档，
 * 不补就会出现「三级标题突然大 2sp」的跳变；按钮和脚注各差 1~2sp 是两边设计基准不同
 * （Mochi 照 iOS 26，按钮字本来就小一档），但在同一屏里混排时会显得按钮文字「缩了」。
 *
 * **不做整体缩放**。上一版 `zhiTextStyles()` 的教训写在 `ZhiTextStyles.kt` 里：
 * 15 个槽位各乘一个系数之后，「到底缩小了多少」没人答得上来。所以这里改的是
 * **具体槽位的具体值**，四个数就是四个数。
 */
fun zhiMochiTextStyles(): MochiTextStyles {
    val base = defaultTextStyles()
    return base.copy(
        // title3 = 20sp / 行高 28sp（Mochi 默认 22/28）
        titleLarge = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
        // title4 = 18sp / 行高 24sp（Mochi 默认 16/24）
        titleMedium = base.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
        // 按钮文字 16sp：与 body1 同级，不再比正文小一档
        labelLarge = base.labelLarge.copy(fontSize = 16.sp, lineHeight = 20.sp),
        // 脚注 13sp / 行高 18sp（Mochi 默认 12/16）
        labelMedium = base.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
    )
}

/**
 * 形状：把 Mochi 的九档圆角**换成 `ZhiRadius` 的值**，并统一走 G2 连续圆角。
 *
 * ## 为什么要映射而不是用 Mochi 的默认九档
 *
 * Mochi 默认的九档是 8 / 12 / 16 / 16 / 20 / 28 / 32 / 64 / full（同样是从
 * `MochiShapes` 构造器字节码里读的）。它与本工程 `ZhiRadius` 有**两个系统性差异**：
 *
 * 1. **缺 14dp 这一档**（本工程卡片就是 14dp）。Mochi 的 medium 是 16dp，
 *    卡片套上去会比 Miuix 那边的卡片圆 2dp —— 同一屏两种卡片看得出不一样。
 * 2. **多出 64dp 这种超大档**，本工程没有对应的容器。
 *
 * 所以这里按「本工程已有语义」重新填九档，让每个槽位都能指出它服务谁：
 *
 * | 槽位 | 值 | 服务的本工程语义 |
 * | --- | --- | --- |
 * | extraSmall | 8dp | [ZhiRadius.actionKey]：30dp 小方键 |
 * | small | 10dp | [ZhiRadius.inner]：卡片内层 |
 * | medium | 12dp | [ZhiRadius.button]：按钮 |
 * | large | 14dp | [ZhiRadius.card]：卡片 |
 * | largeIncreased | 16dp | 页面级容器（与 [ZhiSpacing.pageMargin] 同节奏） |
 * | extraLarge | 20dp | [ZhiRadius.floating]：悬浮栏 / 输入器 |
 * | extraLargeIncreased | 24dp | 底部面板（比悬浮栏再高一档） |
 * | extraExtraLarge | 28dp | 全屏页 / 大卡片 |
 * | full | 胶囊 | 与 Mochi 默认一致（真正的圆角矩形走上面八档） |
 *
 * ## 为什么用 `MochiRoundedRectangle` 而不是 `RoundedCornerShape`
 *
 * 前者是 G2 **连续圆角**（Apple 那一套 superellipse 观感），后者是普通圆弧。
 * 混排时同一个 14dp 在视觉上不等价：连续圆角的过渡更长，看起来更「软」。
 * 既然要调的是「比例观感」，形状就得同源 —— 而且这本来就是 Mochi 的默认形状语言，
 * 换成普通圆角等于把它的设计意图丢掉一半。
 *
 * ⚠️ 不要用 `ui.feice.mochi.shape.G2RoundedShape(radius, smoothing)`：那个函数在
 * 0.2.0 里标了 `@Deprecated`。公开的替代品是 [MochiRoundedRectangle] 与 [MochiCapsule]，
 * 本文件用的是后者这一对。
 */
fun zhiMochiShapes(): MochiShapes = MochiShapes(
    extraSmall = MochiRoundedRectangle(ZhiRadius.actionKey),
    small = MochiRoundedRectangle(ZhiRadius.inner),
    medium = MochiRoundedRectangle(ZhiRadius.button),
    large = MochiRoundedRectangle(ZhiRadius.card),
    largeIncreased = MochiRoundedRectangle(16.dp),
    extraLarge = MochiRoundedRectangle(ZhiRadius.floating),
    extraLargeIncreased = MochiRoundedRectangle(24.dp),
    extraExtraLarge = MochiRoundedRectangle(28.dp),
    full = MochiCapsule,
)

/**
 * 组件比例令牌：Mochi 组件与 Miuix 组件在**同一屏混排**时必须一致的那几个数。
 *
 * 这几项 Mochi 自己也有默认值，但它的基准是 iOS（44pt 行、8pt 节奏），
 * 而本工程的基准是 HyperOS + Android 无障碍（44dp 行、16dp 页边距、48dp 热区）。
 * 差 2~4dp 单独看不出来，**并排**就看得出来，所以显式写死在这里而不是依赖库默认。
 *
 * 使用约定：调用 Mochi 组件时传 `modifier = Modifier.height(MochiMetrics.rowHeight)`
 * 之类的显式尺寸，不要「让组件自己决定」。等真正替换组件时再用这张表，
 * 不要在调用点各写各的数字。
 */
object MochiMetrics {

    /** 列表行 / 设置行高度。走 [MochiBaseline]，与 [ZhiRow.height] 同源。 */
    val rowHeight = MochiBaseline.rowHeight

    /** 紧凑行（终端扩展键、次级信息）。 */
    val compactRowHeight = MochiBaseline.compactRowHeight

    /** 最小触控热区。Android 无障碍规范 48dp。 */
    val minTouchTarget = MochiBaseline.minTouchTarget

    /** 相邻可点元素的间距。 */
    val touchGap = MochiBaseline.touchGap

    /** 页面级水平边距。 */
    val pageMargin = MochiBaseline.pageMargin

    /** 页面内一级区块之间的间距。 */
    val sectionGap = MochiBaseline.sectionGap
}
