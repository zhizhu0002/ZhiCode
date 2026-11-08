// Mirrored from compose-miuix-ui example.

package com.zhizhu.zhicode.compose.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.zhizhu.zhicode.compose.theme.ZhiColors
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.floor

@Composable
fun BgEffectBackground(
    dynamicBackground: Boolean,
    modifier: Modifier = Modifier,
    bgModifier: Modifier = Modifier,
    isFullSize: Boolean = false,
    effectBackground: Boolean = true,
    /**
     * 是否让**分布点**逐帧缓慢漂移。
     *
     * 与 [dynamicBackground] 分开：后者只驱动色带的慢速流转（`colorStage` 那个
     * `Animatable`，几秒一次），而这一项打开时[bgEffectDraw] 里会有一个
     * `withFrameNanos` 循环**每帧**重绘。
     *
     * 演示页无所谓；关于页的 Hero 是常驻的，一直 60fps 画渐变等于白耗电，
     * 那边传 `dynamicBackground = true, animateMotion = false`。
     * 默认值等于旧行为，其他调用方不受影响。
     */
    animateMotion: Boolean = dynamicBackground,
    alpha: () -> Float = { 1f },
    content: @Composable BoxScope.() -> Unit,
) {
    if (!isRuntimeShaderSupported()) {
        Box(modifier = modifier, content = content)
        return
    }
    Box(
        modifier = modifier,
    ) {
        val surface = MiuixTheme.colorScheme.surface
        val deviceType = if (shouldShowSplitPane()) DeviceType.PAD else DeviceType.PHONE
        val isDarkTheme = ZhiColors.isDark()
        val painter = remember { BgEffectPainter() }

        val preset = remember(deviceType, isDarkTheme) {
            BgEffectConfig.get(deviceType, isDarkTheme)
        }

        val colorStage = remember { Animatable(0f) }

        LaunchedEffect(dynamicBackground, preset) {
            if (!dynamicBackground) return@LaunchedEffect
            val animatesColors = preset.colors1 !== preset.colors2 || preset.colors2 !== preset.colors3
            if (!animatesColors) return@LaunchedEffect

            var targetStage = floor(colorStage.value) + 1f
            while (isActive) {
                delay((preset.colorInterpPeriod * 500).toLong())
                colorStage.animateTo(
                    targetValue = targetStage,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
                )
                targetStage += 1f
            }
        }

        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .then(bgModifier)
                .bgEffectDraw(
                    painter = painter,
                    preset = preset,
                    deviceType = deviceType,
                    isDarkTheme = isDarkTheme,
                    surface = surface,
                    effectBackground = effectBackground,
                    isFullSize = isFullSize,
                    playing = animateMotion,
                    colorStage = { colorStage.value },
                    alpha = alpha,
                ),
        )
        content()
    }
}

@Composable
private fun shouldShowSplitPane(): Boolean {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    return with(density) {
        val widthDp = windowInfo.containerSize.width.toDp()
        val heightDp = windowInfo.containerSize.height.toDp()
        val ratio = heightDp / widthDp
        widthDp >= 840.dp || (widthDp >= 600.dp && ratio < 1.2f)
    }
}
