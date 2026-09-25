package com.iqge.iqcode.compose.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.app.Application
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iqge.iqcode.compose.theme.IqColors
import com.iqge.iqcode.compose.theme.LocalIqDark
import com.iqge.iqcode.compose.theme.IqRadius
import com.iqge.iqcode.compose.model.ThemeMode
import com.iqge.iqcode.compose.model.WorkspaceTab
import com.iqge.iqcode.compose.model.AgentTask
import com.iqge.iqcode.compose.model.WorkspaceUiState
import com.iqge.iqcode.compose.state.WorkspaceViewModel
import com.iqge.iqcode.compose.state.WorkspaceViewModelFactory
import com.iqge.iqcode.compose.ui.chat.AgentProgressCard
import com.iqge.iqcode.compose.ui.chat.ChatList
import com.iqge.iqcode.compose.ui.composer.Composer
import com.iqge.iqcode.compose.ui.dialogs.ChoicePickerOverlay
import com.iqge.iqcode.compose.ui.dialogs.ApiConfigOverlay
import com.iqge.iqcode.compose.ui.dialogs.McpConfigOverlay
import com.iqge.iqcode.compose.ui.dialogs.ModelPickerOverlay
import com.iqge.iqcode.compose.ui.dialogs.RoleCardsOverlay
import com.iqge.iqcode.compose.ui.dialogs.SkillsOverlay
import com.iqge.iqcode.compose.ui.dialogs.EnvironmentOverlay
import com.iqge.iqcode.compose.ui.dialogs.PermissionOverlay
import com.iqge.iqcode.compose.ui.dialogs.PlanApprovalOverlay
import com.iqge.iqcode.compose.ui.dialogs.TaskListOverlay
import com.iqge.iqcode.compose.ui.panes.ChangesPane
import com.iqge.iqcode.compose.ui.panes.FilesPane
import com.iqge.iqcode.compose.ui.panes.TerminalPane
import com.iqge.iqcode.compose.ui.settings.SettingsDialog
import top.yukonga.miuix.kmp.basic.FloatingToolbar

import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 取（或创建）主 ViewModel。
 *
 * 不能用默认的 `viewModel()`：反射工厂需要精确的 `(Application)` 构造器，而
 * `WorkspaceViewModel` 的构造器带默认参数，Kotlin 不会生成那个重载，会直接抛
 * `NoSuchMethodException` 并让界面白屏。详见 [WorkspaceViewModelFactory]。
 *
 * 工厂里再注入 `WorkspaceRepository`。注意它现在只剩「终端占位横幅」一项：
 * 对话流、工具执行、会话文件都已由真实引擎负责，不再是模拟实现。
 */
@Composable
private fun rememberWorkspaceViewModel(): WorkspaceViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application) { WorkspaceViewModelFactory(application) }
    return viewModel(factory = factory)
}

