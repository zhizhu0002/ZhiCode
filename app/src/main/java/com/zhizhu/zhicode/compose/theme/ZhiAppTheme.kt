package com.zhizhu.zhicode.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.zhizhu.zhicode.compose.data.ThemeSettings
import com.zhizhu.zhicode.compose.model.ThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import ui.feice.mochi.effect.overscroll.ProvideMochiOverscroll

/**
 * 应用的唯一主题栈：深浅判定 + 配色（含 Monet 种子色）+ 文字字阶 + 界面缩放。
 *
 * ## 为什么要从 `ZhiCodeApp` 里抽出来
 *
 * 这一段原先长在 `ZhiCodeApp` 内部，于是**只有主界面那一棵组合树**在主题里。
 * 第二个 Activity（先有沙箱页、后有编辑器页）各自抄了一份 —— 抄漏一处就是
 * 「设置里选浅色、这一屏还是深色」那类问题；沙箱页当初正是这么错的。
 *
 * 现在只有这一个入口，两个 Activity 都调它。
 *
 * ## 顺序不能换
 *
 * 1. 先算深浅（[ZhiThemeMode.rememberCurrentDark] —— 全局唯一的判定处）；
 * 2. 再算色板（[rememberZhiColors]，Monet 那条路要在这里生成种子色板）；
 * 3. `CompositionLocalProvider(LocalZhiDark, LocalThemeSettings, LocalDensity)`。
 *    **必须早于任何 `ZhiColors` 取值**：那些层级色靠这两个 Local 判断深浅与纯黑，
 *    读早了会拿到默认值（浅色、非纯黑），于是深色模式下背板被设成浅灰、弹窗整片发白；
 * 4. 最后 `MiuixTheme`。`background` 被换成 [ZhiColors.backdrop]：Miuix 浅色方案里
 *    background / surface / surfaceContainer 都是 `#FFFFFF`，纯白铺满后板块分不出来。
 *
 * ## 性能：色板必须 `remember`
 *
 * 改之前这里每次重组都执行 `colors.copy(background = …)` 与 `zhiLightNeutrals()`
 * —— 两个都**新建一个 `Colors` 实例**（50 多个 `mutableStateOf` 字段）。
 * 而 `AppScaffold` 这一层在流式输出期间会反复重组，于是每帧都在造垃圾，
 * 还让 `MiuixTheme` 内部的 `updateColorsFrom` 每次都重跑一遍全量赋值。
 * 现在整段结果按「深浅 + 主题设置」缓存，设置不变就是**同一实例**。
 */
@Composable
fun ZhiAppTheme(theme: ThemeSettings, content: @Composable () -> Unit) {
    val isDark = ZhiThemeMode.rememberCurrentDark(theme.mode)
    // 应用内换主题时状态栏图标要跟着换：onResume 那条路径只在回到前台时补，
    // 期间图标会一直是相反的明暗。
    ZhiThemeMode.ApplySystemBars(isDark)

    CompositionLocalProvider(
        LocalZhiDark provides isDark,
        LocalThemeSettings provides theme,
        // 界面缩放：只改密度，不改布局代码。`fontScale` 必须原样带过去 ——
        // 否则用户系统的「字体大小」设置会在应用里失效。
        LocalDensity provides scaledDensity(theme.uiScale),
    ) {
        // ⚠️⚠️ 色板必须在**这两个 Local 之内**求值，这是修过的一个真实 bug。
        //
        // 原来 `val colors = rememberZhiColors(theme, isDark)` 写在
        // `CompositionLocalProvider` **之前**，于是色板函数里的
        // `ZhiColors.backdrop()` 读到的是 Provider **外层**的值：
        //   · `LocalZhiDark` → 默认 `false`；
        //   · 历史遗留的 AMOLED 信号已移除（Miuix 用不到纯黑）。
        // 结果：**深色模式下整个主题的 `background` 是浅色背板 `#F1F1F3`**，
        // 而面板/卡片（它们读的是 Provider 内的正确值）是深色 —— 板块之间的缝、
        // 顶栏透出的底色、页面切换时露出的那一层全是浅灰；AMOLED 开关同样完全无效
        // （因为 `background` 那一份永远按非纯黑算出来）。
        // 用户说的「主题还是有很多问题」里最主要的一条就是这个。
        MiuixTheme(
            colors = rememberZhiColors(theme, isDark),
            textStyles = zhiTextStyles(),
        ) {
            // 麻薯（Mochi）主题再包一层，位置在这里是**有讲究的**：见 MochiSkin.kt
            // 文件头的说明。一句话：现有调用点读 MiuixTheme 的不动，Mochi 组件读到的是
            // 同一套种子色 / 深浅判定 / G2 圆角，两套组件混排时不会各按各的默认值排版。
            ZhiMochiTheme(theme) {
                /*
                 * 越界回弹：Mochi 组件要求根部提供，`llms.txt:10` 把它和 MochiTheme
                 * 并列为 root 必接项。
                 *
                 * 为什么现在就要接，虽然还没上 Mochi 页面：它提供的是 Mochi 组件
                 * 读的 `LocalMochiOverscroll`，没有它时 Mochi 列表回退到硬停，
                 * 观感与它自己的规范不一致（`philosophy.md:38`「越界用橡皮筋，不硬停」）。
                 * 提前接上，第 4 阶段写第一个 Mochi 页面时不用再改主题层。
                 *
                 * 对本工程现有界面**无影响**：Miuix 的 `overScrollVertical()` 与
                 * 悬浮栏的拖动各有自己的实现，不读这个 Local。
                 */
                ProvideMochiOverscroll {
                    content()
                }
            }
        }
    }
}

