package com.zhizhu.zhicode.compose.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ZhiSideDrawer(
    open: Boolean,
    onClose: () -> Unit,
    width: Dp,
    /** 抽屉内部的浮层宿主。 */
    overlayHost: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val scrimInteraction = remember { MutableInteractionSource() }
    /** 面板自己的空动作交互源（只为吞掉空白处的点击，不带水波纹）。 */
    val panelInteraction = remember { MutableInteractionSource() }

    BackHandler(enabled = open, onBack = onClose)

    val containerModifier = if (open) Modifier.fillMaxSize() else Modifier

    Box(modifier = containerModifier) {
        // 遮罩：走 Miuix Surface(onClick)，不再手写 background + clickable。
        // 颜色用主题的 windowDimming（Miuix 自己给弹窗/抽屉遮罩用的语义色），
        // 不再硬编码 Color.Black —— 浅色模式下黑遮罩会把背景压得过重。
        // 传 indication = null 保持"只有压暗、不出现涟漪"的原有效果。
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(ZhiMotion.fadeInSpec),
            exit = fadeOut(ZhiMotion.fadeOutSpec),
        ) {
            Surface(
                onClick = onClose,
                modifier = Modifier.fillMaxSize(),
                color = MiuixTheme.colorScheme.windowDimming,
                interactionSource = scrimInteraction,
                indication = null,
            ) {
                Box(modifier = Modifier.fillMaxSize())
            }
        }

        // 面板：从左侧滑入
        AnimatedVisibility(
            visible = open,
            enter = slideInHorizontally(
                animationSpec = ZhiMotion.enterSpec,
            ) { -it } + fadeIn(ZhiMotion.fadeInSpec),
            exit = slideOutHorizontally(
                animationSpec = ZhiMotion.exitSpec,
            ) { -it } + fadeOut(ZhiMotion.fadeOutSpec),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Surface(
                modifier = Modifier
                    .width(width.coerceAtMost(320.dp))
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = panelInteraction,
                        indication = null,
                        onClick = {},
                    ),
                // Miuix semantic overlay surface; no independent glass sampling/tint.
                color = scheme.background,
                // 这是本工程**唯一**保留圆弧圆角的悬浮面板：抽屉只有右侧两个角是圆的
                // （左侧贴屏幕边），而 `miuix-squircle` 的 Path API 只提供四角同半径的
                // 轮廓。为两个角单独实现一条非对称 squircle path 的收益远小于代价
                // （角部曲率差异在 20dp 半径、且一半被屏幕边缘裁掉时几乎看不出），
                // 所以这里按规范允许的"明确理由例外"保留 RoundedCornerShape。
                shape = RoundedCornerShape(topEnd = ZhiRadius.floating, bottomEnd = ZhiRadius.floating),
            ) {
                // ⚠️ 内边距只能加在**内容**上，不能加在面板的 `Surface` 上。
                //
                // 这条规则**已经改了**：现在系统栏的让位加在**面板自己**身上
                // （见上面 `Surface` 的 `windowInsetsPadding`）。
                //
                // 原来的做法是"底色铺满、内容让位" —— 代价是面板底色一路顶到
                // 状态栏，状态栏图标直接压在抽屉圆角上，用户看到的就是
                // 「侧栏顶到手机状态栏了」。抽屉是一条**抬升面板**（不是全屏页），
                // 它本来就该整块落在系统栏之内 —— 与对话框、底部抽屉一致。
                //
                // 于是里面的内容**不再**重复加一次 insets，否则会白垫两倍高度。
                //
                // ⚠️⚠️ 里面**必须**再套一个 `Scaffold`，否则抽屉里的长按菜单画不出来。
                //
                // 机制：Miuix 的弹层不自己绘制，而是把状态注册进 `LocalPopupStates`，
                // 真正的绘制由某个 `Scaffold` 的 `popupHost` 负责。抽屉这棵子树里
                // 原本没有任何 Scaffold，于是菜单"注册了却没人画"。
                //
                // `containerColor = Transparent`：底色由外层 `Surface` 画，
                // 这里只借 Scaffold 的弹层宿主。
                top.yukonga.miuix.kmp.basic.Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    popupHost = {
                        top.yukonga.miuix.kmp.utils.MiuixPopupUtils.MiuixPopupHost()
                    },
                ) { _ ->
                    content()
                }
            }
        }

        if (open) {
            top.yukonga.miuix.kmp.basic.Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                popupHost = {
                    top.yukonga.miuix.kmp.utils.MiuixPopupUtils.MiuixPopupHost()
                },
            ) { _ ->
                overlayHost()
            }
        }
    }
}