/** 应用入口：主题 + 主界面。 */
@Composable
fun IqCodeApp(viewModel: WorkspaceViewModel = rememberWorkspaceViewModel()) {
    val state by viewModel.state.collectAsState()

    val systemDark = isSystemInDarkTheme()
    val isDark = when (state.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val colors = if (isDark) darkColorScheme() else lightColorScheme()

    // LocalIqDark 必须**先**提供，之后才能求任何 IqColors 层级色：
    // 这些色函数靠 LocalIqDark 判断深浅，读早了会拿到默认值 false（浅色），
    // 于是深色模式下背板被设成浅灰、弹窗整片发白。
    CompositionLocalProvider(LocalIqDark provides isDark) {
        // 主背板 = 灰（深色 #242424 / 浅色 #EDEDED），侧栏与面板则用纯黑/纯白。
        // 两者是互换过的层级：背板退后，板块站出来。
        // 之所以要覆盖主题的 background：Miuix 浅色方案里 background / surface /
        // surfaceContainer 都是 #FFFFFF，纯白铺满后板块完全分不出来。
        val appColors = colors.copy(background = IqColors.backdrop())
        MiuixTheme(colors = appColors) {
            IqCodeScreen(state = state, viewModel = viewModel, isDark = isDark)
        }
    }
}

@Composable
private fun IqCodeScreen(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    isDark: Boolean,
) {
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp >= 600

    // 提示条已移除：Snackbar 会遮挡底部输入器，且本工程的 message 基本都是
    // 一次性操作反馈（「已复制到剪贴板」「配置已保存」这类），直接静默即可。
    // 若将来需要反馈，改在 Composer 上方做一条内联提示，不要用悬浮 Snackbar。

    // 玻璃对象分两层，因为捕获节点不能包含自己：
    //  · glassMain —— 捕获「整个工作区」；用于顶栏 / 侧栏抽屉 / 弹窗。
    //  · glassChat —— 捕获「对话列表」；用于底部悬浮的任务卡与输入器。
    //    这两层必须分开：输入器就在对话区里面，若共用整屏捕获，它会把自己
    //    录进背景里，模糊时出现自我叠影。
    val glassMain = rememberGlass()
    val glass = rememberGlass()

    Scaffold(
        // 顶栏不再交给 Scaffold 的 topBar 槽位，而是作为**悬浮层**画在内容之上
        // （见下方 Box）。这样它才能像玻璃一样压住滚动内容做背景虚化；
        // 放进 topBar 槽位只会把它挤到内容外面，没有东西可模糊。
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 整块工作区先录进 glassMain 的背景（顶栏/抽屉/弹窗的模糊来源）
            Box(modifier = Modifier.fillMaxSize().then(glassMain.capture(Modifier))) {
                if (wide) {
                    WideWorkspace(state = state, viewModel = viewModel, isDark = isDark, glass = glass)
                } else {
                    CompactWorkspace(state = state, viewModel = viewModel, isDark = isDark, glass = glass)
                }
            }

            // ---- 绘制顺序说明 ----
            // Compose 没有负 z-index：**后写的 Composable 画在上面**。
            // 整个屏幕自下而上是：
            //   1. 工作区内容        —— 被 glassMain 捕获，作为模糊的底
            //   2. 悬浮顶栏          —— 盖住内容；窄屏时又要被侧栏抽屉盖住
            //   3. 侧栏抽屉          —— 模态，含遮罩，必须盖住顶栏
            //   4. 弹窗模糊层 + 弹窗 —— 最上层
            // 宽屏的常驻侧栏与顶栏并列不重叠，靠顶栏的 start 偏移避让。

            // ---- 悬浮顶栏（玻璃效果）----
            // 画在内容之上，才能把滚动到它下面的对话模糊掉；
            // 又必须排在弹窗的模糊背景之前，否则弹窗打开时它不会跟着糊掉。
            IqTopBar(
                state = state,
                wide = wide,
                glass = glassMain,
                // 宽屏时从侧栏右边开始，否则整宽的悬浮顶栏会盖住侧栏顶部。
                //
                // 注意**不能**写成 `align(TopCenter) + padding(start = X)`：
                // 那样 padding 会先给元素加宽 X 再整体居中，实际只右移 X/2。
                // 用 TopStart + fillMaxWidth + padding(start = X) 才是从 X 铺到右边缘。
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = if (wide) SidebarWidth + DividerWidth else 0.dp),
                onOpenSidebar = viewModel::openSidebar,
                onContextClick = {
                    viewModel.onComposerChange("/usage")
                    viewModel.send()
                },
                onCycleTheme = viewModel::cycleThemeMode,
                onFloatingBall = {
                    viewModel.onComposerChange("/canvas")
                    viewModel.send()
                },
                onSettings = viewModel::openSettings,
                // 窄屏：按键组归顺到头部（原先它被悬浮顶栏遮住）
                tabs = if (wide) null else WorkspaceTab.entries,
                onSelectTab = viewModel::selectTab,
            )

            // ---- 侧边栏抽屉（窄屏，替代原来的底部弹层）----
            // 排在顶栏之后 = 画在顶栏之上。抽屉是模态的：它的遮罩本来就应该连顶栏
            // 一起压暗，面板内容也不必再为顶栏留白（不必再 padding）。
            if (!wide) {
                IqSideDrawer(
                    open = state.sidebarOpen,
                    onClose = viewModel::closeSidebar,
                    width = (configuration.screenWidthDp * 0.82f).dp.coerceAtMost(320.dp),
                    glass = glassMain,
                ) {
                    IqSidebarHost(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxHeight(),
                    )
                }
            }

            // ---- Overlay 系列：必须位于 Scaffold 内容里才能找到 popupHost ----
            // 弹窗打开时，先铺一层「模糊背景」：
            // Miuix 的 blur 只能在**同一个窗口**内采样背景（LayerBackdrop 录的是本窗口
            // 的 GraphicsLayer），所以没法在弹窗自己的窗口里做模糊 —— 改成在主窗口里
            // 把整个工作区糊掉，弹窗再画在它上面。视觉效果与 MIUI 的模态背景一致。
            val modalOpen = state.permissionRequest != null ||
                state.planApproval != null ||
                state.choicePicker != null ||
                state.settingsDraft != null ||
                state.environmentOpen ||
                state.taskListOpen
            if (modalOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(glassMain.blur(Modifier, RectangleShape, radius = 48f)),
                )
            }

            PermissionOverlay(
                request = state.permissionRequest,
                onAllowOnce = { viewModel.resolvePermission(allow = true) },
                onAlwaysAllow = { viewModel.resolvePermission(allow = true, alwaysAllow = true) },
                onDeny = { viewModel.resolvePermission(allow = false) },
            )
            PlanApprovalOverlay(
                plan = state.planApproval,
                onApprove = { viewModel.resolvePlan(true) },
                onRevise = { viewModel.resolvePlan(false) },
                onReviseWithFeedback = viewModel::resolvePlanWithFeedback,
            )
            ChoicePickerOverlay(
                picker = state.choicePicker,
                onSubmit = viewModel::onSubmitSelection,
                onDismiss = viewModel::dismissChoicePicker,
                onSubmitFreeForm = viewModel::onSubmitFreeForm,
            )
            SettingsDialog(
                draft = state.settingsDraft,
                onChange = viewModel::setSettingsDraft,
                onDismiss = viewModel::closeSettings,
                onSave = viewModel::saveSettings,
                onNavigate = viewModel::navigateFromSettings,
            )
            TaskListOverlay(
                tasks = state.tasks,
                open = state.taskListOpen,
                onDismiss = viewModel::closeTaskList,
            )
            EnvironmentOverlay(
                open = state.environmentOpen,
                report = state.environmentReport,
                runtimeReady = state.runtimeReady,
                installing = state.runtimeInstalling,
                progress = state.runtimeProgress,
                message = state.runtimeMessage,
                onDismiss = viewModel::closeEnvironment,
                onInstall = viewModel::installRuntime,
                onRepair = viewModel::repairRuntime,
                onRefresh = viewModel::refreshEnvironmentReport,
                onCopy = { viewModel.copyEnvironmentReport() },
            )
            ModelPickerOverlay(
                picker = state.modelPicker,
                onDismiss = viewModel::closeModelPicker,
                onQueryChange = viewModel::setModelQuery,
                onUse = viewModel::applySelectedModel,
                onOpenApiConfig = {
                    viewModel.closeModelPicker()
                    viewModel.openApiConfig()
                },
            )
            ApiConfigOverlay(
                config = state.apiConfig,
                onDismiss = viewModel::closeApiConfig,
                onNew = viewModel::newApiProfile,
                onEdit = viewModel::editApiProfile,
                onSelect = viewModel::selectApiProfile,
                onDelete = viewModel::deleteApiProfile,
                onDraftChange = viewModel::updateApiProfileDraft,
                onSave = viewModel::saveApiProfile,
                onCancelForm = viewModel::cancelApiProfileForm,
            )
            McpConfigOverlay(
                config = state.mcpConfig,
                onDismiss = viewModel::closeMcpConfig,
                onNew = viewModel::newMcpServer,
                onEdit = viewModel::editMcpServer,
                onToggle = viewModel::toggleMcpServer,
                onDelete = viewModel::deleteMcpServer,
                onDraftChange = viewModel::updateMcpDraft,
                onSave = viewModel::saveMcpServer,
                onCancelForm = viewModel::cancelMcpForm,
            )
            SkillsOverlay(
                state = state.skills,
                onDismiss = viewModel::closeSkills,
                onNew = viewModel::newSkill,
                onEdit = viewModel::editSkill,
                onAttach = viewModel::attachSkill,
                onDelete = viewModel::deleteSkill,
                onCreateDraftChange = viewModel::updateSkillCreateDraft,
                onCreate = viewModel::createSkill,
                onCancelCreate = viewModel::cancelSkillCreate,
                onBodyChange = viewModel::updateSkillBody,
                onSave = viewModel::saveSkill,
                onCancelEdit = viewModel::cancelSkillEdit,
            )
            RoleCardsOverlay(
                state = state.roleCards,
                onDismiss = viewModel::closeRoleCards,
                onNew = viewModel::newRoleCard,
                onEdit = viewModel::editRoleCard,
                onSelect = viewModel::selectRoleCard,
                onDisable = viewModel::disableRoleCard,
                onDelete = viewModel::deleteRoleCard,
                onDraftChange = viewModel::updateRoleCardDraft,
                onSave = viewModel::saveRoleCard,
                onCancelEditor = viewModel::cancelRoleCardEditor,
            )
        }
    }
}

