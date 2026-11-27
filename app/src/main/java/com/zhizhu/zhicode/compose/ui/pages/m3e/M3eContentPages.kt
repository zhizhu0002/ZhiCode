package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.MemoryFile
import com.zhizhu.zhicode.compose.model.MemoryState
import com.zhizhu.zhicode.compose.model.PromptSlot
import com.zhizhu.zhicode.compose.model.RoleCard
import com.zhizhu.zhicode.compose.model.RoleCardEditor
import com.zhizhu.zhicode.compose.model.RoleCardsState
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.rememberActionBusy

@Composable
internal fun M3ePromptsPage(
    initPrompt: String,
    summaryPrompt: String,
    compactPromptHead: String,
    onDismiss: () -> Unit,
    onChange: (PromptSlot, String) -> Unit,
    onReset: (PromptSlot) -> Unit,
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = PromptSlot.byId(editingId.orEmpty())
    val custom = mapOf(
        PromptSlot.INIT to initPrompt,
        PromptSlot.COMPACT_SYSTEM to summaryPrompt,
        PromptSlot.COMPACT_HEAD to compactPromptHead,
    )
    val goBack = { if (editing != null) editingId = null else onDismiss() }
    BackHandler(enabled = editing != null, onBack = goBack)

    M3eSubPageScaffold(title = editing?.title ?: "提示词", onBack = goBack) { padding, _ ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (editing == null) {
                Text(
                    text = "这三项影响对应任务的措辞，主系统提示不开放编辑。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                M3eSectionHeader("可自定义")
                M3eSegmentedColumn {
                    PromptSlot.entries.forEachIndexed { index, slot ->
                        val value = custom[slot]
                        M3eNavigationRow(
                            index = index,
                            count = PromptSlot.entries.size,
                            title = slot.title,
                            summary = PromptSlot.preview(slot.resolve(value)),
                            valueText = if (value.isNullOrBlank()) "默认" else "已自定义",
                            onClick = { editingId = slot.id },
                        )
                    }
                }
            } else {
                val value = custom[editing]
                Text(editing.detail, modifier = Modifier.padding(horizontal = 16.dp))
                M3eSectionHeader("可用变量")
                Text(
                    text = editing.variables,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                M3eSectionHeader("正文")
                OutlinedTextField(
                    value = editing.resolve(value),
                    onValueChange = { onChange(editing, it) },
                    label = { Text(editing.title) },
                    minLines = 10,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                TextButton(
                    onClick = { onReset(editing) },
                    enabled = !value.isNullOrBlank(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text("恢复默认") }
            }
        }
    }
}

@Composable
internal fun M3eRoleCardsPage(
    state: RoleCardsState?,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onEdit: (RoleCard) -> Unit,
    onSelect: (RoleCard) -> Unit,
    onDisable: () -> Unit,
    onDelete: (RoleCard) -> Unit,
    onDraftChange: ((RoleCardEditor) -> RoleCardEditor) -> Unit,
    onSave: () -> Unit,
    onCancelEditor: () -> Unit,
) {
    if (state == null) return
    val editor = state.editor
    var confirmDiscard by remember { mutableStateOf(false) }
    val requestBack = {
        if (editor == null) onDismiss()
        else if (editor.dirty) confirmDiscard = true
        else onCancelEditor()
    }
    BackHandler(enabled = editor != null, onBack = requestBack)
    val saving = rememberActionBusy("roleCards.save")
    M3eSubPageScaffold(
        title = if (editor == null) "自定义角色卡" else if (editor.isEditing) "编辑角色卡" else "新建角色卡",
        onBack = requestBack,
        actions = {
            if (editor == null) {
                TextButton(onClick = onNew) { Text("新建") }
            } else {
                TextButton(onClick = onSave, enabled = editor.saveable && !saving) { Text("保存") }
            }
        },
    ) { padding, _ ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (editor == null) {
                Text(
                    text = "启用中的角色卡会随系统提示词发送，同一时刻只有一张生效。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                M3eSectionHeader("角色卡")
                if (state.loading || state.cards.isEmpty()) {
                    Text(
                        if (state.loading) "正在加载…" else "还没有角色卡",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                } else {
                    M3eSegmentedColumn {
                        state.cards.forEachIndexed { index, card ->
                            val active = card.id == state.activeId
                            M3eSegmentedItem(index = index, count = state.cards.size) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = active,
                                        onCheckedChange = { if (active) onDisable() else onSelect(card) },
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(card.name, style = MaterialTheme.typography.titleMedium)
                                        if (card.summary.isNotBlank()) {
                                            Text(card.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                        }
                                    }
                                    IconButton(onClick = { onEdit(card) }) {
                                        Icon(ZhiIcons.edit, contentDescription = "编辑")
                                    }
                                    IconButton(onClick = { onDelete(card) }) {
                                        Icon(ZhiIcons.close, contentDescription = "删除")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                var nameTouched by remember(editor.id) { mutableStateOf(false) }
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { value ->
                        nameTouched = true
                        onDraftChange { it.copy(name = value) }
                    },
                    label = { Text("角色名称") },
                    isError = nameTouched && editor.nameError != null,
                    supportingText = if (nameTouched && editor.nameError != null) {
                        { Text(editor.nameError.orEmpty()) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                OutlinedTextField(
                    value = editor.content,
                    onValueChange = { value -> onDraftChange { it.copy(content = value) } },
                    label = { Text("角色卡内容") },
                    minLines = 8,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Text(
                    text = "从下一完整任务生效。这段文本会进入每一次请求。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃未保存的修改？") },
            confirmButton = {
                Button(onClick = {
                    confirmDiscard = false
                    onCancelEditor()
                }) { Text("放弃修改") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") }
            },
        )
    }
}

@Composable
internal fun M3eMemoryPage(
    state: MemoryState?,
    onDismiss: () -> Unit,
    onEdit: (MemoryFile) -> Unit,
    onRunInit: () -> Unit,
    onBodyChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    if (state == null) return
    val editing = state.editing
    var confirmDiscard by remember { mutableStateOf(false) }
    val requestBack = {
        if (editing == null) onDismiss()
        else if (editing.dirty) confirmDiscard = true
        else onCancelEdit()
    }
    BackHandler(enabled = editing != null, onBack = requestBack)
    val saving = rememberActionBusy("memory.save")

    M3eSubPageScaffold(
        title = editing?.title ?: "记忆文件 · ZhiCode.md",
        onBack = requestBack,
        actions = {
            if (editing == null) {
                TextButton(onClick = onRunInit) { Text("完善") }
            } else {
                TextButton(onClick = onSave, enabled = !saving) { Text("保存") }
            }
        },
    ) { padding, _ ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (editing == null) {
                Text(
                    text = "项目级与用户级说明文件。让 ZhiCode 完善时，Agent 会读取现有说明并整理文件。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                M3eSectionHeader("说明文件")
                if (state.files.isEmpty()) {
                    Text("正在加载…", modifier = Modifier.padding(horizontal = 16.dp))
                } else {
                    M3eSegmentedColumn {
                        state.files.forEachIndexed { index, file ->
                            M3eNavigationRow(
                                index = index,
                                count = state.files.size,
                                title = file.scope.label,
                                summary = "${file.sizeLabel} · ${file.path}",
                                valueText = if (file.exists) "编辑" else "创建",
                                onClick = { onEdit(file) },
                            )
                        }
                    }
                }
            } else {
                Text(editing.path, modifier = Modifier.padding(horizontal = 16.dp))
                OutlinedTextField(
                    value = editing.body,
                    onValueChange = onBodyChange,
                    label = { Text("ZhiCode.md") },
                    minLines = 12,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Text(
                    text = if (editing.empty) "内容为空" else "当前 ${editing.body.length} 字",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃未保存的修改？") },
            confirmButton = {
                Button(onClick = {
                    confirmDiscard = false
                    onCancelEdit()
                }) { Text("放弃修改") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") }
            },
        )
    }
}
