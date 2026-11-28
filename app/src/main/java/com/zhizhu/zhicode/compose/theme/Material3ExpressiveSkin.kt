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

/** Material 3 Expressive 与 Miuix 并存的唯一主题桥接层。 */
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