/**
 * 左侧抽屉式侧边栏。
 *
 * 遮罩淡入淡出，面板从左侧滑入，带轻微位移缓动。
 */
@Composable
private fun IqSideDrawer(
    open: Boolean,
    onClose: () -> Unit,
    width: Dp,
    glass: Glass,
    content: @Composable () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val scrimInteraction = remember { MutableInteractionSource() }

    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩：走 Miuix Surface(onClick)，不再手写 background + clickable。
        // 颜色用主题的 windowDimming（Miuix 自己给弹窗/抽屉遮罩用的语义色），
        // 不再硬编码 Color.Black —— 浅色模式下黑遮罩会把背景压得过重。
        // 传 indication = null 保持"只有压暗、不出现涟漪"的原有效果。
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(IqMotion.FAST)),
            exit = fadeOut(tween(IqMotion.FAST)),
        ) {
            Surface(
                onClick = onClose,
                modifier = Modifier.fillMaxSize(),
                color = MiuixTheme.colorScheme.windowDimming,
                interactionSource = scrimInteraction,
                indication = null,
            ) {
                Box(modifier = Modifier.fillMaxSize())
            }
        }

        // 面板：从左侧滑入
        AnimatedVisibility(
            visible = open,
            enter = slideInHorizontally(
                animationSpec = tween(IqMotion.MEDIUM, easing = EaseOutCubic),
            ) { -it } + fadeIn(tween(IqMotion.FAST)),
            exit = slideOutHorizontally(
                animationSpec = tween(IqMotion.EXPAND, easing = FastOutSlowInEasing),
            ) { -it } + fadeOut(tween(IqMotion.FAST)),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Surface(
                modifier = Modifier
                    .width(width)
                    .fillMaxHeight()
                    .then(
                        glass.blur(
                            Modifier,
                            RoundedCornerShape(topEnd = IqRadius.floating, bottomEnd = IqRadius.floating),
                            radius = 24f,
                        ),
                    ),
                color = glass.surfaceColor(scheme.surfaceContainer),
                shape = RoundedCornerShape(topEnd = IqRadius.floating, bottomEnd = IqRadius.floating),
            ) {
                content()
            }
        }
    }
}

