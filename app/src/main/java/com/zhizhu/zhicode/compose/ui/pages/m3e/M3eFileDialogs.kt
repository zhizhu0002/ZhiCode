@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.FileDeletePrompt
import com.zhizhu.zhicode.compose.model.FileNameForm

/** 新建文件 / 文件夹。 */
@Composable
internal fun M3eNewEntryDialog(
    form: FileNameForm?,
    onDraftChange: (String) -> Unit,
    onSubmit: (directory: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    if (form == null) return
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(form.title) },
        text = { M3eNameField(form, onDraftChange, "名称") },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) { Text("取消") }
                Button(onClick = { onSubmit(true) }, enabled = form.saveable) {
                    Text("文件夹")
                }
                Button(onClick = { onSubmit(false) }, enabled = form.saveable) {
                    Text("文件")
                }
            }
        },
    )
}

/** 重命名。 */
@Composable
internal fun M3eRenameEntryDialog(
    form: FileNameForm?,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    if (form == null) return
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(form.title) },
        text = { M3eNameField(form, onDraftChange, "新名字") },
        confirmButton = {
            Button(onClick = onSubmit, enabled = form.saveable) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}

@Composable
private fun M3eNameField(
    form: FileNameForm,
    onDraftChange: (String) -> Unit,
    placeholder: String,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = form.draft,
            onValueChange = onDraftChange,
            singleLine = true,
            label = { Text(placeholder) },
            isError = (form.draft.isNotEmpty() || form.failure != null) && form.error != null,
            modifier = Modifier.fillMaxWidth(),
        )
        val error = form.error
        if (error != null && (form.draft.isNotEmpty() || form.failure != null)) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** 删除确认。 */
@Composable
internal fun M3eDeleteConfirmDialog(
    prompt: FileDeletePrompt?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    if (prompt == null) return
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("删除") },
        text = {
            Text(
                text = if (prompt.destructive) {
                    "删除${prompt.label}？\n其中的 ${prompt.count - prompt.entries.size} 项内容会一起消失，无法撤销。"
                } else {
                    "删除${prompt.label}？无法撤销。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (prompt.destructive) scheme.error else scheme.onSurface,
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("删除", color = scheme.onErrorContainer)
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}
