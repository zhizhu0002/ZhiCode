package com.zhizhu.zhicode.compose.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
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
 * ## 必须由调用方提供一个「更外层的 Scaffold」
 *
 * 这个组件本身只是一层浮层，**它不会也不能**解决「抽屉要盖住顶栏/底栏」与
 * 「对话框要盖住抽屉」这两个互相冲突的需求。调用方必须把
 * `Scaffold(transparent) { Box { ZhiSideDrawer(...); ZhiOverlayHost(...) } }`
 * 整体排在主 Scaffold **之后**（见 `WorkspacePage` 末尾那段说明）。
 *
 * 三条曾经踩过的坑，都别改回去：
 *
 * 1. 放进主 Scaffold 的 content → 被 `topBar` / `bottomBar` 压住
 *    （它们是 Scaffold 的独立槽位、在 body 之后 place），
 *    表现为「新会话」那一行被顶栏吃掉。
 * 2. 关着的时候保持 `fillMaxSize` → 吃掉全屏点击，
 *    「侧栏按钮点了没反应」（实测 `openSidebar` 已被调用、状态已 true，界面不动）。
 * 3. 用独立 `Dialog` 窗口 → 窗口 attach 有延迟，进出动画在黑屏之后才跑起来，
 *    表现为「打开/关闭时背景黑化有延迟」。窗口级方案还连带让 Miuix 弹层的
 *    `popupHost` 失效。所以**不要**再用 Dialog。
 *
 * 现在的做法：正常画在视图树里，但**关闭时完全不占位**（见下面 `containerModifier`）。
 */
@Composable
internal fun ZhiSideDrawer(
    open: Boolean,
    onClose: () -> Unit,
    width: Dp,
    /**
     * 抽屉内部的浮层宿主。
     *
     * 抽屉画在主 Scaffold 之后（才盖得住顶栏/底栏），而 Miuix 弹层只能在
     * **离它最近的 Scaffold** 里绘制。所以「从侧栏触发、要显示在抽屉之上」的
     * 弹层，必须挂进抽屉自己这个 Scaffold（`renderInRootScaffold = false`）。
     *
     * 调用方传的就是同一份 `ZhiOverlayHost`。
     */
    overlayHost: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val scrimInteraction = remember { MutableInteractionSource() }

    /*
     * ⚠️ 关闭时用零尺寸 Modifier，打开时才是 fillMaxSize。
     *
     * 这一条同时解决"点击被吃"和"动画延迟"：
     *  · 关闭时零尺寸 → 不参与命中测试，汉堡按钮能正常点到；
     *  · 打开时立刻是 fillMaxSize → `AnimatedVisibility` 在同一帧内
     *    从"不可见"过渡到"可见"，滑入与压暗动画从第一帧就播，没有延迟感。
     *
     * ⚠️ 不能用 `if (open) { ... }` 整体移除：那样 `AnimatedVisibility` 的
     * 退场动画来不及播放（组件一移除，动画状态一起消失）。
     */
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
                // 主界面是 edge-to-edge（DecorView 不再消费系统窗口 inset），抽屉要自己
                // 让开系统栏，否则第一行压在状态栏底下。
                //
                // 加在 `Surface` 上的话，面板底色会跟着缩进去：抽屉滑入时状态栏那一条
                // 会露出后面的工作区。所以底色铺满、内容让位。
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
                    Box(modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
                        content()
                    }
                }
            }
        }

        /*
         * 浮层宿主：全屏，且排在面板**之后**（后画的在上层）。
         *
         * ⚠️⚠️ 这里的位置是修一个真实 bug 的关键。
         *
         * 原来 `overlayHost()` 挂在面板自己的那个 `Scaffold` 里 ——
         * 也就是上面那块 `Surface` 内部。抽屉面板只有 76% 屏宽（且被
         * `RoundedCornerShape` 裁角），于是对话框被限制在那块矩形里：
         * 居中变成偏左、宽对话框被切掉右边、圆角外还被面板裁一刀。
         * 更糟的是它和面板同层，弹层的压暗盖不住面板自身。
         *
         * 现在把它提成 `Box` 的直接子节点，铺满整个屏幕：
         *   · 弹层按正常居中规则排列（和主界面里的对话框位置一致）；
         *   · 压暗层盖住侧栏本体，符合"模态"的视觉预期；
         *   · 用 `OverlayDialog` 而不是独立窗口 —— 动画、圆角、压暗曲线
         *     与全应用其它对话框**完全同一条代码路径**，逐帧一致。
         *
         * 这个 `Scaffold` 只借弹层宿主，`containerColor` 透明、内容为空，
         * 且不带任何 pointer input，所以不会挡住下面侧栏的点击。
         */
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