/** 宽屏：侧栏常驻 + 对话主栏 + 工作区副栏。 */
@Composable
private fun WideWorkspace(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    isDark: Boolean,
    glass: Glass,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // 侧栏保持**满高**：悬浮顶栏在宽屏下只覆盖右侧内容区（见 IqCodeScreen 里
        // 给顶栏加的 start 偏移），所以侧栏不需要给它让位，第一项也不会被遮住。
        IqSidebarHost(
            state = state,
            viewModel = viewModel,
            modifier = Modifier.width(SidebarWidth),
        )
        IqVerticalDivider()
        Column(modifier = Modifier.weight(56f)) {
            // 输入器现在由 ChatArea 以悬浮层形式托管
            ChatArea(state = state, viewModel = viewModel, wide = true, modifier = Modifier.weight(1f), glass = glass)
        }
        IqVerticalDivider()
        Column(modifier = Modifier.weight(44f)) {
            val secondary = WorkspaceTab.entries.filter { it != WorkspaceTab.CHAT }
            // 这一栏的按键组不滚动，所以仍要躲开覆盖它的头部
            Column(modifier = Modifier.padding(top = TopBarInset)) {
                WorkspaceTabs(
                    tabs = secondary,
                    selected = state.tab,
                    onSelect = viewModel::selectTab,
                )
            }
            PaneHost(state = state, viewModel = viewModel, isDark = isDark, glass = glass, modifier = Modifier.weight(1f))
        }
    }
}

/** 宽屏侧栏宽度。顶栏的起始偏移也用它，两处必须是同一个值。 */
internal val SidebarWidth = 258.dp

