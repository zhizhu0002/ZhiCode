@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zhizhu.zhicode.compose.ui.panes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp


private val QuickActions = listOf(
    "粘贴",
    "复制选中内容",
    "重置终端",
    "新建会话",
    "重命名会话",
    "关闭会话",
    "结束 shell",
    "字体变小",
    "字体变大",
    "重载 termux.properties",
    "切换常亮锁",
)

/** 十一项快捷动作。 */
@Composable
internal fun TerminalQuickActionsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onAction: (Int) -> Unit,
) {
    if (!show) return
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = scheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = "终端操作",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
            QuickActions.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable {
                            onAction(index)
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                )
            }
        }
    }
}

/** 重命名会话。 */
@Composable
internal fun TerminalRenameDialog(
    show: Boolean,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // 只在重新打开时重置草稿：关闭动画期间保留最后输入，避免退场帧先闪回空串。
    var draft by remember { mutableStateOf(initial) }
    LaunchedEffect(show) {
        if (show) draft = initial
    }
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名会话") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("会话名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(draft) }) { Text("重命名") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
