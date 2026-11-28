@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.FileEntry
import com.zhizhu.zhicode.compose.ui.ZhiIcons

/**
 * M3E 文件多选工具条。
 *
 * 形态取 M3E 的 Surface + 圆角，动作按重要性分两组：
 * 高频（附加 / 复制 / 移动 / 粘贴）直接摆出来，低频（重命名 / 删除 / 全选 / 详情）收进溢出菜单。
 */
@Composable
internal fun M3eFileSelectionToolbar(
    visible: Boolean,
    entries: List<FileEntry>,
    selection: Set<String>,
    onAttach: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    clipboardCount: Int,
    clipboardMove: Boolean,
    onPaste: () -> Unit,
    onSelectAll: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val scheme = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    var detailOpen by remember { mutableStateOf(false) }
    val allSelected = entries.isNotEmpty() && entries.all { it.path in selection }
    val attachable = selection.isNotEmpty() &&
        entries.filter { it.path in selection }.all { !it.directory }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                M3eToolbarAction(ZhiIcons.add, "附加", attachable, onAttach)
                M3eToolbarAction(ZhiIcons.file, "复制", selection.isNotEmpty(), onCopy)
                M3eToolbarAction(ZhiIcons.directory, "移动", selection.isNotEmpty(), onMove)
                M3eToolbarAction(ZhiIcons.paste, "粘贴", clipboardCount > 0, onPaste)
            }
            Box {
                TextButton(onClick = { menuOpen = true }) {
                    Icon(
                        painter = ZhiIcons.more,
                        contentDescription = "更多操作",
                        modifier = Modifier.size(M3eDimens.IconSmall),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        enabled = selection.size == 1,
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = scheme.error) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (allSelected) "取消全选" else "全选") },
                        onClick = { menuOpen = false; onSelectAll() },
                    )
                    DropdownMenuItem(
                        text = { Text("复制路径") },
                        onClick = {
                            menuOpen = false
                            clipboard.setText(
                                AnnotatedString(selection.sorted().joinToString("\n")),
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("详情") },
                        onClick = { menuOpen = false; detailOpen = true },
                    )
                    DropdownMenuItem(
                        text = { Text("退出选择") },
                        onClick = { menuOpen = false; onDismiss() },
                    )
                }
            }
        }
    }

    if (detailOpen) {
        val paths = entries.filter { it.path in selection }
        AlertDialog(
            onDismissRequest = { detailOpen = false },
            title = { Text("详情") },
            text = {
                Text(
                    text = paths.joinToString("\n") { it.path },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(onClick = { detailOpen = false }) { Text("确定") }
            },
        )
    }
}

@Composable
private fun M3eToolbarAction(
    icon: androidx.compose.ui.graphics.painter.Painter,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(
            painter = icon,
            contentDescription = label,
            modifier = Modifier.size(M3eDimens.IconSmall),
            tint = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
        )
    }
}
