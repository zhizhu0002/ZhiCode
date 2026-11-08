package com.zhizhu.zhicode.compose.ui.composer

import androidx.compose.foundation.clickable
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
import com.zhizhu.zhicode.compose.theme.ZhiColors
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
            color = if (ZhiColors.isDark()) scheme.surfaceContainerHigh else ZhiColors.cardSurface(),
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
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                fontWeight = FontWeight.Bold,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "${matches.size} 条匹配",
                color = scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            )
        }
        LazyColumn(
            // 用 heightIn 让内容决定高度，不再手算 `条数 × 42 + 4`：
            // 行高一旦变化，手算的公式就错了（而且 42 这个魔数在别处没有对应常量）。
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 168.dp),
        ) {
            items(
                items = visible,
                key = { it.name },
                contentType = { "slash-command" },
            ) { command ->
                Card(
                    onClick = { onPick(command) },
                    // 打字时命令集一直在收窄：命中的行要**滑上去**，落选的行淡出。
                    //
                    // `animateItem()` 放在最前（与 ChatList 的写法一致），`padding` 在里面：
                    // 内外间距照旧，位置动画交给 LazyColumn 记账。
                    // `key = it.name` 上面已给 —— 少了 key 的话 Compose 会把"剩下的行"
                    // 当成整批新建，动画就退化成每行原地闪一下。
                    modifier = Modifier.animateItem().fillMaxWidth().padding(horizontal = 4.dp),
                    cornerRadius = ZhiRadius.inner,
                    insideMargin = PaddingValues(horizontal = 8.dp, vertical = 9.dp),
                    colors = CardDefaults.defaultColors(
                        color = if (ZhiColors.isDark()) scheme.surfaceContainerHigh else ZhiColors.cardSurface(),
                        contentColor = scheme.onSurface,
                    ),
                    pressFeedbackType = PressFeedbackType.Sink,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = command.name,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(112.dp),
                        )
                        Text(
                            text = command.hint,
                            color = scheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * "@" 文件引用面板。
 *
 * 与 [SlashPalette] 刻意保持**同一套视觉**（同一个 Card 外壳、同样的表头、
 * 同样的最多 5 条）：两者都是"在输入框上方弹出的补全列表"，如果长得不一样，
 * 用户会以为其中一个坏掉了。
 *
 * 区别只在内容：这里是路径字符串，选中后写回文字，而不是执行一个动作。
 */
@Composable
fun FilePalette(
    query: String,
    matches: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    val visible = matches.take(5)
    Card(
        modifier = modifier.fillMaxWidth().padding(bottom = 6.dp),
        cornerRadius = ZhiRadius.card,
        insideMargin = PaddingValues(vertical = 4.dp),
        colors = CardDefaults.defaultColors(
            color = if (ZhiColors.isDark()) scheme.surfaceContainerHigh else ZhiColors.cardSurface(),
            contentColor = scheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "引用文件",
                color = scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                fontWeight = FontWeight.Bold,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                // 把查询串显示出来：用户需要确认"我 @ 的是不是这个"。
                text = if (query.isEmpty()) "${matches.size} 项" else "@$query · ${matches.size} 项",
                color = scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            )
        }
        if (visible.isEmpty()) {
            Text(
                // 空结果也要说清楚：静默不显示面板会让人以为 @ 没生效。
                text = "当前目录里没有匹配的文件",
                color = scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        visible.forEach { path ->
            Card(
                onClick = { onPick(path) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp),
                cornerRadius = ZhiRadius.inner,
                insideMargin = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                colors = CardDefaults.defaultColors(
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = scheme.onSurface,
                ),
            ) {
                Text(
                    text = path,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 排队中的预输入列表。
 *
 * 每条给两个动作：**取回**（编辑后再发）与**取消**。这两个是预输入最容易出问题
 * 的地方 —— 没有它们，用户排错一条就只能等它真的发出去，而那时已经进了会话。
 *
 * 视觉与斜杠/文件面板同源（同一个 Card 外壳与表头行），三块在输入框上方
 * 因此看起来是"同一类东西"。
 */
@Composable
fun QueuedPromptPanel(
    items: List<com.zhizhu.zhicode.compose.model.QueuedPrompt>,
    onRemove: (String) -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth().padding(bottom = 6.dp),
        cornerRadius = ZhiRadius.card,
        insideMargin = PaddingValues(vertical = 4.dp),
        colors = CardDefaults.defaultColors(
            color = if (ZhiColors.isDark()) scheme.surfaceContainerHigh else ZhiColors.cardSurface(),
            contentColor = scheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已排队",
                color = scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                fontWeight = FontWeight.Bold,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                // 说清"为什么还没发出去"，否则用户会以为卡住了。
                text = "${items.size} 条 · 当前回复完成后发送",
                color = scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            )
        }
        items.forEach { queued ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = queued.text,
                    color = scheme.onSurface,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 两个动作都用文字而不是图标：这一行很窄，图标在中文语境里
                // 靠猜（编辑是笔、取消是叉），文字没有歧义。
                Text(
                    text = "取回",
                    color = scheme.primary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable { onEdit(queued.chatItemId) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
                Text(
                    text = "取消",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clickable { onRemove(queued.chatItemId) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}
