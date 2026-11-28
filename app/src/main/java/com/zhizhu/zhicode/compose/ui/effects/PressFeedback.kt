package com.zhizhu.zhicode.compose.ui.effects

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.platform.LocalView
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported

@Composable
fun Modifier.pressHighlight(
    interactionSource: MutableInteractionSource,
    shape: Shape? = null,
    /** 高光半径相对短边的比例。 */
    radiusFactor: Float = 1.2f,
    /** 峰值白色透明度。example 是 0.06（整面）+ 0.12（径向），这里合成一个数。 */
    peakAlpha: Float = 0.12f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    if (!pressed || peakAlpha <= 0f) return this

    return this.then(
        Modifier.drawWithContent {
            // 径向高光：中心点在控件中心（手指位置要额外一整套 pointerInput
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * radiusFactor
            val brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = peakAlpha),
                    Color.Transparent,
                ),
                center = center,
                radius = radius,
            )
            val draw = {
                drawRect(brush = brush, blendMode = BlendMode.Plus)
            }
            if (shape != null) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent))
                }
                clipPath(path) { draw() }
            } else {
                draw()
            }
            drawContent()
        }
    )
}

/** 按压时的轻微下沉（`PressFeedbackType.Sink` 的自绘版本）。 */
@Composable
fun Modifier.pressSink(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = if (pressed) pressedScale else 1f
    return this.then(
        Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
}

@Composable
fun Modifier.selfPressSink(pressedScale: Float = 0.97f): Modifier {
    var pressed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    return this
        .graphicsLayer {
            val scale = if (pressed) pressedScale else 1f
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            // 不用 `detectTapGestures`：它会把**整个点击**接管过去
            awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent(PointerEventPass.Initial)
                        .changes.firstOrNull { it.pressed } ?: continue
                    if (!pressed) pressed = true
                    // 等这一次手势结束（抬起或被取消）。
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                    }
                    pressed = false
                }
            }
        }
}
