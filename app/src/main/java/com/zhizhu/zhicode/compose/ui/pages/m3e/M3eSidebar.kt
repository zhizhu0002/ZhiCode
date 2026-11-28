package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.data.COLLAPSED_SESSION_LIMIT
import com.zhizhu.zhicode.compose.data.FileBrowser
import com.zhizhu.zhicode.compose.data.WorkspacePaths
import com.zhizhu.zhicode.compose.data.buildWorkspaceGroups
import com.zhizhu.zhicode.compose.data.canonicalWorkspacePath
import com.zhizhu.zhicode.compose.model.SessionSummary
import com.zhizhu.zhicode.compose.model.SidebarOrderBy
import com.zhizhu.zhicode.compose.model.WorkspaceGroup
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.SidebarState
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import java.io.File
import androidx.compose.ui.unit.Dp

@Composable
internal fun M3eSidebarHost(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    modifier: Modifier = Modifier,
) {
    M3eSidebar(
        state = SidebarState.from(state),
        workspaceState = state,
        onOpenSession = viewModel::openSession,
        onSessionActions = viewModel::showSessionActions,
        onSessionActionSelect = viewModel::onChoiceSelected,
        onDismissSessionActions = viewModel::dismissChoicePicker,
        onSandbox = viewModel::openSandbox,
        onRuntime = viewModel::openEnvironment,
        onSettings = viewModel::openSettings,
        onUpdateProjectPath = viewModel::updateProjectDirectory,
        onAddWorkspace = { path, label -> viewModel.addWorkspace(path, label, switchToIt = true) },
        onRenameWorkspace = viewModel::renameWorkspace,
        onRemoveWorkspace = viewModel::removeWorkspace,
        onToggleWorkspaceExpanded = viewModel::toggleWorkspaceExpanded,
        onSetAllWorkspacesExpanded = viewModel::setAllWorkspacesExpanded,
        onSetSidebarOrderBy = viewModel::setSidebarOrderBy,
        onNewSessionInWorkspace = viewModel::newSessionInWorkspace,
        modifier = modifier,
    )
}

