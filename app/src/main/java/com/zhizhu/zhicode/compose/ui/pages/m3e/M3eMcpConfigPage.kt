@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.McpConfigState
import com.zhizhu.zhicode.compose.model.McpScope
import com.zhizhu.zhicode.compose.model.McpServer
import com.zhizhu.zhicode.compose.model.McpServerDraft
import com.zhizhu.zhicode.compose.model.McpServerStatus
import com.zhizhu.zhicode.compose.model.McpToolInfo
import com.zhizhu.zhicode.compose.model.McpType
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.rememberActionBusy
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import com.zhizhu.zhicode.compose.data.Clipboard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun M3eMcpConfigPage(
    config: McpConfigState?,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onEdit: (McpServer) -> Unit,
    onToggle: (McpServer) -> Unit,
    onDelete: (McpServer) -> Unit,
    onDraftChange: ((McpServerDraft) -> McpServerDraft) -> Unit,
    onSave: () -> Unit,
    onCancelForm: () -> Unit,
    onTest: (String) -> Unit,
    onToolOptionsChange: (String, String, Boolean, Boolean) -> Unit,
    onOpenImport: () -> Unit,
    onImportTextChange: (String) -> Unit,
    onImportConfirm: () -> Unit,
    onImportCancel: () -> Unit,
    onTestAll: () -> Unit,
    onQueryChange: (String) -> Unit,
) {
    if (config == null) return
    val form = config.form
    val shownForm = rememberLastNonNull(form)
    val shownImportText = rememberLastNonNull(config.importText)
    var errorDetail by remember { mutableStateOf<Pair<String, String>?>(null) }
    val shownErrorDetail = rememberLastNonNull(errorDetail)
    var confirmDiscard by remember { mutableStateOf(false) }
    val saving = rememberActionBusy("mcp.save")

    val requestCancelForm: () -> Unit = {
        if (form?.dirty == true) confirmDiscard = true else onCancelForm()
    }
    BackHandler(enabled = form != null, onBack = requestCancelForm)

    if (form == null) {
        M3eSubPageScaffold(
            title = "MCP 服务器",
            onBack = onDismiss,
            actions = {
                TextButton(onClick = onOpenImport) { Text("导入") }
                TextButton(onClick = onNew) { Text("新增") }
            },
        ) { padding, _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                M3eServerList(
                    config = config,
                    onEdit = onEdit,
                    onToggle = onToggle,
                    onDelete = onDelete,
                    onTest = onTest,
                    onTestAll = onTestAll,
                    onQueryChange = onQueryChange,
                    onShowError = { name, detail -> errorDetail = name to detail },
                )
            }
        }
    } else {
        val draft = shownForm ?: return
        var tab by remember { mutableIntStateOf(0) }
        M3eSubPageScaffold(
            title = if (draft.isEditing) "编辑 MCP 服务器" else "添加 MCP 服务器",
            onBack = requestCancelForm,
            actions = {
                TextButton(onClick = onSave, enabled = draft.saveable && !saving) { Text("保存") }
            },
        ) { padding, _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = M3eDimens.RowPaddingH, vertical = 8.dp),
                ) {
                    McpFormTabs.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index, McpFormTabs.size),
                            selected = tab == index,
                            onClick = { tab = index },
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
                if (tab == 0) {
                    M3eConnectionForm(draft = draft, onChange = onDraftChange)
                } else {
                    M3eToolsTab(
                        draft = draft,
                        status = config.status[draft.name]
                            ?: draft.originalName?.let { config.status[it] },
                        testing = config.testing.contains(draft.name),
                        onTest = { onTest(draft.name) },
                        onToolOptionsChange = { tool, enabled, approval ->
                            onToolOptionsChange(draft.name, tool, enabled, approval)
                        },
                    )
                }
            }
        }
    }

    shownErrorDetail?.let { detail ->
        McpTextDetailDialog(
            show = errorDetail != null,
            title = detail.first,
            body = detail.second,
            onDismiss = { errorDetail = null },
        )
    }
    shownImportText?.let { text ->
        McpImportDialog(
            show = config.importText != null,
            text = text,
            error = config.importError,
            onChange = onImportTextChange,
            onConfirm = onImportConfirm,
            onDismiss = onImportCancel,
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃未保存的修改？") },
            text = { Text("离开后这些改动不会写进配置文件。") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDiscard = false
                        onCancelForm()
                    },
                ) { Text("放弃修改") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") }
            },
        )
    }
}

