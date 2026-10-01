package com.zhizhu.zhicode.compose.ui.dialogs

import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.settings.SettingsSubPage
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * Skill 管理窗口：列表 / 新建表单 / SKILL.md 编辑器共用一个弹窗。
 *
 * ## 这里的操作是"真的"
 *
 * 技能目录与引擎 `SkillTool` 的查找路径完全一致（项目级 `<project>/.zhicode/skills`、
 * 用户级 `$HOME/.zhicode/skills`），所以在这里建好的技能，Agent 下一次就能用 `Skill`
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
    if (state == null) return
    when {
        state.createForm != null -> {
            val draft = state.createForm
            SettingsSubPage(
                title = "新建 Skill",
                onBack = onCancelCreate,
                action = "创建" to (if (draft.saveable) onCreate else null),
            ) {
                SkillCreateForm(
                    draft = draft,
                    onChange = onCreateDraftChange,
                )
            }
        }
        state.editing != null -> SettingsSubPage(
            title = "编辑 " + state.editing.name,
            onBack = onCancelEdit,
            action = "保存" to onSave,
        ) {
            SkillEditor(
                target = state.editing,
                onBodyChange = onBodyChange,
            )
        }
        else -> SettingsSubPage(
            title = "Skill 管理器",
            onBack = onDismiss,
            action = "新建" to onNew,
        ) {
            SkillList(
                state = state,
                onEdit = onEdit,
                onAttach = onAttach,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun SkillList(
    state: SkillsState,
    onEdit: (SkillEntry) -> Unit,
    onAttach: (SkillEntry) -> Unit,
    onDelete: (SkillEntry) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        Text(
            text = "项目级 .zhicode/skills 与用户级 ~/.zhicode/skills（与引擎 Skill 工具的查找路径一致）",
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Micro,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        )

        if (state.skills.isEmpty()) {
            Text(
                text = "还没有技能。点「新建」填一个名称（如 code-review）并选存放位置，" +
                    "会生成一份带说明的 SKILL.md 模板；写好后 Agent 可通过 Skill 工具加载，" +
                    "也可以用这里的「附加」把内容直接带进下一步任务。",
                color = scheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Footnote,
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }

        // 整页模式下不限高，滚动交给外层 SettingsSubPage。
        Column {
        state.skills.forEach { skill ->
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
) {
    val scheme = MiuixTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        ZhiTextField(
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
                fontSize = ZhiTextScale.Footnote,
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
            fontSize = ZhiTextScale.Footnote,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun SkillEditor(
    target: SkillEditTarget,
    onBodyChange: (String) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        Text(
            text = target.path,
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Micro,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        )
        // 滚动由外层 SettingsSubPage 负责，这里不再套滚动容器。
        ZhiTextField(
            value = target.body,
            onValueChange = onBodyChange,
            label = "SKILL.md",
            useLabelAsPlaceholder = false,
            singleLine = false,
            minLines = 10,
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
        )
        Text(
            text = if (target.empty) "内容为空：保存后会生成一个空的 SKILL.md。"
            else "当前 ${target.body.length} 字。",
            color = if (target.empty) scheme.error else scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Footnote,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}
