package com.zhizhu.zhicode.compose.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import top.yukonga.miuix.kmp.squircle.SquircleDefaults
import top.yukonga.miuix.kmp.squircle.addSquircleRect

/**
 * 一个**通用的** squircle [Shape]（连续曲率圆角），可以直接传给
 * `Surface(shape = …)`、`Modifier.border(…, shape = …)`、`Modifier.clip(…)`、
 * `Modifier.shadow(…, shape = …)` 以及 `miuix-blur` 的 `textureBlur(shape = …)`。
 *
 * ## 为什么要自己写一个
 *
 * `miuix-squircle` **没有** `SquircleShape` 这个类。它提供的是一组 `Modifier`：
 * `squircleBackground` / `squircleSurface` / `squircleClip` / `squircleBorder`。
 * 那些改不了本工程里已有的一批调用点 —— 它们拿的是**同一个 `Shape` 对象**，
 * 一次传给底色、描边、阴影和模糊采样：
 *
 * ```kotlin
 * val shape = RoundedCornerShape(28.dp)
 * modifier.blur(shape).border(1.dp, color, shape)   // 必须同一个形状
 * Surface(shape = shape, …)
 * ```
 *
 * 换成 `squircleSurface` 就只能覆盖底色，描边与模糊仍走圆弧圆角，两套轮廓叠在
 * 一起会在角上差出可见的重影。所以这里用库暴露的 **Path API**
 * （`Path.addSquircleRect`）实现成标准 `Shape`，一个对象喂给全部调用点。
 *
 * ## 代价与回退
 *
 * `Outline.Generic` 会让合成器按 path 裁剪，比 `Outline.Rounded` 的解析式圆角贵一点；
 * 但只在**尺寸真的变化**时重算（`createOutline` 由布局驱动，不是每帧），
 * 静态卡片上测不出差别。
 *
 * 用户/系统可以整体关掉 squircle（`LocalSquircleEnabled`）。关闭时这个 Shape
 * 退回**等价圆弧圆角**（同一半径的 `Outline.Rounded`），与库里 shader modifier
 * 的回退行为一致 —— 于是"关掉 squircle"不会只关掉一部分。
 */
class SquircleCornerShape internal constructor(
    private val cornerRadius: Dp,
    private val enabled: Boolean,
    private val extension: Float,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        // 半径不能超过短边一半，否则 path 会自交（与 RoundedCornerShape 的夹取一致）。
        val radiusPx = with(density) { cornerRadius.toPx() }
            .coerceIn(0f, minOf(size.width, size.height) / 2f)
        if (!enabled || radiusPx <= 0f || size.width <= 0f || size.height <= 0f) {
            return androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
                .createOutline(size, layoutDirection, density)
        }
        val path = Path().apply {
            addSquircleRect(
                size.width,
                size.height,
                radiusPx,
                extension,
                true,
            )
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SquircleCornerShape) return false
        return cornerRadius == other.cornerRadius &&
            enabled == other.enabled &&
            extension == other.extension
    }

    override fun hashCode(): Int {
        var result = cornerRadius.hashCode()
        result = 31 * result + enabled.hashCode()
        result = 31 * result + extension.hashCode()
        return result
    }
}

/**
 * 建一个 [SquircleCornerShape]，并跟随 [LocalSquircleEnabled] 的全局开关。
 *
 * `extension` 用库的默认值（`SquircleDefaults.Extension`，1.1）：1.0 等于标准圆弧，
 * 1.1 才是那一档"连续曲率"的观感。**不要**在这里写死数字 —— 库改默认值时
 * 本工程应该跟着变。
 */
@Composable
fun ZhiSquircle(cornerRadius: Dp): Shape {
    val enabled = LocalSquircleEnabled.current
    return remember(cornerRadius, enabled) {
        SquircleCornerShape(cornerRadius, enabled, SquircleDefaults.Extension)
    }
}
