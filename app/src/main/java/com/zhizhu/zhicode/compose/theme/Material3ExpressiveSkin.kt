package com.zhizhu.zhicode.compose.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.data.ThemeSettings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Material 3 Expressive 与 Miuix 并存的唯一主题桥接层。
 *
 * 与 [ZhiMochiTheme] 处于同一层级（包在 [MiuixTheme] 之内）：
 * - Miuix 页面组继续读 [MiuixTheme.colorScheme] 与 [ZhiColors]；
 * - Material 3 Expressive 页面组读 [androidx.compose.material3.MaterialTheme]
 *   的 colorScheme / motionScheme / shapes / typography，使用同一套深浅判定与
 *   用户选定的预设强调色 / Monet 色板，同时保留 MD3 Expressive 的 tonal 容器层次、
 *   弹性空间动效（[MotionScheme.expressive]）与大圆角形状阶梯。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ZhiMaterial3ExpressiveTheme(
    theme: ThemeSettings,
    content: @Composable () -> Unit,
) {
    val isDark = ZhiThemeMode.rememberCurrentDark(theme.mode)
    val colors = rememberZhiM3eColors(theme = theme, isDark = isDark)
    val shapes = remember { zhiM3eShapes() }
    val typography = remember { zhiM3eTypography() }

    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        shapes = shapes,
        typography = typography,
        content = content,
    )
}

@Composable
private fun rememberZhiM3eColors(theme: ThemeSettings, isDark: Boolean): ColorScheme {
    val miuix = MiuixTheme.colorScheme
    val fixedLightPalette = !isDark && !theme.dynamicColor
    val lightBackdrop = if (fixedLightPalette) ZhiColors.backdrop() else miuix.background
    return remember(miuix, theme.dynamicColor, theme.presetId, theme.customSeedArgb, isDark) {
        if (isDark) {
            val base = darkColorScheme()
            val surface = miuix.background
            val fixedDarkPalette = !theme.dynamicColor
            val containerLowest = Color.Black
            val containerLow = if (fixedDarkPalette) Color(0xFF303030) else lerp(miuix.background, miuix.surfaceContainer, 0.55f)
            val container = if (fixedDarkPalette) Color(0xFF373737) else miuix.surfaceContainer
            val containerHigh = if (fixedDarkPalette) Color(0xFF404040) else miuix.surfaceContainerHigh
            val containerHighest = if (fixedDarkPalette) Color(0xFF494949) else miuix.surfaceContainerHighest
            val primaryContainer = if (fixedDarkPalette) {
                lerp(miuix.primaryContainer, miuix.primary, 0.2f)
            } else {
                miuix.primaryContainer
            }
            base.copy(
                primary = miuix.primary,
                onPrimary = miuix.onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = miuix.onPrimaryContainer,
                secondary = miuix.secondary,
                onSecondary = miuix.onSecondary,
                secondaryContainer = miuix.secondaryContainer,
                onSecondaryContainer = miuix.onSecondaryContainer,
                tertiaryContainer = miuix.tertiaryContainer,
                onTertiaryContainer = miuix.onTertiaryContainer,
                background = miuix.background,
                onBackground = miuix.onBackground,
                surface = surface,
                onSurface = miuix.onSurface,
                surfaceVariant = if (fixedDarkPalette) Color(0xFF3A3A3A) else miuix.surfaceVariant,
                onSurfaceVariant = miuix.onSurfaceVariantSummary,
                surfaceContainerLowest = containerLowest,
                surfaceContainerLow = containerLow,
                surfaceContainer = container,
                surfaceContainerHigh = containerHigh,
                surfaceContainerHighest = containerHighest,
                outline = miuix.outline,
                outlineVariant = miuix.outline.copy(alpha = 0.45f),
                error = miuix.error,
                onError = miuix.onError,
                errorContainer = miuix.errorContainer,
                onErrorContainer = miuix.onErrorContainer,
            )
        } else {
            val base = lightColorScheme()
            val containerLow = if (fixedLightPalette) Color.White else lerp(miuix.background, miuix.surfaceContainer, 0.5f)
            val primaryContainer = if (fixedLightPalette) {
                lerp(miuix.primaryContainer, miuix.primary, 0.2f)
            } else {
                miuix.primaryContainer
            }
            base.copy(
                primary = miuix.primary,
                onPrimary = miuix.onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = miuix.onPrimaryContainer,
                secondary = miuix.secondary,
                onSecondary = miuix.onSecondary,
                secondaryContainer = miuix.secondaryContainer,
                onSecondaryContainer = miuix.onSecondaryContainer,
                tertiaryContainer = miuix.tertiaryContainer,
                onTertiaryContainer = miuix.onTertiaryContainer,
                background = lightBackdrop,
                onBackground = miuix.onBackground,
                surface = lightBackdrop,
                onSurface = miuix.onSurface,
                surfaceVariant = miuix.surfaceVariant,
                onSurfaceVariant = miuix.onSurfaceVariantSummary,
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = containerLow,
                surfaceContainer = miuix.surfaceContainer,
                surfaceContainerHigh = miuix.surfaceContainerHigh,
                surfaceContainerHighest = miuix.surfaceContainerHighest,
                outline = miuix.outline,
                outlineVariant = miuix.outline.copy(alpha = 0.5f),
                error = miuix.error,
                onError = miuix.onError,
                errorContainer = miuix.errorContainer,
                onErrorContainer = miuix.onErrorContainer,
            )
        }
    }
}

/**
 * M3E 形状五档 —— **数值取自官方 `ShapeTokens`**。
 *
 * ## 原实现是错的
 *
 * 这里原来是 `8 / 12 / 18 / 24 / 28`，全部偏大。官方值（反编译
 * `material3:1.5.0-alpha16` 的 `androidx.compose.material3.tokens.ShapeTokens`
 * 的 `static {}` 初始化块）是：
 *
 * | 槽位 | 官方 | 原实现 | 偏差 |
 * | --- | --- | --- | --- |
 * | extraSmall | **4dp** | 8dp | +4 |
 * | small | **8dp** | 12dp | +4 |
 * | medium | **12dp** | 18dp | +6 |
 * | large | **16dp** | 24dp | +8 |
 * | extraLarge | **28dp** | 28dp | ✓ |
 *
 * 偏差随档位递增，后果是**小控件比规范圆、卡片比规范更圆**：
 * chip 与输入框（small）多 4dp、按钮（medium）多 6dp、卡片（large）多 8dp。
 * 而 extraLarge 恰好对上，所以"大的看起来还行、小的那一排明显偏圆"。
 *
 * ## 为什么不按半个档位取整
 *
 * 曾有人把 medium 写成 18 是"想在 12 和 24 之间取中间值"。但形状是**离散档位**，
 * 不是连续量 —— 中间值既不对应任何组件，也让「按钮 12 / 卡片 16」这组
 * 同心关系（16 − 16 内边距 = 0）失去参照。要用哪一档就取哪一档的官方值。
 */
private fun zhiM3eShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun zhiM3eTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}
