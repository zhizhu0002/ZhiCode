package com.zhizhu.zhicode.compose.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.data.COLLAPSED_SESSION_LIMIT
import com.zhizhu.zhicode.compose.data.FileBrowser
import com.zhizhu.zhicode.compose.data.WorkspacePaths
import com.zhizhu.zhicode.compose.data.buildWorkspaceGroups
import com.zhizhu.zhicode.compose.data.canonicalWorkspacePath
import java.io.File
import com.zhizhu.zhicode.compose.model.SessionSummary
import com.zhizhu.zhicode.compose.model.SidebarOrderBy
import com.zhizhu.zhicode.compose.model.WorkspaceEntry
import com.zhizhu.zhicode.compose.model.WorkspaceGroup
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiSpace
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideInsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.DialogWideOutsideMargin
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiDialogWidth
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 侧栏订阅的轻量快照。 */
data class SidebarState(
    val projectName: String,
    val projectPath: String,
    val workspaces: List<WorkspaceEntry>,
    val sessions: List<SessionSummary>,
    val activeSessionId: String,
    val runtimeReady: Boolean,
    val runtimeInstalling: Boolean,
    val runtimeProgress: Int,
    val runtimeMessage: String,
    val sidebarOrderBy: SidebarOrderBy,
) {
    companion object {
        fun from(state: WorkspaceUiState) = SidebarState(
            projectName = state.projectName,
            projectPath = state.projectPath,
            workspaces = state.workspaces,
            sessions = state.sessions,
            activeSessionId = state.activeSessionId,
            runtimeReady = state.runtimeReady,
            runtimeInstalling = state.runtimeInstalling,
            runtimeProgress = state.runtimeProgress,
            runtimeMessage = state.runtimeMessage,
            sidebarOrderBy = state.sidebarOrderBy,
        )
    }
}