/** Miuix 纵向分隔线的宽度；顶栏偏移要把它算进去。 */
private val DividerWidth = 1.dp

/** 窄屏：底部 Tab 切换，一次只显示一个面板。 */
@Composable
private fun CompactWorkspace(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    isDark: Boolean,
    glass: Glass,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 面板切换动画：淡入淡出 + 轻微横向位移
        AnimatedContent(
            targetState = state.tab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val offset = if (forward) 1 else -1
                (slideInHorizontally(tween(IqMotion.MEDIUM, easing = EaseOutCubic)) {
                    offset * it / 8
                } + fadeIn(tween(IqMotion.MEDIUM))) togetherWith
                    (slideOutHorizontally(tween(IqMotion.FAST)) { -offset * it / 8 } +
                        fadeOut(tween(IqMotion.FAST)))
            },
            modifier = Modifier.weight(1f),
            label = "workspacePane",
        ) { tab ->
            if (tab == WorkspaceTab.CHAT) {
                // 对话面板的留白由 ChatList 的 topInset 负责：它需要能滚到悬浮头部
                // 下面被模糊。所以这里不额外顶开，避免双重留白。
                ChatArea(
                    state = state,
                    viewModel = viewModel,
                    wide = false,
                    modifier = Modifier.fillMaxSize(),
                    glass = glass,
                )
            } else {
                // 变更 / 终端 / 文件三个面板是普通 Column，不会滚动，
                // 所以必须顶开悬浮头部（含归顺进来的 Tab 行）的高度，
                // 否则它们的第一行会被压住。
                PaneHost(
                    state = state,
                    viewModel = viewModel,
                    isDark = isDark,
                    glass = glass,
                    tabOverride = tab,
                    modifier = Modifier.fillMaxSize().padding(top = TopBarInsetWithTabs),
                )
            }
        }
    }
}

/** 工作区 Tab 行的总高度：够容纳 Miuix 胶囊选中态，避免它被裁掉。 */
internal val WorkspaceTabRowHeight = 42.dp

/**
 * 工作区 Tab 行。转发到 Miuix [TabRow]。
 *
 * 这里以前是手写的 `IqButtonGroup`（等分按键 + 自绘选中胶囊），理由是"TabRow 的轮廓
 * 会向外绘制并盖住相邻内容"。实测确实会溢出，但正确做法不是退回手写，而是
 * **给足高度并裁切外层**：胶囊有地方画，溢出的部分也被裁掉，不会压到下面面板的首行。
 *
 * 代价：Miuix `TabRow` 只接受文本标签，原先每项左侧的图标不再显示。
 */
@Composable
internal fun WorkspaceTabs(
    tabs: List<WorkspaceTab>,
    selected: WorkspaceTab,
    onSelect: (WorkspaceTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(WorkspaceTabRowHeight).clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        TabRow(
            tabs = tabs.map { it.label },
            selectedTabIndex = tabs.indexOf(selected).coerceAtLeast(0),
            onTabSelected = { index -> tabs.getOrNull(index)?.let(onSelect) },
            // 手机宽度下 4 项等分约 90dp；窄到放不下时 TabRow 自己滚动。
            // 窄一点：4 项等分时每项约 90dp，标签居中留白更少，整行更紧凑
            minWidth = 64.dp,
            maxWidth = 104.dp,
        )
    }
}

/** 工作区标签 → 图标。顶栏（TopBar.kt）与宽屏按键组共用，所以是 internal。 */
internal fun iconForTab(tab: WorkspaceTab) = when (tab) {
    WorkspaceTab.CHAT -> IqIcons.chat
    WorkspaceTab.CHANGES -> IqIcons.changes
    WorkspaceTab.TERMINAL -> IqIcons.terminal
    WorkspaceTab.FILES -> IqIcons.files
}

