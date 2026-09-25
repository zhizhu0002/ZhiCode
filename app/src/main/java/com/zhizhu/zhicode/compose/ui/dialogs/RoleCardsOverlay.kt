package com.zhizhu.zhicode.compose.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.model.RoleCard
import com.zhizhu.zhicode.compose.model.RoleCardEditor
import com.zhizhu.zhicode.compose.model.RoleCardsState
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 角色卡管理窗口。
 *
 * 与 API 配置 / MCP / 技能同一套形态：列表页与编辑页共用一个弹窗。
 *
 * ## 这里和"自定义头部提示词"的分工
 *
 * 两者都会被拼进系统提示词，但用途不同：
 * - 自定义头部提示词是**单份**的全局附加指令；
 * - 角色卡是**可切换的多份**人设，同一时刻只有一张生效（也可以一张都不启用）。
 *
 * 界面上要让人一眼看出"哪张正在生效"，所以启用中的那条打勾并高亮。
 */
@Composable
fun RoleCardsOverlay(
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
    OverlayDialog(
        show = state != null,
        onDismissRequest = onDismiss,
        largeScreen = true,
        maxWidth = 640.dp,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        val current = state ?: return@OverlayDialog
        val editor = current.editor
        if (editor == null) {
            RoleCardList(
                state = current,
                onNew = onNew,
                onEdit = onEdit,
                onSelect = onSelect,
                onDisable = onDisable,
                onDelete = onDelete,
                onClose = onDismiss,
            )
        } else {
            RoleCardEditorForm(
                editor = editor,
                onChange = onDraftChange,
                onSave = onSave,
                onCancel = onCancelEditor,
            )
        }
    }
}

@Composable
private fun RoleCardList(
    state: RoleCardsState,
    onNew: () -> Unit,
    onEdit: (RoleCard) -> Unit,
    onSelect: (RoleCard) -> Unit,
    onDisable: () -> Unit,
    onDelete: (RoleCard) -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    DialogShell(
        title = "自定义角色卡",
        groupBody = true,
        actions = {
            SecondaryButton(text = "关闭", onClick = onClose)
            PrimaryButton(text = "新建", onClick = onNew, modifier = Modifier.padding(start = 8.dp))
        },
    ) {
        Text(
            text = "启用中的角色卡会作为 <role_card> 块随每一次系统提示词发送。" +
                "同一时刻只有一张生效；它不能覆盖应用安全规则、权限模式或 Root 限制。",
            color = scheme.onSurfaceVariantSummary,
            fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )

        if (state.cards.isEmpty()) {
            Text(
                text = "还没有角色卡。点「新建」写一段人设指令（例如固定的回答风格、必须遵守的" +
                    "工作流程），保存后会立即启用。",
                color = scheme.onSurfaceVariantSummary,
                fontSize = 10.5.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            return@DialogShell
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
            items(state.cards, key = { it.id }) { card ->
                val active = card.id == state.activeId
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    cornerRadius = ZhiRadius.card,
                    insideMargin = PaddingValues(0.dp),
                    colors = CardDefaults.defaultColors(
                        color = if (active) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh,
                        contentColor = scheme.onBackground,
                    ),
                    pressFeedbackType = PressFeedbackType.None,
                ) {
                    BasicComponent(
                        title = card.name,
                        titleColor = BasicComponentDefaults.titleColor(
                            color = if (active) scheme.primary else scheme.onBackground,
                        ),
                        summary = card.summary,
                        summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
                        startAction = {
                            // 未启用的留一个空位保持左对齐一致（与 API 配置列表同样的处理）。
                            if (active) Checkbox(state = ToggleableState.On, onClick = {}, modifier = Modifier.size(20.dp))
                        },
                        endActions = {
                            ZhiIconButton(icon = ZhiIcons.edit, description = "编辑", onClick = { onEdit(card) }, iconSize = 15.dp)
                            ZhiIconButton(icon = ZhiIcons.close, description = "删除", onClick = { onDelete(card) }, iconSize = 15.dp)
                        },
                        // 点一下就在"启用这张"之间切换；点已启用的那张不做停用（停用有单独按钮，
                        // 避免误触把正在用的角色卡关掉却以为是切换到了别的）。
                        onClick = { if (!active) onSelect(card) },
                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }

        if (state.activeId.isNotEmpty()) {
            SecondaryButton(
                text = "停用当前角色卡",
                onClick = onDisable,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun RoleCardEditorForm(
    editor: RoleCardEditor,
    onChange: ((RoleCardEditor) -> RoleCardEditor) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    DialogShell(
        title = if (editor.isEditing) "编辑角色卡" else "新建角色卡",
        groupBody = true,
        actions = {
            SecondaryButton(text = "取消", onClick = onCancel)
            PrimaryButton(
                text = "保存并启用",
                enabled = editor.saveable,
                onClick = onSave,
                modifier = Modifier.padding(start = 8.dp),
            )
        },
    ) {
        TextField(
            value = editor.name,
            onValueChange = { value -> onChange { it.copy(name = value) } },
            label = "角色名称",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        editor.nameError?.let { error ->
            Text(
                text = error,
                color = scheme.error,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = editor.content,
                onValueChange = { value -> onChange { it.copy(content = value) } },
                label = "角色卡内容",
                useLabelAsPlaceholder = true,
                singleLine = false,
                minLines = 8,
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).padding(top = 8.dp),
            )
        }

        Text(
            text = "从下一完整任务生效。请勿填写 API 密钥——这段文本会进入每一次请求。",
            color = scheme.onSurfaceVariantSummary,
            fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}
