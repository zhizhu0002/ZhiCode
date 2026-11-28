package com.zhizhu.zhicode.compose.ui.effects

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import com.zhizhu.zhicode.compose.theme.LocalThemeSettings
import com.zhizhu.zhicode.compose.theme.LocalZhiDark
import com.zhizhu.zhicode.compose.theme.ZhiGlass
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.material.isRenderEffectSupported
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun rememberPageBlurBackdrop(): LayerBackdrop? {
    val enabled = LocalThemeSettings.current.blurEnabled
    val supported = remember { isRenderEffectSupported() && isRuntimeShaderSupported() }
    if (!enabled || !supported) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/** 顶栏的模糊壳（移植自 example 的 `BlurredBar`）。 */
@Composable
fun PageBlurBar(
    backdrop: LayerBackdrop?,
    blurActive: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = if (blurActive && backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = ZhiGlass.FloatingBlur,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(
                            color = MiuixTheme.colorScheme.surface.copy(
                                if (LocalZhiDark.current) ZhiGlass.TopBarAlpha else 0.78f,
                            ),
                        ),
                    ),
                ),
            )
        } else {
            Modifier
        },
    ) {
        content()
    }
}

@Composable
fun Modifier.foregroundBlur(
    backdrop: LayerBackdrop?,
    radius: Float = 60f,
    brightness: Float = 0f,
    saturation: Float = 1f,
): Modifier {
    if (backdrop == null) return this
    return this.textureBlur(
        backdrop = backdrop,
        // 形状交给内容自己定：文字不裁剪，图标不裁剪。需要圆角的调用方自己套 shape。
        shape = RectangleShape,
        blurRadiusX = radius.coerceIn(0f, BlurDefaults.MaxBlurRadius),
        blurRadiusY = radius.coerceIn(0f, BlurDefaults.MaxBlurRadius),
        colors = BlurDefaults.blurColors(
            brightness = brightness.coerceIn(-1f, 1f),
            saturation = saturation,
        ),
        contentBlendMode = BlendMode.DstIn,
    )
}
