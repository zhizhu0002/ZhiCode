package com.zhizhu.zhicode.compose.ui.dialogs

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.model.MemoryEditor
import com.zhizhu.zhicode.compose.model.MemoryFile
import com.zhizhu.zhicode.compose.model.MemoryState
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.settings.SettingsSubPage
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 记忆文件（`ZhiCode.md`）窗口：文件列表与编辑器共用一个弹窗。
 *
 * ## 说明文字必须诚实
 *
 * 引擎**不会**自动读 ZhiCode.md（`SystemPromptBuilder` 里没有相关引用，原版也一样）。
 * 所以顶部提示写的是"写入不会自动注入模型，它需要被读到"，而不是
 * "会自动作为记忆生效"——后者会让用户以为写完就生效了，然后困惑于模型为什么不遵守。
 */
@Composable
fun MemoryOverlay(
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
    if (editing == null) {
        SettingsSubPage(
            title = "记忆文件 · ZhiCode.md",
            onBack = onDismiss,
            action = "完善" to onRunInit,
        ) {
            MemoryFileList(
                state = state,
                onEdit = onEdit,
            )
        }
    } else {
        SettingsSubPage(
            title = editing.title,
            onBack = onCancelEdit,
            action = "保存" to onSave,
        ) {
            MemoryEditorBody(
                editing = editing,
                onBodyChange = onBodyChange,
            )
        }
    }
}

@Composable
private fun MemoryFileList(
    state: MemoryState,
    onEdit: (MemoryFile) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        Text(
            text = "项目级与用户级两份说明文件。写入后**不会**自动注入模型——" +
                "「让智蛛完善」会让 Agent 读取现有说明与构建清单后直接整理 ZhiCode.md；" +
                "平时的任务里 Agent 也可以自己用 Read 打开它。",
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Footnote,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )

        state.files.forEach { file ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                cornerRadius = ZhiRadius.card,
                insideMargin = PaddingValues(0.dp),
                colors = CardDefaults.defaultColors(
                    color = if (file.exists) scheme.surfaceContainerHigh else scheme.surfaceContainer,
                    contentColor = scheme.onBackground,
                ),
                pressFeedbackType = PressFeedbackType.None,
            ) {
                BasicComponent(
                    title = file.scope.label,
                    titleColor = BasicComponentDefaults.titleColor(
                        color = if (file.exists) scheme.onBackground else scheme.onSurfaceVariantSummary,
                    ),
                    summary = "${file.sizeLabel} · ${file.path}",
                    summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
                    endActions = {
                        // 未创建的文件也给编辑按钮：点进去就是一份空编辑器（新建）。
                        // 只画图标不给文字的话，用户不容易看出"这里可以创建"。
                        Text(
                            text = if (file.exists) "编辑" else "创建",
                            color = scheme.primary,
                            fontSize = ZhiTextScale.Caption,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    },
                    onClick = { onEdit(file) },
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun MemoryEditorBody(
    editing: MemoryEditor,
    onBodyChange: (String) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        Text(
            text = editing.path,
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Micro,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        )
        // 滚动由外层 SettingsSubPage 负责，这里不再套滚动容器。
        ZhiTextField(
            value = editing.body,
            onValueChange = onBodyChange,
            label = "ZhiCode.md",
            useLabelAsPlaceholder = false,
            singleLine = false,
            minLines = 12,
            modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
        )
        Text(
            text = if (editing.empty) {
                "内容为空。建议写清楚：构建/测试命令、目录结构、项目约定。"
            } else {
                "当前 ${editing.body.length} 字"
            },
            color = if (editing.empty) scheme.error else scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Footnote,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}