@Composable
private fun ChatArea(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    wide: Boolean,
    modifier: Modifier = Modifier,
    glass: Glass,
) {
    val floating = state.workingStatus != null || state.tasks.isNotEmpty()

    // 对话区加一个框：纯白/纯黑的背板上如果没有边界，对话与面板会糊成一片。
    // 框用 Surface 的 border（Miuix 原生参数），圆角与其它卡片同一 token。
    Surface(
        modifier = modifier.fillMaxSize().padding(horizontal = ChatFramePadding, vertical = ChatFramePadding),
        shape = RoundedCornerShape(IqRadius.card),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MiuixTheme.colorScheme.dividerLine),
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        ChatList(
            state = state,
            onToggleTool = viewModel::toggleToolExpanded,
            onToggleGroup = { id, expand ->
                val toolIds = state.transcript
                    .firstOrNull { it.id == id }?.tools?.map { it.id }.orEmpty()
                // toggleGroupExpanded 的第二参是「要收起的成员集合」：
                // 收起时把全部成员传进去，展开时传空集。
                viewModel.toggleGroupExpanded(id, if (expand) emptySet() else toolIds.toSet())
            },
            onToggleThinking = viewModel::toggleThinking,
            onMessageActions = viewModel::showMessageActions,
            modifier = glass.capture(Modifier.fillMaxSize()),
            // 底部预留出悬浮层的高度，让被盖住的内容也能滑上来；
            // 顶部预留头部高度，让内容能滚到悬浮头部下面被模糊。
            bottomInset = if (floating) ComposerInset + TaskCardInset else ComposerInset,
            topInset = if (wide) TopBarInset else TopBarInsetWithTabs,
        )

        // 底部悬浮层：任务卡在上、输入器在下，两者都不占布局高度，
        // 所以对话区始终铺满，且它们不随对话滚动。
        // 对应原版把 AgentProgressView + composerHost 放进位于 chatScroll
        // 之外的 chatBottomHost（MainActivity.java:1325-1331）。
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            AnimatedVisibility(
                visible = floating,
                enter = slideInVertically(tween(IqMotion.MEDIUM, easing = EaseOutCubic)) { it / 2 } +
                    fadeIn(tween(IqMotion.MEDIUM)),
                exit = slideOutVertically(tween(IqMotion.FAST)) { it / 2 } +
                    fadeOut(tween(IqMotion.FAST)),
            ) {
                FloatingAgentStatus(
                    status = state.workingStatus.orEmpty(),
                    tasks = state.tasks,
                    onExpand = viewModel::openTaskList,
                    wide = wide,
                    glass = glass,
                )
            }

            // 悬浮输入器
            ComposerHost(state = state, viewModel = viewModel, wide = wide, glass = glass)
        }
    }
    }
}

/** 对话框的四周留白。 */
private val ChatFramePadding = 6.dp

/** 悬浮输入器占位高度（含底部外边距），供对话列表留白使用。 */
private val ComposerInset = 104.dp

/** 顶栏标题行高度（窄屏用 48dp，见 `IqTopBar` 里的 height）。 */
internal val TopBarTitleRowHeight = 48.dp

/** 归顺到顶栏的 Tab 行上下留白（`IqTopBar` 里 `padding(vertical = 6.dp)` 的两倍）。 */
internal val TopBarTabRowPadding = 12.dp

/**
 * 悬浮头部的占位高度：宽屏只有标题行，窄屏还要加上归顺进来的 Tab 行。
 *
 * 这三个数字必须和 `IqTopBar` 里的实际尺寸严格对齐，否则面板的首行
 * （尤其是标题栏右侧的动作图标）会被悬浮头部压掉一半。
 */
private val TopBarInset = TopBarTitleRowHeight
private val TopBarInsetWithTabs =
    TopBarTitleRowHeight + WorkspaceTabRowHeight + TopBarTabRowPadding

/** 悬浮任务卡占位高度（含外边距），仅在任务运行时参与留白计算。 */
private val TaskCardInset = 152.dp

/**
 * 悬浮的任务与状态卡。
 *
 * 原版 `AgentProgressView` 同时承载任务清单与当前工作状态
 * （`agentProgressView.update(workingStatus, ...)`，MainActivity.java:4916），
 * 所以这里只放 [AgentProgressCard] 这一张卡。
 *
 * 外壳用 Miuix 的 [FloatingToolbar]（`outSidePadding` 负责外边距、
 * `shadowElevation` 负责阴影），而不是自己拼 `Surface` + `padding`。
 */