/**
 * 当前主题设置。
 *
 * 分成独立的 Local，而不是让每个调用点都从参数往下传：读它的地方分散在
 * 顶栏、输入器、`FloatingBottomShell`、`rememberGlass`、侧栏抽屉……
 * 逐个传参会让每一层的签名都长出一截，而它们其实只需要读。
 */
val LocalThemeSettings = androidx.compose.runtime.compositionLocalOf { ThemeSettings() }

/**
 * 生成最终色板。
 *
 * ## 两条路，**只有中性色不同**
 *
 * 种子色（预设色卡 / 自定义色）在**两种模式下都生效** —— 它决定强调色（primary 一族）。
 * [ThemeSettings.dynamicColor] 决定的是**中性色**要不要也跟着种子色走：
 *
 * - **Monet 开**：整套色板由 [ThemeController]（Material Color Utilities 的 HCT 算法）
 *   从种子色生成。中性色会带一点种子色的色味，这是 Monet 的标志。
 * - **Monet 关**：中性色（背板 / 面板 / 卡片 / 次级填充）用本工程调过的那一套固定值
 *   （浅色见 [Colors.zhiLightNeutrals]，深色用 `darkColorScheme()` 原值），
 *   **只有强调色一族**取自生成结果。
 *
 * ⚠️ 这条分工是修过的一个真实 bug。之前 `dynamicColor = false` 时整份色板都是写死的，
 * 于是**点预设色卡什么都不变** —— 只有预览卡与设置页里那个色块换了个颜色，
 * 界面其余部分还是原来的蓝。用户看到的就是"主题设置里一堆东西点了没用"。
 *
 * ## 为什么不用 `MiuixTheme(controller = …)`
 *
 * 那个重载会把 `colorSchemeMode` 一起提供出去（`MiuixTheme.isDynamicColor` 依赖它），
 * 但我们还要在**它之上**覆写 `background` 与中性色，而 controller 那条路没有
 * "再覆写一层"的出口。所以这里只借 controller 的 `currentColors()` 拿到生成结果，再自己 copy。
 */
/** 高亮强调色底上的文字色：近黑而非纯黑（纯黑压在饱和色上会显脏）。 */
private val OnBrightSeed = Color(0xFF141414)

