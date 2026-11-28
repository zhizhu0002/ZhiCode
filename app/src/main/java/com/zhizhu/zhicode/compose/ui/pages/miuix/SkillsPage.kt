package com.zhizhu.zhicode.compose.ui.pages.miuix

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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.zhizhu.zhicode.compose.data.SkillStore
import com.zhizhu.zhicode.compose.model.SkillCreateDraft
import com.zhizhu.zhicode.compose.model.SkillDetail
import com.zhizhu.zhicode.compose.model.SkillEditTarget
import com.zhizhu.zhicode.compose.model.SkillEntry
import com.zhizhu.zhicode.compose.model.SkillFile
import com.zhizhu.zhicode.compose.model.SkillFileDraft
import com.zhizhu.zhicode.compose.model.SkillFileNode
import com.zhizhu.zhicode.compose.model.SkillScope
import com.zhizhu.zhicode.compose.model.SkillUrlDraft
import com.zhizhu.zhicode.compose.model.SkillsState
import com.zhizhu.zhicode.compose.ui.ZhiAnchoredActionMenu
import com.zhizhu.zhicode.compose.ui.ZhiFieldError
import com.zhizhu.zhicode.compose.ui.ZhiFloatingActionButton
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiLoadingIndicator
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.ZhiSmallPill
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiDialogWidth
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiSheetCardColors
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsLoadingHint
import com.zhizhu.zhicode.compose.ui.settings.SettingsPageKey
import com.zhizhu.zhicode.compose.ui.settings.SettingsPageStack
import com.zhizhu.zhicode.compose.ui.settings.SettingsSubPage
import com.zhizhu.zhicode.compose.ui.rememberActionBusy
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SkillsPage(
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

    // FAB 拉起的「怎么添加」选择表，以及待确认的删除目标。
    var showAddSheet by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SkillFile?>(null) }

    // 读的是文档类内容，用 OpenDocument 而不是 GetContent：用户多半从"文档"入口找它。
    // 授权只为"立刻读一次文本"服务，读完就进内存，不需要跨进程重启保留的持久授权。
    val pickSkillFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImportFile) }

    // 退出动画期间离场页仍在绘制，那时这些字段已经是 null —— 见 rememberLastNonNull 的说明。
    val shownDetail = rememberLastNonNull(state.detail)

    // 页面**路径**（不是单页）：底下的列表页始终在，上面最多只有详情这一层。
    val path = buildList {
        add(SettingsPageKey("skills.list", 0))
        if (state.detail != null) add(SettingsPageKey("skills.detail", 1))
    }

    // 「返回」是逐级回退：关掉正在编辑的对话框 → 详情 → 列表 → 关掉整页。
    // 正在编辑且有未保存改动时先问一次（判定在 SkillEditTarget.dirty）。
    var confirmDiscardEdit by remember { mutableStateOf(false) }
    val requestCancelEdit: () -> Unit = {
        if (state.editing?.dirty == true) confirmDiscardEdit = true else onCancelEdit()
    }
    val onBack: () -> Unit = when {
        state.editing != null -> requestCancelEdit
        state.detail != null -> onCloseDetail
        else -> onDismiss
    }

    SettingsPageStack(path = path, onBack = onBack) { key ->
        // ⚠️ 分支必须看**正在渲染的那一页**（key），不能看当前状态：
        // 转场期间 key 还是旧值而状态已经变空，按状态分支会让离场页画成别的页。
        when (key.id) {
            "skills.detail" -> {
                val detail = shownDetail ?: return@SettingsPageStack
                SettingsSubPage(
                    title = detail.entry.name,
                    onBack = onCloseDetail,
                    // 加文件走右下 FAB（与参考实现一致），不再占顶栏的「新建文件」。
                    // 向下滚动时收起来：见 hideFabOnScrollDown 的说明。
                    floatingActionButton = {
                        ZhiFloatingActionButton(onClick = onNewFile, description = "新建文件")
                    },
                    hideFabOnScrollDown = true,
                    overlay = {
                        // 三个浮层都不套 `if`：常驻组合、只翻 `show`，退出动画才跑得起来（见文件头契约）。
                        // 退出期间状态已经是 null，内容用 rememberLastNonNull 兜住。
                        val editing = rememberLastNonNull(state.editing)
                        val fileDraft = rememberLastNonNull(state.fileDraft)
                        val deleting = rememberLastNonNull(deleteTarget)
                        editing?.let {
                            SkillEditDialog(
                                show = state.editing != null,
                                target = it,
                                onBodyChange = onBodyChange,
                                onSave = onSave,
                                onDismiss = requestCancelEdit,
                            )
                        }
                        fileDraft?.let {
                            SkillFileDialog(
                                show = state.fileDraft != null,
                                draft = it,
                                onChange = onFileDraftChange,
                                onConfirm = onSaveFile,
                                onDismiss = onCancelFile,
                            )
                        }
                        deleting?.let { file ->
                            DeleteFileDialog(
                                show = deleteTarget != null,
                                file = file,
                                onConfirm = {
                                    deleteTarget = null
                                    onDeleteFile(file.relativePath)
                                },
                                onDismiss = { deleteTarget = null },
                            )
                        }
                    },
                ) {
                    SkillDetailBody(
                        detail = detail,
                        onEdit = onEdit,
                        onDeleteFile = { deleteTarget = it },
                        onDeleteSkill = { onDelete(detail.entry) },
                    )
                }
            }

            else -> SettingsSubPage(
                title = "技能",
                onBack = onDismiss,
                // FAB 走 Scaffold 的**原生槽位**（见 SettingsSubPage 的说明），
                // 不再在外面套 Box 手工 align —— 那样 FAB 的点击与布局都不归 Scaffold 管。
                floatingActionButton = {
                    ZhiFloatingActionButton(
                        onClick = { showAddSheet = true },
                        description = "添加技能",
                    )
                },
                overlay = {
                    // 同上：常驻组合、只翻 `show`。
                    val createDraft = rememberLastNonNull(state.createForm)
                    val urlDraft = rememberLastNonNull(state.urlDraft)
                    AddSkillSheet(
                        show = showAddSheet,
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
                    createDraft?.let {
                        SkillCreateDialog(
                            show = state.createForm != null,
                            draft = it,
                            onChange = onCreateDraftChange,
                            onConfirm = onCreate,
                            onDismiss = onCancelCreate,
                        )
                    }
                    urlDraft?.let {
                        SkillUrlDialog(
                            show = state.urlDraft != null,
                            draft = it,
                            onChange = onUrlDraftChange,
                            onConfirm = onUrlImport,
                            onDismiss = onCancelUrl,
                        )
                    }
                },
            ) {
                SkillListBody(
                    state = state,
                    onQueryChange = onQueryChange,
                    onOpenDetail = onOpenDetail,
                    onEdit = onEdit,
                    onAttach = onAttach,
                    onDelete = onDelete,
                )
            }
        }
    }

    UnsavedChangesDialog(
        visible = confirmDiscardEdit,
        onKeepEditing = { confirmDiscardEdit = false },
        onDiscard = {
            confirmDiscardEdit = false
            onCancelEdit()
        },
    )
}

