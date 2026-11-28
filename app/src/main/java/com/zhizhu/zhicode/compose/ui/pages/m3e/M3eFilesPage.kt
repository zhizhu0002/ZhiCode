@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.FileEntry
import com.zhizhu.zhicode.compose.model.FileRoot
import com.zhizhu.zhicode.compose.model.FileSortKey
import com.zhizhu.zhicode.compose.model.fileRangeSelection
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.LocalFloatingNavInset
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.panes.breadcrumbItems
import com.zhizhu.zhicode.compose.ui.panes.fileInfoLine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val M3eFileSidePadding = 16.dp
private val M3eFileRowMinHeight = 64.dp

/** M3E 文件区：分根切换 + 搜索/多选工具栏 + 面包屑 + 列表。 */
@Composable
internal fun M3eFilesContent(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        M3eFileRootSwitcher(
            selected = state.fileRoot,
            onSelect = viewModel::switchFileRoot,
            modifier = Modifier.padding(horizontal = M3eFileSidePadding, vertical = 8.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            M3eFileBrowser(
                state = state,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun M3eFileRootSwitcher(
    selected: FileRoot,
    onSelect: (FileRoot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roots = FileRoot.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        roots.forEachIndexed { index, root ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index, roots.size),
                selected = root == selected,
                onClick = { if (root != selected) onSelect(root) },
                label = { Text(root.label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun M3eFileBrowser(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val floatingNavInset = LocalFloatingNavInset.current

    val entries = state.fileEntries
    val selection = state.fileSelection
    val selectionMode = state.fileSelectionMode
    val listOptions = state.fileListOptions
    val allSelected = entries.isNotEmpty() && entries.all { it.path in selection }

    val currentEntries by rememberUpdatedState(entries)
    val currentSelection by rememberUpdatedState(selection)
    val currentMode by rememberUpdatedState(selectionMode)
    val setSelection by rememberUpdatedState(viewModel::setFileSelection)
    val enterSelection by rememberUpdatedState(viewModel::longPressFileEntry)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = M3eFileSidePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val motion = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = selectionMode,
                transitionSpec = {
                    val forward = targetState
                    (fadeIn(motion.defaultEffectsSpec()) +
                        slideInHorizontally(motion.fastSpatialSpec()) { w ->
                            if (forward) w / 6 else -w / 6
                        }).togetherWith(
                        fadeOut(motion.fastEffectsSpec()) +
                            slideOutHorizontally(motion.fastSpatialSpec()) { w ->
                                if (forward) -w / 6 else w / 6
                            },
                    )
                },
                label = "m3eFilesToolbar",
            ) { inSelection ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (inSelection) {
                        TextButton(onClick = viewModel::clearFileSelection) {
                            Text("退出")
                        }
                        Text(
                            text = "已选择 ${selection.size} 项",
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::toggleSelectAllFiles) {
                            Text(if (allSelected) "取消全选" else "全选")
                        }
                    } else {
                        OutlinedTextField(
                            value = listOptions.query,
                            onValueChange = viewModel::updateFileQuery,
                            label = { Text("搜索文件") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    painter = ZhiIcons.search,
                                    contentDescription = null,
                                    modifier = Modifier.size(M3eDimens.IconSmall),
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp, max = 56.dp),
                        )
                        if (state.fileClipboard.size > 0) {
                            TextButton(onClick = viewModel::pasteFilesIntoCurrentDirectory) {
                                Icon(
                                    painter = ZhiIcons.paste,
                                    contentDescription = if (state.fileClipboardMove) {
                                        "粘贴移动的 ${state.fileClipboard.size} 项"
                                    } else {
                                        "粘贴复制的 ${state.fileClipboard.size} 项"
                                    },
                                    modifier = Modifier.size(M3eDimens.IconSmall),
                                )
                            }
                        }
                        TextButton(onClick = viewModel::newFileForm) {
                            Icon(
                                painter = ZhiIcons.newEntry,
                                contentDescription = "新建",
                                tint = scheme.primary,
                                modifier = Modifier.size(M3eDimens.IconSmall),
                            )
                        }
                        M3eSortMenu(
                            listOptions = listOptions,
                            onSortChange = viewModel::updateFileSort,
                            onToggleDirection = viewModel::toggleFileSortDirection,
                        )
                    }
                }
            }
        }

        M3eBreadcrumbBar(
            filePath = state.filePath,
            onNavigate = viewModel::navigateTo,
        )

        if (state.fileRoot == FileRoot.SHARED && !state.sharedStorageGranted) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eFileSidePadding, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                color = scheme.errorContainer,
                contentColor = scheme.onErrorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "共享存储需要「所有文件访问权限」",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::openSharedStorageSettings) { Text("去授权") }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    bottom = (if (selectionMode) 96.dp else 16.dp) + floatingNavInset,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.filePath) {
                        fun indexAt(y: Float): Int =
                            listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { y >= it.offset && y < it.offset + it.size }
                                ?.index ?: -1

                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            val anchor = indexAt(down.position.y)
                            val item = currentEntries.getOrNull(anchor) ?: return@awaitEachGesture
                            val base = currentSelection.toSet()
                            val selecting = item.path !in base
                            val endX = size.width - M3eFileSidePadding.toPx()
                            val hotspot = currentMode &&
                                down.position.x in (endX - 26.dp.toPx())..endX
                            var pointer = down.position
                            var scrolling: Job? = null
                            var dragging = false
                            try {
                                if (!hotspot) {
                                    val cancelled = withTimeoutOrNull(
                                        viewConfiguration.longPressTimeoutMillis,
                                    ) {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                            if (change == null || !change.pressed ||
                                                (change.position - down.position).getDistance() >
                                                viewConfiguration.touchSlop
                                            ) {
                                                return@withTimeoutOrNull true
                                            }
                                        }
                                        @Suppress("UNREACHABLE_CODE")
                                        null
                                    }
                                    if (cancelled != null) return@awaitEachGesture
                                    enterSelection(item)
                                }
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        if (dragging || !hotspot) change.consume()
                                        break
                                    }
                                    pointer = change.position
                                    if (!dragging &&
                                        (pointer - down.position).getDistance() <=
                                        viewConfiguration.touchSlop
                                    ) {
                                        continue
                                    }
                                    change.consume()
                                    if (!dragging) {
                                        dragging = true
                                        scrolling = scope.launch {
                                            val edge = 48.dp.toPx()
                                            while (true) {
                                                val height =
                                                    listState.layoutInfo.viewportEndOffset.toFloat()
                                                val delta = when {
                                                    pointer.y < edge ->
                                                        -((edge - pointer.y) / edge)
                                                            .coerceIn(0f, 1f) * 12.dp.toPx()
                                                    pointer.y > height - edge ->
                                                        ((pointer.y - height + edge) / edge)
                                                            .coerceIn(0f, 1f) * 12.dp.toPx()
                                                    else -> 0f
                                                }
                                                if (delta != 0f) {
                                                    listState.scrollBy(delta)
                                                    val visible =
                                                        listState.layoutInfo.visibleItemsInfo
                                                    val end = indexAt(pointer.y).takeIf { it >= 0 }
                                                        ?: (if (delta < 0f) {
                                                            visible.firstOrNull()?.index
                                                        } else {
                                                            visible.lastOrNull()?.index
                                                        })
                                                    if (end != null) {
                                                        setSelection(
                                                            fileRangeSelection(
                                                                currentEntries.map { it.path },
                                                                anchor,
                                                                end,
                                                                base,
                                                                selecting,
                                                            ),
                                                        )
                                                    }
                                                }
                                                delay(16)
                                            }
                                        }
                                    }
                                    val end = indexAt(pointer.y)
                                    if (end >= 0) {
                                        setSelection(
                                            fileRangeSelection(
                                                currentEntries.map { it.path },
                                                anchor,
                                                end,
                                                base,
                                                selecting,
                                            ),
                                        )
                                    }
                                }
                            } finally {
                                scrolling?.cancel()
                            }
                        }
                    },
            ) {
                itemsIndexed(
                    items = entries,
                    key = { _, entry -> entry.path },
                    contentType = { _, entry -> if (entry.directory) "directory" else "file" },
                ) { _, entry ->
                    M3eFileRow(
                        entry = entry,
                        selected = entry.path in selection,
                        selectionMode = selectionMode,
                        onClick = {
                            if (selectionMode) viewModel::toggleFileSelection else viewModel::openFile
                        },
                        onLongPress = { viewModel.longPressFileEntry(entry) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (entries.isEmpty()) {
                Text(
                    text = state.fileNote.ifEmpty { "这个目录是空的" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun M3eSortMenu(
    listOptions: com.zhizhu.zhicode.compose.model.FileListOptions,
    onSortChange: (FileSortKey, Boolean) -> Unit,
    onToggleDirection: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(
                painter = ZhiIcons.listCount,
                contentDescription = "排序与显示方式",
                modifier = Modifier.size(M3eDimens.IconSmall),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FileSortKey.entries.forEach { key ->
                DropdownMenuItem(
                    text = {
                        Text(
                            key.label + if (listOptions.sortKey == key) " ✓" else "",
                        )
                    },
                    onClick = {
                        open = false
                        onSortChange(key, listOptions.descending)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(if (listOptions.descending) "倒序 ✓" else "正序 ✓") },
                onClick = {
                    open = false
                    onToggleDirection()
                },
            )
        }
    }
}

@Composable
private fun M3eBreadcrumbBar(
    filePath: String,
    onNavigate: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val items = remember(filePath) { breadcrumbItems(filePath) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = M3eFileSidePadding, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { index, item ->
            val isLast = index == items.lastIndex
            Surface(
                shape = CircleShape,
                color = if (isLast) scheme.secondaryContainer else scheme.surfaceContainer,
                contentColor = if (isLast) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                onClick = { onNavigate(item.path) },
            ) {
                Text(
                    text = item.text.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (!isLast) {
                Icon(
                    painter = ZhiIcons.arrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(M3eDimens.IconSmall),
                )
            }
        }
    }
}

@Composable
private fun M3eFileRow(
    entry: FileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var dirCount by remember(entry.path, entry.modifiedAt) { mutableStateOf<Int?>(null) }
    if (entry.directory) {
        LaunchedEffect(entry.path, entry.modifiedAt) {
            dirCount = withContextIo { FileChildCount(entry.path) }
        }
    }
    val info = fileInfoLine(entry, System.currentTimeMillis(), dirCount)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = M3eFileRowMinHeight)
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(M3eDimens.SegmentInner + 8.dp),
        color = if (selected) scheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = M3eFileSidePadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = if (entry.directory) {
                            scheme.primaryContainer
                        } else {
                            scheme.surfaceContainerHigh
                        },
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = if (entry.directory) ZhiIcons.directory else ZhiIcons.file,
                    contentDescription = null,
                    tint = if (entry.directory) scheme.onPrimaryContainer else scheme.primary,
                    modifier = Modifier.size(M3eDimens.IconSmall),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (info.isNotEmpty()) {
                    Text(
                        text = info,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            } else if (entry.directory) {
                Icon(
                    painter = ZhiIcons.arrowRight,
                    contentDescription = "打开 ${entry.name}",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(M3eDimens.IconSmall),
                )
            }
        }
    }
    if (!selectionMode) {
        LaunchedEffect(entry.path) { /* 长按由 Surface 的 onLongClick 承担 */ }
    }
}

private suspend fun withContextIo(block: () -> Int?): Int? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }

private fun FileChildCount(path: String): Int? =
    runCatching {
        java.io.File(path).listFiles()?.size
    }.getOrNull()
