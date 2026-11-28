package com.zhizhu.zhicode.compose.ui.pages.miuix

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.RoleCard
import com.zhizhu.zhicode.compose.model.RoleCardEditor
import com.zhizhu.zhicode.compose.model.RoleCardsState
import com.zhizhu.zhicode.compose.ui.ZhiFieldError
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsLoadingHint
import com.zhizhu.zhicode.compose.ui.settings.SettingsPageKey
import com.zhizhu.zhicode.compose.ui.settings.SettingsPageStack
import com.zhizhu.zhicode.compose.ui.settings.SettingsSubPage
import com.zhizhu.zhicode.compose.ui.rememberActionBusy
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RoleCardsPage(
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
    // 退出动画期间离场页仍在绘制，那时 editor 已经是 null —— 见 rememberLastNonNull 的说明。
    val shownEditor = rememberLastNonNull(editor)

    // 同上：有未保存改动时返回先问一次（判定在 RoleCardEditor.dirty）。
    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = editor?.dirty == true
    val requestExit: () -> Unit = {
        if (dirty) confirmDiscard = true else onCancelEditor()
    }

    SettingsPageStack(
        // 路径含最底下那一页：栈要靠整条路径算层级与方向。
        path = listOf(
            SettingsPageKey("roleCards.list", 0),
            if (editor != null) SettingsPageKey("roleCards.editor", 1) else null,
        ).filterNotNull(),
        onBack = if (editor == null) onDismiss else requestExit,
    ) { key ->
        // ⚠️ 分支必须看**正在渲染的那一页**（key），不能看当前状态。
        val open = if (key.id == "roleCards.editor") shownEditor else null
        if (open == null) {
            SettingsSubPage(
                title = "自定义角色卡",
                onBack = onDismiss,
                action = "新建" to onNew,
            ) {
                RoleCardList(
                    state = state,
                    onEdit = onEdit,
                    onSelect = onSelect,
                    onDisable = onDisable,
                    onDelete = onDelete,
                )
            }
        } else {
            SettingsSubPage(
                title = if (open.isEditing) "编辑角色卡" else "新建角色卡",
                onBack = requestExit,
                action = "保存" to (if (open.saveable) onSave else null),
                actionEnabled = !rememberActionBusy("roleCards.save"),
            ) {
                RoleCardEditorForm(
                    editor = open,
                    onChange = onDraftChange,
                )
            }
        }
    }

    UnsavedChangesDialog(
        visible = confirmDiscard,
        onKeepEditing = { confirmDiscard = false },
        onDiscard = {
            confirmDiscard = false
            onCancelEditor()
        },
    )
}

@Composable
private fun RoleCardList(
    state: RoleCardsState,
    onEdit: (RoleCard) -> Unit,
    onSelect: (RoleCard) -> Unit,
    onDisable: () -> Unit,
    onDelete: (RoleCard) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column {
        Text(
            text = "启用中的角色卡会作为 <role_card> 块随每一次系统提示词发送。" +
                "同一时刻只有一张生效；它不能覆盖应用安全规则、权限模式或 Root 限制。",
            color = scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )

        if (state.loading) {
            SettingsGroup("角色卡") {
                // 载荷还在 IO 上读（见 openRoleCards）：「暂时为空」与「本来就没有」
                // 都是空列表，不区分就会把「正在读」误报成「还没建过」。
                SettingsLoadingHint()
            }
        } else if (state.cards.isEmpty()) {
            SettingsGroup("角色卡") {
                Text(
                    text = "还没有角色卡。点「新建」写一段人设指令（例如固定的回答风格、必须遵守的" +
                        "工作流程），保存后会立即启用。",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        } else {
            state.cards.forEach { card ->
                val active = card.id == state.activeId
                val titleColor by animateColorAsState(
                    targetValue = if (active) scheme.primary else scheme.onBackground,
                    animationSpec = ZhiMotion.colorSpec,
                    label = "roleCardTitle",
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    cornerRadius = com.zhizhu.zhicode.compose.theme.ZhiRadius.card,
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    colors = CardDefaults.defaultColors(
                        color = if (active) {
                            com.zhizhu.zhicode.compose.theme.ZhiColors.cardSurface()
                        } else {
                            scheme.surfaceContainer
                        },
                        contentColor = scheme.onSurface,
                    ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        /* 勾选框**就是**启用开关（点它 = 启用这张 / 停用这张）。 */
                        Checkbox(
                            state = if (active) ToggleableState.On else ToggleableState.Off,
                            onClick = {
                                if (active) onDisable() else onSelect(card)
                            },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = card.name,
                                color = titleColor,
                                fontSize = MiuixTheme.textStyles.body1.fontSize,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (card.summary.isNotBlank()) {
                                Text(
                                    text = card.summary,
                                    color = scheme.onSurfaceVariantSummary,
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        ZhiIconButton(
                            icon = ZhiIcons.edit,
                            description = "编辑",
                            onClick = { onEdit(card) },
                            iconSize = 15.dp,
                        )
                        ZhiIconButton(
                            icon = ZhiIcons.close,
                            description = "删除",
                            onClick = { onDelete(card) },
                            iconSize = 15.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleCardEditorForm(
    editor: RoleCardEditor,
    onChange: ((RoleCardEditor) -> RoleCardEditor) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    // 见 ZhiFieldError：没碰过的字段不飘红（一打开表单就红一片像页面坏了）。
    var nameTouched by remember(editor.id) { mutableStateOf(false) }
    Column {
        // 与设置主页同形态：表单收进分组卡。
        SettingsGroup("基本信息") {
            ZhiTextField(
                value = editor.name,
                onValueChange = { value ->
                    nameTouched = true
                    onChange { it.copy(name = value) }
                },
                label = "角色名称",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            ZhiFieldError(message = editor.nameError, touched = nameTouched)
        }

        SettingsGroup("角色卡内容") {
            ZhiTextField(
                value = editor.content,
                onValueChange = { value -> onChange { it.copy(content = value) } },
                label = "角色卡内容",
                useLabelAsPlaceholder = true,
                singleLine = false,
                minLines = 8,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        Text(
            text = "从下一完整任务生效。请勿填写 API 密钥——这段文本会进入每一次请求。",
            color = scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
