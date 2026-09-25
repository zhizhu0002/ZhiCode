package com.zhizhu.zhicode.compose.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.model.SkillCreateDraft
import com.zhizhu.zhicode.compose.model.SkillEditTarget
import com.zhizhu.zhicode.compose.model.SkillEntry
import com.zhizhu.zhicode.compose.model.SkillScope
import com.zhizhu.zhicode.compose.model.SkillsState
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * Skill 管理窗口：列表 / 新建表单 / SKILL.md 编辑器共用一个弹窗。
 *
 * ## 这里的操作是"真的"
 *
 * 技能目录与引擎 `SkillTool` 的查找路径完全一致（项目级 `<project>/.iq/skills`、
 * 用户级 `$HOME/.iq/skills`），所以在这里建好的技能，Agent 下一次就能用 `Skill`
 * 工具加载；「附加」则把内容直接拼进下一条请求。
 */
@Composable
fun SkillsOverlay(
    state: SkillsState?,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onEdit: (SkillEntry) -> Unit,
    onAttach: (SkillEntry) -> Unit,
    onDelete: (SkillEntry) -> Unit,
    onCreateDraftChange: ((SkillCreateDraft) -> SkillCreateDraft) -> Unit,
    onCreate: () -> Unit,
    onCancelCreate: () -> Unit,
    onBodyChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
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
        when {
            current.createForm != null -> SkillCreateForm(
                draft = current.createForm,
                onChange = onCreateDraftChange,
                onCreate = onCreate,
                onCancel = onCancelCreate,
            )
            current.editing != null -> SkillEditor(
                target = current.editing,
                onBodyChange = onBodyChange,
                onSave = onSave,
                onCancel = onCancelEdit,
            )
            else -> SkillList(
                state = current,
                onNew = onNew,
                onEdit = onEdit,
                onAttach = onAttach,
                onDelete = onDelete,
                onClose = onDismiss,
            )
        }
    }
}

@Composable
private fun SkillList(
    state: SkillsState,
    onNew: () -> Unit,
    onEdit: (SkillEntry) -> Unit,
    onAttach: (SkillEntry) -> Unit,
    onDelete: (SkillEntry) -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    DialogShell(
        title = "Skill 管理器",
        groupBody = true,
        actions = {
            SecondaryButton(text = "关闭", onClick = onClose)
            PrimaryButton(text = "新建", onClick = onNew, modifier = Modifier.padding(start = 8.dp))
        },
    ) {
        Text(
            text = "项目级 .iq/skills 与用户级 ~/.iq/skills（与引擎 Skill 工具的查找路径一致）",
            color = scheme.onSurfaceVariantSummary,
            fontSize = 9.5.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        )

        if (state.skills.isEmpty()) {
            Text(
                text = "还没有技能。点「新建」填一个名称（如 code-review）并选存放位置，" +
                    "会生成一份带说明的 SKILL.md 模板；写好后 Agent 可通过 Skill 工具加载，" +
                    "也可以用这里的「附加」把内容直接带进下一步任务。",
                color = scheme.onSurfaceVariantSummary,
                fontSize = 10.5.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            return@DialogShell
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
            items(state.skills, key = { "${it.scope}-${it.name}" }) { skill ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    cornerRadius = ZhiRadius.card,
                    insideMargin = PaddingValues(0.dp),
                    colors = CardDefaults.defaultColors(
                        color = scheme.surfaceContainerHigh,
                        contentColor = scheme.onBackground,
                    ),
                    pressFeedbackType = PressFeedbackType.None,
                ) {
                    BasicComponent(
                        title = skill.name,
                        titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
                        summary = "${skill.scope.label} · ${skill.summary} · ${skill.sizeLabel}",
                        summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
                        endActions = {
                            // 「附加」是这里最常用的动作，放第一位。
                            ZhiIconButton(
                                icon = ZhiIcons.floatingBall,
                                description = "附加到下一步任务",
                                onClick = { onAttach(skill) },
                                iconSize = 15.dp,
                            )
                            ZhiIconButton(
                                icon = ZhiIcons.edit,
                                description = "编辑",
                                onClick = { onEdit(skill) },
                                iconSize = 15.dp,
                            )
                            ZhiIconButton(
                                icon = ZhiIcons.close,
                                description = "删除",
                                onClick = { onDelete(skill) },
                                iconSize = 15.dp,
                            )
                        },
                        onClick = { onEdit(skill) },
                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillCreateForm(
    draft: SkillCreateDraft,
    onChange: ((SkillCreateDraft) -> SkillCreateDraft) -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    DialogShell(
        title = "新建 Skill",
        groupBody = true,
        actions = {
            SecondaryButton(text = "取消", onClick = onCancel)
            PrimaryButton(
                text = "创建并编辑",
                enabled = draft.saveable,
                onClick = onCreate,
                modifier = Modifier.padding(start = 8.dp),
            )
        },
    ) {
        TextField(
            value = draft.name,
            onValueChange = { value -> onChange { it.copy(name = value) } },
            label = "名称",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        draft.nameError?.let { error ->
            Text(
                text = error,
                color = scheme.error,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }

        OverlayDropdownPreference(
            items = SkillScope.entries.map { it.label },
            selectedIndex = SkillScope.entries.indexOf(draft.scope).coerceAtLeast(0),
            title = "保存位置",
            summary = null,
            onSelectedIndexChange = { index ->
                SkillScope.entries.getOrNull(index)?.let { scope -> onChange { it.copy(scope = scope) } }
            },
        )

        Text(
            text = "名称会成为目录名，只能包含字母、数字、. _ -。" +
                "如果同名技能已存在，不会覆盖它，而是直接打开现有内容编辑。",
            color = scheme.onSurfaceVariantSummary,
            fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun SkillEditor(
    target: SkillEditTarget,
    onBodyChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    DialogShell(
        title = "编辑 ${target.name}",
        groupBody = true,
        actions = {
            SecondaryButton(text = "取消", onClick = onCancel)
            PrimaryButton(text = "保存", onClick = onSave, modifier = Modifier.padding(start = 8.dp))
        },
    ) {
        Text(
            text = target.path,
            color = scheme.onSurfaceVariantSummary,
            fontSize = 9.5.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        )
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            TextField(
                value = target.body,
                onValueChange = onBodyChange,
                label = "SKILL.md",
                useLabelAsPlaceholder = false,
                singleLine = false,
                minLines = 10,
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
            )
        }
        Text(
            text = if (target.empty) "内容为空：保存后会生成一个空的 SKILL.md。"
            else "当前 ${target.body.length} 字。",
            color = if (target.empty) scheme.error else scheme.onSurfaceVariantSummary,
            fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}
