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
import ui.feice.mochi.theme.MochiMaterials
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.darkColorScheme
import ui.feice.mochi.theme.defaultShapes
import ui.feice.mochi.theme.defaultTextStyles
import ui.feice.mochi.theme.lightColorScheme

@Composable
fun ZhiMochiTheme(
    theme: ThemeSettings,
    materials: MochiMaterials = MochiMaterials(),
    content: @Composable () -> Unit,
) {
    val isDark = ZhiThemeMode.rememberCurrentDark(theme.mode)
    MochiTheme(
        colors = rememberZhiMochiColors(theme, isDark),
        textStyles = zhiMochiTextStyles(),
        shapes = zhiMochiShapes(),
        materials = materials,
        /* 这里**不**传那个 Boolean（MochiTheme 里真实的名字是 `systemBarAppearance`）。 */
        content = content,
    )
}

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

/** 组件比例令牌：Mochi 组件与 Miuix 组件在**同一屏混排**时必须一致的那几个数。 */
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
