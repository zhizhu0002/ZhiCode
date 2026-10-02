package com.zhizhu.zhicode.compose.ui.dialogs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.zhizhu.zhicode.compose.model.SkillCreateDraft
import com.zhizhu.zhicode.compose.model.SkillDetail
import com.zhizhu.zhicode.compose.model.SkillEditTarget
import com.zhizhu.zhicode.compose.model.SkillEntry
import com.zhizhu.zhicode.compose.model.SkillFileDraft
import com.zhizhu.zhicode.compose.model.SkillScope
import com.zhizhu.zhicode.compose.model.SkillsState
import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import com.zhizhu.zhicode.compose.ui.ZhiAnchoredActionMenu
import com.zhizhu.zhicode.compose.ui.ZhiFloatingActionButton
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiSmallPill
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsPageKey
import com.zhizhu.zhicode.compose.ui.settings.SettingsPageStack
import com.zhizhu.zhicode.compose.ui.settings.SettingsSubPage
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

/**
 * Skill 管理：技能列表 / 手动添加 / 技能详情（文件列表 + 编辑）。
 *
 * ## 这里的操作是"真的"
 *
 * 技能目录与引擎 `SkillTool` 的查找路径完全一致（项目级 `<project>/.zhicode/skills`、
 * 用户级 `$HOME/.zhicode/skills`），所以在这里建好的技能，Agent 下一次就能用 `Skill`
 * 工具加载；「附加」则把内容直接拼进下一条请求。
 *
 * ## 四态走**页面栈**，不是 when 硬切
 *
 * 列表 → 详情 → 编辑、列表 → 手动添加 都是 [SettingsPageStack] 里的页。
 * 以前这四态是调用方一个 `when {}` 里的内容硬切，而 `AppScaffold` 的页面栈只反映
 * 「技能页开着没有」—— 栈深度不变，整页转场就不会触发，于是**一点动画都没有**。
 *
 * ## 与参考实现（rikkahub 的技能页）的差异
 *
 * 保留了它的结构：列表带筛选框、卡片行是「图标 + 名称 + 描述」、右下 FAB、
 * FAB 拉起一个选择表、详情页列该技能目录下的文件。
 * 三处**刻意不同**：
 *
 * 1. 多一个「作用域」选择。rikkahub 只有一份全局技能目录，ZhiCode 有项目级与用户级两层，
 *    这是存储层的既有语义，不能省。
 * 2. **不做 GitHub 导入**。那需要联网拉取 + 解压，与本工程「离线构建、不加依赖」的
 *    约束冲突。改成「从本机文件导入」，能力上覆盖同一件事（拿到一份 SKILL.md）。
 * 3. 没有内置技能的概念，所以不做只读项与「Built-in」标签。
 */
