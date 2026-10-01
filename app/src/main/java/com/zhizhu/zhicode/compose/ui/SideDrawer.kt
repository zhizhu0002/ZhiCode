package com.zhizhu.zhicode.compose.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 左侧抽屉式侧边栏。
 *
 * 遮罩淡入淡出，面板从左侧滑入，带轻微位移缓动。
 * （从 `AppScaffold.kt` 原地拆出，内容逐字未改。）
 */
@Composable
internal fun ZhiSideDrawer(
    open: Boolean,
    onClose: () -> Unit,
    width: Dp,
    glass: Glass,
    content: @Composable () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val scrimInteraction = remember { MutableInteractionSource() }

    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩：走 Miuix Surface(onClick)，不再手写 background + clickable。
        // 颜色用主题的 windowDimming（Miuix 自己给弹窗/抽屉遮罩用的语义色），
        // 不再硬编码 Color.Black —— 浅色模式下黑遮罩会把背景压得过重。
        // 传 indication = null 保持"只有压暗、不出现涟漪"的原有效果。
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(ZhiMotion.FAST)),
            exit = fadeOut(tween(ZhiMotion.FAST)),
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
                animationSpec = tween(ZhiMotion.MEDIUM, easing = EaseOutCubic),
            ) { -it } + fadeIn(tween(ZhiMotion.FAST)),
            exit = slideOutHorizontally(
                animationSpec = tween(ZhiMotion.EXPAND, easing = FastOutSlowInEasing),
            ) { -it } + fadeOut(tween(ZhiMotion.FAST)),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Surface(
                modifier = Modifier
                    .width(width)
                    .fillMaxHeight()
                    .then(
                        glass.blur(
                            Modifier,
                            RoundedCornerShape(topEnd = ZhiRadius.floating, bottomEnd = ZhiRadius.floating),
                            radius = 24f,
                        ),
                    ),
                color = glass.surfaceColor(scheme.surfaceContainer),
                shape = RoundedCornerShape(topEnd = ZhiRadius.floating, bottomEnd = ZhiRadius.floating),
            ) {
                content()
            }
        }
    }
}
