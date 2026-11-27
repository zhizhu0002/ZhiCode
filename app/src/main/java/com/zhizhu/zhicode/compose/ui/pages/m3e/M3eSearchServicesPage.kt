package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.SearchFieldName
import com.zhizhu.zhicode.compose.model.SearchService
import com.zhizhu.zhicode.compose.model.SearchServiceDraft
import com.zhizhu.zhicode.compose.model.SearchServiceType
import com.zhizhu.zhicode.compose.model.SearchServicesState
import com.zhizhu.zhicode.compose.ui.ZhiIcons

@Composable
internal fun M3eSearchServicesPage(
    state: SearchServicesState?,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onEdit: (SearchService) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDraftChange: ((SearchServiceDraft) -> SearchServiceDraft) -> Unit,
    onSave: () -> Unit,
    onCancelForm: () -> Unit,
) {
    if (state == null) return
    val draft = state.form
    val goBack = { if (draft == null) onDismiss() else onCancelForm() }
    BackHandler(enabled = draft != null, onBack = goBack)
    var choosingType by remember { mutableStateOf(false) }

    M3eSubPageScaffold(
        title = if (draft == null) "搜索服务" else if (draft.isEditing) "编辑搜索服务" else "新增搜索服务",
        onBack = goBack,
        actions = {
            if (draft == null) {
                TextButton(onClick = onNew) { Text("新增") }
            } else {
                TextButton(onClick = onSave, enabled = draft.saveable) { Text("保存") }
            }
        },
    ) { padding, _ ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (draft == null) {
                M3eSectionHeader("已添加的服务")
                if (state.loading || state.services.isEmpty()) {
                    Text(
                        if (state.loading) "正在加载…" else "服务列表为空，搜索会使用内置后端。",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                } else {
                    M3eSegmentedColumn {
                        state.services.forEachIndexed { index, service ->
                            M3eSegmentedItem(
                                index, state.services.size,
                                onClick = { onSelect(service.id) },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = service.id == state.activeId,
                                        enabled = service.enabled,
                                        onCheckedChange = { if (service.enabled) onSelect(service.id) },
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(service.name, style = MaterialTheme.typography.titleMedium)
                                        Text(service.subtitle, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    IconButton(onClick = { onEdit(service) }) {
                                        Icon(ZhiIcons.edit, contentDescription = "编辑")
                                    }
                                    IconButton(onClick = { onDelete(service.id) }) {
                                        Icon(ZhiIcons.close, contentDescription = "删除")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                M3eSectionHeader("基本信息")
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { value -> onDraftChange { it.copy(name = value) } },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                M3eSegmentedColumn {
                    M3eNavigationRow(
                        index = 0, count = 2, title = "服务类型", valueText = draft.type.label,
                        summary = draft.type.detail, onClick = { choosingType = true },
                    )
                    M3eSwitchRow(
                        index = 1, count = 2, title = "启用", checked = draft.enabled,
                        onCheckedChange = { enabled -> onDraftChange { it.copy(enabled = enabled) } },
                    )
                }
                M3eSectionHeader("连接与选项")
                draft.type.keyUrl?.let { url ->
                    Text("申请地址：$url", modifier = Modifier.padding(horizontal = 16.dp))
                }
                draft.type.fields.forEach { field ->
                    val value = if (field.name == SearchFieldName.API_KEY) draft.apiKey
                        else draft.config[field.name].orEmpty()
                    if (field.options.isNotEmpty()) {
                        M3eSegmentedColumn {
                            M3eChoiceRow(
                                index = 0, count = 1, title = field.label, options = field.options,
                                selectedIndex = field.options.indexOf(value).coerceAtLeast(0),
                                summary = field.hint,
                                onSelect = { selected ->
                                    onDraftChange { it.copy(config = it.config + (field.name to field.options[selected])) }
                                },
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { text ->
                                onDraftChange {
                                    if (field.name == SearchFieldName.API_KEY) it.copy(apiKey = text)
                                    else it.copy(config = it.config + (field.name to text))
                                }
                            },
                            label = { Text(field.label) },
                            supportingText = if (field.hint.isBlank()) null else ({ Text(field.hint) }),
                            visualTransformation = if (field.secret) PasswordVisualTransformation() else VisualTransformation.None,
                            singleLine = field.name != SearchFieldName.HEADERS,
                            minLines = if (field.name == SearchFieldName.HEADERS) 3 else 1,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                }
                listOfNotNull(draft.nameError, draft.fieldError, draft.keyError).forEach { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                }
                if (draft.isEditing) {
                    TextButton(onClick = { draft.id?.let(onDelete) }, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("删除这条服务", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (choosingType && draft != null) {
        AlertDialog(
            onDismissRequest = { choosingType = false },
            title = { Text("服务类型") },
            text = {
                Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    SearchServiceType.entries.forEach { type ->
                        TextButton(
                            onClick = {
                                choosingType = false
                                onDraftChange {
                                    it.copy(
                                        type = type,
                                        config = it.config.filterKeys { key -> type.fields.any { field -> field.name == key } },
                                        apiKey = "",
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(type.label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingType = false }) { Text("取消") } },
        )
    }
}
