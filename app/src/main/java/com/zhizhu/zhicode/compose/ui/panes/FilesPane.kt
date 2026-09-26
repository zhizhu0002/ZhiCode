package com.zhizhu.zhicode.compose.ui.panes

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
        // 上限放到 280dp：第一项要显示真实路径（`/data/user/0/<包名>/files/home`
        // 这类形式就到了 ~40 字符），96dp 会把它截成 `/data/user/0/com…`。
        // 这只是**上限**：像 `workspace` 这种短段仍然按内容宽度收缩。
        itemMaxWidth = 280.dp,
    )
}

/**
 * 生成面包屑层级。
 *
 * 第一项是**真实路径**，不是写死的「根」：文件面板的根只是「当前项目」
 * （默认 `$HOME/workspace`），把它标成「根」会让人以为自己站在文件系统根上，
 * 也就看不出自己在哪。真实路径按「留头留尾、省中间」折叠 ——
 * 头是软件根目录（`/data/user/0/<包名>`），尾是「现在在哪」。
 *
 * 例：`/data/user/0/com.zhizhu.code/files/home/workspace`
 *   → `[/data/user/0/com.zhizhu.code/files/home]` `[workspace]`
 *
 * 之后每一项都是真实目录名，点哪一级就回到哪一级。
 */
private fun breadcrumbItems(filePath: String): List<BreadcrumbItem> {
    val normalized = filePath.trimEnd('/').ifEmpty { "/" }
    if (normalized == "/") return listOf(BreadcrumbItem(path = "/", text = "/"))

    // 当前这一级始终单独成项：既让「在哪一级」一眼可见，也能点它回到这一级。
    val prefix = normalized.substringBeforeLast('/', "")
    val here = normalized.removePrefix("$prefix/").split('/').filter { it.isNotEmpty() }
    val prefixPath = prefix.ifEmpty { "/" }

    val items = mutableListOf(
        BreadcrumbItem(path = prefixPath, text = collapsePath(prefixPath)),
    )
    var accumulated = prefix
    here.forEach { segment ->
        accumulated += "/$segment"
        items += BreadcrumbItem(path = accumulated, text = segment)
    }
    return items
}

/** 折叠路径时保留的头部段数。4 段正好是 `/data/user/0/<包名>` —— 软件根目录。 */
private const val PATH_HEAD_SEGMENTS = 4

/** 折叠路径时保留的尾部段数。尾几段是「现在在哪」，也不能省。 */
private const val PATH_TAIL_SEGMENTS = 2

/**
 * 路径折叠：段数不超过上限就原样返回，超了就省略中间。
 *
 * `/data/user/0/com.zhizhu.code/files/home`（6 段，不超）→ 原样
 * 更深时 → `/data/user/0/com.zhizhu.code/…/home/workspace`
 */
private fun collapsePath(path: String): String {
    val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
    if (segments.size <= PATH_HEAD_SEGMENTS + PATH_TAIL_SEGMENTS) return path
    val head = segments.take(PATH_HEAD_SEGMENTS).joinToString("/")
    val tail = segments.takeLast(PATH_TAIL_SEGMENTS).joinToString("/")
    return "/$head/…/$tail"
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
