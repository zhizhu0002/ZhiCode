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
                shape = RoundedCornerShape(topEnd = ZhiRadius.floating, bottomEnd = ZhiRadius.floating),
            ) {
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
