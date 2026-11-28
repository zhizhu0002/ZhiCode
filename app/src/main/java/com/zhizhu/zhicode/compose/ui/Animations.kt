package com.zhizhu.zhicode.compose.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import top.yukonga.miuix.kmp.anim.DecelerateEasing
import top.yukonga.miuix.kmp.anim.SinOutEasing
import top.yukonga.miuix.kmp.anim.folmeSpring

object ZhiMotion {

    /** 淡入时长：`PopupDimEnter`。 */
    const val FADE_IN_MILLIS = 300

    /** 淡出时长：`PopupDimExit`。 */
    const val FADE_OUT_MILLIS = 150

    /** 位移退出时长：`DialogDimExit` 的手机分支。 */
    const val EXIT_MILLIS = 200

    /** 出现（淡入）：300ms + `SinOutEasing`。 */
    val fadeInSpec: FiniteAnimationSpec<Float> =
        tween(FADE_IN_MILLIS, easing = SinOutEasing)

    /** 消失（淡出）：150ms + `SinOutEasing`。 */
    val fadeOutSpec: FiniteAnimationSpec<Float> =
        tween(FADE_OUT_MILLIS, easing = SinOutEasing)

    /** 进入位移：`spring(0.88f, 450f)` —— Miuix 官方**不带回弹**的推入手感。 */
    val enterSpec: SpringSpec<IntOffset> = folmeSpring(damping = 0.9f, response = 0.3f)

    /** 退出位移：200ms + `DecelerateEasing(1.5f)`。 */
    val exitSpec: FiniteAnimationSpec<IntOffset> =
        tween(EXIT_MILLIS, easing = DecelerateEasing(1.5f))

    /** 尺寸变化（展开/收起、`animateContentSize`）：与退出位移同一条曲线。 */
    val sizeSpec: FiniteAnimationSpec<IntSize> =
        tween(EXIT_MILLIS, easing = DecelerateEasing(1.5f))

    /** 颜色/透明度淡变：与淡出同一条（150ms + `SinOutEasing`）。 */
    val colorSpec: FiniteAnimationSpec<Color> =
        tween(FADE_OUT_MILLIS, easing = SinOutEasing)

    /** 连续量（进度、比例）的收敛：`folmeSpring(1.0f, 0.3f)` —— TopAppBar 折叠同款 */
    val progressSpec: AnimationSpec<Float> = folmeSpring(damping = 1.0f, response = 0.3f)

    val pressSpec: SpringSpec<Float> = folmeSpring(damping = 0.95f, response = 0.35f)

    /** 缩放进入：`spring(0.9f, 438.6f)` —— Miuix 大屏弹窗进入（`scaleIn`）的原参数。 */
    val scaleEnterSpec: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 0.9f, stiffness = 438.6f, visibilityThreshold = 0.0001f)

    /** 缩放退出：200ms + `DecelerateEasing(1.5f)`（Miuix 大屏弹窗 `scaleOut`）。 */
    val scaleExitSpec: FiniteAnimationSpec<Float> =
        tween(EXIT_MILLIS, easing = DecelerateEasing(1.5f))
}