@Composable
fun SkillsOverlay(
    state: SkillsState?,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onNew: () -> Unit,
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
) {
    if (state == null) return

    // 退出动画期间离场页仍在绘制，那时这些字段已经是 null —— 见 rememberLastNonNull 的说明。
    val shownDetail = rememberLastNonNull(state.detail)
    val shownCreate = rememberLastNonNull(state.createForm)
    val shownEditing = rememberLastNonNull(state.editing)

    val page = when {
        state.editing != null -> SettingsPageKey("skills.editor", 2)
        state.createForm != null -> SettingsPageKey("skills.create", 1)
        state.detail != null -> SettingsPageKey("skills.detail", 1)
        else -> SettingsPageKey("skills.list", 0)
    }

    // 「返回」是逐级回退：编辑器 → 详情 → 列表 → 关掉整页。
    // 以前没有这层处理，三级页按系统返回会**直接关掉整个技能页**。
    val onBack: () -> Unit = when {
        state.createForm != null -> onCancelCreate
        state.detail != null -> onCloseDetail
        else -> onDismiss
    }

    SettingsPageStack(current = page, onBack = onBack) { key ->
        // ⚠️ 分支必须看**正在渲染的那一页**（key），不能看当前状态：
        // 退出动画期间 key 还是旧值而状态已经变空，按状态分支会让离场页画成别的页。
        when (key.id) {
            "skills.editor" -> {
                val editing = shownEditing ?: return@SettingsPageStack
                SkillEditorPage(
                    target = editing,
                    onBodyChange = onBodyChange,
                    onBack = onCancelEdit,
                    onSave = onSave,
                )
            }

            "skills.create" -> {
                val draft = shownCreate ?: return@SettingsPageStack
                SettingsSubPage(
                    title = "手动添加技能",
                    onBack = onCancelCreate,
                    action = "创建" to (if (draft.saveable) onCreate else null),
                ) {
                    SkillCreateForm(draft = draft, onChange = onCreateDraftChange)
                }
            }

            "skills.detail" -> {
                val detail = shownDetail ?: return@SettingsPageStack
                SettingsSubPage(
                    title = detail.entry.name,
                    onBack = onCloseDetail,
                    action = "新建文件" to onNewFile,
                ) {
                    SkillDetailBody(
                        detail = detail,
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }
            }

            else -> SkillListPage(
                state = state,
                onQueryChange = onQueryChange,
                onOpenDetail = onOpenDetail,
                onEdit = onEdit,
                onAttach = onAttach,
                onDelete = onDelete,
                onDismiss = onDismiss,
                onNew = onNew,
                onImportFile = onImportFile,
            )
        }
    }

    // 「新建文件」用对话框而不是又一层页面：只有两个字段，做成整页要滑来滑去。
    // 它不属于页面栈，所以放在栈外面。
    if (state.fileDraft != null) {
        SkillFileDialog(
            draft = state.fileDraft,
            onChange = onFileDraftChange,
            onConfirm = onSaveFile,
            onDismiss = onCancelFile,
        )
    }
}

// ------------------------------------------------------------------ 列表页

@Composable
private fun SkillListPage(
    state: SkillsState,
    onQueryChange: (String) -> Unit,
    onOpenDetail: (SkillEntry) -> Unit,
    onEdit: (SkillEntry, String) -> Unit,
    onAttach: (SkillEntry) -> Unit,
    onDelete: (SkillEntry) -> Unit,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onImportFile: (Uri) -> Unit,
) {
    // FAB 拉起的「怎么添加」选择表。
    var showAddSheet by remember { mutableStateOf(false) }

    // 读的是文档类内容，用 OpenDocument 而不是 GetContent：用户多半从"文档"入口找它。
    // 授权只为"立刻读一次文本"服务，读完就进内存，不需要跨进程重启保留的持久授权。
    val pickSkillFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImportFile) }

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsSubPage(title = "技能", onBack = onDismiss) {
            SkillListBody(
                state = state,
                onQueryChange = onQueryChange,
                onOpenDetail = onOpenDetail,
                onEdit = onEdit,
                onAttach = onAttach,
                onDelete = onDelete,
            )
        }

        // 悬浮在右下角而不是放顶栏 TextButton：技能列表会越攒越长，
        // 顶栏的按钮滑下去就够不着了。
        ZhiFloatingActionButton(
            onClick = { showAddSheet = true },
            description = "添加技能",
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    if (showAddSheet) {
        OverlayBottomSheet(
            show = true,
            onDismissRequest = { showAddSheet = false },
            title = "添加技能",
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SheetAction(
                    title = "手动添加",
                    summary = "粘贴完整的 SKILL.md，名称从内容里解析",
                    onClick = {
                        showAddSheet = false
                        onNew()
                    },
                )
                SheetAction(
                    title = "从文件导入",
                    summary = "选一份本机的 .md，读进来再确认",
                    onClick = {
                        showAddSheet = false
                        pickSkillFile.launch(arrayOf("text/*", "application/octet-stream"))
                    },
                )
            }
        }
    }
}

