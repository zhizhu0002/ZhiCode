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
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 整页顶栏的模糊背板（移植自 Miuix 官方 example 的 `rememberBlurBackdrop`）。
 *
 * ## 与 `Glass` 的关系
 *
 * `ui/Glass.kt` 里的 `rememberGlass()` 是**悬浮层**用的（输入器、底部栏），
 * 它自己持有一份 backdrop 并成对提供 `capture` / `blur`。这里是**整页**用：
 * 背景由 `BgEffectBackground` 绘制，顶栏只需要在它之上取样，所以返回一个裸
 * `LayerBackdrop?`，调用方自己决定挂在哪一层。
 *
 * ## 两道门控
 *
 * - `isRenderEffectSupported()`：`layerBackdrop` 捕获背景内容走的是 RenderEffect；
 * - `isRuntimeShaderSupported()`：`textureBlur` 本身是 RuntimeShader（Android 需 API 33+）。
 *
 * 缺一不可：只判后者、在不支持 RenderEffect 的平台上仍会去建 layer。
 *
 * 关掉模糊（「主题设置 → 显示 → 模糊」）时返回 null，调用方据此走不透明底色分支 ——
 * 这条路径同时也避免了在 API < 33 上执行到 RuntimeShader 代码而崩。
 */
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

/**
 * 顶栏的模糊壳（移植自 example 的 `BlurredBar`）。
 *
 * [blurActive] 为假或没有 backdrop 时是恒等包装 —— 滚动到顶部、背景还没被内容盖住时
 * 不做无意义的采样。
 */
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

/**
 * 让内容变成"模糊渐变的遮罩"（`contentBlendMode = DstIn`）。
 *
 * 效果的实质：把背板采样、模糊后，再用**内容自身的 alpha** 当遮罩画出来。
 * 所以文字的颜色不重要，字形才是形状。
 *
 * ## [brightness] / [saturation] 为什么要能调
 *
 * 采样源就是字的颜色 —— 背板偏暗时，字形会跟着变暗到几乎看不见
 * （实测：深蓝渐变上的深蓝字）。KSU Style 的做法是在混合时把采到的像素提亮，
 * 于是字形比底色亮、能读。经验值：整页深色渐变配 `brightness ≈ 0.35f`。
 *
 * [backdrop] 为 null 时是**恒等**修饰符 —— 关掉模糊、或 API < 33 时调用方
 * 不需要写分支，文字照常以正常颜色显示。
 */
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
