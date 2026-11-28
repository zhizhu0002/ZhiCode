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

    // 用 message 做 key（而不是 shown）：同一段文案被 dismiss 后再次出现时，
    // shown 还留着旧值，key=shown 不会重启协程导致无法自动消失；
    // message 从 null 变为非 null 会稳定重启计时器。
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
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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

/** 自动消失时长。 */
private const val INFO_DURATION_MS = 2200L
private const val ERROR_DURATION_MS = 4000L

/** 最多三行。 */
private const val MAX_LINES = 3
