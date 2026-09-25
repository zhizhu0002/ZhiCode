package com.zhizhu.zhicode.compose.ui.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.model.SlashCommand
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * "/" 命令面板，对应原版 updateSlashPalette()。
 * 内联在输入卡片上方，按前缀过滤，最多显示 5 条。
 *
 * 外层与每一行都用 Miuix `Card`，行直接吃 `onClick` 的按压反馈。
 */
@Composable
fun SlashPalette(
    matches: List<SlashCommand>,
    onPick: (SlashCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    val visible = matches.take(5)
    Card(
        modifier = modifier.fillMaxWidth().padding(bottom = 6.dp),
        cornerRadius = ZhiRadius.card,
        insideMargin = PaddingValues(vertical = 4.dp),
        colors = CardDefaults.defaultColors(
            color = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "斜杠命令",
                color = scheme.onSurfaceVariantSummary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "${matches.size} 条匹配",
                color = scheme.onSurfaceVariantSummary,
                fontSize = 10.sp,
            )
        }
        LazyColumn(
            // 用 heightIn 让内容决定高度，不再手算 `条数 × 42 + 4`：
            // 行高一旦变化，手算的公式就错了（而且 42 这个魔数在别处没有对应常量）。
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 168.dp),
        ) {
            items(visible, key = { it.name }) { command ->
                Card(
                    onClick = { onPick(command) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    cornerRadius = ZhiRadius.inner,
                    insideMargin = PaddingValues(horizontal = 8.dp, vertical = 9.dp),
                    colors = CardDefaults.defaultColors(
                        color = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                    ),
                    pressFeedbackType = PressFeedbackType.Sink,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = command.name,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(112.dp),
                        )
                        Text(
                            text = command.hint,
                            color = scheme.onSurfaceVariantSummary,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