@Composable
private fun rememberZhiColors(theme: ThemeSettings, isDark: Boolean): Colors {
    val backdrop = ZhiColors.backdrop()
    if (!theme.dynamicColor) {
        // 固定中性色 + 种子色当强调色。不经过 HCT：预设色卡上的那个颜色就该是
        // 界面里按钮/开关/选中态的颜色，HCT 的 TonalSpot 会把它压成低饱和的中间调
        // （默认蓝 #3482FF 会变成 #415F91 那种灰蓝），"选了绿色结果界面是灰绿"。
        return remember(isDark, backdrop, theme.seedArgb) {
            val base = if (isDark) darkColorScheme().zhiDarkNeutrals() else lightColorScheme().zhiLightNeutrals()
            val seed = Color(theme.seedArgb)
            // 亮色种子（暖阳橙 / 樱花粉）配白字会糊，按亮度换深色字。
            // 用命名常量而不是字面量：它是"高亮底上近乎纯黑的字"，与纯黑不同
            // （纯黑压在饱和色上会显得脏），这个区别值得有个名字。
            val onSeed = if (seed.luminance() > 0.45f) OnBrightSeed else Color.White
            val container = if (isDark) lerp(seed, Color.Black, 0.62f) else lerp(seed, Color.White, 0.78f)
            val onContainer = if (isDark) lerp(seed, Color.White, 0.55f) else lerp(seed, Color.Black, 0.45f)
            base.copy(
                /*
                 * ⚠️ `background` **不要**覆盖成页面背板。
                 *
                 * 它曾经写成 `background = backdrop`（图省事，想让整页与背板同色）。
                 * 但 Miuix 把 `background` 用作**抬升面板**的底色：
                 *  · `BottomSheetDefaults.backgroundColor()` = `colorScheme.background`
                 *  · `DialogDefaults.backgroundColor` 同上（见 `ui/pages/overlays/PermissionOverlay.kt` 的注释）
                 *  · `ui/SideDrawer.kt` 抽屉直接取 `scheme.background`
                 *
                 * 于是背板一旦改成纯黑（Miuix 规范的页面色），底部弹层、对话框、
                 * 侧抽屉全跟着变纯黑，与页面糊成一片 —— 用户报的
                 * 「dialog 和抽屉变成纯黑色」就是这个覆盖造成的回归。
                 *
                 * 正确分工：页面背板用 `surface`（纯黑）与 `ZhiColors.backdrop()`，
                 * `background` 留给 Miuix 自己的 `#242424`（浅色为白）。
                 *
                 * ⚠️ `surface` 同样**不要覆写**：它恰恰是页面背板
                 * （`Scaffold.containerColor` 的默认值），Miuix 深色下就是
                 * `Color.Black`。一度把它抬到 `#242424`，只会让页面与卡片
                 * 只差一档、整体发灰，却完全解决不了浮层的问题。
                 */
                surface = base.surface,
                primary = seed,
                onPrimary = onSeed,
                primaryVariant = seed,
                onPrimaryVariant = onSeed,
                primaryContainer = container,
                onPrimaryContainer = onContainer,
                // `secondary` 一族**不**跟着种子色走：浅色里它们被
                // `zhiLightNeutrals()` 用作中性填充（`SoftFillLight`），
                // 换成带色味的值会让浅色下"发灰发脏"那个老问题回来。
            )
        }
    }

    // Monet：整套色板由 HCT 从种子色生成（含中性色）。控制器本身要 remember，
    // 否则每次重组都重建一套（含算法计算）。
    val controller = remember(theme.seedArgb, theme.paletteStyle, theme.colorSpec, isDark) {
        ThemeController(
            colorSchemeMode = ColorSchemeMode.MonetSystem,
            keyColor = Color(theme.seedArgb),
            // 存的是枚举的 name（见 ThemeSettings 的说明），非法值回落到默认，
            // 而不是抛异常：老版本写下的名字读不到是常态。
            paletteStyle = ThemePaletteStyle.entries
                .firstOrNull { it.name == theme.paletteStyle } ?: ThemePaletteStyle.TonalSpot,
            colorSpec = ThemeColorSpec.entries
                .firstOrNull { it.name == theme.colorSpec } ?: ThemeColorSpec.Spec2021,
            // 显式给 isDark：三条深浅模式（跟随系统/浅色/深色）都已经在
            // `ZhiThemeMode.resolve` 里收敛成这一个布尔，不必让控制器再读一次系统。
            isDark = isDark,
        )
    }
    val generated = controller.currentColors()
    return remember(generated, backdrop, isDark) {
        // Monet 的浅色板用它自己的中性色（带种子色的灰）——**不要**再套
        // `zhiLightNeutrals()`：那套灰是为"固定色板"调的，叠在生成色板上
        // 会让强调色与容器色对不上（生成算法保证的是它自己那一套对比度）。
        // 同上面固定色板分支：`background` 不覆盖（它服务于底部弹层 / 对话框 / 抽屉），
        // 页面背板由 `ZhiColors.backdrop()` 与 `surface` 负责。
        //
        // ⚠️ 但**弱化文字/图标**这一项要盖掉，理由与固定色板分支完全相同：
        // Monet 生成出来的 `onBackgroundVariant` 带种子色的色相，在整屏图标上
        // 就是一层"发灰的滤镜"。它表达的是"弱一档"，不该表达"另一种颜色"。
        // 注意这里**只**动这两个字段，中性容器仍用生成结果。
        if (isDark) {
            generated.copy(
                onBackgroundVariant = ZhiColors.MutedDark,
                onSurfaceVariantSummary = ZhiColors.SummaryDark,
            )
        } else {
            generated.copy(onBackgroundVariant = ZhiColors.MutedLight)
        }
    }
}

