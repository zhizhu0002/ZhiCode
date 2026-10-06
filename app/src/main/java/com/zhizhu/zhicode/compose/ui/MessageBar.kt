package com.zhizhu.zhicode.compose.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 操作反馈条（「设置已保存」「保存失败：…」）。
 *
 * ## 为什么要重建它
 *
 * 这个位置原来挂的是 Miuix `Snackbar`，后来被整个删掉了，理由记在
 * `WorkspaceViewModel`：「浮层会遮挡底部输入器」。删掉之后 29 处 `copy(message = …)`
 * 成了**死代码** —— 保存失败、附件读不了、没有可复制的内容，界面上都毫无反应。
 *
 * 所以这次不是"再放一个 Snackbar"，而是解决当时删它的那个原因：
 *
 * 1. **位置**：作为悬浮列表的**一层**（在任务卡与输入器之间），而不是 `SnackbarHost`
 *    那种覆盖在内容之上的浮层。它参与 Column 的布局，所以**永远不会**压住输入器，
 *    也不需要猜"输入器现在多高"。
 * 2. **颜色**：错误走 [ZhiNoticeBar] 的通知条（`errorContainer` / `error`，红底红字），
 *    普通提示走 `surfaceContainer` / `onSurfaceVariantSummary` —— 都在主题里，
 *    不写死红绿，深浅色模式各自成立。
 *    **错误那一档与沙箱页的后端错误共用同一个分量**（见 [ZhiNoticeBar]）：同一件事
 *    在这份工程里原来两种长相（这里是浮动工具栏、那里是深灰卡片配红字），现在一致。
 *    普通提示仍然保持原来的浮动工具栏（带模糊）—— 它不是"通知"，
 *    只是输入器上方的轻提示，这一档的外观没有被这次改动碰到。
 * 3. **生命周期**：没有"每条提示都要手动 dismiss"的负担。消息一变就重新计时，
 *    到时自动消失；点一下可以立刻关掉。`SnackbarHostState` 的队列模型在
 *    "连续三次保存失败"时会把三条排队慢慢放，反馈反而迟了。
 *
 * @param message 当前提示，null 表示不显示
 * @param isError 是不是错误（决定配色）
 * @param onDismiss 用户点击关闭 / 自动超时时回调
 */
@Composable
fun MessageBar(
    message: String?,
    isError: Boolean,
    onDismiss: () -> Unit,
    wide: Boolean,
    glass: Glass,
) {
    val scheme = MiuixTheme.colorScheme
    // 淡出期间还要显示旧文案，所以要自己留一份。
    // 直接把 message 传给 Text 的话，dismiss 的那一刻文案会先变空、再播动画，
    // 看到的是"一截空条滑走"。
    var shown by remember { mutableStateOf(message) }
    var shownError by remember { mutableStateOf(isError) }
    if (message != null) {
        shown = message
        shownError = isError
    }

    // 同一段文案反复出现（例如连点两次保存）也要重新计时，
    // 所以 key 里带上 shown 本身；只在 shown 变化时才重启协程。
    LaunchedEffect(shown) {
        if (shown != null) {
            delay(if (shownError) ERROR_DURATION_MS else INFO_DURATION_MS)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(ZhiMotion.enterSpec) { it / 2 } + fadeIn(ZhiMotion.fadeInSpec),
        exit = slideOutVertically(ZhiMotion.exitSpec) { it / 2 } + fadeOut(ZhiMotion.fadeOutSpec),
    ) {
        val text = shown ?: return@AnimatedVisibility
        // 与输入器、任务卡共用同一个内缩值（见 Common.kt 的 floatingHorizontalInset）。
        val inset = PaddingValues(
            horizontal = floatingHorizontalInset(wide),
            vertical = 6.dp,
        )
        if (shownError) {
            // 错误走与沙箱页**同一个**通知条分量，见 [ZhiNoticeBar] 的说明：
            // 原来这里是一个带投影的浮动工具栏，而沙箱页那处是一张深灰 Card 配红字 ——
            // 同一件事两种长相。现在两处都是"贴边红底横幅"。
            //
            // padding 而不是 FloatingToolbar 的 outSidePadding：通知条是**贴边**的，
            // 不需要阴影与 squircle 底，也就用不上那个外壳。
            Box(modifier = Modifier.fillMaxWidth().padding(inset)) {
                ZhiNoticeBar(
                    text = text,
                    tone = ZhiNoticeTone.ERROR,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }
        } else {
            // 外壳走共用的 FloatingBottomShell：内缩/圆角/阴影/模糊与任务卡、
            // 输入器同源 —— 三块在同一列上，宽度与观感必须一致
            // （形态以之前的发送栏为准，见 Common.kt）。
            // verticalPadding = 6dp：沿用反馈条原本的纵向呼吸空间。
            FloatingBottomShell(wide = wide, glass = glass, verticalPadding = 6.dp) {
                // 点一下就消失：错误分支走 ZhiNoticeBar 的 clickable，这里对齐。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 4.dp),
                ) {
                    Text(
                        text = text,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = scheme.onSurface,
                        maxLines = MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 自动消失时长。
 *
 * 错误比普通提示留得久：普通提示（「已复制」「已刷新」）用户不需要读完，
 * 而错误往往带着原因（「保存失败：权限被拒绝」），太短会来不及看；
 * 同时两者都**不是**无限期 —— 无限期会永久占着输入器上方那条空间。
 */
private const val INFO_DURATION_MS = 2200L
private const val ERROR_DURATION_MS = 4000L

/**
 * 最多三行。
 *
 * 提示条是悬浮层的**一层**，它长高会把输入器往上顶（这是刻意选的：宁可顶也不要盖住），
 * 但长到十几行（某些异常会把整段堆栈塞进 message）就等于把整屏吃掉了。
 */
private const val MAX_LINES = 3
