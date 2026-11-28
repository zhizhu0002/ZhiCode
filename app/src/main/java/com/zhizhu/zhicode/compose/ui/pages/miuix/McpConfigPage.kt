package com.zhizhu.zhicode.compose.ui.pages.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.data.Clipboard
import com.zhizhu.zhicode.compose.model.McpConfigState
import com.zhizhu.zhicode.compose.model.McpScope
import com.zhizhu.zhicode.compose.model.McpServer
import com.zhizhu.zhicode.compose.model.McpServerDraft
import com.zhizhu.zhicode.compose.model.McpServerStatus
import com.zhizhu.zhicode.compose.model.McpToolInfo
import com.zhizhu.zhicode.compose.model.McpType
import com.zhizhu.zhicode.compose.ui.ZhiAnchoredActionMenu
import com.zhizhu.zhicode.compose.ui.rememberActionBusy
import com.zhizhu.zhicode.compose.ui.ZhiFieldError
import com.zhizhu.zhicode.compose.ui.ZhiFloatingActionButton
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiSegmentedTabs
import com.zhizhu.zhicode.compose.ui.ZhiLoadingIndicator
import com.zhizhu.zhicode.compose.ui.ZhiSmallPill
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.TopBarTabRowPadding
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.springAnimateToPage
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiDialogWidth
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiSheetCardColors
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsLoadingHint
import com.zhizhu.zhicode.compose.ui.settings.SettingsPageKey
import com.zhizhu.zhicode.compose.ui.settings.SettingsPageStack
import com.zhizhu.zhicode.compose.ui.settings.SettingsSubPage
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun McpConfigPage(
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
    /** 「全部测试」：一次测完所有启用的服务器（引擎本来就支持批量）。 */
    onTestAll: () -> Unit = {},
    /** 列表页筛选词（名称 / 命令 / 地址）。 */
    onQueryChange: (String) -> Unit = {},
) {
    if (config == null) return
    val form = config.form
    // 用「最后一次非空」而不是直接读 form：退出动画期间离场页还在画，
    // 那时 form 已经是 null 了。见 rememberLastNonNull 的说明。
    val shownForm = rememberLastNonNull(form)
    // 错误全文对话框：点某台服务器的错误摘要时打开。
    var errorDetail by remember { mutableStateOf<Pair<String, String>?>(null) }
    // FAB 拉起的「怎么添加」选择表。
    var showAddSheet by remember { mutableStateOf(false) }
    // 同 shownForm：退出动画期间这两个字段已经是 null，内容得靠「最后一次非空」兜住。
    val shownErrorDetail = rememberLastNonNull(errorDetail)
    val shownImportText = rememberLastNonNull(config.importText)

    // 表单有未保存的改动时，返回先问一次（顶栏返回 / 边缘滑动 / 系统返回都走 onBack）。
    // 判定在 McpServerDraft.dirty：与打开表单那一刻的字段指纹相比。
    var confirmDiscard by remember { mutableStateOf(false) }
    val requestCancelForm: () -> Unit = {
        if (form?.dirty == true) confirmDiscard = true else onCancelForm()
    }

    SettingsPageStack(
        // 路径含最底下那一页：栈要靠整条路径算层级与方向。
        path = listOf(
            SettingsPageKey("mcp.list", 0),
            if (form != null) SettingsPageKey("mcp.form", 1) else null,
        ).filterNotNull(),
        onBack = if (form == null) onDismiss else requestCancelForm,
    ) { key ->
        val draft = if (key.id == "mcp.form") shownForm else null
        if (draft == null) {
            SettingsSubPage(
                title = "MCP 服务器",
                onBack = onDismiss,
                // 「添加」与「导入」合成一个 FAB 拉起的底栏（与技能页同一形态）
                floatingActionButton = {
                    ZhiFloatingActionButton(
                        onClick = { showAddSheet = true },
                        description = "添加 MCP 服务器",
                    )
                },
                overlay = {
                    // 浮层**常驻组合、只翻 `show`**：Miuix 的退出动画在 `*ContentLayout`
                    // 内部的 `Animatable` 里，组件一被移除就再也没机会播（见 SkillsOverlay 文件头）。
                    AddMcpSheet(
                        show = showAddSheet,
                        onDismiss = { showAddSheet = false },
                        onManual = {
                            showAddSheet = false
                            onNew()
                        },
                        onImport = {
                            showAddSheet = false
                            onOpenImport()
                        },
                    )
                    shownErrorDetail?.let { detail ->
                        TextDetailDialog(
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
                },
            ) {
                McpServerList(
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
        } else {
            var tab by rememberSaveable { mutableIntStateOf(0) }
            val tabs = remember { MCP_FORM_TABS }
            val pagerState = rememberPagerState(initialPage = tab, pageCount = { tabs.size })
            val pagerScope = rememberCoroutineScope()
            val currentTab by rememberUpdatedState(tab)
            LaunchedEffect(pagerState, currentTab) {
                if (pagerState.currentPage != currentTab) pagerState.springAnimateToPage(currentTab)
            }
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
                    .collect { (page, scrolling) ->
                        if (!scrolling && page in tabs.indices && page != currentTab) tab = page
                    }
            }
            SettingsSubPage(
                title = if (draft.isEditing) "编辑 MCP 服务器" else "添加 MCP 服务器",
                onBack = requestCancelForm,
                action = "保存" to (if (draft.saveable) onSave else null),
                // 保存动作在飞时禁用顶栏按钮：连点会写两份配置（第二次走进同名分支）。
                actionEnabled = !rememberActionBusy("mcp.save"),
                // TAB 栏走固定 header 槽位：切到「工具」往下滚几屏之后还得能切回去。
                header = {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = TopBarTabRowPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        ZhiSegmentedTabs(
                            tabs = tabs,
                            selectedIndex = pagerState.currentPage,
                            onSelect = { index -> pagerScope.launch { pagerState.springAnimateToPage(index) } },
                            modifier = Modifier.fillMaxWidth(),
                            matchWidth = true,
                        )
                    }
                },
                overlay = {
                    // 错误详情是从列表页拉起来的（见 onShowError），但表单页里也留着同一位客人：
                    // 它不套 `if`，退出动画才跑得起来。
                    shownErrorDetail?.let { detail ->
                        TextDetailDialog(
                            show = errorDetail != null,
                            title = detail.first,
                            body = detail.second,
                            onDismiss = { errorDetail = null },
                        )
                    }
                },
            ) {
                // 内容直接由 Pager 跟随 TAB 弹簧切换，页面位移与顶栏高亮同源；
                // 不再额外叠 AnimatedContent 淡变，避免两套动画互相竞争。
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    userScrollEnabled = false,
                    beyondViewportPageCount = 0,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapAnimationSpec = PagerNavigationSpringSpec,
                    ),
                    key = { tabs[it] },
                ) { tabIndex ->
                    if (tabIndex == 0) {
                        McpConnectionForm(draft = draft, onChange = onDraftChange)
                    } else {
                        McpToolsTab(
                            draft = draft,
                            // 工具清单来自「测试连接」，按**正在编辑的这个名字**去取。
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
    }

    // 表单页的「放弃修改？」确认。放在页面栈外面：栈里每一页都会重画，
    // 而对话框是叠在整页之上的（与技能 / 角色卡 / 记忆三页同一写法）。
    UnsavedChangesDialog(
        visible = confirmDiscard,
        onKeepEditing = { confirmDiscard = false },
        onDiscard = {
            confirmDiscard = false
            onCancelForm()
        },
    )
}

/** 表单的两个 TAB。顺序即页面顺序；切换靠 [McpConfigOverlay] 里的 [rememberSaveable] 下标。 */
private val MCP_FORM_TABS = listOf("基本设置", "工具")

@Composable
private fun McpServerList(
    config: McpConfigState,
    onEdit: (McpServer) -> Unit,
    onToggle: (McpServer) -> Unit,
    onDelete: (McpServer) -> Unit,
    onTest: (String) -> Unit,
    onTestAll: () -> Unit,
    onQueryChange: (String) -> Unit,
    onShowError: (String, String) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column {
        Text(
            text = "配置文件：${config.filePath}",
            color = scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )

        if (config.loading) {
            // 载荷在 IO 上读（见 openMcpConfig）：同 API 页，先于空态判断。
            SettingsLoadingHint()
        } else if (config.servers.isEmpty()) {
            McpEmptyState()
        } else {
            // ---- 汇总 + 批量测试 
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
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
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.weight(1f),
                )
                val testing = config.testing.isNotEmpty()
                TextButton(
                    text = if (testing) "测试中…" else "全部测试",
                    onClick = onTestAll,
                    // 一台启用的都没有时没什么可测；正在测时不让再点（会重复起进程）。
                    enabled = !testing && config.enabledCount > 0,
                )
            }

            // 服务器多了才给筛选框：两台的时候一个输入框只是占地方。
            if (config.servers.size > 3) {
                ZhiTextField(
                    value = config.query,
                    onValueChange = onQueryChange,
                    label = "筛选服务器（名称 / 命令 / 地址）",
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            val visible = config.visibleServers
            if (visible.isEmpty()) {
                // 「筛出 0 条」不能说成「没有服务器」—— 后者会让用户以为配置丢了。
                Text(
                    text = "没有匹配「${config.query}」的服务器。",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
            color = scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** FAB 拉起的「怎么添加」选择表。 */
@Composable
private fun AddMcpSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onManual: () -> Unit,
    onImport: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    OverlayBottomSheet(
        show = show,
        onDismissRequest = onDismiss,
        title = "添加 MCP 服务器",
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            colors = ZhiSheetCardColors(),
        ) {
            BasicComponent(
                title = "手动添加",
                titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
                summary = "自己填名称、连接方式与地址（或启动命令）",
                summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
                startAction = { RowIcon(ZhiIcons.edit, scheme.primary) },
                onClick = onManual,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            )
            BasicComponent(
                title = "从 JSON 导入",
                titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
                summary = "粘贴 {\"mcpServers\": { … }}，可一次导入多台",
                summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
                startAction = { RowIcon(ZhiIcons.file, scheme.primary) },
                onClick = onImport,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** 行首的小图标，统一尺寸。与技能页那张表同一个写法。 */
@Composable
private fun RowIcon(icon: androidx.compose.ui.graphics.painter.Painter, tint: androidx.compose.ui.graphics.Color) {
    Icon(
        painter = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(18.dp).padding(end = 2.dp),
    )
}

/** 一台都没有时的空态：大图标 + 一句话 + 怎么开始。与技能页同一形态。 */
@Composable
private fun McpEmptyState() {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            // 与设置主页那一行的图标**同一个**（ZhiIcons.sandbox）：点进来时看到的
            painter = ZhiIcons.sandbox,
            contentDescription = null,
            tint = scheme.onSurfaceVariantSummary.copy(alpha = 0.45f),
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = "没有 MCP 服务器",
            color = scheme.onBackground,
            fontSize = MiuixTheme.textStyles.main.fontSize,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "点右下角 + 添加，或用右上角「导入」粘贴一份 JSON 配置",
            color = scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
        )
    }
}

/** 一台服务器一张卡。 */
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
    val scheme = MiuixTheme.colorScheme
    var menuAt by remember { mutableStateOf<DpOffset?>(null) }

    Box {
        Card(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            colors = CardDefaults.defaultColors(color = scheme.surfaceContainerHighest),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧图标表达**连接状态**（参考 rikkahub 的 McpServerItem：状态图标在左
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        testing -> ZhiLoadingIndicator(size = 16.dp)
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
                            // 未测试：灰的服务器图标，配下面那行"未测试"的说明。
                            painter = ZhiIcons.sandbox,
                            contentDescription = "未测试",
                            tint = scheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = server.name,
                            color = if (server.enabled) scheme.onBackground else scheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.main.fontSize,
                            fontWeight = FontWeight.Medium,
                        )
                        // 启用状态的圆点：与状态图标分开，两个维度不能合成一个颜色。
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZhiSmallPill(label = transportLabel(server.type))
                        ZhiSmallPill(label = if (server.scope == McpScope.PROJECT) "项目级" else "用户级")
                    }
                    McpStatusLine(
                        server = server,
                        status = status,
                        testing = testing,
                        onShowError = onShowError,
                    )
                    // 「连到哪」：两台同名服务器（项目级 + 用户级各一份）光看名字与标签
                    // 分不出来，而它们的命令/地址往往完全不同。截断成一行，编辑页里看全文。
                    Text(
                        text = server.summary,
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
            labels = listOf(
                "测试连接",
                    "编辑",
                    if (server.enabled) "停用" else "启用",
                    "删除",
                ),
                onSelect = { index ->
                    menuAt = null
                    when (index) {
                        0 -> onTest()
                        1 -> onEdit()
                        2 -> onToggle()
                        else -> onDelete()
                    }
                },
                onDismiss = { menuAt = null },
                fingerOffset = null,
            )
    }
}

/** 连接状态那一行：未测过 / 测试中 / 已连接 N 个工具 / 失败（可点看全文）。 */
@Composable
private fun McpStatusLine(
    server: McpServer,
    status: McpServerStatus?,
    testing: Boolean,
    onShowError: (String) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    when {
        testing -> Row(verticalAlignment = Alignment.CenterVertically) {
            ZhiLoadingIndicator(size = 13.dp)
            Text(
                text = "正在连接…",
                color = scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        status == null -> Text(
            text = "未测试 · 点 ⋮ 里的「测试连接」",
            color = scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
        )

        status.connected -> Text(
            text = buildString {
                append("已连接 · 提供 ${status.tools.size} 个工具")
                val off = status.tools.count { !it.enabled }
                if (off > 0) append("（其中 $off 个已停用）")
            },
            color = scheme.primary,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
        )

        else -> Text(
            // 点整行看全文并复制：超时/证书/401 这类信息截断成一行就没法排查了。
            text = "连接失败：${status.error}（点这里看全文）",
            color = scheme.error,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .clickableText { onShowError(status.error) },
        )
    }
}

/** 只用来给「连接失败」那行加点击：名字直白，避免和 Compose 的 clickable 混淆。 */
private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
private fun transportLabel(type: McpType): String = when (type) {
    McpType.STDIO -> "stdio"
    McpType.HTTP -> "HTTP"
    McpType.SSE -> "SSE"
}

/** 「基本设置」页：名称 / 连接方式 / 启用 + 启动参数或 HTTP 连接 + 作用范围。 */
@Composable
private fun McpConnectionForm(
    draft: McpServerDraft,
    onChange: ((McpServerDraft) -> McpServerDraft) -> Unit,
) {
    // 表单滚动由外层 SettingsSubPage 的 LazyColumn 负责，这里不套 verticalScroll
    // （嵌套滚动容器会拿到无限高度约束而崩溃）。
    val scheme = MiuixTheme.colorScheme
    // 「用户碰过哪一项」：没碰过就不飘红（见 FieldError 的说明）。
    var nameTouched by remember(draft.originalName) { mutableStateOf(false) }
    var commandTouched by remember(draft.originalName) { mutableStateOf(false) }
    var urlTouched by remember(draft.originalName) { mutableStateOf(false) }
    var envTouched by remember(draft.originalName) { mutableStateOf(false) }
    var headersTouched by remember(draft.originalName) { mutableStateOf(false) }
    Column {
        SettingsGroup("服务器") {
            ZhiTextField(
                value = draft.name,
                onValueChange = { v ->
                    nameTouched = true
                    onChange { it.copy(name = v) }
                },
                label = "服务器名称",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            FieldError(draft.nameError, nameTouched)

            OverlayDropdownPreference(
                items = McpType.entries.map { it.label },
                selectedIndex = McpType.entries.indexOf(draft.type).coerceAtLeast(0),
                title = "连接方式",
                summary = null,
                onSelectedIndexChange = { index ->
                    McpType.entries.getOrNull(index)?.let { type -> onChange { it.copy(type = type) } }
                },
            )

            SwitchPreference(
                title = "启用",
                summary = "停用后配置仍然保留，但 Agent 不会调用该服务器，也不会被测试。",
                checked = draft.enabled,
                onCheckedChange = { v -> onChange { it.copy(enabled = v) } },
            )
        }

        SettingsGroup(if (draft.type.needsCommand) "启动参数" else "HTTP 连接") {
            if (draft.type.needsCommand) {
                ZhiTextField(
                    value = draft.command,
                    onValueChange = { v ->
                        commandTouched = true
                        onChange { it.copy(command = v) }
                    },
                    label = "启动命令",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                )
                FieldError(draft.commandError, commandTouched)
                ZhiTextField(
                    value = draft.argsText,
                    onValueChange = { v -> onChange { it.copy(argsText = v) } },
                    label = "命令参数（每行一个）",
                    useLabelAsPlaceholder = true,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                )
                // 环境变量同样是键值对：stdio 服务器常常要传 API key。
                KeyValueEditor(
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
                ZhiTextField(
                    value = draft.url,
                    onValueChange = { v ->
                        urlTouched = true
                        onChange { it.copy(url = v) }
                    },
                    label = "服务器 URL",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                )
                FieldError(draft.urlError, urlTouched)
                // 请求头从「一整块 JSON 文本框」改成**键值行**（参考 rikkahub 的
                KeyValueEditor(
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

            OverlayDropdownPreference(
                items = McpScope.entries.map { it.label },
                selectedIndex = McpScope.entries.indexOf(draft.scope).coerceAtLeast(0),
                title = "作用范围",
                summary = null,
                onSelectedIndexChange = { index ->
                    McpScope.entries.getOrNull(index)?.let { scope -> onChange { it.copy(scope = scope) } }
                },
            )
        }

        Text(
            text = "「名称」用来在 Agent 调用时标识这台服务器，改动名称等于换了一台" +
                "（原名称的记录会被覆盖）。",
            color = scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 「工具」页：连接测试 + 逐工具开关。 */
@Composable
private fun McpToolsTab(
    draft: McpServerDraft,
    status: McpServerStatus?,
    testing: Boolean,
    onTest: () -> Unit,
    onToolOptionsChange: (String, Boolean, Boolean) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column {
        // 这个 TAB 是**只读**的：工具清单由服务器声明，不能在这里手工增删。
        if (!draft.isEditing || !draft.saveable) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                colors = CardDefaults.defaultColors(color = scheme.surfaceContainerHighest),
            ) {
                BasicComponent(
                    title = "先把上面的设置填完",
                    titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
                    summary = "工具开关要按服务器名写进配置，所以名称与连接信息得先有效。",
                    summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        SettingsGroup("工具") {
            when {
                testing -> Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ZhiLoadingIndicator(size = 15.dp)
                    Text(
                        text = "正在连接服务器，读取工具清单…",
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                status == null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "还没有工具清单。工具由服务器自己声明，要先连上去才拿得到。",
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    )
                    TextButton(
                        text = "测试连接",
                        onClick = onTest,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                !status.connected -> Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "连接失败，读不到工具清单：${status.error}",
                        color = scheme.error,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        fontWeight = FontWeight.Medium,
                    )
                    TextButton(
                        text = "重试",
                        onClick = onTest,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                status.tools.isEmpty() -> Text(
                    text = "这台服务器声明了 0 个工具。",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )

                else -> {
                    // 汇总行 + 批量开关：一台服务器可能有几十个工具，
                    // 逐个点开关不现实（rikkahub 的 MCP 页也给了整体开关）。
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "共 ${status.tools.size} 个工具，" +
                                "${status.tools.count { !it.enabled }} 个已停用。",
                            color = scheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            modifier = Modifier.weight(1f),
                        )
                        val allEnabled = status.tools.all { it.enabled }
                        TextButton(
                            text = if (allEnabled) "全部停用" else "全部启用",
                            // 批量写盘：一次只落一次（`setToolOptions` 是逐条读改写，
                            // 这里循环调用，但界面在飞期间禁用按钮避免叠加点击）。
                            onClick = {
                                status.tools.forEach { tool ->
                                    if (tool.enabled == allEnabled) {
                                        onToolOptionsChange(tool.name, !allEnabled, tool.approval)
                                    }
                                }
                            },
                        )
                    }
                    status.tools.forEach { tool ->
                        McpToolRow(
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
            color = scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 一个工具一行：名称、两个开关，点开看描述与参数。 */
@Composable
private fun McpToolRow(
    tool: McpToolInfo,
    onEnabledChange: (Boolean) -> Unit,
    onApprovalChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MiuixTheme.colorScheme

    Column {
        BasicComponent(
            title = tool.name,
            titleColor = BasicComponentDefaults.titleColor(
                color = if (tool.enabled) scheme.onBackground else scheme.onSurfaceVariantSummary,
            ),
            summary = if (tool.parameters.isEmpty()) "无参数" else "${tool.parameters.size} 个参数",
            summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
            startAction = {
                Icon(
                    painter = if (expanded) ZhiIcons.collapse else ZhiIcons.expand,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(16.dp),
                )
            },
            endActions = {
                // 两个开关都放在**折叠行**上（参考实现 rikkahub 的 McpToolCard 同款）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "审批",
                        color = if (tool.enabled) scheme.onSurfaceVariantSummary else scheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    )
                    // 用裸 Switch 而不是 SwitchPreference：后者自带标题行与内边距，
                    // 塞进 BasicComponent 的 endActions（一个 RowScope）里会撑成两层。
                    Switch(
                        checked = tool.approval,
                        onCheckedChange = onApprovalChange,
                        // 停用的工具永远不会走到"要不要确认"，所以这个开关跟着失效
                        // （与展开区里那条说明同一个语义，不再各说一套）。
                        enabled = tool.enabled,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                    Text(
                        text = "启用",
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                    Switch(
                        checked = tool.enabled,
                        onCheckedChange = onEnabledChange,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            },
            onClick = { expanded = !expanded },
            insideMargin = PaddingValues(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        )

        if (expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (tool.description.isNotBlank()) {
                    Text(
                        text = tool.description,
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    )
                }
                if (tool.parameters.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tool.parameters.take(6).forEach { parameter ->
                            ZhiSmallPill(
                                label = parameter + if (parameter in tool.required) " *" else "",
                                highlighted = parameter in tool.required,
                            )
                        }
                        if (tool.parameters.size > 6) {
                            ZhiSmallPill(label = "+${tool.parameters.size - 6}")
                        }
                    }
                    Text(
                        text = "带 * 的是必填参数。",
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    )
                }
                // 审批开关已挪到折叠行上（见 endActions）。这里只保留展开后的解释文字：
                // 同一个开关不放两处，否则两处状态要各维护一次、也容易以为它们是两个设置。
                Text(
                    text = if (tool.enabled) {
                        "「审批」开：每次调用这个工具都会弹出确认框。"
                    } else {
                        "工具已停用，「审批」开关暂时不起作用。"
                    },
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                )
            }
        }
    }
}

/** 长文本详情（连接错误的全文）。可滚动 + 复制。 */
@Composable
private fun TextDetailDialog(show: Boolean, title: String, body: String, onDismiss: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    OverlayDialog(
        show = show,
        onDismissRequest = onDismiss,
        // 居中形态（`largeScreen` 是**居中**开关，不是「大屏专用」）——
        // 不传的话手机竖屏会被判成「非大屏」，弹窗从底部升起。理由见 PermissionOverlay。
        largeScreen = true,
        maxWidth = ZhiDialogWidth.Compact,
    ) {
        DialogShell(
            title = title,
            actions = {
                TextButton(
                    text = "复制",
                    onClick = {
                        // 走共用的 Clipboard：它会挡掉空内容（清空用户的剪贴板），
                        // 也告诉我们系统是否已经自己弹了"已复制"提示。
                        Clipboard.copy(context, title, body)
                        onDismiss()
                    },
                )
                TextButton(text = "关闭", onClick = onDismiss, modifier = Modifier.padding(start = 8.dp))
            },
        ) {
            Text(
                text = body,
                color = scheme.onBackground,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 从 JSON 导入。格式是 Claude Desktop / 多数 MCP 文档里的那一份。 */
@Composable
private fun McpImportDialog(
    show: Boolean,
    text: String,
    error: String?,
    onChange: (String) -> Unit,
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
            title = "导入 MCP 配置",
            actions = {
                TextButton(text = "取消", onClick = onDismiss)
                TextButton(
                    text = "导入",
                    onClick = onConfirm,
                    enabled = text.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ZhiTextField(
                    value = text,
                    onValueChange = onChange,
                    label = "JSON 配置",
                    useLabelAsPlaceholder = false,
                    singleLine = false,
                    minLines = 8,
                    textStyle = MiuixTheme.textStyles.main.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = error ?: "粘贴 {\"mcpServers\": { … }} 形式的配置。" +
                        "同名服务器会被跳过（不覆盖你已有的设置），" +
                        "既没有 url 也没有 command 的条目会被拒绝。",
                    color = if (error != null) scheme.error else scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    fontWeight = if (error != null) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

/** MCP 表单里的校验提示：统一走 [ZhiFieldError]（见那里的两条纪律）。 */
@Composable
private fun FieldError(message: String?, touched: Boolean = true) =
    ZhiFieldError(message = message, touched = touched)

@Composable
private fun KeyValueEditor(
    title: String,
    value: String,
    onChange: (String) -> Unit,
    resetKey: String?,
    error: String?,
    touched: Boolean,
    keyHint: String,
    valueHint: String,
) {
    val scheme = MiuixTheme.colorScheme
    // 解析失败 = 显示原始文本框（见上面的说明）。
    val initialPairs = remember(resetKey, value) { parsePairsOrNull(value) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(
            text = title,
            color = scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        )

        if (initialPairs == null) {
            // 退路：原样编辑那段文本。
            ZhiTextField(
                value = value,
                onValueChange = onChange,
                label = title,
                useLabelAsPlaceholder = true,
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            ZhiFieldError(message = error, touched = touched)
            Text(
                text = "这段内容不是合法的 JSON 对象，暂时以原文编辑；改成 { } 形式的键值对后会切回逐行编辑。",
                color = scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            return@Column
        }

        // 逐行编辑：本地保留"还没填名字的空行"，写回时只序列化有名字的那些。
        var rows by remember(resetKey) { mutableStateOf(initialPairs) }
        rows.forEachIndexed { index, pair ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZhiTextField(
                    value = pair.first,
                    onValueChange = { v ->
                        rows = rows.toMutableList().also { it[index] = v to it[index].second }
                        onChange(serializePairs(rows))
                    },
                    label = "名称",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                ZhiTextField(
                    value = pair.second,
                    onValueChange = { v ->
                        rows = rows.toMutableList().also { it[index] = it[index].first to v }
                        onChange(serializePairs(rows))
                    },
                    label = "值",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                ZhiIconButton(
                    icon = ZhiIcons.close,
                    description = "删除这一条",
                    onClick = {
                        rows = rows.toMutableList().also { it.removeAt(index) }
                        onChange(serializePairs(rows))
                    },
                    iconSize = 16.dp,
                )
            }
        }

        TextButton(
            text = "添加一条",
            onClick = {
                rows = rows + ("" to "")
                // 空行不写回：否则会先往磁盘塞一个空名字的头。
            },
            modifier = Modifier.padding(top = 2.dp),
        )
        ZhiFieldError(message = error, touched = touched)
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
    return org.json.JSONObject().apply { named.forEach { (k, v) -> put(k.trim(), v) } }.toString(2)
}
