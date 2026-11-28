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
        MiuixTheme(
            colors = rememberZhiColors(theme, isDark),
            textStyles = zhiTextStyles(),
        ) {
            // 麻薯（Mochi）主题再包一层，位置在这里是**有讲究的**：见 MochiSkin.kt
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
        // 固定中性色 + 种子色当强调色。
        return remember(isDark, backdrop, theme.seedArgb) {
            val base = if (isDark) darkColorScheme().zhiDarkNeutrals() else lightColorScheme().zhiLightNeutrals()
            val seed = Color(theme.seedArgb)
            // 亮色种子（暖阳橙 / 樱花粉）配白字会糊，按亮度换深色字。
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