/** DeepSeek Harness 风格工作区与会话分组树侧栏。 */
@Composable
fun ZhiSidebar(
    state: SidebarState,
    onNewSession: () -> Unit,
    onOpenSession: (SessionSummary) -> Unit,
    onSessionActions: (SessionSummary) -> Unit,
    anchoredMenu: @Composable (String, DpOffset?) -> Unit,
    onSandbox: () -> Unit,
    onRuntime: () -> Unit,
    onSettings: () -> Unit,
    onUpdateProjectPath: (String) -> Unit = {},
    onAddWorkspace: (String, String) -> Unit = { path, _ -> onUpdateProjectPath(path) },
    onRenameWorkspace: (String, String) -> Unit = { _, _ -> },
    onRemoveWorkspace: (String) -> Unit = {},
    onToggleWorkspaceExpanded: (String) -> Unit = {},
    onSetAllWorkspacesExpanded: (Boolean) -> Unit = {},
    onSetSidebarOrderBy: (SidebarOrderBy) -> Unit = {},
    onNewSessionInWorkspace: (String) -> Unit = { _ -> onNewSession() },
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    var searchVisible by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var addDialogOpen by remember { mutableStateOf(false) }
    var browseDir by remember { mutableStateOf("") }
    var addPathDraft by remember(state.projectPath) { mutableStateOf(state.projectPath) }
    var renamingWorkspace by remember { mutableStateOf<WorkspaceGroup?>(null) }
    var renameLabelDraft by remember { mutableStateOf("") }
    var menuWorkspace by remember { mutableStateOf<WorkspaceGroup?>(null) }
    val watermarks = remember { mutableStateMapOf<String, Int>() }

    val defaultPath = remember { WorkspacePaths.defaultProject() }
    val groups = remember(
        state.workspaces,
        state.sessions,
        state.activeSessionId,
        state.projectPath,
        state.sidebarOrderBy,
        query,
        watermarks.toMap(),
    ) {
        buildWorkspaceGroups(
            registeredWorkspaces = state.workspaces,
            sessions = state.sessions,
            defaultWorkspacePath = defaultPath,
            activeSessionId = state.activeSessionId,
            activeProjectPath = state.projectPath,
            query = query,
            orderBy = state.sidebarOrderBy,
            watermarks = watermarks,
        )
    }

    val rootShortcuts = remember(defaultPath) {
        buildList {
            add("主目录" to defaultPath)
            val shared = File("/storage/emulated/0")
            if (shared.isDirectory && shared.canRead()) {
                add("共享存储" to shared.absolutePath)
            }
        }
    }
    val browseFolders = remember(addDialogOpen, browseDir) {
        if (addDialogOpen && browseDir.isNotBlank()) {
            FileBrowser.children(browseDir).filter { it.directory }
        } else {
            emptyList()
        }
    }

    Surface(modifier = modifier, color = ZhiColors.panelSurface()) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 10.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "工作区",
                    style = MiuixTheme.textStyles.headline1,
                    color = scheme.onSurfaceVariantSummary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                ZhiIconButton(
                    icon = ZhiIcons.search,
                    description = "搜索会话或工作区",
                    onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) {
                            query = ""
                            searchExpanded = false
                        }
                    },
                    iconSize = 18.dp,
                )
                ZhiIconDropdownMenu(
                    items = listOf(
                        ZhiMenuItem(
                            text = "按最近活跃排序",
                            selected = state.sidebarOrderBy == SidebarOrderBy.UPDATED,
                            onClick = { onSetSidebarOrderBy(SidebarOrderBy.UPDATED) },
                        ),
                        ZhiMenuItem(
                            text = "按添加顺序排序",
                            selected = state.sidebarOrderBy == SidebarOrderBy.MANUAL,
                            onClick = { onSetSidebarOrderBy(SidebarOrderBy.MANUAL) },
                        ),
                        ZhiMenuItem(
                            text = "展开全部工作区",
                            onClick = { onSetAllWorkspacesExpanded(true) },
                        ),
                        ZhiMenuItem(
                            text = "折叠全部工作区",
                            onClick = { onSetAllWorkspacesExpanded(false) },
                        ),
                    ),
                    backgroundColor = Color.Transparent,
                    minHeight = 32.dp,
                    minWidth = 32.dp,
                ) {
                    Icon(
                        painter = ZhiIcons.tune,
                        contentDescription = "视图选项",
                        tint = scheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(6.dp).size(18.dp),
                    )
                }
                ZhiIconButton(
                    icon = ZhiIcons.add,
                    description = "添加工作区",
                    onClick = {
                        val initialDir = state.projectPath.takeIf { File(it).isDirectory } ?: defaultPath
                        browseDir = initialDir
                        addPathDraft = initialDir
                        addDialogOpen = true
                    },
                    iconSize = 19.dp,
                )
            }

            AnimatedVisibility(
                visible = searchVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                InputField(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = {},
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SidebarInset, vertical = 4.dp),
                    label = "搜索工作区或会话",
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .padding(horizontal = SidebarInset)
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(SessionRowSpacing),
            ) {
                if (groups.isEmpty()) {
                    item(key = "empty-workspaces") {
                        Text(
                            text = if (query.isBlank()) "暂无会话，点击工作区右侧 + 开始对话" else "没有匹配的工作区或会话",
                            color = scheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = ZhiSpace.s),
                        )
                    }
                }
                groups.forEach { group ->
                    item(key = "ws:${group.key}", contentType = "workspace-header") {
                        WorkspaceHeaderRow(
                            group = group,
                            menuOpen = menuWorkspace?.key == group.key,
                            onToggle = { onToggleWorkspaceExpanded(group.path) },
                            onCreateSession = { onNewSessionInWorkspace(group.path) },
                            onOpenMenu = { menuWorkspace = group },
                            onDismissMenu = { menuWorkspace = null },
                            onSwitchActive = { onUpdateProjectPath(group.path) },
                            onRename = {
                                renameLabelDraft = group.title
                                renamingWorkspace = group
                            },
                            onRemove = { onRemoveWorkspace(group.path) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (group.expanded) {
                        items(
                            items = group.sessions,
                            key = { "s:${group.key}:${it.id}" },
                            contentType = { "session-row" },
                        ) { session ->
                            SessionRow(
                                session = session,
                                active = session.id == state.activeSessionId,
                                onOpen = { onOpenSession(session) },
                                onActions = { onSessionActions(session) },
                                anchoredMenu = anchoredMenu,
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (group.hiddenCount > 0) {
                            item(key = "more:${group.key}", contentType = "overflow-row") {
                                Text(
                                    text = "显示更多会话 (${group.hiddenCount})",
                                    color = scheme.onSurfaceVariantSummary,
                                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(SidebarRowCorner))
                                        .clickable {
                                            watermarks[group.key] = (watermarks[group.key] ?: COLLAPSED_SESSION_LIMIT) + COLLAPSED_SESSION_LIMIT
                                        }
                                        .padding(start = 24.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                                )
                            }
                        } else if ((watermarks[group.key] ?: COLLAPSED_SESSION_LIMIT) > COLLAPSED_SESSION_LIMIT && group.totalCount > COLLAPSED_SESSION_LIMIT) {
                            item(key = "less:${group.key}", contentType = "overflow-row") {
                                Text(
                                    text = "收起部分会话",
                                    color = scheme.onSurfaceVariantSummary,
                                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(SidebarRowCorner))
                                        .clickable { watermarks.remove(group.key) }
                                        .padding(start = 24.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            SmallTitle(text = "更多")
            SidebarGroup {
                SidebarRow(label = "沙箱", icon = ZhiIcons.sandbox, onClick = onSandbox)
                val runtimeText = when {
                    state.runtimeInstalling -> "环境准备中 ${state.runtimeProgress}%"
                    state.runtimeReady -> "运行环境就绪"
                    else -> "准备内置 Debian 环境"
                }
                SidebarRow(
                    label = runtimeText,
                    icon = ZhiIcons.runtime,
                    tint = if (state.runtimeReady) ZhiColors.green() else Color.Unspecified,
                    onClick = onRuntime,
                )
                SidebarRow(label = "设置", icon = ZhiIcons.settings, onClick = onSettings)
            }
        }
    }

    OverlayDialog(
        show = addDialogOpen,
        onDismissRequest = { addDialogOpen = false },
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        DialogShell(
            title = "选择工作区目录",
            actions = {
                SecondaryButton(text = "取消", onClick = { addDialogOpen = false })
                PrimaryButton(
                    text = "选择此目录",
                    enabled = addPathDraft.trim().isNotEmpty(),
                    onClick = {
                        onAddWorkspace(addPathDraft.trim(), "")
                        addDialogOpen = false
                    },
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            if (rootShortcuts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rootShortcuts.forEach { (label, rootPath) ->
                        val activeRoot = canonicalWorkspacePath(browseDir) == canonicalWorkspacePath(rootPath)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (activeRoot) scheme.primaryContainer else scheme.secondaryContainer,
                                )
                                .clickable {
                                    browseDir = rootPath
                                    addPathDraft = rootPath
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = label,
                                color = if (activeRoot) scheme.onPrimaryContainer else scheme.onSecondaryContainer,
                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            )
                        }
                    }
                }
            }
            val parentDir = remember(browseDir) {
                File(browseDir).parentFile?.takeIf { it.isDirectory && it.canRead() }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.surfaceContainer)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (parentDir != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.secondaryContainer)
                            .clickable {
                                browseDir = parentDir.absolutePath
                                addPathDraft = parentDir.absolutePath
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painter = ZhiIcons.back,
                            contentDescription = "上一级",
                            tint = scheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "上一级",
                            color = scheme.onSecondaryContainer,
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        )
                    }
                }
                BasicTextField(
                    value = addPathDraft,
                    onValueChange = { next ->
                        addPathDraft = next
                        val trimmed = next.trim()
                        if (trimmed.isNotEmpty() && File(trimmed).isDirectory) {
                            browseDir = trimmed
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = scheme.onSurface,
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    ),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val trimmed = addPathDraft.trim()
                            if (trimmed.isNotEmpty() && File(trimmed).isDirectory) {
                                browseDir = trimmed
                            }
                        },
                    ),
                    decorationBox = { innerTextField ->
                        if (addPathDraft.isEmpty()) {
                            Text(
                                text = "输入路径回车跳转",
                                color = scheme.onSurfaceVariantSummary,
                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 250.dp),
                cornerRadius = 12.dp,
                colors = CardDefaults.defaultColors(color = scheme.surfaceContainer),
            ) {
                if (browseFolders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "当前目录下无可选子文件夹",
                            color = scheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 250.dp)
                            .padding(vertical = 4.dp),
                    ) {
                        items(browseFolders, key = { it.path }) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        browseDir = entry.path
                                        addPathDraft = entry.path
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    painter = ZhiIcons.directory,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = entry.name,
                                    color = scheme.onSurface,
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    painter = ZhiIcons.arrowRight,
                                    contentDescription = null,
                                    tint = scheme.onSurfaceVariantSummary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val activeRenameTarget = renamingWorkspace
    OverlayDialog(
        show = activeRenameTarget != null,
        onDismissRequest = { renamingWorkspace = null },
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
        outsideMargin = DialogWideOutsideMargin,
        insideMargin = DialogWideInsideMargin,
    ) {
        DialogShell(
            title = "重命名工作区",
            actions = {
                SecondaryButton(text = "取消", onClick = { renamingWorkspace = null })
                PrimaryButton(
                    text = "保存",
                    enabled = renameLabelDraft.trim().isNotEmpty(),
                    onClick = {
                        activeRenameTarget?.let { onRenameWorkspace(it.path, renameLabelDraft.trim()) }
                        renamingWorkspace = null
                    },
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            ZhiTextField(
                value = renameLabelDraft,
                onValueChange = { renameLabelDraft = it },
                label = "工作区名称",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceHeaderRow(
    group: WorkspaceGroup,
    menuOpen: Boolean,
    onToggle: () -> Unit,
    onCreateSession: () -> Unit,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onSwitchActive: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    val items = buildList {
        add(DropdownItem(text = "在此工作区新建会话", onClick = { onDismissMenu(); onCreateSession() }))
        add(DropdownItem(text = "设为当前工作区", onClick = { onDismissMenu(); onSwitchActive() }))
        if (!group.isDefault) {
            add(DropdownItem(text = "重命名工作区", onClick = { onDismissMenu(); onRename() }))
            add(DropdownItem(text = "从侧栏移除", onClick = { onDismissMenu(); onRemove() }))
        }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            onClick = onToggle,
            onLongPress = onOpenMenu,
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = SidebarRowCorner,
            colors = CardDefaults.defaultColors(color = Color.Transparent, contentColor = scheme.onSurface),
            pressFeedbackType = PressFeedbackType.Sink,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 2.dp, top = 7.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = if (group.expanded) ZhiIcons.collapse else ZhiIcons.expand,
                    contentDescription = if (group.expanded) "收起" else "展开",
                    tint = scheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                val titleColor = if (group.containsActive) scheme.primary else scheme.onSurfaceVariantSummary
                Text(
                    text = buildAnnotatedString {
                        append(group.title)
                        withStyle(
                            SpanStyle(
                                color = scheme.onSurfaceVariantSummary.copy(alpha = 0.78f),
                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                fontWeight = FontWeight.Normal,
                            ),
                        ) {
                            append(" (${group.path})")
                        }
                    },
                    style = MiuixTheme.textStyles.headline1,
                    color = titleColor,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ZhiIconButton(
                    icon = ZhiIcons.add,
                    description = "在 ${group.title} 新建会话",
                    onClick = onCreateSession,
                    iconSize = 15.dp,
                    compact = 28.dp,
                )
            }
        }
        OverlayDropdownPopup(
            entries = listOf(DropdownEntry(items = items)),
            show = menuOpen,
            onDismiss = onDismissMenu,
            onDismissFinished = {},
            maxHeight = 320.dp,
            dropdownColors = DropdownDefaults.dropdownColors(),
            renderInRootScaffold = false,
            collapseOnSelection = true,
        )
    }
}

@Composable
private fun SidebarGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = SidebarInset),
        content = content,
    )
}

@Composable
private fun SidebarRow(
    label: String,
    onClick: () -> Unit,
    icon: Painter? = null,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    iconTint: Color = Color.Unspecified,
) {
    val scheme = MiuixTheme.colorScheme
    val foreground = if (tint != Color.Unspecified) tint else scheme.onSurface
    val glyph = if (iconTint != Color.Unspecified) iconTint else foreground
    BasicComponent(
        onClick = onClick,
        title = label,
        titleColor = BasicComponentDefaults.titleColor(color = foreground),
        insideMargin = SidebarRowInsideMargin,
        startAction = icon?.let {
            {
                Icon(
                    painter = it,
                    contentDescription = null,
                    tint = glyph,
                    modifier = Modifier.size(SidebarRowIconSize),
                )
            }
        },
        modifier = modifier.fillMaxWidth().heightIn(max = SidebarRowMinHeight),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: SessionSummary,
    active: Boolean,
    onOpen: () -> Unit,
    onActions: () -> Unit,
    anchoredMenu: @Composable (String, DpOffset?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val finger = rememberFingerTracker()
    var fingerOffset by remember { mutableStateOf<DpOffset?>(null) }
    Box(modifier = modifier.then(finger.modifier)) {
        SessionRowInner(
            session = session,
            active = active,
            onOpen = onOpen,
            onActions = {
                fingerOffset = finger.offset()
                onActions()
            },
        )
        anchoredMenu(session.id, fingerOffset)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRowInner(
    session: SessionSummary,
    active: Boolean,
    onOpen: () -> Unit,
    onActions: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (active) scheme.secondaryContainer.copy(alpha = 0.78f) else Color.Transparent,
        animationSpec = ZhiMotion.colorSpec,
        label = "sessionRowBackground",
    )
    val titleColor = when {
        active -> scheme.onSecondaryContainer
        session.busy -> scheme.primary
        else -> scheme.onSurface
    }
    Card(
        onClick = onOpen,
        onLongPress = onActions,
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = SidebarRowCorner,
        colors = CardDefaults.defaultColors(color = background, contentColor = titleColor),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SessionRowMinHeight)
                .padding(start = 22.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (session.busy) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
            }
            Text(
                text = session.title,
                style = MiuixTheme.textStyles.headline1,
                color = titleColor,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (session.updatedAtLabel.isNotBlank()) {
                Text(
                    text = session.updatedAtLabel,
                    style = MiuixTheme.textStyles.footnote1,
                    color = scheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

private val SidebarInset = 12.dp
private val SidebarRowHorizontal = 16.dp
private val SidebarRowInsideMargin = PaddingValues(horizontal = SidebarRowHorizontal, vertical = 4.dp)
private val SidebarRowMinHeight = 44.dp
private val SessionRowMinHeight = 40.dp
private val SidebarRowIconSize = 20.dp
private val SidebarRowCorner = 12.dp
private val SessionRowSpacing = 2.dp
