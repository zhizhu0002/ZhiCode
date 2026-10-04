package com.zhizhu.zhicode.compose.ui.panes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.FileDeletePrompt
import com.zhizhu.zhicode.compose.model.FileEntry
import com.zhizhu.zhicode.compose.model.FileNameForm
import com.zhizhu.zhicode.compose.model.FileRoot
import com.zhizhu.zhicode.compose.model.OpenFile
import com.zhizhu.zhicode.compose.model.fileRangeSelection
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMaterialIcons
import com.zhizhu.zhicode.compose.ui.ZhiNoticeBar
import com.zhizhu.zhicode.compose.ui.ZhiNoticeTone
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    emptyNote: String = "",
    root: FileRoot = FileRoot.HOME,
    onSwitchRoot: (FileRoot) -> Unit = {},
    draft: String? = null,
    onStartEdit: () -> Unit = {},
    onDraftChange: (String) -> Unit = {},
    onSave: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    nameForm: FileNameForm? = null,
    onNewEntry: () -> Unit = {},
    onRename: (FileEntry) -> Unit = {},
    onNameDraftChange: (String) -> Unit = {},
    onSubmitName: (Boolean) -> Unit = {},
    onCancelName: () -> Unit = {},
    deletePrompt: FileDeletePrompt? = null,
    onConfirmDelete: () -> Unit = {},
    onCancelDelete: () -> Unit = {},
    sharedStorageGranted: Boolean = true,
    onGrantSharedStorage: () -> Unit = {},
    selection: Set<String> = emptySet(),
    selectionMode: Boolean = false,
    fileClipboardCount: Int = 0,
    fileClipboardMove: Boolean = false,
    onPasteFiles: () -> Unit = {},
    onLongPressEntry: (FileEntry) -> Unit = {},
    onToggleEntry: (FileEntry) -> Unit = {},
    onSetSelection: (Set<String>) -> Unit = {},
    onToggleSelectAll: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onRenameSelected: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onAttachSelected: () -> Unit = {},
    active: Boolean = true,
) {
    val effectiveSelectionMode = selectionMode || selection.isNotEmpty()
    BackHandler(active && effectiveSelectionMode, onClearSelection)
    Surface(modifier.fillMaxSize(), color = ZhiColors.panelSurface()) {
        Column(Modifier.fillMaxSize()) {
            FileRootSwitcher(root, onSwitchRoot)
            if (openFile == null) {
                FileBrowserList(
                    filePath, entries, root, emptyNote, sharedStorageGranted, onGrantSharedStorage,
                    selection, effectiveSelectionMode, fileClipboardCount, fileClipboardMove, onPasteFiles, onOpen, onUp, onNavigate, onNewEntry,
                    onLongPressEntry, onToggleEntry, onSetSelection, onToggleSelectAll, onClearSelection,
                )
            } else {
                FileEditor(openFile, draft, onDraftChange, onStartEdit, onSave, onCancelEdit, onCloseFile, onNavigate)
            }
        }
    }
    NewEntryDialog(nameForm?.takeIf { it.target == null }, onNameDraftChange, onSubmitName, onCancelName)
    RenameEntryDialog(nameForm?.takeIf { it.target != null }, onNameDraftChange, { onSubmitName(false) }, onCancelName)
    DeleteConfirmDialog(deletePrompt, onConfirmDelete, onCancelDelete)
}