private val McpFormTabs = listOf("基本设置", "工具")

/** 列表页：文件位置、汇总、筛选、每台一张卡。 */
@Composable
private fun M3eServerList(
    config: McpConfigState,
    onEdit: (McpServer) -> Unit,
    onToggle: (McpServer) -> Unit,
    onDelete: (McpServer) -> Unit,
    onTest: (String) -> Unit,
    onTestAll: () -> Unit,
    onQueryChange: (String) -> Unit,
    onShowError: (String, String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Text(
            text = "配置文件：${config.filePath}",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
        )

        if (config.loading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoadingIndicator(modifier = Modifier.size(20.dp))
                Text(
                    text = "正在加载…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        } else if (config.servers.isEmpty()) {
            McpEmptyState()
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = M3eDimens.RowPaddingH, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        append("${config.servers.size} 台 · ${config.enabledCount} 台启用")
                        val tested = config.status.values
                        if (tested.isNotEmpty()) {
                            append(" · ").append(tested.count { it.connected }).append(" 台已连接")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                val testing = config.testing.isNotEmpty()
                TextButton(
                    onClick = onTestAll,
                    enabled = !testing && config.enabledCount > 0,
                ) { Text(if (testing) "测试中…" else "全部测试") }
            }

            if (config.servers.size > 3) {
                OutlinedTextField(
                    value = config.query,
                    onValueChange = onQueryChange,
                    label = { Text("筛选服务器（名称 / 命令 / 地址）") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            painter = ZhiIcons.search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
                )
            }

            val visible = config.visibleServers
            if (visible.isEmpty()) {
                Text(
                    text = "没有匹配「${config.query}」的服务器。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = M3eDimens.RowPaddingH, vertical = 12.dp),
                )
            }
            visible.forEach { server ->
                McpServerCard(
                    server = server,
                    status = config.status[server.name],
                    testing = config.testing.contains(server.name),
                    onEdit = { onEdit(server) },
                    onToggle = { onToggle(server) },
                    onDelete = { onDelete(server) },
                    onTest = { onTest(server.name) },
                    onShowError = { detail -> onShowError(server.name, detail) },
                )
            }
        }

        Text(
            text = "测试连接会真的启动本地进程或访问远端地址（每台最长等 30 秒），" +
                "所以只在点「测试连接」时才执行 —— 打开这一页不会自动跑。" +
                "停用的服务器不会被 Agent 调用，也不会被测试。",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = 12.dp),
        )
    }
}

@Composable
private fun McpEmptyState() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = ZhiIcons.sandbox,
            contentDescription = null,
            tint = scheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = "没有 MCP 服务器",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
        )
        Text(
            text = "点右上角「新增」添加，或「导入」粘贴一份 JSON 配置",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun McpServerCard(
    server: McpServer,
    status: McpServerStatus?,
    testing: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    onShowError: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = M3eDimens.RowPaddingH, vertical = 4.dp),
        shape = m3eSegmentShape(0, 1),
        color = scheme.surfaceContainer,
        onClick = onEdit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = M3eDimens.RowPaddingH, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                when {
                    testing -> LoadingIndicator(modifier = Modifier.size(16.dp))
                    status?.connected == true -> Icon(
                        painter = ZhiIcons.done,
                        contentDescription = "已连接",
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    status != null -> Icon(
                        painter = ZhiIcons.failed,
                        contentDescription = "连接失败",
                        tint = scheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    else -> Icon(
                        painter = ZhiIcons.sandbox,
                        contentDescription = "未测试",
                        tint = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (server.enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(6.dp)
                            .background(
                                color = if (server.enabled) scheme.primary else scheme.error,
                                shape = CircleShape,
                            ),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    M3ePill(label = mcpTransportLabel(server.type))
                    M3ePill(label = if (server.scope == McpScope.PROJECT) "项目级" else "用户级")
                }
                McpStatusLine(
                    status = status,
                    testing = testing,
                    onShowError = onShowError,
                )
                Text(
                    text = server.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                TextButton(onClick = { menuOpen = true }) {
                    Icon(
                        painter = ZhiIcons.more,
                        contentDescription = "更多操作",
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("测试连接") }, onClick = { menuOpen = false; onTest() })
                    DropdownMenuItem(text = { Text("编辑") }, onClick = { menuOpen = false; onEdit() })
                    DropdownMenuItem(
                        text = { Text(if (server.enabled) "停用" else "启用") },
                        onClick = { menuOpen = false; onToggle() },
                    )
                    DropdownMenuItem(text = { Text("删除") }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun McpStatusLine(
    status: McpServerStatus?,
    testing: Boolean,
    onShowError: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyMedium
    when {
        testing -> Row(verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(modifier = Modifier.size(13.dp))
            Text(
                text = "正在连接…",
                style = style,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        status == null -> Text(
            text = "未测试 · 点 ⋮ 里的「测试连接」",
            style = style,
            color = scheme.onSurfaceVariant,
        )

        status.connected -> Text(
            text = buildString {
                append("已连接 · 提供 ${status.tools.size} 个工具")
                val off = status.tools.count { !it.enabled }
                if (off > 0) append("（其中 $off 个已停用）")
            },
            style = style,
            color = scheme.primary,
        )

        else -> Text(
            text = "连接失败：${status.error}（点这里看全文）",
            style = style,
            color = scheme.error,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .clickable { onShowError(status.error) },
        )
    }
}

@Composable
private fun M3ePill(label: String) {
    Surface(
        shape = CircleShape,
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

private fun mcpTransportLabel(type: McpType): String = when (type) {
    McpType.STDIO -> "stdio"
    McpType.HTTP -> "HTTP"
    McpType.SSE -> "SSE"
}

@Composable
private fun M3eConnectionForm(
    draft: McpServerDraft,
    onChange: ((McpServerDraft) -> McpServerDraft) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var nameTouched by remember(draft.originalName) { mutableStateOf(false) }
    var commandTouched by remember(draft.originalName) { mutableStateOf(false) }
    var urlTouched by remember(draft.originalName) { mutableStateOf(false) }
    var envTouched by remember(draft.originalName) { mutableStateOf(false) }
    var headersTouched by remember(draft.originalName) { mutableStateOf(false) }

    Column {
        M3eSectionHeader("服务器")
        OutlinedTextField(
            value = draft.name,
            onValueChange = { v ->
                nameTouched = true
                onChange { it.copy(name = v) }
            },
            label = { Text("服务器名称") },
            singleLine = true,
            isError = nameTouched && draft.nameError != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
        )
        McpFieldError(draft.nameError, nameTouched)

        M3eSegmentedColumn {
            M3eChoiceRow(
                index = 0,
                count = 3,
                title = "连接方式",
                options = McpType.entries.map { it.label },
                selectedIndex = McpType.entries.indexOf(draft.type).coerceAtLeast(0),
                onSelect = { index ->
                    McpType.entries.getOrNull(index)?.let { type -> onChange { it.copy(type = type) } }
                },
            )
            M3eSwitchRow(
                index = 1,
                count = 3,
                title = "启用",
                summary = "停用后配置仍然保留，但 Agent 不会调用该服务器，也不会被测试。",
                checked = draft.enabled,
                onCheckedChange = { v -> onChange { it.copy(enabled = v) } },
            )
            M3eChoiceRow(
                index = 2,
                count = 3,
                title = "作用范围",
                options = McpScope.entries.map { it.label },
                selectedIndex = McpScope.entries.indexOf(draft.scope).coerceAtLeast(0),
                onSelect = { index ->
                    McpScope.entries.getOrNull(index)?.let { scope -> onChange { it.copy(scope = scope) } }
                },
            )
        }

        M3eSectionHeader(if (draft.type.needsCommand) "启动参数" else "HTTP 连接")
        if (draft.type.needsCommand) {
            OutlinedTextField(
                value = draft.command,
                onValueChange = { v ->
                    commandTouched = true
                    onChange { it.copy(command = v) }
                },
                label = { Text("启动命令") },
                singleLine = true,
                isError = commandTouched && draft.commandError != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
            )
            McpFieldError(draft.commandError, commandTouched)
            OutlinedTextField(
                value = draft.argsText,
                onValueChange = { v -> onChange { it.copy(argsText = v) } },
                label = { Text("命令参数（每行一个）") },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
            )
            McpKeyValueEditor(
                title = "环境变量（可选）",
                value = draft.envText,
                onChange = { v ->
                    envTouched = true
                    onChange { it.copy(envText = v) }
                },
                resetKey = draft.originalName,
                error = draft.envError,
                touched = envTouched,
                keyHint = "名称，例如 API_KEY",
                valueHint = "值",
            )
        } else {
            OutlinedTextField(
                value = draft.url,
                onValueChange = { v ->
                    urlTouched = true
                    onChange { it.copy(url = v) }
                },
                label = { Text("服务器 URL") },
                singleLine = true,
                isError = urlTouched && draft.urlError != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
            )
            McpFieldError(draft.urlError, urlTouched)
            McpKeyValueEditor(
                title = "请求头（可选）",
                value = draft.headersText,
                onChange = { v ->
                    headersTouched = true
                    onChange { it.copy(headersText = v) }
                },
                resetKey = draft.originalName,
                error = draft.headersError,
                touched = headersTouched,
                keyHint = "名称，例如 Authorization",
                valueHint = "值，例如 Bearer …",
            )
        }

        Text(
            text = "「名称」用来在 Agent 调用时标识这台服务器，改动名称等于换了一台" +
                "（原名称的记录会被覆盖）。",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
        )
    }
}

@Composable
private fun M3eToolsTab(
    draft: McpServerDraft,
    status: McpServerStatus?,
    testing: Boolean,
    onTest: () -> Unit,
    onToolOptionsChange: (String, Boolean, Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        if (!draft.isEditing || !draft.saveable) {
            Text(
                text = "先把上面的设置填完。工具开关要按服务器名写进配置，" +
                    "所以名称与连接信息得先有效。",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = 8.dp),
            )
        }

        M3eSectionHeader("工具")
        when {
            testing -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoadingIndicator(modifier = Modifier.size(15.dp))
                Text(
                    text = "正在连接服务器，读取工具清单…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            status == null -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = 12.dp),
            ) {
                Text(
                    text = "还没有工具清单。工具由服务器自己声明，要先连上去才拿得到。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                TextButton(onClick = onTest) { Text("测试连接") }
            }

            !status.connected -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = 12.dp),
            ) {
                Text(
                    text = "连接失败，读不到工具清单：${status.error}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error,
                )
                TextButton(onClick = onTest) { Text("重试") }
            }

            status.tools.isEmpty() -> Text(
                text = "这台服务器声明了 0 个工具。",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = 12.dp),
            )

            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = M3eDimens.RowPaddingH, end = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "共 ${status.tools.size} 个工具，" +
                            "${status.tools.count { !it.enabled }} 个已停用。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    val allEnabled = status.tools.all { it.enabled }
                    TextButton(
                        onClick = {
                            status.tools.forEach { tool ->
                                if (tool.enabled == allEnabled) {
                                    onToolOptionsChange(tool.name, !allEnabled, tool.approval)
                                }
                            }
                        },
                    ) { Text(if (allEnabled) "全部停用" else "全部启用") }
                }
                M3eSegmentedColumn {
                    status.tools.forEachIndexed { index, tool ->
                        McpToolRow(
                            index = index,
                            count = status.tools.size,
                            tool = tool,
                            onEnabledChange = { v -> onToolOptionsChange(tool.name, v, tool.approval) },
                            onApprovalChange = { v -> onToolOptionsChange(tool.name, tool.enabled, v) },
                        )
                    }
                }
            }
        }

        Text(
            text = "工具开关只影响本机：关掉的工具不会出现在 Agent 看到的清单里，也调用不了。" +
                "「调用前需要确认」是让每次调用都弹一次确认框（停用的工具不会走到那一步）。",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
        )
    }
}

@Composable
private fun McpToolRow(
    index: Int,
    count: Int,
    tool: McpToolInfo,
    onEnabledChange: (Boolean) -> Unit,
    onApprovalChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    M3eSegmentedItem(index = index, count = count, onClick = { expanded = !expanded }) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(
                        start = M3eDimens.RowPaddingH,
                        end = 12.dp,
                        top = M3eDimens.RowSpaceV,
                        bottom = M3eDimens.RowSpaceV,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = if (expanded) ZhiIcons.collapse else ZhiIcons.expand,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = tool.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (tool.enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
                    )
                    Text(
                        text = if (tool.parameters.isEmpty()) "无参数" else "${tool.parameters.size} 个参数",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "审批",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = tool.approval,
                        onCheckedChange = onApprovalChange,
                        enabled = tool.enabled,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Text(
                        text = "启用",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Switch(
                        checked = tool.enabled,
                        onCheckedChange = onEnabledChange,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp, end = M3eDimens.RowPaddingH, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (tool.description.isNotBlank()) {
                        Text(
                            text = tool.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (tool.parameters.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tool.parameters.take(6).forEach { parameter ->
                                M3ePill(label = parameter + if (parameter in tool.required) " *" else "")
                            }
                            if (tool.parameters.size > 6) {
                                M3ePill(label = "+${tool.parameters.size - 6}")
                            }
                        }
                        Text(
                            text = "带 * 的是必填参数。",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = if (tool.enabled) {
                            "「审批」开：每次调用这个工具都会弹出确认框。"
                        } else {
                            "工具已停用，「审批」开关暂时不起作用。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun McpTextDetailDialog(
    show: Boolean,
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    if (!show) return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    Clipboard.copy(context, title, body)
                    onDismiss()
                },
            ) { Text("复制") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun McpImportDialog(
    show: Boolean,
    text: String,
    error: String?,
    onChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入 MCP 配置") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onChange,
                    label = { Text("JSON 配置") },
                    minLines = 8,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = error ?: "粘贴 {\"mcpServers\": { … }} 形式的配置。" +
                        "同名服务器会被跳过（不覆盖你已有的设置），" +
                        "既没有 url 也没有 command 的条目会被拒绝。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error != null) scheme.error else scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = text.isNotBlank()) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun McpFieldError(message: String?, touched: Boolean) {
    if (message == null || !touched) return
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(start = M3eDimens.RowPaddingH, bottom = 4.dp),
    )
}

@Composable
private fun McpKeyValueEditor(
    title: String,
    value: String,
    onChange: (String) -> Unit,
    resetKey: String?,
    error: String?,
    touched: Boolean,
    keyHint: String,
    valueHint: String,
) {
    val scheme = MaterialTheme.colorScheme
    val initialPairs = remember(resetKey, value) { parsePairsOrNull(value) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        if (initialPairs == null) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(title) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            McpFieldError(error, touched)
            Text(
                text = "这段内容不是合法的 JSON 对象，暂时以原文编辑；" +
                    "改成 { } 形式的键值对后会切回逐行编辑。",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            return@Column
        }

        var rows by remember(resetKey) { mutableStateOf(initialPairs) }
        rows.forEachIndexed { index, pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = pair.first,
                    onValueChange = { v ->
                        rows = rows.toMutableList().also { it[index] = v to it[index].second }
                        onChange(serializePairs(rows))
                    },
                    label = { Text(keyHint) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = pair.second,
                    onValueChange = { v ->
                        rows = rows.toMutableList().also { it[index] = it[index].first to v }
                        onChange(serializePairs(rows))
                    },
                    label = { Text(valueHint) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp),
                )
                TextButton(
                    onClick = {
                        rows = rows.toMutableList().also { it.removeAt(index) }
                        onChange(serializePairs(rows))
                    },
                ) {
                    Icon(
                        painter = ZhiIcons.close,
                        contentDescription = "删除这一条",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        TextButton(onClick = { rows = rows + ("" to "") }) { Text("添加一条") }
        McpFieldError(error, touched)
    }
}

/** JSON 文本 → 有序键值对；不是合法对象时返回 null（调用方退回原文编辑）。 */
private fun parsePairsOrNull(text: String): List<Pair<String, String>>? {
    if (text.isBlank()) return emptyList()
    return runCatching {
        val obj = org.json.JSONObject(text)
        obj.keys().asSequence().map { key -> key to obj.optString(key, "") }.toList()
    }.getOrNull()
}

/** 键值对 → JSON 文本。 */
private fun serializePairs(pairs: List<Pair<String, String>>): String {
    val named = pairs.filter { it.first.isNotBlank() }
    if (named.isEmpty()) return ""
    return org.json.JSONObject().apply {
        named.forEach { (k, v) -> put(k.trim(), v) }
    }.toString(2)
}
