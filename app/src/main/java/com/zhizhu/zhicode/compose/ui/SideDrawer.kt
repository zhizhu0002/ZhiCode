package com.zhizhu.zhicode.compose.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.zhizhu.zhicode.compose.ui.effects.ZhiSquircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
                    .fillMaxHeight(),
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
                // 主界面是 edge-to-edge（DecorView 不再消费系统窗口 inset），而侧栏是画在
                // `Scaffold` **之上**的浮层 —— 它不吃 Scaffold 给内容区的 `padding`，
                // 自己也没有任何 inset 修饰符，于是内容从 y=0 开始，第一行压在状态栏底下。
                //
                // 加在 `Surface` 上的话，面板底色会跟着缩进去：抽屉滑入时状态栏那一条
                // 会露出后面的工作区。所以底色铺满、内容让位。
                // ⚠️⚠️ 这里**必须**再套一个 `Scaffold`，否则抽屉里的长按菜单画不出来。
                //
                // 机制：Miuix 的弹层（`OverlayDropdownPopup` / `OverlayDialog` …）并不自己
                // 绘制，而是把一个 `PopupState` 注册进 **`LocalPopupStates`** 这个列表，
                // 真正的绘制由某个 `Scaffold` 的 `popupHost: @Composable () -> Unit =
                // { MiuixPopupHost() }` 负责（见 `Scaffold.kt:108` 的
                // `LocalPopupStates provides popupStates`）。
                //
                // 而侧栏抽屉是 `WorkspacePage` 里 `Scaffold` **之后**的兄弟节点 ——
                // 这棵子树里**没有任何 Scaffold**，所以：
                //  · `renderInRootScaffold = true` → 菜单注册进最外层 Scaffold 的宿主，
                //    但那里在抽屉**下层**，被抽屉整块盖住（看不见）；
                //  · `renderInRootScaffold = false` → 读 `LocalPopupStates`，
                //    而这里是 `staticCompositionLocalOf` 的**默认空列表**，注册进去
                //    没有任何宿主认领（同样看不见）。
                // 两种写法都"点了没反应"，这正是用户反复报的那个 bug。
                //
                // 套一个 Scaffold 之后，`LocalPopupStates` 由它提供、`MiuixPopupHost`
                // 也在抽屉内部绘制 —— 菜单落在抽屉这一层，层级正确。
                //
                // `containerColor = Transparent`：抽屉自己的底色由外层 `Surface` 画，
                // 这里只是借 Scaffold 的**弹层宿主**，不能再加一层不透明底。
                top.yukonga.miuix.kmp.basic.Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    popupHost = {
                        com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace.note("drawer/popupHost rendered")
                        top.yukonga.miuix.kmp.utils.MiuixPopupUtils.MiuixPopupHost()
                    },
                ) { _ ->
                    com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace.note("drawer/scaffold content")
                    Box(modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
                        content()
                    }
                }
            }
        }
    }
}
