package com.zhizhu.zhicode.compose.ui.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termux.app.zhicode.core.FileOps
import com.termux.shared.termux.TermuxConstants
import com.zhizhu.zhicode.compose.model.FileEntry
import com.zhizhu.zhicode.compose.model.FileFormat
import com.zhizhu.zhicode.compose.model.FileRoot
import com.zhizhu.zhicode.compose.ui.ZhiCheckboxPreference
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.ZhiSegmentedTabs
import com.zhizhu.zhicode.compose.ui.WorkspaceTabRowHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BreadcrumbBar
import top.yukonga.miuix.kmp.basic.BreadcrumbItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import java.io.File

internal val FileRowMinHeight = 64.dp
internal val FileRowSidePadding = 20.dp

@Composable
internal fun FileRootSwitcher(selected: FileRoot, onSelect: (FileRoot) -> Unit, modifier: Modifier = Modifier) {
    val roots = FileRoot.entries
    ZhiSegmentedTabs(
        tabs = roots.map { it.label },
        selectedIndex = roots.indexOf(selected).coerceIn(0, roots.lastIndex),
        onSelect = { index -> roots.getOrNull(index)?.takeIf { it != selected }?.let(onSelect) },
        modifier = modifier.fillMaxWidth().height(WorkspaceTabRowHeight),
        matchWidth = true,
    )
}

@Composable
internal fun FilePathBar(
    filePath: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    summary: String = "",
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = FileRowSidePadding, vertical = 0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileBreadcrumbBar(filePath, onNavigate, Modifier.weight(1f))
            trailing?.invoke(this)
        }
        if (summary.isNotEmpty()) Text(summary, fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

internal fun fileCountSummary(entries: List<FileEntry>): String {
    val directories = entries.count { it.directory }
    return "文件夹 $directories · 文件 ${entries.size - directories}"
}

@Composable
internal fun FileBreadcrumbBar(filePath: String, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val items = remember(filePath) { breadcrumbItems(filePath) }
    BreadcrumbBar(
        items = items,
        onItemClick = { index -> items.getOrNull(index)?.let { onNavigate(it.path) } },
        highlightIndex = items.lastIndex,
        modifier = modifier,
        insideMargin = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    )
}

internal fun breadcrumbItems(filePath: String): List<BreadcrumbItem> {
    val normalized = filePath.trimEnd('/').ifEmpty { "/" }
    val appRoot = TermuxConstants.TERMUX_DATA_DIR_PATH.trimEnd('/')
    val sharedRoot = "/storage/emulated/0"
    val isSharedPath = normalized == sharedRoot || normalized.startsWith("$sharedRoot/")
    val start = when {
        normalized == appRoot || normalized.startsWith("$appRoot/") -> appRoot
        isSharedPath -> sharedRoot
        else -> "/"
    }
    val rest = if (normalized == start) "" else normalized.removePrefix(if (start == "/") "/" else "$start/")
    val startLabel = if (start == sharedRoot) "sdcard" else start.substringAfterLast('/').ifEmpty { "/" }
    val items = mutableListOf(BreadcrumbItem(start, startLabel))
    var path = if (start == "/") "" else start
    rest.split('/').filter(String::isNotEmpty).forEach {
        path += "/$it"
        items += BreadcrumbItem(path, it)
    }
    return items
}

@Composable
internal fun FileListRow(
    entry: FileEntry,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    now: Long = System.currentTimeMillis(),
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val scheme = MiuixTheme.colorScheme
    var dirCount by remember(entry.path, entry.modifiedAt) { mutableStateOf<Int?>(null) }
    if (entry.directory) LaunchedEffect(entry.path, entry.modifiedAt) {
        dirCount = withContext(Dispatchers.IO) { FileOps.childCount(File(entry.path)).takeIf { it >= 0 } }
    }
    val info = fileInfoLine(entry, now, dirCount)
    val entryIcon: @Composable () -> Unit = {
        Icon(
            painter = if (entry.directory) androidx.compose.ui.res.painterResource(com.zhizhu.zhicode.compose.R.drawable.file_browser_folder) else ZhiIcons.file,
            contentDescription = null,
            tint = if (entry.directory) Color.Unspecified else scheme.primary,
            modifier = Modifier.size(36.dp),
        )
    }
    if (selectionMode) {
        // 选择模式使用官方 Miuix preference 行：整行与复选框共享同一个语义动作。
        ZhiCheckboxPreference(
            title = entry.name,
            summary = info.ifEmpty { null },
            checked = selected,
            onCheckedChange = { onOpen() },
            modifier = modifier.fillMaxWidth(),
            startAction = entryIcon,
        )
    } else {
        Card(
            onClick = onOpen,
            onLongPress = onLongPress,
            modifier = modifier.fillMaxWidth().heightIn(min = FileRowMinHeight),
            cornerRadius = ZhiRadius.inner,
            insideMargin = PaddingValues(horizontal = FileRowSidePadding, vertical = 10.dp),
            colors = CardDefaults.defaultColors(color = Color.Transparent, contentColor = scheme.onSurface),
            pressFeedbackType = PressFeedbackType.Sink,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                entryIcon()
                Column(Modifier.weight(1f)) {
                    Text(entry.name, fontSize = MiuixTheme.textStyles.title4.fontSize, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (info.isNotEmpty()) Text(info, fontSize = MiuixTheme.textStyles.footnote1.fontSize, color = scheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
                }
                when {
                    trailing != null -> trailing.invoke(this)
                    entry.directory -> Icon(ZhiIcons.arrowRight, "打开 ${entry.name}", tint = scheme.onSurfaceVariantSummary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

internal fun fileInfoLine(entry: FileEntry, now: Long, dirCount: Int? = null): String {
    val date = if (entry.directory) FileFormat.dateFull(entry.modifiedAt, now) else FileFormat.date(entry.modifiedAt, now)
    val detail = if (entry.directory) dirCount?.let { "${it}项" }.orEmpty() else FileFormat.size(entry.size)
    return listOf(date, detail).filter(String::isNotEmpty).joinToString(" | ")
}
