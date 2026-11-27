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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.McpConfigState
import com.zhizhu.zhicode.compose.model.McpScope
import com.zhizhu.zhicode.compose.model.McpServer
import com.zhizhu.zhicode.compose.model.McpServerDraft
import com.zhizhu.zhicode.compose.model.McpType
import com.zhizhu.zhicode.compose.ui.rememberActionBusy

@Composable
internal fun M3eMcpConfigPage(
    config: McpConfigState?,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onEdit: (McpServer) -> Unit,
    onToggle: (McpServer) -> Unit,
    onDelete: (McpServer) -> Unit,
    onDraftChange: ((McpServerDraft) -> McpServerDraft) -> Unit,
    onSave: () -> Unit,
    onCancelForm: () -> Unit,
    onTest: (String) -> Unit,
    onToolOptionsChange: (String, String, Boolean, Boolean) -> Unit,
    onOpenImport: () -> Unit,
    onImportTextChange: (String) -> Unit,
    onImportConfirm: () -> Unit,
    onImportCancel: () -> Unit,
    onTestAll: () -> Unit,
    onQueryChange: (String) -> Unit,
) {
    if (config == null) return
    val draft = config.form
    var discard by remember { mutableStateOf(false) }
    val back = {
        if (draft == null) onDismiss()
        else if (draft.dirty) discard = true
        else onCancelForm()
    }
    BackHandler(enabled = draft != null, onBack = back)
    val saving = rememberActionBusy("mcp.save")
    M3eSubPageScaffold(
        title = if (draft == null) "MCP 服务器" else if (draft.isEditing) "编辑 MCP 服务器" else "添加 MCP 服务器",
        onBack = back,
        actions = {
            if (draft == null) {
                TextButton(onClick = onOpenImport) { Text("导入") }
                TextButton(onClick = onNew) { Text("新增") }
            } else {
                TextButton(onClick = onSave, enabled = draft.saveable && !saving) { Text("保存") }
            }
        },
    ) { padding, _ ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (draft == null) {
                OutlinedTextField(
                    value = config.query, onValueChange = onQueryChange,
                    label = { Text("搜索服务器") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                TextButton(
                    onClick = onTestAll,
                    enabled = config.enabledCount > 0 && config.testing.isEmpty(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text("全部测试") }
                if (config.loading) {
                    Text("正在加载…", modifier = Modifier.padding(horizontal = 16.dp))
                } else if (config.visibleServers.isEmpty()) {
                    Text("没有服务器", modifier = Modifier.padding(horizontal = 16.dp))
                } else {
                    M3eSegmentedColumn {
                        config.visibleServers.forEachIndexed { index, server ->
                            val status = config.status[server.name]
                            M3eSegmentedItem(index, config.visibleServers.size) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(server.name, style = MaterialTheme.typography.titleMedium)
                                            Text(server.summary, style = MaterialTheme.typography.bodyMedium)
                                            if (server.name in config.testing) Text("正在测试…")
                                            else if (status != null) Text(if (status.connected) "已连接" else status.error)
                                        }
                                        Switch(checked = server.enabled, onCheckedChange = { onToggle(server) })
                                    }
                                    Row {
                                        TextButton(onClick = { onTest(server.name) }, enabled = server.name !in config.testing) { Text("测试") }
                                        TextButton(onClick = { onEdit(server) }) { Text("编辑") }
                                        TextButton(onClick = { onDelete(server) }) { Text("删除") }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                M3eSectionHeader("连接")
                McpField("名称", draft.name, { value -> onDraftChange { it.copy(name = value) } })
                M3eSegmentedColumn {
                    M3eChoiceRow(
                        0, 3, "连接方式", McpType.entries.map { it.label }, McpType.entries.indexOf(draft.type),
                        onSelect = { index -> onDraftChange { it.copy(type = McpType.entries[index]) } },
                    )
                    M3eChoiceRow(
                        1, 3, "作用范围", McpScope.entries.map { it.label }, McpScope.entries.indexOf(draft.scope),
                        onSelect = { index -> onDraftChange { it.copy(scope = McpScope.entries[index]) } },
                    )
                    M3eSwitchRow(2, 3, "启用", draft.enabled, { value -> onDraftChange { it.copy(enabled = value) } })
                }
                if (draft.type.needsCommand) {
                    McpField("命令", draft.command, { value -> onDraftChange { it.copy(command = value) } })
                    McpField("参数（每行一个）", draft.argsText, { value -> onDraftChange { it.copy(argsText = value) } }, true)
                    McpField("环境变量 JSON", draft.envText, { value -> onDraftChange { it.copy(envText = value) } }, true)
                } else {
                    McpField("URL", draft.url, { value -> onDraftChange { it.copy(url = value) } })
                    McpField("请求头 JSON", draft.headersText, { value -> onDraftChange { it.copy(headersText = value) } }, true)
                }
                listOfNotNull(draft.nameError, draft.commandError, draft.urlError, draft.envError, draft.headersError)
                    .forEach { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
                val serverName = draft.originalName ?: draft.name
                val status = config.status[draft.name] ?: config.status[serverName]
                if (draft.isEditing) {
                    TextButton(
                        onClick = { onTest(serverName) },
                        enabled = serverName !in config.testing,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) { Text("测试连接") }
                }
                if (status != null) {
                    M3eSectionHeader("工具")
                    if (!status.connected) Text(status.error, modifier = Modifier.padding(horizontal = 16.dp))
                    status.tools.forEach { tool ->
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Text(tool.name, style = MaterialTheme.typography.titleMedium)
                            if (tool.description.isNotBlank()) Text(tool.description)
                            if (tool.parameters.isNotEmpty()) Text(tool.parameters.joinToString(", "))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("启用", modifier = Modifier.weight(1f))
                                Switch(tool.enabled, { onToolOptionsChange(serverName, tool.name, it, tool.approval) })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("需要审批", modifier = Modifier.weight(1f))
                                Switch(tool.approval, { onToolOptionsChange(serverName, tool.name, tool.enabled, it) })
                            }
                        }
                    }
                }
            }
        }
    }
    if (discard) {
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text("放弃未保存的修改？") },
            confirmButton = { Button(onClick = { discard = false; onCancelForm() }) { Text("放弃修改") } },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } },
        )
    }
    if (config.importText != null) {
        AlertDialog(
            onDismissRequest = onImportCancel,
            title = { Text("导入 MCP JSON") },
            text = {
                Column {
                    OutlinedTextField(
                        value = config.importText, onValueChange = onImportTextChange,
                        label = { Text("JSON") }, minLines = 6,
                    )
                    config.importError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = onImportConfirm) { Text("导入") } },
            dismissButton = { TextButton(onClick = onImportCancel) { Text("取消") } },
        )
    }
}

@Composable
private fun McpField(label: String, value: String, onChange: (String) -> Unit, multiline: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = !multiline, minLines = if (multiline) 3 else 1,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}
