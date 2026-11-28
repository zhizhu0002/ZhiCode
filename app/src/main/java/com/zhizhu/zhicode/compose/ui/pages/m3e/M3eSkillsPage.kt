@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.zhizhu.zhicode.compose.ui.pages.m3e

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.data.SkillStore
import com.zhizhu.zhicode.compose.model.SkillCreateDraft
import com.zhizhu.zhicode.compose.model.SkillDetail
import com.zhizhu.zhicode.compose.model.SkillEditTarget
import com.zhizhu.zhicode.compose.model.SkillEntry
import com.zhizhu.zhicode.compose.model.SkillFile
import com.zhizhu.zhicode.compose.model.SkillFileDraft
import com.zhizhu.zhicode.compose.model.SkillFileNode
import com.zhizhu.zhicode.compose.model.SkillScope
import com.zhizhu.zhicode.compose.model.SkillsState
import com.zhizhu.zhicode.compose.model.SkillUrlDraft
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.rememberActionBusy
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull

private const val SkillPreviewLines = 20

private val SkillImportMimeTypes = arrayOf(
    listOf("text", "*").joinToString("/"),
    "application/octet-stream",
    "application/zip",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun M3eSkillsPage(
    state: SkillsState?,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onNew: () -> Unit,
    onNewUrl: () -> Unit,
    onOpenDetail: (SkillEntry) -> Unit,
    onCloseDetail: () -> Unit,
    onEdit: (SkillEntry, String) -> Unit,
    onAttach: (SkillEntry) -> Unit,
    onDelete: (SkillEntry) -> Unit,
    onCreateDraftChange: ((SkillCreateDraft) -> SkillCreateDraft) -> Unit,
    onCreate: () -> Unit,
    onCancelCreate: () -> Unit,
    onBodyChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    onNewFile: () -> Unit,
    onFileDraftChange: ((SkillFileDraft) -> SkillFileDraft) -> Unit,
    onSaveFile: () -> Unit,
    onCancelFile: () -> Unit,
    onImportFile: (Uri) -> Unit,
    onDeleteFile: (String) -> Unit,
    onUrlDraftChange: ((SkillUrlDraft) -> SkillUrlDraft) -> Unit,
    onUrlImport: () -> Unit,
    onCancelUrl: () -> Unit,
) {
    if (state == null) return

    var showAddSheet by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SkillFile?>(null) }
    var confirmDiscardEdit by remember { mutableStateOf(false) }

    val pickSkillFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImportFile) }

    val shownDetail = rememberLastNonNull(state.detail)
    val requestCancelEdit: () -> Unit = {
        if (state.editing?.dirty == true) confirmDiscardEdit = true else onCancelEdit()
    }
    val onBack: () -> Unit = when {
        state.editing != null -> requestCancelEdit
        state.detail != null -> onCloseDetail
        else -> onDismiss
    }
    BackHandler(onBack = onBack)

    val detail = if (state.detail != null) shownDetail else null
    if (detail == null) {
        M3eSubPageScaffold(
            title = "技能",
            onBack = onDismiss,
            actions = {
                TextButton(onClick = { showAddSheet = true }) { Text("添加") }
            },
        ) { padding, _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                M3eSkillListBody(
                    state = state,
                    onQueryChange = onQueryChange,
                    onOpenDetail = onOpenDetail,
                    onEdit = onEdit,
                    onAttach = onAttach,
                    onDelete = onDelete,
                )
            }
        }
    } else {
        M3eSubPageScaffold(
            title = detail.entry.name,
            onBack = onCloseDetail,
            actions = {
                TextButton(onClick = onNewFile) { Text("新建文件") }
            },
        ) { padding, _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                M3eSkillDetailBody(
                    detail = detail,
                    onEdit = onEdit,
                    onDeleteFile = { deleteTarget = it },
                    onDeleteSkill = { onDelete(detail.entry) },
                )
            }
        }
    }

    if (showAddSheet) {
        M3eAddSkillDialog(
            onDismiss = { showAddSheet = false },
            onManual = {
                showAddSheet = false
                onNew()
            },
            onImport = {
                showAddSheet = false
                pickSkillFile.launch(SkillImportMimeTypes)
            },
            onUrl = {
                showAddSheet = false
                onNewUrl()
            },
        )
    }

    state.createForm?.let { draft ->
        M3eSkillCreateDialog(
            draft = draft,
            onChange = onCreateDraftChange,
            onConfirm = onCreate,
            onDismiss = onCancelCreate,
        )
    }
    state.urlDraft?.let { draft ->
        M3eSkillUrlDialog(
            draft = draft,
            onChange = onUrlDraftChange,
            onConfirm = onUrlImport,
            onDismiss = onCancelUrl,
        )
    }
    state.editing?.let { target ->
        M3eSkillEditDialog(
            target = target,
            onBodyChange = onBodyChange,
            onSave = onSave,
            onDismiss = requestCancelEdit,
        )
    }
    state.fileDraft?.let { draft ->
        M3eSkillFileDialog(
            draft = draft,
            onChange = onFileDraftChange,
            onConfirm = onSaveFile,
            onDismiss = onCancelFile,
        )
    }
    deleteTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除文件") },
            text = { Text("确定删除 ${file.relativePath} 吗？这个操作不可恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTarget = null
                        onDeleteFile(file.relativePath)
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
    if (confirmDiscardEdit) {
        AlertDialog(
            onDismissRequest = { confirmDiscardEdit = false },
            title = { Text("放弃未保存的修改？") },
            text = { Text("离开后这些改动不会写进文件。") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDiscardEdit = false
                        onCancelEdit()
                    },
                ) { Text("放弃修改") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscardEdit = false }) { Text("继续编辑") }
            },
        )
    }
}