@Composable
private fun FileBrowserList(
    filePath: String, entries: List<FileEntry>, root: FileRoot, emptyNote: String,
    sharedStorageGranted: Boolean, onGrantSharedStorage: () -> Unit,
    selection: Set<String>, selectionMode: Boolean,
    fileClipboardCount: Int, fileClipboardMove: Boolean, onPasteFiles: () -> Unit,
    onOpen: (FileEntry) -> Unit, onUp: () -> Unit, onNavigate: (String) -> Unit, onNewEntry: () -> Unit,
    onLongPressEntry: (FileEntry) -> Unit, onToggleEntry: (FileEntry) -> Unit,
    onSetSelection: (Set<String>) -> Unit, onToggleSelectAll: () -> Unit, onClearSelection: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentEntries by rememberUpdatedState(entries)
    val currentSelection by rememberUpdatedState(selection)
    val currentMode by rememberUpdatedState(selectionMode)
    val setSelection by rememberUpdatedState(onSetSelection)
    val enterSelection by rememberUpdatedState(onLongPressEntry)
    val allSelected = entries.isNotEmpty() && entries.all { it.path in selection }

    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = FileRowSidePadding), verticalAlignment = Alignment.CenterVertically) {
        if (selectionMode) {
            ZhiIconButton(ZhiIcons.close, "退出选择", onClearSelection, compact = 40.dp)
            Text("已选择 ${selection.size} 项", fontSize = ZhiTextScale.Body, modifier = Modifier.weight(1f))
            ZhiIconButton(ZhiIcons.listCount, if (allSelected) "取消全选" else "全选", onToggleSelectAll, compact = 40.dp)
        } else {
            Text(fileCountSummary(entries), fontSize = ZhiTextScale.Footnote, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.weight(1f))
            if (fileClipboardCount > 0) ZhiIconButton(ZhiIcons.directory, if (fileClipboardMove) "粘贴移动的 $fileClipboardCount 项" else "粘贴复制的 $fileClipboardCount 项", onPasteFiles, compact = 40.dp)
            Button(
                onClick = onNewEntry,
                minWidth = 68.dp,
                minHeight = 40.dp,
                cornerRadius = ButtonDefaults.CornerRadius,
                insideMargin = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary, contentColor = MiuixTheme.colorScheme.onPrimary),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(ZhiIcons.add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("新建", fontSize = ZhiTextScale.Caption)
                }
            }
            ZhiIconButton(ZhiIcons.back, "上一级目录", onUp, compact = 40.dp)
        }
    }
    FilePathBar(filePath, onNavigate)
    if (root == FileRoot.SHARED && !sharedStorageGranted) {
        ZhiNoticeBar(
            text = "共享存储需要「所有文件访问权限」",
            modifier = Modifier.padding(horizontal = 28.dp),
            tone = ZhiNoticeTone.WARN,
            content = { TextButton("去授权", onGrantSharedStorage) },
        )
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = if (selectionMode) 96.dp else 16.dp),
            modifier = Modifier.fillMaxSize().pointerInput(filePath) {
                fun indexAt(y: Float): Int = listState.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }?.index ?: -1
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val anchor = indexAt(down.position.y)
                    val item = currentEntries.getOrNull(anchor) ?: return@awaitEachGesture
                    val base = currentSelection.toSet()
                    val selecting = item.path !in base
                    val endX = size.width - FileRowSidePadding.toPx()
                    val hotspot = currentMode && down.position.x in (endX - 26.dp.toPx())..endX
                    var pointer = down.position
                    var scrolling: Job? = null
                    var dragging = false
                    try {
                        if (!hotspot) {
                            val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
                                }
                            }
                            if (cancelled != null) return@awaitEachGesture
                            enterSelection(item)
                        }
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) { if (dragging || !hotspot) change.consume(); break }
                            pointer = change.position
                            if (!dragging && (pointer - down.position).getDistance() <= viewConfiguration.touchSlop) continue
                            change.consume()
                            if (!dragging) {
                                dragging = true
                                scrolling = scope.launch {
                                    val edge = 48.dp.toPx()
                                    while (true) {
                                        val height = listState.layoutInfo.viewportEndOffset.toFloat()
                                        val delta = when {
                                            pointer.y < edge -> -((edge - pointer.y) / edge).coerceIn(0f, 1f) * 12.dp.toPx()
                                            pointer.y > height - edge -> ((pointer.y - height + edge) / edge).coerceIn(0f, 1f) * 12.dp.toPx()
                                            else -> 0f
                                        }
                                        if (delta != 0f) {
                                            listState.scrollBy(delta)
                                            val visible = listState.layoutInfo.visibleItemsInfo
                                            val end = indexAt(pointer.y).takeIf { it >= 0 } ?: (if (delta < 0f) visible.firstOrNull()?.index else visible.lastOrNull()?.index)
                                            if (end != null) setSelection(fileRangeSelection(currentEntries.map { it.path }, anchor, end, base, selecting))
                                        }
                                        delay(16)
                                    }
                                }
                            }
                            val end = indexAt(pointer.y)
                            if (end >= 0) setSelection(fileRangeSelection(currentEntries.map { it.path }, anchor, end, base, selecting))
                        }
                    } finally { scrolling?.cancel() }
                }
            },
        ) {
            itemsIndexed(entries, key = { _, entry -> entry.path }) { _, entry ->
                FileListRow(entry = entry, onOpen = { if (selectionMode) onToggleEntry(entry) else onOpen(entry) }, selected = entry.path in selection, selectionMode = selectionMode, modifier = Modifier.animateItem())
            }
        }
        if (entries.isEmpty()) Text(emptyNote.ifEmpty { "这个目录是空的" }, modifier = Modifier.align(Alignment.Center), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
internal fun FileSelectionToolbar(
    visible: Boolean, entries: List<FileEntry>, selection: Set<String>,
    onAttach: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
    onCopy: () -> Unit, onMove: () -> Unit, clipboardCount: Int, clipboardMove: Boolean, onPaste: () -> Unit,
    onSelectAll: () -> Unit, onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var details by remember { mutableStateOf<List<FileEntry>?>(null) }
    val allSelected = entries.isNotEmpty() && entries.all { it.path in selection }
    val more = DropdownEntry(items = listOf(
        DropdownItem("复制", enabled = selection.isNotEmpty(), onClick = onCopy),
        DropdownItem("移动", enabled = selection.isNotEmpty(), onClick = onMove),
        DropdownItem(if (clipboardCount == 0) "粘贴" else if (clipboardMove) "粘贴移动项（$clipboardCount）" else "粘贴副本（$clipboardCount）", enabled = clipboardCount > 0, onClick = onPaste),
        DropdownItem(if (allSelected) "取消全选" else "全选", onClick = onSelectAll),
        DropdownItem("复制路径", onClick = { clipboard.setText(AnnotatedString(selection.sorted().joinToString("\n"))) }),
        DropdownItem("详情", onClick = { details = entries.filter { it.path in selection } }),
        DropdownItem("退出选择", onClick = onDismiss),
    ))
    AnimatedVisibility(visible, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        FloatingToolbar {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FileToolbarAction(ZhiMaterialIcons.Add, "附加", onAttach, selection.isNotEmpty())
                FileToolbarAction(ZhiMaterialIcons.Edit, "重命名", onRename, selection.size == 1)
                FileToolbarAction(ZhiMaterialIcons.Delete, "删除", onDelete, selection.isNotEmpty())
                OverlayIconDropdownMenu(more, minHeight = 48.dp, minWidth = 48.dp) { Icon(ZhiMaterialIcons.MoreHoriz, "更多", modifier = Modifier.size(24.dp), tint = MiuixTheme.colorScheme.onSurfaceContainer) }
            }
        }
    }
    OverlayDialog(show = details != null && visible, onDismissRequest = { details = null }) {
        val shown = rememberLastNonNull(details)
        DialogShell("详情", actions = { PrimaryButton("确定", { details = null }) }) { Text(shown.orEmpty().joinToString("\n") { it.path }) }
    }
}

