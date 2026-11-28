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
import ui.feice.mochi.theme.MochiMaterials
import ui.feice.mochi.theme.mochiGlassMaterials
import com.zhizhu.zhicode.compose.ui.pages.LocalZhiPageSet
import com.zhizhu.zhicode.compose.ui.pages.ZhiPageSets

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
            ZhiMochiTheme(
                theme = theme,
                /* 材质：只有 Mochi 组 + 用户显式打开时才给玻璃。 */
                materials = remember(theme.mochiGlass) {
                    if (theme.mochiGlass) mochiGlassMaterials() else MochiMaterials()
                },
            ) {
                CompositionLocalProvider(LocalZhiPageSet provides ZhiPageSets.byId(theme.pageSetId)) {
                    ZhiMaterial3ExpressiveTheme(theme = theme) {
                        ProvideMochiOverscroll {
                            content()
                        }
                    }
                }
            }
        }
    }
}

/** 当前主题设置。 */
val LocalThemeSettings = androidx.compose.runtime.compositionLocalOf { ThemeSettings() }

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

private fun Colors.zhiLightNeutrals(): Colors = copy(
    surfaceContainerHigh = ZhiColors.CardLight,
    surfaceContainerHighest = ZhiColors.CardInnerLight,
    secondary = ZhiColors.SoftFillLight,
    secondaryVariant = ZhiColors.SoftFillLight,
    secondaryContainer = ZhiColors.SoftFillLight,
    onBackgroundVariant = ZhiColors.MutedLight,
    /* 下面三项是**容器上的次级文字**。 */
    onSurfaceContainerHigh = ZhiColors.MutedLight,
    onSurfaceContainerVariant = ZhiColors.MutedLight,
)

private fun Colors.zhiDarkNeutrals(): Colors = copy(
    onBackgroundVariant = ZhiColors.MutedDark,
    onSurfaceVariantSummary = ZhiColors.SummaryDark,
    onSurfaceContainerHigh = ZhiColors.SummaryDark,
    onSurfaceContainerVariant = ZhiColors.SecondaryDark,
    onSecondaryContainer = ZhiColors.SecondaryDark,
)
