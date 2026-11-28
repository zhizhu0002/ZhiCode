package com.zhizhu.zhicode.compose.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.zhizhu.zhicode.compose.ui.effects.ZhiSquircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.theme.ZhiSpacing
import kotlinx.coroutines.delay

@Composable
fun PageFeedback(
    message: String?,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // 淡出期间还要显示旧文案，否则会看到"一截空条滑走"（同 MessageBar）。
    var shown by remember { mutableStateOf(message) }
    var shownError by remember { mutableStateOf(isError) }
    if (message != null) {
        shown = message
        shownError = isError
    }

    // 用 message 做 key：同一段文案关闭后再次弹出时，message 从 null 变为非 null 才能重新计时。
    LaunchedEffect(message) {
        if (message != null) {
            delay(if (isError) ERROR_DURATION_MS else INFO_DURATION_MS)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(ZhiMotion.enterSpec) { it / 2 } + fadeIn(ZhiMotion.fadeInSpec),
        exit = slideOutVertically(ZhiMotion.exitSpec) { it / 2 } + fadeOut(ZhiMotion.fadeOutSpec),
        modifier = modifier,
    ) {
        val text = shown ?: return@AnimatedVisibility
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ZhiSpacing.pageMargin, vertical = 8.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismiss)
                    // 整条都是"点一下关掉"的触控目标，抬到 48dp 下限
                    // （单行文案 + 10dp 内边距只有约 41dp）。
                    .defaultMinSize(minHeight = ZhiSpacing.minTouchTarget),
                shape = ZhiSquircle(ZhiRadius.card),
                color = if (shownError) scheme.errorContainer else scheme.surfaceContainerHigh,
                contentColor = if (shownError) scheme.error else scheme.onSurface,
                shadowElevation = 8.dp,
            ) {
                Text(
                    text = text,
                    color = if (shownError) scheme.error else scheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** 与 [MessageBar] 保持同一组时长：错误留得久，普通提示短一些。 */
private const val INFO_DURATION_MS = 2200L
private const val ERROR_DURATION_MS = 4000L

/** 最多三行（同 [MessageBar]）：异常可能把整段堆栈塞进 message。 */
private const val MAX_LINES = 3

data class PageFeedbackData(
    val message: String?,
    val isError: Boolean,
    val onDismiss: () -> Unit,
)

/** 由 `AppScaffold` 提供；没有提供时 [PageFeedbackSlot] 什么也不画。 */
val LocalPageFeedback = androidx.compose.runtime.compositionLocalOf<PageFeedbackData?> { null }

/** 在**整页**底部显示当前反馈（设置页、二级页用它）。 */
@Composable
fun PageFeedbackSlot(modifier: Modifier = Modifier) {
    val data = LocalPageFeedback.current ?: return
    PageFeedback(
        message = data.message,
        isError = data.isError,
        onDismiss = data.onDismiss,
        modifier = modifier,
    )
}

/** 在飞动作的快照（[com.zhizhu.zhicode.compose.model.ActionBusy] 的只读视图）。 */
val LocalActionBusy = androidx.compose.runtime.compositionLocalOf<Set<String>> { emptySet() }

/** 某个页面动作是否在跑；没有提供数据源时恒为 false。 */
@Composable
fun rememberActionBusy(key: String): Boolean = key in LocalActionBusy.current