/** 见 [ZhiAppTheme] 里的界面缩放说明。 */
@Composable
private fun scaledDensity(scale: Float): Density {
    val base = LocalDensity.current
    val clamped = clampUiScale(scale)
    // 1.0 时返回**同一个** Density 实例：绝大多数用户不会改这一项，
    // 为此多产生一个 Density 只会让下游白重组一次。
    return if (clamped == 1.0f) base else Density(base.density * clamped, base.fontScale)
}

/**
 * 把 Miuix **浅色**方案里那几个"反了方向"的灰，收进本工程自己的中性阶。
 *
 * ## 症状与根因
 *
 * 用户在两轮反馈里说的都是同一件事：**浅色下很多组件是一块发灰的脏色**（"组件颜色都会
 * 发灰，好丑"），而深色下没有。逐个调用点换色修不掉它，因为两种灰**同时存在**：
 *
 * * 本工程自己的中性阶（[ZhiColors]）：背板 `#F1F1F3` → 面板 `#FFFFFF` →
 *   卡片 `#FAFAFC` → 卡内小块 `#F7F7F8`。**越靠近内容越亮**，与深色主题
 *   （背板 `#28282A` → 卡片 `#323235`）同一个方向：卡片是"浮起来"的。
 * * Miuix 0.9.4 浅色方案的容器色（从 `miuix-ui` 字节码里取出的真实值）：
 *
 * | Miuix 字段 | 浅色实际值 | 谁在取 |
 * | --- | --- | --- |
 * | `background` / `surface` / `surfaceVariant` / `surfaceContainer` | `#FFFFFF` | 白 |
 * | `surfaceContainerHigh` / `surfaceContainerHighest` | `#E8E8E8`（**同一个值**） | 分组容器、代码块、次级 Card |
 * | `secondary` | `#E6E6E6` | 次级填充 |
 * | `secondaryVariant` / `secondaryContainer` | `#F0F0F0` | 次级容器 |
 * | `onBackgroundVariant` | `#8C93B0` | 弱化文字/图标 |
 *
 * `#E6E6E6`~`#E8E8E8` 比本工程的**背板** `#F1F1F3` 还暗一档，于是浅色下凡是走
 * Miuix 容器色的地方都成了**比背板更暗的灰块** —— 方向反了，看起来就是"脏"。
 *
 * ## 为什么只在入口重映射
 *
 * 逐个调用点换成 [ZhiColors] 也能修，但那有 200+ 个取值点，而且 Miuix **组件内部**
 * 取的色根本改不到。在这一个入口重映射则调用点一行都不用动。
 *
 * ## 深浅**两侧都要**重映射（这曾经是发灰的根因）
 *
 * 这段原先只改了浅色，注释还写着"深色方案这几个字段本来就是 `#242424` 一族"。
 * 那句话对容器色成立，但**对 `onBackgroundVariant` 不成立** ——
 *
 * | | Miuix 原值 | 性质 |
 * | --- | --- | --- |
 * | 浅色 `onBackgroundVariant` | `#8C93B0` | 蓝灰，白底上对比度约 2.9:1 |
 * | 深色 `onBackgroundVariant` | `#787E96` | **同样是蓝灰**，在 `#242424` 上偏灰偏冷 |
 *
 * 于是"浅色改过、深色没改"直接表现为：深色模式下大量图标与次级文字发灰、发闷，
 * 而浅色下同一处是正常的中性灰 —— 用户看到的是"深浅色模式下的图标要么发灰"。
 *
 * 更要紧的是**语义**：`onBackgroundVariant` 是"比正文弱一档的文字/图标"，
 * 它不该带色相。带蓝味会让整屏图标看起来蒙了一层灰，尤其配彩色图标块
 * （`SettingsIconPlate`）时更明显。
 *
 * 现在两侧都换成中性灰（深色 [ZhiColors.MutedDark] / 浅色 [ZhiColors.MutedLight]），
 * 且各自与所在背板的对比度都在 4.5:1 以上。
 */
