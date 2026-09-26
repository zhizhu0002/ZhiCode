package com.zhizhu.zhicode.compose.ui.panes

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.shared.termux.TermuxConstants
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.model.FileEntry
import com.zhizhu.zhicode.compose.model.OpenFile
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import top.yukonga.miuix.kmp.basic.BreadcrumbBar
import top.yukonga.miuix.kmp.basic.BreadcrumbItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 文件面板，对应原版 renderFiles() / showFileBrowser() / showFileEditor()。
 * 本期为只读浏览 + 只读查看，不做写入。
 */
@Composable
fun FilesPane(
    filePath: String,
    entries: List<FileEntry>,
    openFile: OpenFile?,
    onOpen: (FileEntry) -> Unit,
    onUp: () -> Unit,
    onNavigate: (String) -> Unit,
    onCloseFile: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 列表为空时的说明（目录不存在等）。
     * 默认空串 = 目录真的为空，此时不显示任何提示。
     */
    emptyNote: String = "",
) {
    val scheme = MiuixTheme.colorScheme
    Surface(modifier = modifier.fillMaxSize(), color = ZhiColors.panelSurface()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (openFile == null) {
                PaneHeader(
                    title = "文件",
                    actionIcon = ZhiIcons.upLevel,
                    actionDescription = "上一级目录",
                    onAction = onUp,
                    subtitle = "${entries.size} 项",
                )
                // 路径用 Miuix BreadcrumbBar 展示，点击任一层级都能直接跳转
                FileBreadcrumbBar(filePath = filePath, onNavigate = onNavigate)
                // 不做常驻过滤框：Miuix InputField 有 45dp 最小高度，
                // 常驻会把文件列表挤下去，与"文件 UI 紧凑"的要求冲突。
                Box(modifier = Modifier.fillMaxSize()) {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        // 左右只留 4dp：文件名要尽可能宽，列表本身已经是最外层容器，
                        // 再往里缩只会让长文件名提前被省略号截掉。
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    ) {
                        items(entries, key = { it.path }) { entry ->
                            FileRow(entry = entry, onOpen = { onOpen(entry) })
                        }
                        // 目录不存在时把原因说出来，而不是让面板空着（"0 项"）
                        // 让用户以为应用坏了。
                        if (entries.isEmpty() && emptyNote.isNotEmpty()) {
                            item {
                                Text(
                                    text = emptyNote,
                                    color = scheme.onSurfaceVariantSummary,
                                    fontSize = ZhiTextScale.Caption,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                                )
                            }
                        }
                        item { Box(modifier = Modifier.padding(bottom = 8.dp)) }
                    }
                    // Miuix 滚动条
                    VerticalScrollBar(
                        adapter = rememberScrollBarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            } else {
                PaneHeader(
                    title = openFile.name,
                    actionIcon = ZhiIcons.close,
                    actionDescription = "关闭文件",
                    onAction = onCloseFile,
                    subtitle = openFile.language + " · 只读",
                )
                FileBreadcrumbBar(filePath = openFile.path, onNavigate = onNavigate)
                val lines = remember(openFile.path) { openFile.content.split('\n') }
                Surface(modifier = Modifier.fillMaxSize(), color = ZhiColors.panelSurface()) {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 3.dp)) {
                        items(lines.size) { index ->
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                                Text(
                                    text = "${index + 1}",
                                    color = scheme.onSurfaceVariantSummary,
                                    fontSize = ZhiTextScale.Footnote,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(26.dp),
                                )
                                Text(
                                    text = lines[index].ifEmpty { " " },
                                    color = scheme.onSurface,
                                    fontSize = ZhiTextScale.Footnote,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 把绝对路径拆成 Miuix `BreadcrumbBar` 需要的层级列表。 */
@Composable
private fun FileBreadcrumbBar(
    filePath: String,
    onNavigate: (String) -> Unit,
) {
    val items = remember(filePath) { breadcrumbItems(filePath) }
    if (items.isEmpty()) return

    BreadcrumbBar(
        items = items,
        onItemClick = { index -> items.getOrNull(index)?.let { onNavigate(it.path) } },
        highlightIndex = items.lastIndex,
        // 面包屑只占一行的引导作用，不需要占满宽度：
        // 压小 insideMargin，给下面的文件列表让位。
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 0.dp),
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        // itemMaxWidth 用 Miuix 的默认值 160dp，不覆盖 —— 每一项都是单独的目录名，
        // 最长的是包名 `com.zhizhu.code`（15 字符，约 146dp），160dp 放得下。
        // ⚠️ 别改成把好几级拼成一条路径：那一定会被省略号截断，反而什么都看不见。
    )
}

/**
 * 生成面包屑层级：**一级目录一个项**，从「软件根目录」开始。
 *
 * `/data/user/0/com.zhizhu.code/files/home/workspace`
 *   → `com.zhizhu.code` `files` `home` `workspace`
 *
 * 为什么砍掉前面的 `/data/user/0`：它在应用沙箱里是 `drwx--x--x`
 * （other 只有 x 没有 r），**列不出来** —— 挂一个点进去只能看到
 * 「无法读取（权限不足）」的层级，纯粹是噪音。首项文字直接用包名，
 * 它确实就是软件根目录，语义也对得上。
 *
 * 路径在应用根**之外**（设置里把项目路径改到了 `/sdcard/...` 等）时，
 * 没有“包名”可以当起点，就逐级展示真实层级；此时首项是 `/`。
 *
 * 每一项的 `path` 都是真实层级，点哪一级就回到哪一级。
 */
private fun breadcrumbItems(filePath: String): List<BreadcrumbItem> {
    val normalized = filePath.trimEnd('/').ifEmpty { "/" }
    val appRoot = TermuxConstants.TERMUX_DATA_DIR_PATH.trimEnd('/')

    val start = if (normalized == appRoot || normalized.startsWith("$appRoot/")) appRoot else "/"
    val rest = when {
        normalized == start -> ""
        start == "/" -> normalized.removePrefix("/")
        else -> normalized.removePrefix("$start/")
    }

    val items = mutableListOf(
        BreadcrumbItem(path = start, text = start.substringAfterLast('/').ifEmpty { "/" }),
    )
    var accumulated = if (start == "/") "" else start
    rest.split('/').filter { it.isNotEmpty() }.forEach { segment ->
        accumulated += "/$segment"
        items += BreadcrumbItem(path = accumulated, text = segment)
    }
    return items
}

/** 文件行：上下各 20dp 内边距 ≈ 60dp 行高，触摸目标也够大。 */
@Composable
private fun FileRow(entry: FileEntry, onOpen: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        cornerRadius = ZhiRadius.inner,
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 20.dp),
        colors = CardDefaults.defaultColors(
            color = ZhiColors.cardSurface(),
            contentColor = scheme.onSurface,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = if (entry.directory) ZhiIcons.directory else ZhiIcons.file,
                contentDescription = null,
                tint = if (entry.directory) scheme.primary else scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = entry.name,
                fontSize = ZhiTextScale.Caption,
                fontWeight = if (entry.directory) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!entry.directory) {
                Text(
                    text = formatSize(entry.size),
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Micro,
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1_000L -> "$bytes B"
    bytes < 1_000_000L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1000f)
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000f)
}
