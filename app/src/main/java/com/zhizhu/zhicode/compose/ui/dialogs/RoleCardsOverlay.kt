package com.zhizhu.zhicode.compose.ui.dialogs

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.RoleCard
import com.zhizhu.zhicode.compose.model.RoleCardEditor
import com.zhizhu.zhicode.compose.model.RoleCardsState
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsPageKey
import com.zhizhu.zhicode.compose.ui.settings.SettingsPageStack
import com.zhizhu.zhicode.compose.ui.settings.SettingsSubPage
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    if (state == null) return
    val editor = state.editor
    // 退出动画期间离场页仍在绘制，那时 editor 已经是 null —— 见 rememberLastNonNull 的说明。
    val shownEditor = rememberLastNonNull(editor)
    SettingsPageStack(
        // 路径含最底下那一页：栈要靠整条路径算层级与方向。
        path = listOf(
            SettingsPageKey("roleCards.list", 0),
            if (editor != null) SettingsPageKey("roleCards.editor", 1) else null,
        ).filterNotNull(),
        onBack = if (editor == null) onDismiss else onCancelEditor,
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
                onBack = onCancelEditor,
                action = "保存" to (if (open.saveable) onSave else null),
            ) {
                RoleCardEditorForm(
                    editor = open,
                    onChange = onDraftChange,
                )
            }
        }
    }
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
            fontSize = ZhiTextScale.Footnote,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )

        // 与设置主页同形态：一张分组卡里若干行，每行不再各套一张卡。
        // （整页模式下不限高，滚动交给外层 SettingsSubPage。）
        SettingsGroup("角色卡") {
            if (state.cards.isEmpty()) {
                Text(
                    text = "还没有角色卡。点「新建」写一段人设指令（例如固定的回答风格、必须遵守的" +
                        "工作流程），保存后会立即启用。",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Footnote,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            } else {
                state.cards.forEach { card ->
                    val active = card.id == state.activeId
                    BasicComponent(
                        title = card.name,
                        titleColor = BasicComponentDefaults.titleColor(
                            color = if (active) scheme.primary else scheme.onBackground,
                        ),
                        summary = card.summary,
                        summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
                        startAction = {
                            // 未启用的留一个空位保持左对齐一致（与 API 配置列表同样的处理）。
                            // 选中标记用 Check 图标而非 Checkbox，理由见 Dialogs.kt 同一处。
                            if (active) {
                                Icon(
                                    imageVector = MiuixIcons.Basic.Check,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(DropdownDefaults.CheckIconSize),
                                )
                            }
                        },
                        endActions = {
                            ZhiIconButton(icon = ZhiIcons.edit, description = "编辑", onClick = { onEdit(card) }, iconSize = 15.dp)
                            ZhiIconButton(icon = ZhiIcons.close, description = "删除", onClick = { onDelete(card) }, iconSize = 15.dp)
                        },
                        // 点一下就在"启用这张"之间切换；点已启用的那张不做停用（停用有单独按钮，
                        // 避免误触把正在用的角色卡关掉却以为是切换到了别的）。
                        onClick = { if (!active) onSelect(card) },
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }

        if (state.activeId.isNotEmpty()) {
            // 整页模式下「停用」放列表尾（原生 TextButton，与顶栏动作同族）
            TextButton(
                text = "停用当前角色卡",
                onClick = onDisable,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun RoleCardEditorForm(
    editor: RoleCardEditor,
    onChange: ((RoleCardEditor) -> RoleCardEditor) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column {
        // 与设置主页同形态：表单收进分组卡。
        SettingsGroup("基本信息") {
            ZhiTextField(
                value = editor.name,
                onValueChange = { value -> onChange { it.copy(name = value) } },
                label = "角色名称",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            editor.nameError?.let { error ->
                Text(
                    text = error,
                    color = scheme.error,
                    fontSize = ZhiTextScale.Footnote,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
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
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .heightIn(min = 180.dp),
            )
        }

        Text(
            text = "从下一完整任务生效。请勿填写 API 密钥——这段文本会进入每一次请求。",
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Footnote,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
