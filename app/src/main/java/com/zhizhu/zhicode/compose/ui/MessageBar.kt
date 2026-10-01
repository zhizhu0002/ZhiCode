package com.zhizhu.zhicode.compose.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
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
import com.zhizhu.zhicode.compose.theme.ZhiTextScale
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
 * 2. **颜色**：错误走 `errorContainer` / `error`，普通提示走
 *    `surfaceContainer` / `onSurfaceVariantSummary` —— 都在主题里，
 *    不写死红绿，深浅色模式各自成立（截图里那种红底红字就是前者）。
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
        val shape = RoundedCornerShape(ZhiRadius.floating)
        FloatingToolbar(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    // 错误条**不做模糊**：错误常常伴随玻璃层刚好没铺满（空对话、切页瞬间），
                    // 那时 `capture` 拿到的是空背景，模糊出来是一块灰斑，
                    // 反而把红底红字压得不清楚。
                    if (shownError || !glass.supported) Modifier
                    else glass.blur(Modifier, shape, radius = 16f),
                )
                .clickable(onClick = onDismiss),
            color = if (shownError) scheme.errorContainer
            else glass.surfaceColor(scheme.surfaceContainer),
            cornerRadius = ZhiRadius.floating,
            outSidePadding = PaddingValues(
                // 与任务卡、输入器共用同一个内缩值（见 Common.kt）。
                horizontal = floatingHorizontalInset(wide),
                vertical = 6.dp,
            ),
            shadowElevation = 6.dp,
            showDivider = false,
        ) {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    text = text,
                    fontSize = ZhiTextScale.Footnote,
                    // 错误文字用 `error` 而不是 `onErrorContainer`：
                    // `errorContainer` 是浅红底，`onErrorContainer` 在浅色模式下是深红、
                    // 在深色模式下才是浅红 —— 而 `error` 恰好两端都是"底色的对比色"，
                    // 与截图里"红底 / 更亮的红字"一致。
                    color = if (shownError) scheme.error else scheme.onSurface,
                    maxLines = MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
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