// ------------------------------------------------------------------ 添加技能的选择表

/** FAB 拉起的「怎么添加」选择表。 */
@Composable
private fun AddSkillSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onManual: () -> Unit,
    onImport: () -> Unit,
    onUrl: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    OverlayBottomSheet(
        show = show,
        onDismissRequest = onDismiss,
        title = "添加技能",
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            colors = ZhiSheetCardColors(),
        ) {
            BasicComponent(
                title = "手动添加",
                titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
                summary = "粘贴完整的 SKILL.md，名称从内容里解析",
                summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
                startAction = { RowIcon(ZhiIcons.edit, scheme.primary) },
                onClick = onManual,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            )
            BasicComponent(
                title = "从文件导入",
                titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
                summary = "选一份本机的 .md，读进来再确认",
                summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
                startAction = { RowIcon(ZhiIcons.file, scheme.primary) },
                onClick = onImport,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            )
            BasicComponent(
                title = "从 URL 导入",
                titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
                summary = "填一个链接：SKILL.md 直接确认，zip 自动解开",
                summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
                startAction = { RowIcon(ZhiIcons.runtime, scheme.primary) },
                onClick = onUrl,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** 行首的小图标（选择表与树里共用），统一尺寸与右侧间距。 */
@Composable
private fun RowIcon(icon: androidx.compose.ui.graphics.painter.Painter, tint: androidx.compose.ui.graphics.Color) {
    Icon(
        painter = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(18.dp).padding(end = 2.dp),
    )
}

@Composable
private fun SkillListBody(
    state: SkillsState,
    onQueryChange: (String) -> Unit,
    onOpenDetail: (SkillEntry) -> Unit,
    onEdit: (SkillEntry, String) -> Unit,
    onAttach: (SkillEntry) -> Unit,
    onDelete: (SkillEntry) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column {
        when {
            // 载荷还在 IO 上读（见 openSkills）：必须先于「一条都没有」判断，
            // 否则空态会在读取期间闪一下（连同它那句「从哪开始」）。
            state.loading -> SettingsLoadingHint()

            // 一条都没有时：筛选框只会占地方，直接给一屏空态 + "从哪开始"。
            state.skills.isEmpty() -> SkillEmptyState()

            else -> {
                ZhiTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = "筛选技能",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            painter = ZhiIcons.search,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )

                // 「筛出 0 条」与「有结果」之间淡变：敲字时结果列表会反复跨过空/非空
                AnimatedContent(
                    targetState = state.visibleSkills.isEmpty(),
                    transitionSpec = {
                        fadeIn(ZhiMotion.fadeInSpec) togetherWith fadeOut(ZhiMotion.fadeOutSpec)
                    },
                    label = "skillListEmpty",
                ) { empty ->
                    if (empty) {
                        EmptyHint("没有匹配「${state.query}」的技能。")
                    } else {
                        Column {
                            Text(
                                text = "${state.visibleSkills.size} 个技能",
                                color = scheme.onSurfaceVariantSummary,
                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                            )
                            // 按作用域分组（项目级在前）：两级的同名技能可以共存
                            listOf(SkillScope.PROJECT, SkillScope.USER).forEach { scope ->
                                val group = state.visibleSkills.filter { it.scope == scope }
                                if (group.isEmpty()) return@forEach
                                Text(
                                    text = "${scope.label} · ${group.size}",
                                    color = scheme.onSurfaceVariantSummary,
                                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(start = 16.dp, top = 6.dp, bottom = 2.dp),
                                )
                                // 每张技能各一张卡（与参考实现一致）：整组塞在一张卡里时，
                                // 长长的技能说明会把相邻的两条糊成一块。
                                group.forEach { skill ->
                                    SkillRow(
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

                Text(
                    text = "技能按需加载：Agent 用 Skill 工具读取，你也可以把它附加到下一条消息。" +
                        "SKILL.md 是技能本体，同目录的其它文件会跟着一起分发。",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** 一个技能都没有时的空态：大图标 + 一句话 + 怎么开始。 */
@Composable
private fun SkillEmptyState() {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = ZhiIcons.skill,
            contentDescription = null,
            // 半透明的图标：空态要看得出来"这里什么都没有"，但不能抢过页面标题。
            tint = scheme.onSurfaceVariantSummary.copy(alpha = 0.45f),
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = "无技能",
            color = scheme.onBackground,
            fontSize = MiuixTheme.textStyles.main.fontSize,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "点击右下角的 + 按钮以添加技能",
            color = scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
        )
    }
}

/** 技能列表的一行：图标 + 名称 + 描述 + 作用域，行尾是 ⋮ 溢出菜单。 */
@Composable
private fun SkillRow(
    skill: SkillEntry,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onAttach: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    // ⋮ 菜单挂在**这一行**上（MenuHost 读行内坐标），不是挂在整页上。
    var menuAt by remember { mutableStateOf<DpOffset?>(null) }

    Box {
        Card(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            colors = CardDefaults.defaultColors(
                color = scheme.surfaceContainerHighest,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = ZhiIcons.skill,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = skill.name,
                        color = scheme.onBackground,
                        fontSize = MiuixTheme.textStyles.main.fontSize,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = skill.summary,
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        maxLines = 2,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZhiSmallPill(label = skill.scope.label)
                        if (skill.sizeLabel.isNotBlank()) {
                            Text(
                                text = skill.sizeLabel,
                                color = scheme.onSurfaceVariantSummary,
                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
                ZhiIconButton(
                    icon = ZhiIcons.more,
                    description = "更多操作",
                    onClick = { menuAt = DpOffset.Zero },
                    iconSize = 18.dp,
                )
            }
        }

        // 不套 `if`：`open` 是布尔入参，浮层常驻才播得完退出动画（见 ZhiAnchoredActionMenu）。
        ZhiAnchoredActionMenu(
            open = menuAt != null,
            labels = listOf("编辑 SKILL.md", "附加到下一步任务", "删除"),
                onSelect = { index ->
                    menuAt = null
                    when (index) {
                        0 -> onEdit()
                        1 -> onAttach()
                        else -> onDelete()
                    }
                },
                onDismiss = { menuAt = null },
                // 贴行锚定（不用手指坐标）：⋮ 是个小按钮，菜单从它下方长出来最稳。
                fingerOffset = null,
            )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

// ------------------------------------------------------------------ 详情页

/** 技能详情：这个技能的路径 + 它目录下的**文件树**。 */
@Composable
private fun SkillDetailBody(
    detail: SkillDetail,
    onEdit: (SkillEntry, String) -> Unit,
    onDeleteFile: (SkillFile) -> Unit,
    onDeleteSkill: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column {
        Text(
            text = detail.entry.path,
            color = scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )

        SettingsGroup("SKILL.md 预览") {
            // 详情页此前只有文件树：想知道这个技能到底教 Agent 做什么，得先点进编辑器。
            // 这里给一份**只读**预览（前 20 行），看内容确认是哪一个技能再决定要不要编辑。
            val preview = detail.body.lineSequence().take(SKILL_PREVIEW_LINES).joinToString("\n")
            when {
                detail.body.isBlank() && detail.entry.sizeLabel.isNotBlank() -> Text(
                    text = "读不到正文（可能是权限或文件被删除）。",
                    color = scheme.error,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                )
                else -> Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    if (preview.isBlank()) {
                        Text(
                            text = "SKILL.md 是空的。",
                            color = scheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        )
                    } else {
                        Text(
                            text = preview,
                            color = scheme.onSurface,
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val total = detail.body.lines().size
                        if (total > SKILL_PREVIEW_LINES) {
                            Text(
                                text = "…共 $total 行，编辑 SKILL.md 看全文。",
                                color = scheme.onSurfaceVariantSummary,
                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        SettingsGroup("文件") {
            // 「目录里一个文件都没有」与「有文件树」之间淡变。
            // 真会出现：新建技能后再删掉 SKILL.md 之外的文件、或导入一个空目录的 zip。
            AnimatedContent(
                targetState = detail.tree.isEmpty(),
                transitionSpec = {
                    fadeIn(ZhiMotion.fadeInSpec) togetherWith fadeOut(ZhiMotion.fadeOutSpec)
                },
                label = "skillDetailTreeEmpty",
            ) { empty ->
                if (empty) {
                    EmptyHint("这个目录里没有文件。点右下角的 + 新建一个。")
                } else {
                    SkillTree(
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
            color = scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )

        // 删除放在详情页而不是列表的滑动里：删目录是不可逆的，
        // 多一步"先进详情看清楚是哪个技能"能挡住误删。
        Card(
            onClick = onDeleteSkill,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            colors = CardDefaults.defaultColors(color = scheme.surfaceContainerHighest),
        ) {
            BasicComponent(
                title = "删除这个技能",
                titleColor = BasicComponentDefaults.titleColor(color = scheme.error),
                summary = "连整个目录一起删，包含上面列出的所有文件",
                summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
                onClick = onDeleteSkill,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun SkillTree(
    nodes: List<SkillFileNode>,
    depth: Int,
    entry: SkillEntry,
    onEdit: (SkillEntry, String) -> Unit,
    onDeleteFile: (SkillFile) -> Unit,
) {
    nodes.forEach { node ->
        when (node) {
            is SkillFileNode.DirNode -> DirNodeRow(node = node, depth = depth) {
                SkillTree(node.children, depth + 1, entry, onEdit, onDeleteFile)
            }

            is SkillFileNode.FileNode -> FileNodeRow(
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
private fun DirNodeRow(
    node: SkillFileNode.DirNode,
    depth: Int,
    content: @Composable () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    var expanded by rememberSaveable(node.relativePath) { mutableStateOf(false) }
    Column {
        BasicComponent(
            title = node.name,
            titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
            summary = "${countFiles(node.children)} 个文件",
            summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
            startAction = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = if (expanded) ZhiIcons.collapse else ZhiIcons.expand,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(16.dp),
                    )
                    Icon(
                        painter = ZhiIcons.directory,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(18.dp).padding(start = 6.dp),
                    )
                }
            },
            onClick = { expanded = !expanded },
            // 每层缩进 16dp：树的结构靠缩进表达，缩进不够时父子看起来是并列的。
            insideMargin = PaddingValues(
                start = 16.dp + (depth * 16).dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
        )
        // 展开/收起走 [AnimatedVisibility]（官方裸默认：淡变 + 竖直展开/收起）。
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) { content() }
    }
}

@Composable
private fun FileNodeRow(
    file: SkillFile,
    depth: Int,
    entry: SkillEntry,
    onEdit: (SkillEntry, String) -> Unit,
    onDeleteFile: (SkillFile) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    BasicComponent(
        title = file.name,
        titleColor = BasicComponentDefaults.titleColor(
            color = if (file.primary) scheme.primary else scheme.onBackground,
        ),
        summary = file.sizeLabel,
        summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
        startAction = {
            Icon(
                painter = ZhiIcons.file,
                contentDescription = null,
                tint = if (file.primary) scheme.primary else scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp),
            )
        },
        endActions = {
            ZhiIconButton(
                icon = ZhiIcons.edit,
                description = "编辑 ${file.relativePath}",
                onClick = { onEdit(entry, file.relativePath) },
                iconSize = 15.dp,
            )
            // SKILL.md 不给删除按钮：它是技能本体，`SkillStore.deleteFile` 也会拒绝。
            // 界面上不显示（而不是点了才报错）—— 一个必然失败的按钮不该存在。
            if (!file.primary) {
                ZhiIconButton(
                    icon = ZhiIcons.delete,
                    description = "删除 ${file.relativePath}",
                    onClick = { onDeleteFile(file) },
                    iconSize = 15.dp,
                    tint = scheme.error,
                )
            }
        },
        onClick = { onEdit(entry, file.relativePath) },
        insideMargin = PaddingValues(
            start = 16.dp + (depth * 16).dp,
            end = 8.dp,
            top = 10.dp,
            bottom = 10.dp,
        ),
    )
}

/** 子树里的文件总数（目录行显示用）。 */
private fun countFiles(nodes: List<SkillFileNode>): Int = nodes.sumOf { node ->
    when (node) {
        is SkillFileNode.FileNode -> 1
        is SkillFileNode.DirNode -> countFiles(node.children)
    }
}

// ------------------------------------------------------------------ 手动添加（对话框）

/** 「手动添加技能」对话框。 */
@Composable
private fun SkillCreateDialog(
    show: Boolean,
    draft: SkillCreateDraft,
    onChange: ((SkillCreateDraft) -> SkillCreateDraft) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val empty = draft.content.isBlank()
    val nameBroken = (draft.nameMissing || draft.nameInvalid) && !empty
    OverlayDialog(
        show = show,
        onDismissRequest = onDismiss,
        // 居中形态（`largeScreen` 是**居中**开关，不是「大屏专用」）——
        // 不传的话手机竖屏会被判成「非大屏」，弹窗从底部升起。理由见 PermissionOverlay。
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
    ) {
        DialogShell(
            title = "添加技能",
            actions = {
                TextButton(text = "取消", onClick = onDismiss)
                TextButton(
                    text = "保存",
                    onClick = onConfirm,
                    enabled = draft.saveable && !rememberActionBusy("skills.create"),
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ZhiTextField(
                    value = draft.content,
                    onValueChange = { value -> onChange { it.copy(content = value) } },
                    /* 空内容时显示为**灰色占位符**（`useLabelAsPlaceholder = true` */
                    label = if (empty) SkillStore.starterTemplate else "SKILL.md 内容",
                    useLabelAsPlaceholder = true,
                    singleLine = false,
                    minLines = 8,
                    maxLines = 12,
                    textStyle = MiuixTheme.textStyles.main.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = when {
                        // 空内容 = 还没开始写。这里给的是"要写什么形状"的指引，
                        // 不是错误提示（示例就在上面的灰字占位符里）。
                        empty -> "把 name 里的 <技能名> 换成你自己的名字（只能用字母、数字、. _ -）"

                        draft.nameMissing -> "找不到名称：frontmatter 里必须有一行 name: <技能名>"
                        draft.nameInvalid -> "名称有非法字符：只能包含字母、数字、. _ -（1–64 个字符）"
                        draft.name.isNotBlank() -> "技能名称：${draft.name}"
                        else -> "粘贴完整的 SKILL.md 文件内容"
                    },
                    // 空内容用次级色（指引），真写了还非法才飘红。
                    color = if (nameBroken) scheme.error else scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    fontWeight = if (nameBroken) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )

                // 作用域：参考实现没有这一项（它只有一份全局技能目录），
                // ZhiCode 有项目级/用户级两层，必须让用户选。
                ScopeDropdown(
                    scope = draft.scope,
                    onScopeChange = { scope -> onChange { it.copy(scope = scope) } },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 从 URL 导入（对话框）

/** 「从 URL 导入」对话框。 */
@Composable
private fun SkillUrlDialog(
    show: Boolean,
    draft: SkillUrlDraft,
    onChange: ((SkillUrlDraft) -> SkillUrlDraft) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    OverlayDialog(
        show = show,
        onDismissRequest = onDismiss,
        // 居中形态（`largeScreen` 是**居中**开关，不是「大屏专用」）——
        // 不传的话手机竖屏会被判成「非大屏」，弹窗从底部升起。理由见 PermissionOverlay。
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
    ) {
        DialogShell(
            title = "从 URL 导入",
            actions = {
                TextButton(text = "取消", onClick = onDismiss)
                TextButton(
                    text = "导入",
                    // 下载中禁用：连点会并发拉好几份，而用户只会看到一个"卡住了"。
                    onClick = onConfirm,
                    enabled = draft.ready,
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ZhiTextField(
                    value = draft.url,
                    onValueChange = { value -> onChange { it.copy(url = value) } },
                    label = "链接",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "支持 http(s)：指向一份 SKILL.md 就直接进确认框；指向 zip 压缩包" +
                        "（例如仓库的下载地址）会把里面每个 SKILL.md 都导入。",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (draft.loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        ZhiLoadingIndicator(size = 16.dp)
                        Text(
                            text = "正在下载…",
                            color = scheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                ScopeDropdown(
                    scope = draft.scope,
                    onScopeChange = { scope -> onChange { it.copy(scope = scope) } },
                )
            }
        }
    }
}

@Composable
private fun ScopeDropdown(scope: SkillScope, onScopeChange: (SkillScope) -> Unit) {
    OverlayDropdownPreference(
        items = SkillScope.entries.map { it.label },
        selectedIndex = SkillScope.entries.indexOf(scope).coerceAtLeast(0),
        title = "保存位置",
        summary = null,
        onSelectedIndexChange = { index ->
            SkillScope.entries.getOrNull(index)?.let(onScopeChange)
        },
    )
}

// ------------------------------------------------------------------ 编辑文件（对话框）

@Composable
private fun SkillEditDialog(
    show: Boolean,
    target: SkillEditTarget,
    onBodyChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    OverlayDialog(
        show = show,
        onDismissRequest = onDismiss,
        // 居中形态（`largeScreen` 是**居中**开关，不是「大屏专用」）——
        // 不传的话手机竖屏会被判成「非大屏」，弹窗从底部升起。理由见 PermissionOverlay。
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
    ) {
        DialogShell(
            title = target.fileName,
            actions = {
                TextButton(text = "取消", onClick = onDismiss)
                TextButton(
                    text = "保存",
                    onClick = onSave,
                    enabled = target.saveable && !rememberActionBusy("skills.save"),
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${target.name} / ${target.relativePath}",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                ZhiTextField(
                    value = target.body,
                    onValueChange = onBodyChange,
                    label = target.fileName,
                    useLabelAsPlaceholder = false,
                    singleLine = false,
                    minLines = 10,
                    textStyle = MiuixTheme.textStyles.main.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                // 这里 touched 恒真：nameError 说的是**磁盘上那个文件名**本身不合法
                // （不是「用户还没填」），打开就要说，否则问题会被藏到保存那一刻。
                ZhiFieldError(message = target.nameError, touched = true)
                Text(
                    text = if (target.empty) "内容为空：保存后会生成一个空文件。"
                    else "当前 ${target.body.length} 字。",
                    color = if (target.empty) scheme.error else scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

/** 详情页 SKILL.md 预览的行数上限：够认出"这是哪一个技能"，又不至于把整份文档铺开。 */
private const val SKILL_PREVIEW_LINES = 20

private val SkillImportMimeTypes = arrayOf(
    listOf("text", "*").joinToString("/"),
    "application/octet-stream",
    "application/zip",
)

// ------------------------------------------------------------------ 新建文件（对话框）

/** 「新建文件」对话框。 */
@Composable
private fun SkillFileDialog(
    show: Boolean,
    draft: SkillFileDraft,
    onChange: ((SkillFileDraft) -> SkillFileDraft) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    // 见 ZhiFieldError：没碰过文件名就不飘红。用 show 作 key，避免每次敲字 draft 变化都把 touched 重置为 false。
    var nameTouched by remember(show) { mutableStateOf(false) }
    OverlayDialog(
        show = show,
        onDismissRequest = onDismiss,
        // 居中形态（`largeScreen` 是**居中**开关，不是「大屏专用」）——
        // 不传的话手机竖屏会被判成「非大屏」，弹窗从底部升起。理由见 PermissionOverlay。
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
    ) {
        DialogShell(
            title = "新建文件",
            actions = {
                TextButton(text = "取消", onClick = onDismiss)
                TextButton(
                    text = "创建",
                    onClick = onConfirm,
                    enabled = draft.saveable,
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ZhiTextField(
                    value = draft.fileName,
                    onValueChange = { value ->
                        nameTouched = true
                        onChange { it.copy(fileName = value) }
                    },
                    label = "文件名",
                    useLabelAsPlaceholder = false,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // 「用户还没填」这一类：碰过才提示（见 ZhiFieldError）。
                ZhiFieldError(message = draft.nameError, touched = nameTouched)

                ZhiTextField(
                    value = draft.content,
                    onValueChange = { value -> onChange { it.copy(content = value) } },
                    label = "内容",
                    useLabelAsPlaceholder = false,
                    singleLine = false,
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )

                Text(
                    text = "文件会建在这个技能自己的目录里，跟着技能一起分发；" +
                        "名字里可以用 / 建子目录（例：reference/api.md）。",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 删除文件确认

/** 删除单个文件前的确认。删文件不可逆，所以要多这一下。 */
@Composable
private fun DeleteFileDialog(
    show: Boolean,
    file: SkillFile,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    OverlayDialog(
        show = show,
        onDismissRequest = onDismiss,
        // 居中形态（`largeScreen` 是**居中**开关，不是「大屏专用」）——
        // 不传的话手机竖屏会被判成「非大屏」，弹窗从底部升起。理由见 PermissionOverlay。
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
    ) {
        DialogShell(
            title = "删除文件",
            actions = {
                TextButton(text = "取消", onClick = onDismiss)
                TextButton(text = "删除", onClick = onConfirm, modifier = Modifier.padding(start = 8.dp))
            },
        ) {
            Text(
                text = "确定删除 ${file.relativePath} 吗？这个操作不可恢复。",
                color = scheme.onBackground,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