@Composable
private fun FloatingAgentStatus(
    status: String,
    tasks: List<AgentTask>,
    onExpand: () -> Unit,
    wide: Boolean,
    glass: Glass,
) {
    val scheme = MiuixTheme.colorScheme
    // 整块悬浮壳是唯一一层圆角：卡片在里面不再画自己的底板与外边距，
    // 否则会出现"圆角 20dp 的模糊面 + 圆角 14dp 的实心卡"两层不齐的观感。
    val shape = RoundedCornerShape(IqRadius.floating)
    FloatingToolbar(
        modifier = Modifier.fillMaxWidth().then(glass.blur(Modifier, shape, radius = 24f)),
        color = glass.surfaceColor(scheme.surfaceContainer),
        cornerRadius = IqRadius.floating,
        outSidePadding = PaddingValues(
            horizontal = if (wide) 24.dp else 12.dp,
            vertical = 8.dp,
        ),
        shadowElevation = 10.dp,
        showDivider = false,
    ) {
        AgentProgressCard(
            status = status,
            tasks = tasks,
            onExpand = onExpand,
            embedded = true,
        )
    }
}

@Composable
private fun PaneHost(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    isDark: Boolean,
    glass: Glass,
    modifier: Modifier = Modifier,
    tabOverride: WorkspaceTab? = null,
) {
    when (val tab = tabOverride ?: state.tab) {
        WorkspaceTab.CHANGES -> ChangesPane(
            diff = state.diff,
            isDark = isDark,
            onRefresh = viewModel::refreshDiff,
            modifier = modifier,
        )
        WorkspaceTab.TERMINAL -> TerminalPane(
            lines = state.terminalLines,
            projectName = state.projectName,
            // 内置 Termux 环境是否就绪决定这个是"真终端"还是"只读占位"。
            // runtimeReady 是真实探测（见 EnvDoctor / RuntimeInstaller），不是常量。
            runtimeReady = state.runtimeReady,
            workingDirectory = state.projectPath,
            modifier = modifier,
        )
        WorkspaceTab.FILES -> FilesPane(
            filePath = state.filePath,
            rootPath = state.projectPath,
            entries = state.fileEntries,
            openFile = state.openFile,
            emptyNote = state.fileNote,
            onOpen = viewModel::openFile,
            onUp = viewModel::navigateUp,
            onNavigate = viewModel::navigateTo,
            onCloseFile = viewModel::closeFile,
            modifier = modifier,
        )
        WorkspaceTab.CHAT -> ChatArea(state = state, viewModel = viewModel, wide = false, modifier = modifier, glass = glass)
    }
}

@Composable
private fun ComposerHost(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    wide: Boolean,
    glass: Glass,
) {
    // 用 GetContent 而不是 OpenDocument：这里的授权只为"立刻读一次字节"服务，
    // 内容读完就进内存了，不需要跨进程重启保留的持久授权。
    // （原版用 ACTION_OPEN_DOCUMENT + takePersistableUriPermission，是因为它把 Uri
    //  留在附件列表里直到用户点发送，中间可能经历一次重组甚至进程重启。）
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) viewModel.attachImage(uri) }

    Composer(
        state = state,
        wide = wide,
        glass = glass,
        onTextChange = viewModel::onComposerChange,
        onSend = viewModel::send,
        onStop = viewModel::stop,
        onAttach = { pickImage.launch("image/*") },
        onRemoveAttachment = { viewModel.removeAttachment(it.id) },
        onPermissionChip = viewModel::showPermissionPicker,
        onEffortChip = viewModel::showEffortPicker,
        onModelChip = viewModel::showModelPicker,
        onPickSlash = viewModel::pickSlashCommand,
    )
}

@Composable
private fun IqSidebarHost(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    modifier: Modifier = Modifier,
) {
    IqSidebar(
        state = state,
        onNewSession = viewModel::newSession,
        onProjectHistory = { viewModel.onComposerChange("/resume"); viewModel.send() },
        onProjectPath = { viewModel.onComposerChange("/status"); viewModel.send() },
        onOpenSession = viewModel::openSession,
        onSessionActions = viewModel::showSessionActions,
        onDeleteSession = { viewModel.deleteSession(it.id) },
        onSelectTab = { tab ->
            viewModel.selectTab(tab)
            viewModel.closeSidebar()
        },
        onSkills = viewModel::openSkills,
        onRoleCard = viewModel::openRoleCards,
        onSandbox = { viewModel.onComposerChange("/sandbox"); viewModel.send() },
        // 「运行环境」行现在是真实探测结果 + 真实的安装/自检窗口，
        // 不再是把 /doctor 当普通消息发出去（那样只会得到一句 Mock 回复）。
        onRuntime = viewModel::openEnvironment,
        onSettings = viewModel::openSettings,
        modifier = modifier,
    )
}
