package com.zhizhu.zhicode.compose.ui.panes

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.model.DiffFile
import com.zhizhu.zhicode.compose.model.DiffState
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 变更面板，对应原版 renderChanges() + colorDiff() + updateDiffStats()。 */
@Composable
fun ChangesPane(
    diff: DiffState,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    /**
     * 刷新动作。**默认 `null` = 不显示**「刷新」按钮。
     *
     * 原来默认是 `{}`，于是按钮永远画出来、点了却什么都不发生（调用方也没传）。
     * 改成可空后，没接线就不会出现"死按钮"；现已由 `AppScaffold` 接上
     * `viewModel::refreshDiff`。
     */
    onRefresh: (() -> Unit)? = null,
) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    Surface(modifier = modifier.fillMaxSize(), color = ZhiColors.panelSurface()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PaneHeader(
                title = "Git 变更",
                actionIcon = ZhiIcons.refresh,
                actionDescription = "刷新变更列表",
                onAction = onRefresh,
                subtitle = if (diff.files.isEmpty()) null else "+${diff.additions}  −${diff.deletions}",
            )
            if (diff.files.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        // 空白态的文案必须区分「确实没有变更」与「没能读到变更」：
                        // 原来写死"工作区没有未提交的变更"，于是 git 失败时界面
                        // 会一口咬定没有变更 —— 明明什么都没查到却给了确定性结论。
                        text = diff.note.ifEmpty { "工作区没有未提交的变更" },
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = ZhiTextScale.BodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 28.dp),
                    )
                }
                return@Surface
            }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                item { DiffStatBar(diff) }
                items(diff.files, key = { it.name }) { file ->
                    DiffFileCard(
                        file = file,
                        isDark = isDark,
                        expanded = expanded[file.name] == true,
                        onToggle = { expanded[file.name] = expanded[file.name] != true },
                    )
                }
                item { Box(modifier = Modifier.padding(bottom = 10.dp)) }
            }
        }
    }
}

@Composable
private fun DiffStatBar(diff: DiffState) {
    val scheme = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        cornerRadius = ZhiRadius.inner,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
        colors = CardDefaults.defaultColors(
            color = ZhiColors.cardSurface(),
            contentColor = scheme.onSurface,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${diff.files.size} 个文件已修改",
                fontSize = ZhiTextScale.BodySmall,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "+${diff.additions}",
                color = ZhiColors.green(),
                fontSize = ZhiTextScale.BodySmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "  −${diff.deletions}",
                color = ZhiColors.red(),
                fontSize = ZhiTextScale.BodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DiffFileCard(
    file: DiffFile,
    isDark: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .animateContentSize(
                animationSpec = tween(ZhiMotion.EXPAND, easing = FastOutSlowInEasing),
            ),
        cornerRadius = ZhiRadius.card,
        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        colors = CardDefaults.defaultColors(
            color = ZhiColors.cardSurface(),
            contentColor = scheme.onSurface,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 展开指示用 Miuix 图标，不再用 ⌄ / › 字形
            Icon(
                imageVector = if (expanded) ZhiIcons.collapse else ZhiIcons.expand,
                contentDescription = if (expanded) "折叠该文件" else "展开该文件",
                tint = scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = file.name.substringAfterLast('/'),
                fontSize = ZhiTextScale.BodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp).weight(1f),
            )
            if (file.additions > 0) {
                Text(text = "+${file.additions}", color = ZhiColors.green(), fontSize = ZhiTextScale.Caption)
            }
            if (file.deletions > 0) {
                Text(text = " −${file.deletions}", color = ZhiColors.red())
            }
        }
        Text(
            text = file.name.substringBeforeLast('/', ""),
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Micro,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 19.dp, top = 2.dp),
        )
        if (expanded) {
            DiffBlock(diff = file.diff, isDark = isDark, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** 逐行着色渲染 unified diff，对应原版 colorDiff()。 */
@Composable
fun DiffBlock(diff: String, isDark: Boolean, modifier: Modifier = Modifier) {
    val scheme = MiuixTheme.colorScheme
    val lines = remember(diff) { diff.split('\n') }
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = ZhiRadius.inner,
        insideMargin = PaddingValues(vertical = 6.dp),
        colors = CardDefaults.defaultColors(
            color = ZhiColors.cardInnerSurface(),
            contentColor = scheme.onSurface,
        ),
    ) {
        lines.forEach { line ->
            // 逐行背景是 diff 的语义着色（+绿/−红），不是装饰容器，因此保留 background
            val (foreground, background) = when {
                line.startsWith("+++") || line.startsWith("---") ->
                    scheme.onSurfaceVariantSummary to Color.Transparent
                line.startsWith("+") -> ZhiColors.green() to ZhiColors.greenContainer()
                line.startsWith("-") -> ZhiColors.red() to ZhiColors.redContainer()
                line.startsWith("@@") -> scheme.primary to Color.Transparent
                line.startsWith("diff ") || line.startsWith("index ") || line.startsWith("new file") ->
                    scheme.onSurfaceVariantSummary to Color.Transparent
                else -> scheme.onSurface to Color.Transparent
            }
            Text(
                text = line.ifEmpty { " " },
                color = foreground,
                fontSize = ZhiTextScale.Footnote,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(background)
                    .padding(horizontal = 8.dp),
            )
        }
    }
}