/** 选择表里的一行。用 `Card` + `BasicComponent`，与设置页的行同形态。 */
@Composable
private fun SheetAction(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        // 显式给 `secondaryContainer`：sheet 背板是 `background`，而 `Card` 的默认色
        // 是 `surfaceContainer`，两者在暗色下**是同一个值**（#242424），卡片会看不见。
        colors = CardDefaults.defaultColors(color = scheme.secondaryContainer),
    ) {
        BasicComponent(
            title = title,
            titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
            summary = summary,
            summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
            onClick = onClick,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        )
    }
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
        // 筛选框只在真有技能可筛时出现；一条都没有时它只会占地方。
        if (state.skills.isNotEmpty()) {
            ZhiTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = "筛选技能",
                useLabelAsPlaceholder = true,
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = ZhiIcons.search,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        SettingsGroup("${state.visibleSkills.size} 个技能") {
            when {
                state.skills.isEmpty() -> EmptyHint(
                    "还没有技能。点右下角的 + 选「手动添加」，粘贴一份 SKILL.md；" +
                        "写好后 Agent 可通过 Skill 工具加载。",
                )

                state.visibleSkills.isEmpty() -> EmptyHint("没有匹配「${state.query}」的技能。")

                else -> state.visibleSkills.forEach { skill ->
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
                    imageVector = ZhiIcons.skill,
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
                        fontSize = ZhiTextScale.Body,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = skill.summary,
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = ZhiTextScale.Footnote,
                        maxLines = 2,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZhiSmallPill(label = skill.scope.label)
                        if (skill.sizeLabel.isNotBlank()) {
                            Text(
                                text = skill.sizeLabel,
                                color = scheme.onSurfaceVariantSummary,
                                fontSize = ZhiTextScale.Micro,
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

        if (menuAt != null) {
            ZhiAnchoredActionMenu(
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
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = ZhiTextScale.Footnote,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

// ------------------------------------------------------------------ 详情页

/**
 * 技能详情：这个技能的路径 + 它目录下的文件列表。
 *
 * 技能不只有 `SKILL.md` —— 参考文档、脚本都可以放在同一目录里跟着一起分发
 * （`SkillStore.delete` 用递归删除就是为此）。所以这里把文件列出来，
 * 每个都可点进编辑器，也能新建。
 */
@Composable
private fun SkillDetailBody(
    detail: SkillDetail,
    onEdit: (SkillEntry, String) -> Unit,
    onDelete: (SkillEntry) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column {
        Text(
            text = detail.entry.path,
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Micro,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )

        SettingsGroup("文件") {
            if (detail.files.isEmpty()) {
                EmptyHint("这个目录里没有文件。点右上角「新建文件」补一个。")
            } else {
                detail.files.forEach { file ->
                    BasicComponent(
                        title = file.name,
                        titleColor = BasicComponentDefaults.titleColor(
                            color = if (file.primary) scheme.primary else scheme.onBackground,
                        ),
                        summary = file.sizeLabel,
                        summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
                        endActions = {
                            ZhiIconButton(
                                icon = ZhiIcons.edit,
                                description = "编辑 ${file.name}",
                                onClick = { onEdit(detail.entry, file.name) },
                                iconSize = 15.dp,
                            )
                        },
                        onClick = { onEdit(detail.entry, file.name) },
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }

        Text(
            text = "SKILL.md 是技能本体，引擎的 Skill 工具按这个名字加载；" +
                "同目录的其它文件会跟着技能一起分发，参考文档放这里最合适。",
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Footnote,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )

        // 删除放在详情页而不是列表的滑动里：删目录是不可逆的，
        // 多一步"先进详情看清楚是哪个技能"能挡住误删。
        Card(
            onClick = { onDelete(detail.entry) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            colors = CardDefaults.defaultColors(color = scheme.surfaceContainerHighest),
        ) {
            BasicComponent(
                title = "删除这个技能",
                titleColor = BasicComponentDefaults.titleColor(color = scheme.error),
                summary = "连整个目录一起删，包含上面列出的所有文件",
                summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
                onClick = { onDelete(detail.entry) },
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ 手动添加

/**
 * 「手动添加」表单：粘贴一整份 SKILL.md，名称从内容里解析。
 *
 * 与参考实现一致 —— 不再单独问一遍名字。理由不只是省一步：用户手上拿到的本来就是
 * 一份完整的 SKILL.md，让它自己声明名字，目录名就不会和内容里的 `name` 不一致。
 *
 * 名字解析不出来时**明确报错并禁用提交**，不回退到默认名：猜一个会建出用户没打算建的目录。
 */
@Composable
private fun SkillCreateForm(
    draft: SkillCreateDraft,
    onChange: ((SkillCreateDraft) -> SkillCreateDraft) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column {
        SettingsGroup("SKILL.md 内容") {
            ZhiTextField(
                value = draft.content,
                onValueChange = { value -> onChange { it.copy(content = value) } },
                label = "SKILL.md",
                useLabelAsPlaceholder = false,
                singleLine = false,
                minLines = 8,
                textStyle = MiuixTheme.textStyles.main.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .heightIn(min = 200.dp),
            )
        }

        // 实时回显解析结果：用户粘完立刻知道名字对不对，不用等提交。
        Text(
            text = when {
                draft.nameMissing -> "找不到名称：frontmatter 里必须有一行 name: <技能名>"
                draft.nameInvalid -> "名称有非法字符：只能包含字母、数字、. _ -（1–64 个字符）"
                draft.name.isNotBlank() -> "技能名称：${draft.name}"
                else -> "粘贴完整的 SKILL.md 内容"
            },
            color = if (draft.nameMissing || draft.nameInvalid) scheme.error else scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Footnote,
            fontWeight = if (draft.nameMissing || draft.nameInvalid) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )

        SettingsGroup("保存位置") {
            OverlayDropdownPreference(
                items = SkillScope.entries.map { it.label },
                selectedIndex = SkillScope.entries.indexOf(draft.scope).coerceAtLeast(0),
                title = "作用域",
                summary = null,
                onSelectedIndexChange = { index ->
                    SkillScope.entries.getOrNull(index)?.let { scope -> onChange { it.copy(scope = scope) } }
                },
            )
        }

        Text(
            text = "名称会成为目录名。如果同名技能已存在，不会覆盖它，而是直接打开现有内容编辑。",
            color = scheme.onSurfaceVariantSummary,
            fontSize = ZhiTextScale.Footnote,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

// ------------------------------------------------------------------ 编辑器

@Composable
private fun SkillEditorPage(
    target: SkillEditTarget,
    onBodyChange: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    SettingsSubPage(
        title = "${target.name} / ${target.fileName}",
        onBack = onBack,
        action = "保存" to onSave,
    ) {
        Column {
            SettingsGroup(target.fileName) {
                ZhiTextField(
                    value = target.body,
                    onValueChange = onBodyChange,
                    label = target.fileName,
                    useLabelAsPlaceholder = false,
                    singleLine = false,
                    minLines = 10,
                    textStyle = MiuixTheme.textStyles.main.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .heightIn(min = 240.dp),
                )
            }
            Text(
                text = if (target.empty) "内容为空：保存后会生成一个空文件。"
                else "当前 ${target.body.length} 字。",
                color = if (target.empty) scheme.error else scheme.onSurfaceVariantSummary,
                fontSize = ZhiTextScale.Footnote,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ 新建文件

/**
 * 「新建文件」对话框。
 *
 * 重名会被 `WorkspaceViewModel.saveSkillFile` 挡掉并提示，不在这里做校验：
 * 判重需要读文件系统，放在对话框里会让每次输入都摸一次磁盘。
 */
@Composable
private fun SkillFileDialog(
    draft: SkillFileDraft,
    onChange: ((SkillFileDraft) -> SkillFileDraft) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    OverlayDialog(
        show = true,
        onDismissRequest = onDismiss,
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
                    onValueChange = { value -> onChange { it.copy(fileName = value) } },
                    label = "文件名",
                    useLabelAsPlaceholder = false,
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
                    text = "文件会建在这个技能自己的目录里，跟着技能一起分发。",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = ZhiTextScale.Footnote,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }
}
