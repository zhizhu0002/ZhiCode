package com.zhizhu.zhicode.compose.ui.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.ui.pages.LocalZhiPageSet
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.model.SlashCommand

/** "/" 命令面板，对应原版 updateSlashPalette()。 */
@Composable
fun SlashPalette(
    matches: List<SlashCommand>,
    onPick: (SlashCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val visible = matches.take(5)
    Surface(
        modifier = modifier.fillMaxWidth().padding(bottom = 6.dp),
        shape = RoundedCornerShape(LocalZhiPageSet.current.cardCorner),
        color = if (ZhiColors.isDark()) scheme.surfaceContainerHigh else LocalZhiPageSet.current.cardSurface(),
        contentColor = scheme.onSurface,
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "斜杠命令",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "${matches.size} 条匹配",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
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
                Surface(
                    onClick = { onPick(command) },
                    // 打字时命令集一直在收窄：命中的行要**滑上去**，落选的行淡出。
                    modifier = Modifier.animateItem().fillMaxWidth().padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(LocalZhiPageSet.current.innerCorner),
                    color = if (ZhiColors.isDark()) scheme.surfaceContainerHigh else LocalZhiPageSet.current.cardSurface(),
                    contentColor = scheme.onSurface,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = command.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(112.dp),
                        )
                        Text(
                            text = command.hint,
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
        }
        }
}

/** "@" 文件引用面板。 */
@Composable
fun FilePalette(
    query: String,
    matches: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val visible = matches.take(5)
    Surface(
        modifier = modifier.fillMaxWidth().padding(bottom = 6.dp),
        shape = RoundedCornerShape(LocalZhiPageSet.current.cardCorner),
        color = if (ZhiColors.isDark()) scheme.surfaceContainerHigh else LocalZhiPageSet.current.cardSurface(),
        contentColor = scheme.onSurface,
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "引用文件",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                // 把查询串显示出来：用户需要确认"我 @ 的是不是这个"。
                text = if (query.isEmpty()) "${matches.size} 项" else "@$query · ${matches.size} 项",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (visible.isEmpty()) {
            Text(
                // 空结果也要说清楚：静默不显示面板会让人以为 @ 没生效。
                text = "当前目录里没有匹配的文件",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        visible.forEach { path ->
            Surface(
                onClick = { onPick(path) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp),
                shape = RoundedCornerShape(LocalZhiPageSet.current.innerCorner),
                color = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = scheme.onSurface,
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
        }
        }
}

/** 排队中的预输入列表。 */
@Composable
fun QueuedPromptPanel(
    items: List<com.zhizhu.zhicode.compose.model.QueuedPrompt>,
    onRemove: (String) -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().padding(bottom = 6.dp),
        shape = RoundedCornerShape(LocalZhiPageSet.current.cardCorner),
        color = if (ZhiColors.isDark()) scheme.surfaceContainerHigh else LocalZhiPageSet.current.cardSurface(),
        contentColor = scheme.onSurface,
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已排队",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                // 说清"为什么还没发出去"，否则用户会以为卡住了。
                text = "${items.size} 条 · 当前回复完成后发送",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
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
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 两个动作都用文字而不是图标：这一行很窄，图标在中文语境里
                // 靠猜（编辑是笔、取消是叉），文字没有歧义。
                Text(
                    text = "取回",
                    color = scheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable { onEdit(queued.chatItemId) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
                Text(
                    text = "取消",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clickable { onRemove(queued.chatItemId) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
    }
}
