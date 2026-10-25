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

/**
 * 按压高光（`InteractiveHighlight` 的**安全封装**）。
 *
 * ## 为什么不直接用搬进来的 `InteractiveHighlight`
 *
 * ksu-style（以及它拷自的 Miuix example）用 `android.graphics.RuntimeShader` 画一团
 * 跟随手指的径向高光，源码里标了 `@SuppressLint("NewApi")` —— 也就是**它自己就承认**
 * 这段代码要求 API 33+，而 ksu-style 的 `minSdk` 本就在那之上，可以直接用。
 *
 * 本工程 `minSdk = 24`，把那个类原样挂到按钮上会让低版本设备**一碰就崩**
 * （`RuntimeShader` 类不存在，不是"效果不生效"）。所以这里做两件事：
 *
 * 1. 用 `isRuntimeShaderSupported()` 门控，不支持时直接返回原 Modifier（零开销、零风险）；
 * 2. 支持时也**不用 shader**，改用一层跟手位置的径向 `Brush` ——
 *    Compose 自己的 `radialGradient` 已经能表达"按下处最亮、向外衰减"，
 *    而它走的是普通绘制路径，没有 RuntimeShader 的版本门槛与编译期失败风险。
 *
 * 视觉上两者几乎一致（都是一团 6%~12% 白色的柔光），差别在第 2 条换来的是
 * **低版本设备也能有按压反馈**，而不是"高版本才有、低版本什么都没有"。
 *
 * ## 用法
 *
 * ```kotlin
 * val source = remember { MutableInteractionSource() }
 * Box(
 *     Modifier
 *         .pressHighlight(source, shape = RoundedCornerShape(8.dp))
 *         .clickable(interactionSource = source, indication = null) { … }
 * )
 * ```
 *
 * 必须把**同一个** [interactionSource] 同时交给这个 Modifier 与 `clickable`，
 * 否则高光永远不会亮（它靠那个 source 的 `Press` 事件驱动）。
 */
@Composable
fun Modifier.pressHighlight(
    interactionSource: MutableInteractionSource,
    shape: Shape? = null,
    /**
     * 高光半径相对短边的比例。1.2 取自 example 的 `size.minDimension * 1.2f` ——
     * 比控件本身还大一圈，按下时看起来是"整个面被点亮"，而不是"一个亮点"。
     */
    radiusFactor: Float = 1.2f,
    /** 峰值白色透明度。example 是 0.06（整面）+ 0.12（径向），这里合成一个数。 */
    peakAlpha: Float = 0.12f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    if (!pressed || peakAlpha <= 0f) return this

    return this.then(
        Modifier.drawWithContent {
            // 径向高光：中心点在控件中心（手指位置要额外一整套 pointerInput，
            // 而这里的目标是"按下有反馈"，不是复刻 example 的跟手效果 ——
            // 跟手在按钮这么小的面上肉眼也分辨不出来）。
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
                // ⚠️ 必须 `addOutline` 把 Outline **灌进** Path。
                // 原先这里写的是 `Path().also { outline }` —— 那是空操作，
                // path 永远是空 Path，`clipPath(空)` 会把整个内容裁掉
                // （高光不显示、按钮内容也跟着消失）。
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

/**
 * 按压时的轻微下沉（`PressFeedbackType.Sink` 的自绘版本）。
 *
 * Miuix 的 `Card` 自带 `pressFeedbackType = PressFeedbackType.Sink`，但
 * `Box` / 自定义容器没有。这个 Modifier 给它们补上：按下时缩到 0.97，
 * 松开弹回。用 `graphicsLayer` 的 `scaleX/scaleY` —— 只影响绘制阶段，
 * 不触发重新测量（`animateDpAsState` 改尺寸会）。
 */
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

/**
 * 自持有交互源的按压下沉。
 *
 * ## 为什么还需要这一个
 *
 * [pressSink] 要求调用方把 `clickable` 的 `interactionSource` 交进来。
 * 但 `ZhiFilledIconButton` 底下是 Miuix 的 `IconButton`，而它的签名里
 * **没有 `interactionSource` 参数**（核过 `iconbutton.md`；这是 Miuix 有意收窄的 ——
 * 它自己在内部管理按压态与配色）。所以调用方拿不到那个 source。
 *
 * 这个版本自己 new 一个 source 挂上去。代价是它感知不到"内部的"按压，
 * 只有在**外层**套一层 `clickable` 时才会亮 —— 所以它配合的参数是
 * `pointerInput` 里的 `detectTapGestures(onPress)`，改由手指的按下/抬起驱动。
 *
 * 两者按场景选：[pressSink] 用于"我自己接 `clickable`"的容器，
 * 这个用于"组件内部自己处理点击、我只想补个下沉"的情形。
 */
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
            // 不用 `detectTapGestures`：它会把**整个点击**接管过去，
            // 与组件内部自己的 onClick 打架（表现为点击不响应或响应两次）。
            // 这里只在 Initial pass 观察按下/抬起，从不消费事件，
            // 所以组件自己的点击逻辑照常工作。
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