@Composable
private fun M3eSidebar(
    state: SidebarState,
    workspaceState: WorkspaceUiState,
    onOpenSession: (SessionSummary) -> Unit,
    onSessionActions: (SessionSummary) -> Unit,
    onSessionActionSelect: (Int) -> Unit,
    onDismissSessionActions: () -> Unit,
    onSandbox: () -> Unit,
    onRuntime: () -> Unit,
    onSettings: () -> Unit,
    onUpdateProjectPath: (String) -> Unit,
    onAddWorkspace: (String, String) -> Unit,
    onRenameWorkspace: (String, String) -> Unit,
    onRemoveWorkspace: (String) -> Unit,
    onToggleWorkspaceExpanded: (String) -> Unit,
    onSetAllWorkspacesExpanded: (Boolean) -> Unit,
    onSetSidebarOrderBy: (SidebarOrderBy) -> Unit,
    onNewSessionInWorkspace: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var searchVisible by remember { mutableStateOf(false) }
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
            if (shared.isDirectory && shared.canRead()) add("共享存储" to shared.absolutePath)
        }
    }
    val browseFolders = remember(addDialogOpen, browseDir) {
        if (addDialogOpen && browseDir.isNotBlank()) {
            FileBrowser.children(browseDir).filter { it.directory }
        } else {
            emptyList()
        }
    }

    Surface(modifier = modifier, color = scheme.surfaceContainerLow) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 10.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "工作区",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                    color = scheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                SidebarIconButton(
                    icon = ZhiIcons.search,
                    description = "搜索会话或工作区",
                    onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) query = ""
                    },
                    iconSize = 18.dp,
                )
                Box {
                    var orderMenuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { orderMenuOpen = true }) {
                        Icon(
                            painter = ZhiIcons.tune,
                            contentDescription = "视图选项",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = orderMenuOpen,
                        onDismissRequest = { orderMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("按最近活跃排序") },
                            leadingIcon = { SidebarMenuCheck(state.sidebarOrderBy == SidebarOrderBy.UPDATED) },
                            onClick = { orderMenuOpen = false; onSetSidebarOrderBy(SidebarOrderBy.UPDATED) },
                        )
                        DropdownMenuItem(
                            text = { Text("按添加顺序排序") },
                            leadingIcon = { SidebarMenuCheck(state.sidebarOrderBy == SidebarOrderBy.MANUAL) },
                            onClick = { orderMenuOpen = false; onSetSidebarOrderBy(SidebarOrderBy.MANUAL) },
                        )
                        DropdownMenuItem(
                            text = { Text("展开全部工作区") },
                            onClick = { orderMenuOpen = false; onSetAllWorkspacesExpanded(true) },
                        )
                        DropdownMenuItem(
                            text = { Text("折叠全部工作区") },
                            onClick = { orderMenuOpen = false; onSetAllWorkspacesExpanded(false) },
                        )
                    }
                }
                SidebarIconButton(
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

            // 搜索框进/出：位置与尺寸走 spatial，透明度走 effects
            val motion = MaterialTheme.motionScheme
            AnimatedVisibility(
                visible = searchVisible,
                enter = fadeIn(motion.defaultEffectsSpec()) +
                    expandVertically(motion.defaultSpatialSpec()),
                exit = fadeOut(motion.fastEffectsSpec()) +
                    shrinkVertically(motion.fastSpatialSpec()),
            ) {
                SidebarSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SidebarInset, vertical = 4.dp),
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
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
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
                                workspaceState = workspaceState,
                                onOpen = { onOpenSession(session) },
                                onActions = { onSessionActions(session) },
                                onSelectAction = onSessionActionSelect,
                                onDismissActions = onDismissSessionActions,
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (group.hiddenCount > 0) {
                            item(key = "more:${group.key}", contentType = "overflow-row") {
                                Text(
                                    text = "显示更多会话 (${group.hiddenCount})",
                                    color = scheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem()
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
                                    color = scheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                        .clip(RoundedCornerShape(SidebarRowCorner))
                                        .clickable { watermarks.remove(group.key) }
                                        .padding(start = 24.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "更多",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 18.dp, top = 6.dp, bottom = 4.dp),
            )
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

    if (addDialogOpen) {
        AlertDialog(
            onDismissRequest = { addDialogOpen = false },
            title = { Text("选择工作区目录") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (rootShortcuts.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            rootShortcuts.forEach { (label, rootPath) ->
                                val activeRoot = canonicalWorkspacePath(browseDir) == canonicalWorkspacePath(rootPath)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (activeRoot) scheme.primaryContainer else scheme.secondaryContainer,
                                    modifier = Modifier.clickable {
                                        browseDir = rootPath
                                        addPathDraft = rootPath
                                    },
                                ) {
                                    Text(
                                        text = label,
                                        color = if (activeRoot) scheme.onPrimaryContainer else scheme.onSecondaryContainer,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.surfaceContainer)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (parentDir != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = scheme.secondaryContainer,
                                modifier = Modifier.clickable {
                                    browseDir = parentDir.absolutePath
                                    addPathDraft = parentDir.absolutePath
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        painter = ZhiIcons.back,
                                        contentDescription = "上一级",
                                        tint = scheme.onSecondaryContainer,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text("上一级", color = scheme.onSecondaryContainer, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        BasicTextField(
                            value = addPathDraft,
                            onValueChange = { next ->
                                addPathDraft = next
                                val trimmed = next.trim()
                                if (trimmed.isNotEmpty() && File(trimmed).isDirectory) browseDir = trimmed
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = scheme.onSurface),
                            cursorBrush = SolidColor(scheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                val trimmed = addPathDraft.trim()
                                if (trimmed.isNotEmpty() && File(trimmed).isDirectory) browseDir = trimmed
                            }),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (addPathDraft.isEmpty()) {
                                        Text(
                                            text = "输入路径回车跳转",
                                            color = scheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 250.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.surfaceContainer,
                    ) {
                        if (browseFolders.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(140.dp).padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "当前目录下无可选子文件夹",
                                    color = scheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 250.dp).padding(vertical = 4.dp),
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
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Icon(
                                            painter = ZhiIcons.arrowRight,
                                            contentDescription = null,
                                            tint = scheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = addPathDraft.trim().isNotEmpty(),
                    onClick = {
                        onAddWorkspace(addPathDraft.trim(), "")
                        addDialogOpen = false
                    },
                ) { Text("选择此目录") }
            },
            dismissButton = {
                TextButton(onClick = { addDialogOpen = false }) { Text("取消") }
            },
        )
    }

    val activeRenameTarget = renamingWorkspace
    if (activeRenameTarget != null) {
        AlertDialog(
            onDismissRequest = { renamingWorkspace = null },
            title = { Text("重命名工作区") },
            text = {
                OutlinedTextField(
                    value = renameLabelDraft,
                    onValueChange = { renameLabelDraft = it },
                    placeholder = { Text("工作区名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    enabled = renameLabelDraft.trim().isNotEmpty(),
                    onClick = {
                        onRenameWorkspace(activeRenameTarget.path, renameLabelDraft.trim())
                        renamingWorkspace = null
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renamingWorkspace = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SidebarIconButton(
    icon: Painter,
    description: String,
    onClick: () -> Unit,
    iconSize: Dp,
) {
    IconButton(onClick = onClick) {
        Icon(
            painter = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun SidebarMenuCheck(checked: Boolean) {
    if (checked) {
        Icon(painter = ZhiIcons.check, contentDescription = null, modifier = Modifier.size(18.dp))
    } else {
        Spacer(Modifier.size(18.dp))
    }
}

@Composable
private fun SidebarSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        color = scheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = ZhiIcons.search,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Box(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                if (query.isEmpty()) {
                    Text("搜索工作区或会话", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        painter = ZhiIcons.close,
                        contentDescription = "清除搜索",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
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
    val scheme = MaterialTheme.colorScheme
    val titleColor by animateColorAsState(
        targetValue = if (group.containsActive) scheme.primary else scheme.onSurfaceVariant,
        animationSpec = ZhiSidebarMotion,
        label = "workspaceHeaderColor",
    )
    val arrowRotation by animateFloatAsState(
        targetValue = if (group.expanded) 90f else 0f,
        // 箭头旋转是"空间"动效（角度 = 位置），走 spatial 的 fast 档：
        // 它紧跟着手指/点击发生，用 default 会显得拖沓。
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "workspaceHeaderArrow",
    )
    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SidebarRowCorner))
                .combinedClickable(onClick = onToggle, onLongClick = onOpenMenu),
            shape = RoundedCornerShape(SidebarRowCorner),
            color = Color.Transparent,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 2.dp, top = 7.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = ZhiIcons.expand,
                    contentDescription = if (group.expanded) "收起" else "展开",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = arrowRotation },
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = buildAnnotatedString {
                        append(group.title)
                        withStyle(
                            SpanStyle(
                                color = scheme.onSurfaceVariant.copy(alpha = 0.78f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                        ) {
                            append(" (${group.path})")
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                    color = titleColor,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                SidebarIconButton(
                    icon = ZhiIcons.add,
                    description = "在 ${group.title} 新建会话",
                    onClick = onCreateSession,
                    iconSize = 15.dp,
                )
            }
        }
        WorkspaceMenu(
            expanded = menuOpen,
            isDefault = group.isDefault,
            onDismiss = onDismissMenu,
            onCreateSession = onCreateSession,
            onSwitchActive = onSwitchActive,
            onRename = onRename,
            onRemove = onRemove,
        )
    }
}

@Composable
private fun WorkspaceMenu(
    expanded: Boolean,
    isDefault: Boolean,
    onDismiss: () -> Unit,
    onCreateSession: () -> Unit,
    onSwitchActive: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("在此工作区新建会话") },
            onClick = { onDismiss(); onCreateSession() },
        )
        DropdownMenuItem(
            text = { Text("设为当前工作区") },
            onClick = { onDismiss(); onSwitchActive() },
        )
        if (!isDefault) {
            DropdownMenuItem(
                text = { Text("重命名工作区") },
                onClick = { onDismiss(); onRename() },
            )
            DropdownMenuItem(
                text = { Text("从侧栏移除") },
                onClick = { onDismiss(); onRemove() },
            )
        }
    }
}

@Composable
private fun SidebarGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = SidebarInset),
        shape = RoundedCornerShape(SidebarRowCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
) {
    val scheme = MaterialTheme.colorScheme
    val foreground = if (tint != Color.Unspecified) tint else scheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SidebarRowMinHeight)
            .clip(RoundedCornerShape(SidebarRowCorner))
            .clickable(onClick = onClick)
            .padding(SidebarRowInsideMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(SidebarRowIconSize),
            )
            Spacer(Modifier.width(16.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: SessionSummary,
    active: Boolean,
    workspaceState: WorkspaceUiState,
    onOpen: () -> Unit,
    onActions: () -> Unit,
    onSelectAction: (Int) -> Unit,
    onDismissActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (active) scheme.secondaryContainer.copy(alpha = 0.78f) else Color.Transparent,
        animationSpec = ZhiSidebarMotion,
        label = "sessionRowBackground",
    )
    val titleColor = when {
        active -> scheme.onSecondaryContainer
        session.busy -> scheme.primary
        else -> scheme.onSurface
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SidebarRowCorner))
                .combinedClickable(onClick = onOpen, onLongClick = onActions),
            shape = RoundedCornerShape(SidebarRowCorner),
            color = background,
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
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                    color = titleColor,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (session.updatedAtLabel.isNotBlank()) {
                    Text(
                        text = session.updatedAtLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }
        SessionActionMenu(
            state = workspaceState,
            anchorId = session.id,
            onSelect = onSelectAction,
            onDismiss = onDismissActions,
        )
    }
}

@Composable
private fun SessionActionMenu(
    state: WorkspaceUiState,
    anchorId: String,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker = state.choicePicker
    val shownPicker = rememberLastNonNull(picker?.takeIf { it.isActionMenu && it.anchorId == anchorId }) ?: return
    val open = picker?.let { it.isActionMenu && it.anchorId == anchorId } == true
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        shownPicker.options.forEachIndexed { index, option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                onClick = { onSelect(index) },
            )
        }
    }
}

private val SidebarInset = 12.dp
private val SidebarRowInsideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)
private val SidebarRowMinHeight = 44.dp
private val SessionRowMinHeight = 40.dp
private val SidebarRowIconSize = 20.dp
private val SidebarRowCorner = 12.dp
private val SessionRowSpacing = 2.dp
private val ZhiSidebarMotion = ZhiMotion.colorSpec
