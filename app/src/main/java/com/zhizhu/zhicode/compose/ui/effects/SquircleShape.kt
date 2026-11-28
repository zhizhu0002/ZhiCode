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

/** 建一个 [SquircleCornerShape]，并跟随 [LocalSquircleEnabled] 的全局开关。 */
@Composable
fun ZhiSquircle(cornerRadius: Dp): Shape {
    val enabled = LocalSquircleEnabled.current
    return remember(cornerRadius, enabled) {
        SquircleCornerShape(cornerRadius, enabled, SquircleDefaults.Extension)
    }
}