@Composable
private fun FileToolbarAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, enabled: Boolean) {
    Button(onClick = onClick, enabled = enabled, minWidth = 72.dp, minHeight = 64.dp, cornerRadius = ButtonDefaults.CornerRadius, insideMargin = PaddingValues(horizontal = 10.dp, vertical = 7.dp), colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.surfaceContainerHigh, contentColor = MiuixTheme.colorScheme.onSurfaceContainer)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(21.dp))
            Text(label, fontSize = ZhiTextScale.Caption, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

private data class LogRow(val index: Int, val raw: String, val time: String, val body: String, val status: String?)

private fun isLogFile(file: OpenFile): Boolean = file.name.endsWith(".log", ignoreCase = true) || file.name.contains("log", ignoreCase = true)

private fun parseLogRows(content: String): List<LogRow> = content.split('\n').mapIndexed { index, raw ->
    val match = Regex("^(\\d{2}-\\d{2}[^ ]*\\s+[^ ]+)\\s+(.*)$").find(raw)
    val time = match?.groupValues?.getOrNull(1).orEmpty()
    val body = match?.groupValues?.getOrNull(2) ?: raw
    val status = when {
        Regex("\\b(ok|success|done)\\b", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "ok"
        Regex("\\b(fail|error|exception)\\b", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "fail"
        else -> null
    }
    LogRow(index, raw, time, body, status)
}

@Composable
private fun FileEditor(file: OpenFile, draft: String?, onDraftChange: (String) -> Unit, onStartEdit: () -> Unit, onSave: () -> Unit, onCancelEdit: () -> Unit, onCloseFile: () -> Unit, onNavigate: (String) -> Unit) {
    val editing = draft != null
    val scheme = MiuixTheme.colorScheme
    var logLine by remember(file.path) { mutableStateOf<LogRow?>(null) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(file.name, color = scheme.onBackground, fontSize = ZhiTextScale.Subheading, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (editing) "${file.language.ifBlank { "文本" }} · 编辑中" else "${file.language.ifBlank { "文本" }} · ${if (isLogFile(file)) "日志预览" else "只读预览"}", color = scheme.onSurfaceVariantSummary, fontSize = ZhiTextScale.Caption)
                }
                if (editing) {
                    TextButton("放弃", onCancelEdit, minWidth = 54.dp, insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp))
                    Button(onClick = onSave, minWidth = 76.dp, minHeight = 40.dp, colors = ButtonDefaults.buttonColorsPrimary(), insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text("保存", fontSize = ZhiTextScale.Caption) }
                } else {
                    ZhiIconButton(ZhiIcons.close, "关闭文件", onCloseFile, compact = 40.dp)
                    Button(onClick = onStartEdit, minWidth = 72.dp, minHeight = 40.dp, insideMargin = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) { Text("编辑", fontSize = ZhiTextScale.Caption) }
                }
            }
        }
        FilePathBar(file.path, onNavigate)
        if (editing) FileEditSurface(draft.orEmpty(), onDraftChange, Modifier.weight(1f))
        else if (isLogFile(file)) LogPreviewSurface(file.content, onLineEdit = { logLine = it }, modifier = Modifier.weight(1f))
        else FilePreviewSurface(file.content, Modifier.weight(1f))
    }
    if (logLine != null) SingleLogLineEditor(logLine!!, onDismiss = { logLine = null }, onApply = { updated ->
        val rows = file.content.split('\n').toMutableList()
        rows[updated.index] = updated.raw
        onDraftChange(rows.joinToString("\n"))
        logLine = null
    })
}