private fun Colors.zhiLightNeutrals(): Colors = copy(
    surfaceContainerHigh = ZhiColors.CardLight,
    surfaceContainerHighest = ZhiColors.CardInnerLight,
    secondary = ZhiColors.SoftFillLight,
    secondaryVariant = ZhiColors.SoftFillLight,
    secondaryContainer = ZhiColors.SoftFillLight,
    onBackgroundVariant = ZhiColors.MutedLight,
    /*
     * 下面三项是**容器上的次级文字**。Miuix 浅色给的是 `#A2A2A2` / `#959595`，
     * 压在 `#FAFAFC` / `#FFFFFF` 上只有 **2.45:1 / 3.00:1** —— 远低于正文要求的
     * 4.5:1。它们被 `onSurfaceContainerHigh` / `onSurfaceContainerVariant`
     * 这两个槽位取用（对话框内容、下拉项、设置行的说明），于是浅色下这些字
     * 看起来"很淡、像被洗过"。
     *
     * 换成 [ZhiColors.MutedLight] 的同一族中性灰，实测 5.27:1 / 5.64:1。
     */
    onSurfaceContainerHigh = ZhiColors.MutedLight,
    onSurfaceContainerVariant = ZhiColors.MutedLight,
)

/**
 * 深色侧的同一批重映射。
 *
 * 容器色沿用 Miuix 原值（`#242424` 一族本来就是对的，它们与 [ZhiColors.PanelDark]
 * 一致），改的是**容器上的文字** —— 见 [zhiLightNeutrals] 上方那段说明。
 *
 * ## 三个槽位的对比度（这是「深色模式也有点问题」的主要成分）
 *
 * Miuix 深色给出的这三档在深色容器上都是**读不清**的：
 *
 * | 槽位 | Miuix 原值 | 所在底 | 对比度 | 换成 | 新对比度 |
 * | --- | --- | --- | --- | --- | --- |
 * | `onSurfaceVariantSummary` | `#80FFFFFF` | `#2D2D2D` | 4.1:1 | [ZhiColors.SummaryDark] | 4.7:1 |
 * | `onSurfaceContainerHigh` | `#666666` | `#2D2D2D` | **2.40:1** | [ZhiColors.SummaryDark] | 4.7:1 |
 * | `onSurfaceContainerVariant` | `#737373` | `#2D2D2D` | **2.90:1** | [ZhiColors.SecondaryDark] | 5.3:1 |
 * | `onSecondaryContainer` | `#7C7C7C` | `#434343` | **2.37:1** | [ZhiColors.SecondaryDark] | 4.6:1 |
 *
 * `onSecondaryContainer` 是**最严重**的一个：它被侧栏选中行、设置行、沙箱磁贴
 * 用来当"选中态正文色"，而 2.37:1 意味着选中的那一行比没选中的还难读 ——
 * 这也是「深色下有些字发灰、糊成一团」的来源。
 */
private fun Colors.zhiDarkNeutrals(): Colors = copy(
    onBackgroundVariant = ZhiColors.MutedDark,
    onSurfaceVariantSummary = ZhiColors.SummaryDark,
    onSurfaceContainerHigh = ZhiColors.SummaryDark,
    onSurfaceContainerVariant = ZhiColors.SecondaryDark,
    onSecondaryContainer = ZhiColors.SecondaryDark,
)
