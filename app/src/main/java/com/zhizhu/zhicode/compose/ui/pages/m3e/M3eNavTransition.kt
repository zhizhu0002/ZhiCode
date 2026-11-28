package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.compose.ui.graphics.GraphicsLayerScope
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.navDirectionalTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition

/**
 * M3E 页面转场。弹簧参数取自 `ExpressiveMotionTokens`，不用 Miuix 的默认曲线。
 *
 * Miuix 只暴露 `MiuixDefault` / `Modal` / `None` 三种现成转场，都不是 M3E 的手感，
 * 所以这里用 `navGraphicsTransition` 自己拼一个。
 */
internal object M3eNavTransition {

    /** 进入时从下方偏移一个身高的比例。 */
    private const val EnterOffset = 0.04f

    /** 被覆盖页面的最终缩放。 */
    private const val CoveredScale = 0.94f

    /** 被覆盖页面的最终不透明度。 */
    private const val CoveredAlpha = 0.88f

    /** 被覆盖页面的最终压暗强度。 */
    private const val CoveredScrim = 0.12f

    private val spatial = NavMotion(
        NavSettleSpec.Spring(dampingRatio = 0.80f, stiffness = 380f, clampOvershoot = false),
        NavSettleSpec.Spring(dampingRatio = 0.60f, stiffness = 800f, clampOvershoot = false),
        NavSettleSpec.Spring(dampingRatio = 0.80f, stiffness = 380f, clampOvershoot = false),
    )

    private fun lerpF(from: Float, to: Float, fraction: Float): Float =
        from + (to - from) * fraction

    private fun scrim(scope: NavTransitionScope): Float = when (scope.role) {
        NavRole.Covered -> scope.relativeDepth.coerceIn(0f, 1f) * CoveredScrim
        else -> 0f
    }

    private fun GraphicsLayerScope.applyM3e(scope: NavTransitionScope) {
        val height = size.height
        when (scope.role) {
            NavRole.Incoming -> {
                // relativeDepth 从 -1 升到 0：progress 从 0 到 1。
                val progress = (1f + scope.relativeDepth).coerceIn(0f, 1f)
                val scale = lerpF(0.92f, 1f, progress)
                scaleX = scale
                scaleY = scale
                alpha = progress
                translationY = height * EnterOffset * (1f - progress)
            }
            NavRole.Outgoing -> {
                // relativeDepth 从 0 降到 -1：progress 从 0 到 1。
                val progress = (-scope.relativeDepth).coerceIn(0f, 1f)
                val scale = lerpF(1f, 0.96f, progress)
                scaleX = scale
                scaleY = scale
                alpha = 1f - progress
                translationY = height * EnterOffset * progress
            }
            NavRole.Covered -> {
                val progress = scope.relativeDepth.coerceIn(0f, 1f)
                val scale = lerpF(1f, CoveredScale, progress)
                scaleX = scale
                scaleY = scale
                alpha = lerpF(1f, CoveredAlpha, progress)
                translationY = 0f
            }
            NavRole.Top -> {
                scaleX = 1f
                scaleY = 1f
                alpha = 1f
                translationY = 0f
            }
        }
    }

    private fun branch(): NavTransition = navGraphicsTransition(
        1f,
        NavSwipeDirection.None,
        spatial,
        { scope -> scrim(scope) },
        { scope -> applyM3e(scope) },
    )

    /** 前进、返回、预测性返回共用同一套形变，只有方向由 role 决定。 */
    val transition: NavTransition = navDirectionalTransition(branch(), branch(), branch())
}
