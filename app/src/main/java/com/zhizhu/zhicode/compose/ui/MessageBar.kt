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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import kotlinx.coroutines.delay

@Composable
fun MessageBar(
    message: String?,
    isError: Boolean,
    onDismiss: () -> Unit,
    wide: Boolean,
    glass: Glass,
) {
    val scheme = MaterialTheme.colorScheme
    // 淡出期间还要显示旧文案，所以要自己留一份。
    var shown by remember { mutableStateOf(message) }
    var shownError by remember { mutableStateOf(isError) }
    if (message != null) {
        shown = message
        shownError = isError
    }

    // 用 message 做 key（而不是 shown）：同一段文案被 dismiss 后再次出现时
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
    ) {
        val text = shown ?: return@AnimatedVisibility
        // 与输入器、任务卡共用同一个内缩值（见 Common.kt 的 floatingHorizontalInset）。
        val inset = PaddingValues(
            horizontal = floatingHorizontalInset(wide),
            vertical = 6.dp,
        )
        if (shownError) {
            // 错误走与沙箱页**同一个**通知条分量，见 [ZhiNoticeBar] 的说明
            Box(modifier = Modifier.fillMaxWidth().padding(inset)) {
                ZhiNoticeBar(
                    text = text,
                    tone = ZhiNoticeTone.ERROR,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }
        } else {
            // 外壳走共用的 FloatingBottomShell：内缩/圆角/阴影/模糊与任务卡
            FloatingBottomShell(wide = wide, glass = glass, verticalPadding = 6.dp) {
                // 点一下就消失：错误分支走 ZhiNoticeBar 的 clickable，这里对齐。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurface,
                        maxLines = MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 自动消失时长。 */
private const val INFO_DURATION_MS = 2200L
private const val ERROR_DURATION_MS = 4000L

/** 最多三行。 */
private const val MAX_LINES = 3
