package com.iqge.iqcode.compose.ui.panes

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
import com.iqge.iqcode.compose.theme.IqColors
import com.iqge.iqcode.compose.theme.IqRadius
import com.iqge.iqcode.compose.model.FileEntry
import com.iqge.iqcode.compose.model.OpenFile
import com.iqge.iqcode.compose.ui.IqIcons
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
    rootPath: String,
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
    Surface(modifier = modifier.fillMaxSize(), color = IqColors.panelSurface()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (openFile == null) {
                PaneHeader(
                    title = "文件",
                    actionIcon = IqIcons.upLevel,
                    actionDescription = "上一级目录",
                    onAction = onUp,
                    subtitle = "${entries.size} 项",
                )
                // 路径用 Miuix BreadcrumbBar 展示，点击任一层级都能直接跳转
                FileBreadcrumbBar(
                    filePath = filePath,
                    rootPath = rootPath,
                    onNavigate = onNavigate,
                )
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
                                    fontSize = 11.sp,
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
                    actionIcon = IqIcons.close,
                    actionDescription = "关闭文件",
                    onAction = onCloseFile,
                    subtitle = openFile.language + " · 只读",
                )
                FileBreadcrumbBar(
                    filePath = openFile.path,
                    rootPath = rootPath,
                    onNavigate = onNavigate,
                )
                val lines = remember(openFile.path) { openFile.content.split('\n') }
                Surface(modifier = Modifier.fillMaxSize(), color = IqColors.panelSurface()) {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 3.dp)) {
                        items(lines.size) { index ->
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                                Text(
                                    text = "${index + 1}",
                                    color = scheme.onSurfaceVariantSummary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(26.dp),
                                )
                                Text(
                                    text = lines[index].ifEmpty { " " },
                                    color = scheme.onSurface,
                                    fontSize = 10.sp,
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
    rootPath: String,
    onNavigate: (String) -> Unit,
) {
    val items = remember(filePath, rootPath) { breadcrumbItems(filePath, rootPath) }
    if (items.isEmpty()) return

    BreadcrumbBar(
        items = items,
        onItemClick = { index -> items.getOrNull(index)?.let { onNavigate(it.path) } },
        highlightIndex = items.lastIndex,
        // 面包屑只占一行的引导作用，不需要占满宽度：
        // 压小 insideMargin 与 itemMaxWidth，让路径胶囊紧凑一些，给下面的文件列表让位。
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 0.dp),
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        itemMaxWidth = 96.dp,
    )
}

/**
 * 生成面包屑层级。
 *
 * 项目根目录之前的系统路径（`/data/user/0/com.iqge/files/home/...`）折叠成一项，
 * 只展开真正对用户有意义的部分，否则首屏会被十几个无名层级占满。
 */
private fun breadcrumbItems(
    filePath: String,
    rootPath: String,
): List<BreadcrumbItem> {
    if (filePath.isBlank()) return emptyList()
    val normalizedRoot = rootPath.trimEnd('/')
    val withinRoot = filePath == normalizedRoot || filePath.startsWith("$normalizedRoot/")

    val result = mutableListOf<BreadcrumbItem>()
    result += BreadcrumbItem(path = (if (withinRoot) normalizedRoot else "/"), text = "⌂ 根")

    if (!withinRoot) {
        // 不在项目内：逐级展示
        val segments = filePath.trim('/').split('/').filter { it.isNotEmpty() }
        var accumulated = ""
        segments.forEach { segment ->
            accumulated += "/$segment"
            result += BreadcrumbItem(path = accumulated, text = segment)
        }
        return result
    }

    if (filePath == normalizedRoot) return result

    val relative = filePath.removePrefix("$normalizedRoot/")
    val segments = relative.split('/').filter { it.isNotEmpty() }
    val rootName = normalizedRoot.substringAfterLast('/').ifEmpty { "根目录" }
    var accumulated = normalizedRoot

    segments.forEachIndexed { index, segment ->
        accumulated += "/$segment"
        val text = if (index == 0) "$rootName/$segment" else segment
        result += BreadcrumbItem(path = accumulated, text = text)
    }
    return result
}

/** 文件行：上下各 20dp 内边距 ≈ 60dp 行高，触摸目标也够大。 */
@Composable
private fun FileRow(entry: FileEntry, onOpen: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        cornerRadius = IqRadius.inner,
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 20.dp),
        colors = CardDefaults.defaultColors(
            color = IqColors.cardSurface(),
            contentColor = scheme.onSurface,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = if (entry.directory) IqIcons.directory else IqIcons.file,
                contentDescription = null,
                tint = if (entry.directory) scheme.primary else scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = entry.name,
                fontSize = 11.5.sp,
                fontWeight = if (entry.directory) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!entry.directory) {
                Text(
                    text = formatSize(entry.size),
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = 9.5.sp,
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
