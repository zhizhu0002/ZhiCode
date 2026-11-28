package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.UbuntuMirrorSource
import com.zhizhu.zhicode.compose.model.AgentTask
import com.zhizhu.zhicode.compose.model.AttachBrowserState
import com.zhizhu.zhicode.compose.model.ChoicePickerState
import com.zhizhu.zhicode.compose.model.CopySheetState
import com.zhizhu.zhicode.compose.model.ExportSheetState
import com.zhizhu.zhicode.compose.model.FileEntry
import com.zhizhu.zhicode.compose.model.FileRoot
import com.zhizhu.zhicode.compose.model.TaskState
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.floatingNavSheetInset
import com.zhizhu.zhicode.compose.ui.ZhiMarkdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun M3eExportOverlay(
    sheet: ExportSheetState?,
    onDismiss: () -> Unit,
    onIncludeReasoningChange: (Boolean) -> Unit,
    onExpandReasoningChange: (Boolean) -> Unit,
    onExportMarkdown: () -> Unit,
    onExportImage: () -> Unit,
    onSelectMore: () -> Unit,
) {
    if (sheet == null) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp + floatingNavSheetInset()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(sheet.title, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("包含思考过程", Modifier.weight(1f))
                Switch(sheet.includeReasoning, onIncludeReasoningChange)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("图片展开思考", Modifier.weight(1f), color = if (sheet.includeReasoning) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(sheet.expandReasoning, onExpandReasoningChange, enabled = sheet.includeReasoning)
            }
            M3eSectionHeader("导出为")
            M3eSegmentedColumn {
                M3eNavigationRow(0, 2, "Markdown", summary = "保存为 .md 文件", onClick = onExportMarkdown)
                M3eNavigationRow(1, 2, "图片", summary = "保存为 PNG 图片", onClick = onExportImage)
            }
            if (sheet.singleSource) OutlinedButton(onClick = onSelectMore, modifier = Modifier.fillMaxWidth()) { Text("选择更多消息") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun M3eCopyOverlay(sheet: CopySheetState?, onDismiss: () -> Unit, onCopyAll: () -> Unit) {
    if (sheet == null) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp + floatingNavSheetInset())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(sheet.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onCopyAll) { Text("复制全部") }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                SelectionContainer {
                    Text(
                        sheet.text,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun M3eChoiceOverlay(
    picker: ChoicePickerState?,
    onSubmit: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
    onSubmitFreeForm: (String) -> Unit,
) {
    if (picker == null) return
    var freeForm by remember(picker) { mutableStateOf("") }
    var single by remember(picker) { mutableStateOf(picker.options.indexOfFirst { it.checked }) }
    var multiple by remember(picker) { mutableStateOf(picker.options.indices.filter { picker.options[it].checked }.toSet()) }
    val selected = if (picker.multiSelect) multiple else if (single >= 0) setOf(single) else emptySet()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(picker.title) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (picker.prompt.isNotBlank()) Text(picker.prompt, style = MaterialTheme.typography.bodyLarge)
                picker.options.forEachIndexed { index, option ->
                    val checked = index in selected && freeForm.isBlank()
                    Surface(
                        color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().toggleable(value = checked, onValueChange = {
                            freeForm = ""
                            if (picker.multiSelect) multiple = if (index in multiple) multiple - index else multiple + index
                            else single = index
                        }),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (picker.multiSelect) Checkbox(checked, onCheckedChange = null) else RadioButton(checked, onClick = null)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(option.label)
                                if (option.detail.isNotBlank()) Text(option.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (picker.allowFreeForm) OutlinedTextField(freeForm, { freeForm = it }, label = { Text(picker.freeFormHint) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = freeForm.isNotBlank() || selected.isNotEmpty(),
                onClick = { if (freeForm.isNotBlank()) onSubmitFreeForm(freeForm) else onSubmit(selected.sorted()) },
            ) { Text(picker.submitLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(picker.cancelLabel) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun M3eTaskOverlay(tasks: List<AgentTask>, open: Boolean, onDismiss: () -> Unit) {
    if (!open) return
    val complete = tasks.count { it.state == TaskState.DONE }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 560.dp).padding(bottom = 12.dp + floatingNavSheetInset())) {
            Text("$complete / ${tasks.size} 已完成", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks) { task ->
                    M3eSegmentedItem(0, 1) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when (task.state) {
                                    // M3E LoadingIndicator：形状持续变形，非匀速转圈
                                    TaskState.RUNNING -> androidx.compose.material3.LoadingIndicator(
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                    TaskState.DONE -> androidx.compose.material3.Icon(ZhiIcons.done, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    TaskState.PENDING -> androidx.compose.material3.Icon(ZhiIcons.pending, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(task.title, style = MaterialTheme.typography.titleMedium)
                            }
                            if (task.detail.isNotBlank()) ZhiMarkdown(task.detail, modifier = Modifier.padding(start = 28.dp, top = 6.dp))
                        }
                    }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("完成") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun M3eEnvironmentOverlay(
    open: Boolean,
    report: String,
    runtimeReady: Boolean,
    installing: Boolean,
    progress: Int,
    message: String,
    selectedMirrorId: String,
    onSelectMirror: (String) -> Unit,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onRepair: () -> Unit,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
) {
    if (!open) return
    val selected = UbuntuMirrorSource.findById(selectedMirrorId)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("环境自检") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ubuntu 24.04 · ${if (runtimeReady) "已就绪" else "未安装"}", style = MaterialTheme.typography.titleMedium)
                if (!installing) UbuntuMirrorSource.entries.forEach { mirror ->
                    Row(Modifier.fillMaxWidth().toggleable(value = selected == mirror, onValueChange = { onSelectMirror(mirror.id) }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == mirror, onClick = null)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(mirror.displayName)
                            Text(mirror.summary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (installing) {
                    // M3E 波浪形进度条，波峰随进度推进
                    androidx.compose.material3.LinearWavyProgressIndicator(
                        progress = { progress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("$progress% · $message")
                } else if (message.isNotBlank()) Text(message)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("诊断报告", modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefresh) { Text("重新检测") }
                    if (runtimeReady) TextButton(onClick = onRepair) { Text("修复") }
                }
                SelectionContainer {
                    Text(report.ifBlank { "（无内容）" }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onInstall, enabled = !installing && (runtimeReady || selected != null)) { Text(if (runtimeReady) "重新安装" else "安装") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCopy) { Text("复制报告") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun M3eAttachOverlay(
    open: Boolean,
    browser: AttachBrowserState,
    onFilterChange: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onSwitchRoot: (FileRoot) -> Unit,
    onPick: (FileEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 600.dp).padding(bottom = 8.dp + floatingNavSheetInset())) {
            Text("附加项目文件", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FileRoot.entries.forEach { root ->
                    if (browser.root == root) Button(onClick = { onSwitchRoot(root) }) { Text(root.label) }
                    else OutlinedButton(onClick = { onSwitchRoot(root) }) { Text(root.label) }
                }
            }
            val parts = browser.path.split('/').filter { it.isNotBlank() }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onSwitchRoot(browser.root) }) { Text(browser.root.label) }
                var prefix = ""
                parts.forEach { part ->
                    prefix += "/$part"
                    val target = prefix
                    Text("/")
                    TextButton(onClick = { onNavigate(target) }) { Text(part) }
                }
            }
            OutlinedTextField(browser.filter, onFilterChange, label = { Text("过滤当前目录") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (browser.note.isNotBlank()) Text(browser.note, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.weight(1f, fill = false).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(browser.visibleEntries, key = { it.path }) { entry ->
                    M3eSegmentedItem(0, 1, onClick = { if (entry.directory) onNavigate(entry.path) else onPick(entry) }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(if (entry.directory) ZhiIcons.directory else ZhiIcons.file, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(entry.name, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            if (!entry.directory) Text(entry.size.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (browser.visibleEntries.isEmpty()) item {
                    Text(
                        when {
                            browser.filter.isNotBlank() -> "没有匹配文件"
                            browser.note.isNotBlank() -> browser.note
                            else -> "目录为空"
                        },
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun M3eOverlayHost(state: WorkspaceUiState, viewModel: WorkspaceViewModel) {
    M3eExportOverlay(
        sheet = state.exportSheet,
        onDismiss = viewModel::dismissExportSheet,
        onIncludeReasoningChange = viewModel::setExportIncludeReasoning,
        onExpandReasoningChange = viewModel::setExportExpandReasoning,
        onExportMarkdown = viewModel::exportSheetAsMarkdown,
        onExportImage = viewModel::exportSheetAsImage,
        onSelectMore = viewModel::exportSelectMore,
    )
    M3eCopyOverlay(state.copySheet, viewModel::closeCopySheet, viewModel::copySheetAll)
    M3eChoiceOverlay(state.choicePicker?.takeUnless { it.isActionMenu }, viewModel::onSubmitSelection, viewModel::closeChoicePicker, viewModel::onSubmitFreeForm)
    M3eAttachOverlay(state.attachPickerOpen, state.attachBrowser, viewModel::updateAttachFilter, viewModel::attachNavigateTo, viewModel::attachSwitchRoot, { viewModel.attachProjectFile(it.path) }, viewModel::closeAttachPicker)
    M3eTaskOverlay(state.tasks, state.taskListOpen, viewModel::closeTaskList)
    M3eEnvironmentOverlay(
        open = state.environmentOpen,
        report = state.environmentReport,
        runtimeReady = state.runtimeReady,
        installing = state.runtimeInstalling,
        progress = state.runtimeProgress,
        message = state.runtimeMessage,
        selectedMirrorId = state.ubuntuMirrorId,
        onSelectMirror = viewModel::selectUbuntuMirror,
        onDismiss = viewModel::closeEnvironment,
        onInstall = viewModel::installRuntime,
        onRepair = viewModel::repairRuntime,
        onRefresh = viewModel::refreshEnvironmentReport,
        onCopy = viewModel::copyEnvironmentReport,
    )
}
