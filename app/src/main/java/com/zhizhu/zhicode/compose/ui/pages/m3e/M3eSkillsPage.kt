package com.zhizhu.zhicode.compose.ui.pages.m3e

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.SkillCreateDraft
import com.zhizhu.zhicode.compose.model.SkillDetail
import com.zhizhu.zhicode.compose.model.SkillEntry
import com.zhizhu.zhicode.compose.model.SkillFile
import com.zhizhu.zhicode.compose.model.SkillFileDraft
import com.zhizhu.zhicode.compose.model.SkillFileNode
import com.zhizhu.zhicode.compose.model.SkillScope
import com.zhizhu.zhicode.compose.model.SkillUrlDraft
import com.zhizhu.zhicode.compose.model.SkillsState
import com.zhizhu.zhicode.compose.ui.ZhiIcons

@Composable
internal fun M3eSkillsPage(
    state: SkillsState?,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onNew: () -> Unit,
    onNewUrl: () -> Unit,
    onOpenDetail: (SkillEntry) -> Unit,
    onCloseDetail: () -> Unit,
    onEdit: (SkillEntry, String) -> Unit,
    onAttach: (SkillEntry) -> Unit,
    onDelete: (SkillEntry) -> Unit,
    onCreateDraftChange: ((SkillCreateDraft) -> SkillCreateDraft) -> Unit,
    onCreate: () -> Unit,
    onCancelCreate: () -> Unit,
    onBodyChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    onNewFile: () -> Unit,
    onFileDraftChange: ((SkillFileDraft) -> SkillFileDraft) -> Unit,
    onSaveFile: () -> Unit,
    onCancelFile: () -> Unit,
    onImportFile: (Uri) -> Unit,
    onDeleteFile: (String) -> Unit,
    onUrlDraftChange: ((SkillUrlDraft) -> SkillUrlDraft) -> Unit,
    onUrlImport: () -> Unit,
    onCancelUrl: () -> Unit,
) {
    if (state == null) return
    var deleteSkill by remember { mutableStateOf<SkillEntry?>(null) }
    var deleteFile by remember { mutableStateOf<SkillFile?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onImportFile) }
    val back = {
        when {
            state.editing != null -> if (state.editing.dirty) confirmDiscard = true else onCancelEdit()
            state.fileDraft != null -> onCancelFile()
            state.createForm != null -> onCancelCreate()
            state.urlDraft != null -> onCancelUrl()
            state.detail != null -> onCloseDetail()
            else -> onDismiss()
        }
    }
    BackHandler(enabled = true, onBack = back)
    M3eSubPageScaffold(
        title = state.detail?.entry?.name ?: "技能",
        onBack = back,
        actions = {
            if (state.detail != null) {
                TextButton(onClick = { state.detail?.entry?.let(onAttach) }) { Text("附加") }
                TextButton(onClick = onNewFile) { Text("新建文件") }
            } else {
                TextButton(onClick = onNew) { Text("新增") }
                TextButton(onClick = onNewUrl) { Text("导入") }
            }
        },
    ) { padding, _ ->
        if (state.detail == null) {
            SkillList(
                state = state,
                padding = padding,
                onQueryChange = onQueryChange,
                onOpenDetail = onOpenDetail,
                onEdit = onEdit,
                onAttach = onAttach,
                onDelete = { deleteSkill = it },
            )
        } else {
            SkillDetailContent(
                detail = state.detail,
                padding = padding,
                onEdit = onEdit,
                onDeleteFile = { deleteFile = it },
            )
        }
    }
    state.createForm?.let { draft ->
        SkillDialog(
            title = "新建技能", confirm = "创建", enabled = draft.saveable,
            onDismiss = onCancelCreate, onConfirm = onCreate,
        ) {
            ScopePicker(draft.scope) { scope -> onCreateDraftChange { it.copy(scope = scope) } }
            OutlinedTextField(
                value = draft.content,
                onValueChange = { value -> onCreateDraftChange { it.copy(content = value) } },
                label = { Text("SKILL.md") }, minLines = 10,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(draft.name.ifBlank { if (draft.nameMissing) "缺少 frontmatter name" else "" }, color = if (draft.nameInvalid || draft.nameMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    state.editing?.let { target ->
        SkillDialog(
            title = "编辑 ${target.relativePath}", confirm = "保存", enabled = target.saveable,
            onDismiss = back, onConfirm = onSave,
        ) {
            target.nameError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(
                value = target.body, onValueChange = onBodyChange,
                minLines = 12, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    state.fileDraft?.let { draft ->
        SkillDialog(
            title = "新建文件", confirm = "保存", enabled = draft.saveable,
            onDismiss = onCancelFile, onConfirm = onSaveFile,
        ) {
            OutlinedTextField(draft.fileName, { text -> onFileDraftChange { it.copy(fileName = text) } }, label = { Text("文件路径") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            draft.nameError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(draft.content, { text -> onFileDraftChange { it.copy(content = text) } }, label = { Text("内容") }, minLines = 8, modifier = Modifier.fillMaxWidth())
        }
    }
    state.urlDraft?.let { draft ->
        SkillDialog(
            title = "从 URL 导入", confirm = "导入", enabled = draft.ready,
            onDismiss = onCancelUrl, onConfirm = onUrlImport,
        ) {
            ScopePicker(draft.scope) { scope -> onUrlDraftChange { it.copy(scope = scope) } }
            OutlinedTextField(draft.url, { url -> onUrlDraftChange { it.copy(url = url) } }, label = { Text("SKILL.md 或 ZIP 地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (draft.loading) Text("正在下载…")
        }
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false }, title = { Text("放弃更改？") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onCancelEdit() }) { Text("放弃") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } },
    )
    deleteSkill?.let { skill -> AlertDialog(
        onDismissRequest = { deleteSkill = null }, title = { Text("删除 ${skill.name}？") },
        confirmButton = { TextButton(onClick = { deleteSkill = null; onDelete(skill) }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteSkill = null }) { Text("取消") } },
    ) }
    deleteFile?.let { file -> AlertDialog(
        onDismissRequest = { deleteFile = null }, title = { Text("删除 ${file.relativePath}？") },
        confirmButton = { TextButton(onClick = { deleteFile = null; onDeleteFile(file.relativePath) }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteFile = null }) { Text("取消") } },
    ) }
}

@Composable
private fun SkillList(
    state: SkillsState,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onQueryChange: (String) -> Unit,
    onOpenDetail: (SkillEntry) -> Unit,
    onEdit: (SkillEntry, String) -> Unit,
    onAttach: (SkillEntry) -> Unit,
    onDelete: (SkillEntry) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
        if (state.skills.isNotEmpty()) OutlinedTextField(
            value = state.query, onValueChange = onQueryChange,
            label = { Text("筛选技能") }, singleLine = true,
            leadingIcon = { Icon(ZhiIcons.search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when {
            state.loading -> Text("正在加载…", modifier = Modifier.padding(24.dp))
            state.visibleSkills.isEmpty() -> Text(if (state.query.isBlank()) "暂无技能" else "没有匹配的技能", modifier = Modifier.padding(24.dp))
            else -> SkillScope.entries.forEach { scope ->
                val skills = state.visibleSkills.filter { it.scope == scope }
                if (skills.isNotEmpty()) {
                    M3eSectionHeader(scope.label)
                    M3eSegmentedColumn {
                        skills.forEachIndexed { index, skill ->
                            M3eSegmentedItem(index, skills.size) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(skill.name, style = MaterialTheme.typography.titleMedium)
                                            Text(skill.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(skill.sizeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        TextButton(onClick = { onEdit(skill, "SKILL.md") }) { Text("编辑") }
                                        TextButton(onClick = { onAttach(skill) }) { Text("附加") }
                                        TextButton(onClick = { onDelete(skill) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                                    }
                                    TextButton(onClick = { onOpenDetail(skill) }) { Text("查看文件") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillDetailContent(
    detail: SkillDetail,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onEdit: (SkillEntry, String) -> Unit,
    onDeleteFile: (SkillFile) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
        if (detail.body.isNotBlank()) {
            M3eSectionHeader("SKILL.md")
            M3eSegmentedColumn {
                M3eSegmentedItem(0, 1, onClick = { onEdit(detail.entry, "SKILL.md") }) {
                    Text(detail.body, modifier = Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        M3eSectionHeader("文件")
        M3eSegmentedColumn {
            SkillTree(detail.tree, detail.entry, 0, onEdit, onDeleteFile)
        }
    }
}

@Composable
private fun SkillTree(
    nodes: List<SkillFileNode>,
    entry: SkillEntry,
    depth: Int,
    onEdit: (SkillEntry, String) -> Unit,
    onDeleteFile: (SkillFile) -> Unit,
) {
    nodes.forEach { node ->
        when (node) {
            is SkillFileNode.DirNode -> {
                var expanded by rememberSaveable(node.relativePath) { mutableStateOf(false) }
                M3eSegmentedItem(0, 1, onClick = { expanded = !expanded }) {
                    Text("${if (expanded) "⌄" else "›"}  ${node.name}", modifier = Modifier.fillMaxWidth().padding(start = (16 + depth * 16).dp, top = 12.dp, bottom = 12.dp))
                }
                if (expanded) SkillTree(node.children, entry, depth + 1, onEdit, onDeleteFile)
            }
            is SkillFileNode.FileNode -> M3eSegmentedItem(0, 1) {
                Row(Modifier.fillMaxWidth().padding(start = (16 + depth * 16).dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(node.file.relativePath, color = if (node.file.primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(node.file.sizeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onEdit(entry, node.file.relativePath) }) { Text("编辑") }
                    if (!node.file.primary) TextButton(onClick = { onDeleteFile(node.file) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun ScopePicker(scope: SkillScope, onChange: (SkillScope) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SkillScope.entries.forEach { option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = option == scope, onCheckedChange = { onChange(option) })
                Text(option.label)
            }
        }
    }
}

@Composable
private fun SkillDialog(
    title: String,
    confirm: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } },
        confirmButton = { TextButton(enabled = enabled, onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
