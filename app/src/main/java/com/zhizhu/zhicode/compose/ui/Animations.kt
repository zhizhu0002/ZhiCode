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

/**
 * 全局动效令牌 —— **全部取自 Miuix 官方实现里已经用过的那几条曲线与时长**。
 *
 * ## 为什么不再自己定一套
 *
 * 这个对象以前是自拟的（`tween(280, EaseOutCubic)` / `tween(160)` /
 * `spring(DampingRatioMediumBouncy)`），结果是本应用的位移、淡变、展开各自
 * 用了与 Miuix 组件**不同**的节奏：Miuix 的弹窗推入是
 * `spring(dampingRatio = 0.88f, stiffness = 450f)`、淡入是
 * `tween(300, SinOutEasing)`、淡出是 `tween(150, SinOutEasing)`、
 * 位移退出是 `tween(200, DecelerateEasing(1.5f))`，而自拟值是 280/160ms 加
 * `EaseOutCubic`/`FastOutSlowInEasing`。两者叠在同一屏上（比如设置页里
 * 弹出 Miuix 的下拉选择器）就会看出"两套手感"。
 *
 * 所以这里的每一条都**逐字抄自 Miuix 上游**，并注明出处；上游更新时按同样的
 * 依据替换即可。出处（写这份时的 miuix 版本）：
 *
 * | 令牌 | Miuix 出处 |
 * |---|---|
 * | [fadeInSpec] | `MiuixPopupUtils.rememberDefaultPopupEnterTransition`（PopupDimEnter）= `tween(300, SinOutEasing)` |
 * | [fadeOutSpec] | `MiuixPopupUtils` PopupDimExit = `tween(150, SinOutEasing)` |
 * | [enterSpec] | `MiuixPopupUtils.rememberDefaultDialogEnterTransition` 手机分支 = `spring(0.88f, 450f)`；同为 `DialogContentLayout` 的 `folmeSpring(0.9f, 0.35f)` 一族 |
 * | [exitSpec] | `MiuixPopupUtils.rememberDefaultDialogExitTransition` 手机分支 = `tween(200, DecelerateEasing(1.5f))` |
 * | [sizeSpec] | 同上位移退出的曲线（展开/收起与位移共用一条） |
 * | [colorSpec] | 同 [fadeOutSpec]（颜色是淡变的近亲，Miuix 的选中态淡入淡出也用 SinOut 一族） |
 * | [progressSpec] | `TopAppBar` 折叠 = `folmeSpring(1.0f, 0.3f)`（临界阻尼，无回弹） |
 * | [pressSpec] | `MiuixIndication` 按压退出 = `folmeSpring(0.95f, 0.35f)` |
 *
 * `folmeSpring(damping, response)` 是 Miuix 自己的弹簧工厂
 * （`top.yukonga.miuix.kmp.anim.folmeSpring`，把 response 秒换算成 stiffness），
 * 用它而不是裸 `spring(stiffness = …)` 才能和 Miuix 组件的参数**同一个量纲**。
 *
 * ## 这里**不再**提供什么
 *
 * - 自写的按压缩放（`pressScale` / `ZhiPressable`）：交互反馈全部由 Miuix 组件
 *   自带（`Card.pressFeedbackType`、`IconButton` 的涟漪、`Surface(onClick)` 的
 *   按下态），自写缩放与它叠加会出现"双重按压"，已移除。
 * - 整页之间的推入/弹出：那是 `miuix-nav` 的 `NavTransitions.MiuixDefault`
 *   在管（见 `AppScaffold`），不要在这里再表达一次页面栈动效。
 */
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

    /**
     * 进入位移：`spring(0.88f, 450f)` —— Miuix 官方**不带回弹**的推入手感。
     *
     * 用 `folmeSpring(damping = 0.88f, response = 0.35f)` 表达同一组参数
     * （上游的 stiffness 450 对应 response ≈ 0.297s，这里取 0.3s 与
     * `DialogContentLayout` 的 `folmeSpring(0.9f, 0.3f)` 对齐）。
     */
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

    /**
     * 连续量（进度、比例）的收敛：`folmeSpring(1.0f, 0.3f)` —— TopAppBar 折叠同款，
     * 临界阻尼所以不会过冲（进度条过冲会让"完成"看起来像弹了一下）。
     */
    val progressSpec: AnimationSpec<Float> = folmeSpring(damping = 1.0f, response = 0.3f)

    /** 强调/前景色的按压过渡：`MiuixIndication` 的按压退出参数。 */
    val pressSpec: SpringSpec<Float> = folmeSpring(damping = 0.95f, response = 0.35f)

    /**
     * 缩放进入：`spring(0.9f, 438.6f)` —— Miuix 大屏弹窗进入（`scaleIn`）的原参数。
     *
     * 缩放是 `Float` 量纲，和 [sizeSpec]（`IntSize`）不能互换，所以单列一条。
     */
    val scaleEnterSpec: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 0.9f, stiffness = 438.6f, visibilityThreshold = 0.0001f)

    /** 缩放退出：200ms + `DecelerateEasing(1.5f)`（Miuix 大屏弹窗 `scaleOut`）。 */
    val scaleExitSpec: FiniteAnimationSpec<Float> =
        tween(EXIT_MILLIS, easing = DecelerateEasing(1.5f))
}