@Composable
private fun FileEditorSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), shape = RoundedCornerShape(20.dp), color = MiuixTheme.colorScheme.surfaceContainer) { content() }
}

@Composable
private fun FilePreviewSurface(content: String, modifier: Modifier = Modifier) {
    val lines = remember(content) { content.split('\n') }
    FileEditorSurface(modifier) {
        LazyColumn(Modifier.fillMaxSize().padding(vertical = 10.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
            itemsIndexed(lines) { index, line -> CodeLine(index + 1, line) }
        }
    }
}

@Composable
private fun FileEditSurface(content: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    FileEditorSurface(modifier) {
        ZhiTextField(value = content, onValueChange = onChange, modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), minLines = 16, textStyle = MiuixTheme.textStyles.main.copy(fontFamily = FontFamily.Monospace), label = "编辑正文", useLabelAsPlaceholder = false)
    }
}

@Composable
private fun CodeLine(number: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(number.toString(), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = ZhiTextScale.Footnote, fontFamily = FontFamily.Monospace, modifier = Modifier.width(42.dp))
        SelectionContainer(Modifier.weight(1f)) { Text(text.ifEmpty { " " }, color = MiuixTheme.colorScheme.onSurface, fontSize = ZhiTextScale.Caption, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun LogPreviewSurface(content: String, onLineEdit: (LogRow) -> Unit, modifier: Modifier = Modifier) {
    val rows = remember(content) { parseLogRows(content) }
    FileEditorSurface(modifier) {
        LazyColumn(Modifier.fillMaxSize().padding(vertical = 8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
            itemsIndexed(rows, key = { _, row -> row.index }) { _, row ->
                Surface(onClick = { onLineEdit(row) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), shape = RoundedCornerShape(12.dp), color = MiuixTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Text((row.index + 1).toString(), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = ZhiTextScale.Footnote, fontFamily = FontFamily.Monospace, modifier = Modifier.width(34.dp))
                        Column(Modifier.weight(1f)) {
                            if (row.time.isNotBlank()) Text(row.time, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = ZhiTextScale.Micro, fontFamily = FontFamily.Monospace)
                            SelectionContainer { Text(row.body, color = MiuixTheme.colorScheme.onSurface, fontSize = ZhiTextScale.Caption, fontFamily = FontFamily.Monospace) }
                        }
                        if (row.status != null) Text(row.status, color = if (row.status == "ok") MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error, fontSize = ZhiTextScale.Micro, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleLogLineEditor(row: LogRow, onDismiss: () -> Unit, onApply: (LogRow) -> Unit) {
    var value by remember(row.index, row.raw) { mutableStateOf(row.raw) }
    OverlayBottomSheet(show = true, title = "编辑第 ${row.index + 1} 行", onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            ZhiTextField(value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = "日志内容", useLabelAsPlaceholder = false)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton("取消", onDismiss)
                Button(onClick = { onApply(row.copy(raw = value)) }, colors = ButtonDefaults.buttonColorsPrimary()) { Text("应用") }
            }
        }
    }
}