/** 「怎么添加」的选择框：手动 / 导入文件 / 从 URL。 */
@Composable
private fun M3eAddSkillDialog(
    onDismiss: () -> Unit,
    onManual: () -> Unit,
    onImport: () -> Unit,
    onUrl: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加技能") },
        text = {
            M3eSegmentedColumn {
                M3eNavigationRow(
                    index = 0,
                    count = 3,
                    title = "手动添加",
                    summary = "粘贴一份 SKILL.md",
                    icon = ZhiIcons.edit,
                    onClick = onManual,
                )
                M3eNavigationRow(
                    index = 1,
                    count = 3,
                    title = "导入文件",
                    summary = "从本机选一份 SKILL.md 或 zip",
                    icon = ZhiIcons.file,
                    onClick = onImport,
                )
                M3eNavigationRow(
                    index = 2,
                    count = 3,
                    title = "从 URL 导入",
                    summary = "指向 SKILL.md 或仓库压缩包",
                    icon = ZhiIcons.search,
                    onClick = onUrl,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun M3eSkillListBody(
    state: SkillsState,
    onQueryChange: (String) -> Unit,
    onOpenDetail: (SkillEntry) -> Unit,
    onEdit: (SkillEntry, String) -> Unit,
    onAttach: (SkillEntry) -> Unit,
    onDelete: (SkillEntry) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        when {
            state.loading -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoadingIndicator(modifier = Modifier.size(M3eDimens.IconSmall))
                Text(
                    text = "正在加载…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }

            state.skills.isEmpty() -> M3eSkillEmptyState()

            else -> {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = { Text("筛选技能") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            painter = ZhiIcons.search,
                            contentDescription = null,
                            modifier = Modifier.size(M3eDimens.IconSmall),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = M3eDimens.RowPaddingH, vertical = 8.dp),
                )

                val listMotion = MaterialTheme.motionScheme
                AnimatedContent(
                    targetState = state.visibleSkills.isEmpty(),
                    transitionSpec = {
                        fadeIn(listMotion.defaultEffectsSpec()) togetherWith
                            fadeOut(listMotion.fastEffectsSpec())
                    },
                    label = "skillListEmpty",
                ) { empty ->
                    if (empty) {
                        M3eEmptyHint("没有匹配「${state.query}」的技能。")
                    } else {
                        Column {
                            Text(
                                text = "${state.visibleSkills.size} 个技能",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = M3eDimens.RowPaddingH, top = 4.dp, bottom = 4.dp),
                            )
                            listOf(SkillScope.PROJECT, SkillScope.USER).forEach { scope ->
                                val group = state.visibleSkills.filter { it.scope == scope }
                                if (group.isEmpty()) return@forEach
                                M3eSectionHeader("${scope.label} · ${group.size}")
                                M3eSegmentedColumn {
                                    group.forEachIndexed { index, skill ->
                                        M3eSegmentedItem(index = index, count = group.size) {
                                            M3eSkillRowBody(
                                                skill = skill,
                                                onOpen = { onOpenDetail(skill) },
                                                onEdit = { onEdit(skill, "SKILL.md") },
                                                onAttach = { onAttach(skill) },
                                                onDelete = { onDelete(skill) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "技能按需加载：Agent 用 Skill 工具读取，你也可以把它附加到下一条消息。" +
                        "SKILL.md 是技能本体，同目录的其它文件会跟着一起分发。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = M3eDimens.RowPaddingH, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun M3eSkillEmptyState() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = ZhiIcons.skill,
            contentDescription = null,
            tint = scheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = "无技能",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
        )
        Text(
            text = "点右上角「添加」新增，或导入一份 SKILL.md",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun M3eSkillRowBody(
    skill: SkillEntry,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onAttach: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = M3eDimens.RowPaddingH,
                end = 4.dp,
                top = M3eDimens.RowSpaceV,
                bottom = M3eDimens.RowSpaceV,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = ZhiIcons.skill,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(M3eDimens.IconSmall),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = skill.name,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
            )
            Text(
                text = skill.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                M3ePillLabel(skill.scope.label)
                if (skill.sizeLabel.isNotBlank()) {
                    Text(
                        text = skill.sizeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpen) { Text("详情") }
            Box2 {
                TextButton(onClick = { menuOpen = true }) {
                    Icon(
                        painter = ZhiIcons.more,
                        contentDescription = "更多操作",
                        modifier = Modifier.size(M3eDimens.IconSmall),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑 SKILL.md") },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("附加到下一步任务") },
                        onClick = { menuOpen = false; onAttach() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** 局部的 Box 包装，避免与布局别名冲突。 */
@Composable
private fun Box2(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box { content() }
}

@Composable
private fun M3ePillLabel(label: String) {
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun M3eEmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = M3eDimens.RowPaddingH, vertical = 12.dp),
    )
}

@Composable
private fun M3eSkillDetailBody(
    detail: SkillDetail,
    onEdit: (SkillEntry, String) -> Unit,
    onDeleteFile: (SkillFile) -> Unit,
    onDeleteSkill: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Text(
            text = detail.entry.path,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
        )

        M3eSectionHeader("SKILL.md 预览")
        val preview = detail.body.lineSequence().take(SkillPreviewLines).joinToString("\n")
        when {
            detail.body.isBlank() && detail.entry.sizeLabel.isNotBlank() -> Text(
                text = "读不到正文（可能是权限或文件被删除）。",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = 10.dp),
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = 10.dp),
            ) {
                if (preview.isBlank()) {
                    Text(
                        text = "SKILL.md 是空的。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = scheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val total = detail.body.lines().size
                    if (total > SkillPreviewLines) {
                        Text(
                            text = "…共 $total 行，编辑 SKILL.md 看全文。",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }

        M3eSectionHeader("文件")
        val treeMotion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = detail.tree.isEmpty(),
            transitionSpec = {
                fadeIn(treeMotion.defaultEffectsSpec()) togetherWith
                    fadeOut(treeMotion.fastEffectsSpec())
            },
            label = "skillTreeEmpty",
        ) { empty ->
            if (empty) {
                M3eEmptyHint("这个目录里没有文件。")
            } else {
                M3eSegmentedColumn {
                    M3eSkillTree(
                        nodes = detail.tree,
                        depth = 0,
                        entry = detail.entry,
                        onEdit = onEdit,
                        onDeleteFile = onDeleteFile,
                    )
                }
            }
        }

        Text(
            text = "SKILL.md 是技能本体，引擎的 Skill 工具按这个名字加载，所以它的 name 字段" +
                "必须和技能名一致；同目录的其它文件（含子目录）会跟着技能一起分发。",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
        )

        TextButton(
            onClick = onDeleteSkill,
            modifier = Modifier.padding(horizontal = M3eDimens.RowPaddingH, vertical = 8.dp),
        ) {
            Text("删除这个技能（连整个目录）", color = scheme.error)
        }
    }
}

@Composable
private fun M3eSkillTree(
    nodes: List<SkillFileNode>,
    depth: Int,
    entry: SkillEntry,
    onEdit: (SkillEntry, String) -> Unit,
    onDeleteFile: (SkillFile) -> Unit,
) {
    nodes.forEach { node ->
        when (node) {
            is SkillFileNode.DirNode -> M3eDirNodeRow(node = node, depth = depth) {
                M3eSkillTree(node.children, depth + 1, entry, onEdit, onDeleteFile)
            }

            is SkillFileNode.FileNode -> M3eFileNodeRow(
                file = node.file,
                depth = depth,
                entry = entry,
                onEdit = onEdit,
                onDeleteFile = onDeleteFile,
            )
        }
    }
}

@Composable
private fun M3eDirNodeRow(
    node: SkillFileNode.DirNode,
    depth: Int,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable(node.relativePath) { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = M3eDimens.RowPaddingH + (depth * 16).dp,
                    end = M3eDimens.RowPaddingH,
                    top = 10.dp,
                    bottom = 10.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = if (expanded) ZhiIcons.collapse else ZhiIcons.expand,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(M3eDimens.IconSmall),
            )
            Icon(
                painter = ZhiIcons.directory,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier
                    .size(M3eDimens.IconSmall)
                    .padding(start = 6.dp),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                )
                Text(
                    text = "${m3eCountFiles(node.children)} 个文件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起" else "展开")
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
        ) { content() }
    }
}

@Composable
private fun M3eFileNodeRow(
    file: SkillFile,
    depth: Int,
    entry: SkillEntry,
    onEdit: (SkillEntry, String) -> Unit,
    onDeleteFile: (SkillFile) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = M3eDimens.RowPaddingH + (depth * 16).dp,
                end = 8.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = ZhiIcons.file,
            contentDescription = null,
            tint = if (file.primary) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(M3eDimens.IconSmall),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (file.primary) scheme.primary else scheme.onSurface,
            )
            Text(
                text = file.sizeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { onEdit(entry, file.relativePath) }) { Text("编辑") }
        if (!file.primary) {
            TextButton(onClick = { onDeleteFile(file) }) {
                Text("删除", color = scheme.error)
            }
        }
    }
}

private fun m3eCountFiles(nodes: List<SkillFileNode>): Int = nodes.sumOf { node ->
    when (node) {
        is SkillFileNode.FileNode -> 1
        is SkillFileNode.DirNode -> m3eCountFiles(node.children)
    }
}

@Composable
private fun M3eScopeChoice(
    scope: SkillScope,
    onScopeChange: (SkillScope) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        SkillScope.entries.forEachIndexed { index, item ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index, SkillScope.entries.size),
                selected = item == scope,
                onClick = { onScopeChange(item) },
                label = { Text(item.label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun M3eSkillCreateDialog(
    draft: SkillCreateDraft,
    onChange: ((SkillCreateDraft) -> SkillCreateDraft) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val empty = draft.content.isBlank()
    val nameBroken = (draft.nameMissing || draft.nameInvalid) && !empty
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加技能") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = draft.content,
                    onValueChange = { value -> onChange { it.copy(content = value) } },
                    label = { Text(if (empty) SkillStore.starterTemplate else "SKILL.md 内容") },
                    minLines = 8,
                    maxLines = 12,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = when {
                        empty -> "把 name 里的 <技能名> 换成你自己的名字（只能用字母、数字、. _ -）"
                        draft.nameMissing -> "找不到名称：frontmatter 里必须有一行 name: <技能名>"
                        draft.nameInvalid -> "名称有非法字符：只能包含字母、数字、. _ -（1–64 个字符）"
                        draft.name.isNotBlank() -> "技能名称：${draft.name}"
                        else -> "粘贴完整的 SKILL.md 文件内容"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (nameBroken) scheme.error else scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                M3eScopeChoice(
                    scope = draft.scope,
                    onScopeChange = { scope -> onChange { it.copy(scope = scope) } },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = draft.saveable && !rememberActionBusy("skills.create"),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun M3eSkillUrlDialog(
    draft: SkillUrlDraft,
    onChange: ((SkillUrlDraft) -> SkillUrlDraft) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从 URL 导入") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = draft.url,
                    onValueChange = { value -> onChange { it.copy(url = value) } },
                    label = { Text("链接") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "支持 http(s)：指向一份 SKILL.md 就直接进确认框；指向 zip 压缩包" +
                        "（例如仓库的下载地址）会把里面每个 SKILL.md 都导入。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (draft.loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        LoadingIndicator(modifier = Modifier.size(M3eDimens.IconSmall))
                        Text(
                            text = "正在下载…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                M3eScopeChoice(
                    scope = draft.scope,
                    onScopeChange = { scope -> onChange { it.copy(scope = scope) } },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = draft.ready) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun M3eSkillEditDialog(
    target: SkillEditTarget,
    onBodyChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.fileName) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${target.name} / ${target.relativePath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = target.body,
                    onValueChange = onBodyChange,
                    label = { Text(target.fileName) },
                    minLines = 10,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                target.nameError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    text = if (target.empty) "内容为空：保存后会生成一个空文件。"
                    else "当前 ${target.body.length} 字。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (target.empty) scheme.error else scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = target.saveable && !rememberActionBusy("skills.save"),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun M3eSkillFileDialog(
    draft: SkillFileDraft,
    onChange: ((SkillFileDraft) -> SkillFileDraft) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var nameTouched by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文件") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = draft.fileName,
                    onValueChange = { value ->
                        nameTouched = true
                        onChange { it.copy(fileName = value) }
                    },
                    label = { Text("文件名") },
                    singleLine = true,
                    isError = nameTouched && draft.nameError != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (nameTouched) {
                    draft.nameError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = draft.content,
                    onValueChange = { value -> onChange { it.copy(content = value) } },
                    label = { Text("内容") },
                    minLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                Text(
                    text = "文件会建在这个技能自己的目录里，跟着技能一起分发；" +
                        "名字里可以用 / 建子目录（例：reference/api.md）。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = draft.saveable) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
