package com.zhizhu.zhicode.compose.ui

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 全局动效常量。
 *
 * 统一时长/缓动，避免各处动画节奏不一致。
 *
 * 注意：这里**不再提供**自写的按压缩放修饰符（原 `iqPressScale` / `ZhiPressable`）。
 * 交互反馈现在全部由 Miuix 组件自带（`Card` 的 `pressFeedbackType`、
 * `IconButton` 的涟漪、`Surface(onClick)` 的按下态），
 * 自写缩放与 Miuix 反馈叠加会出现"双重按压"，所以已移除。
 */
object ZhiMotion {
    /** 面板切换、抽屉开合这类大范围位移。 */
    const val MEDIUM = 280

    /** 颜色、透明度等轻量变化。 */
    const val FAST = 160

    /** 展开/折叠等尺寸变化。 */
    const val EXPAND = 240

    val easeOut = tween<Float>(MEDIUM, easing = EaseOutCubic)
    val fastEase = tween<Float>(FAST, easing = FastOutSlowInEasing)
    val linear = tween<Float>(1_200, easing = LinearEasing)

    /** 轻微回弹，用于图标缩放等强调。 */
    fun <T> softSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
