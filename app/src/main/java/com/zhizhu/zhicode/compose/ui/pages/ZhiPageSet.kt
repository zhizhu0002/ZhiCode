package com.zhizhu.zhicode.compose.ui.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.staticCompositionLocalOf
import com.zhizhu.zhicode.compose.ui.pages.m3e.Material3ExpressivePageSet
import com.zhizhu.zhicode.compose.ui.pages.mochi.MochiPageSet
import com.zhizhu.zhicode.compose.data.ThemeSettings
import com.zhizhu.zhicode.compose.model.ApiConfigState
import com.zhizhu.zhicode.compose.model.ExtensionSummary
import com.zhizhu.zhicode.compose.model.McpConfigState
import com.zhizhu.zhicode.compose.model.MemoryState
import com.zhizhu.zhicode.compose.model.RoleCardsState
import com.zhizhu.zhicode.compose.model.SearchServicesState
import com.zhizhu.zhicode.compose.model.SettingsDraft
import com.zhizhu.zhicode.compose.model.SkillsState
import com.zhizhu.zhicode.compose.model.UpdateUiState
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.Glass
import com.zhizhu.zhicode.compose.theme.LocalThemeSettings
import com.zhizhu.zhicode.compose.data.OobeStore
import com.zhizhu.zhicode.compose.ui.pages.miuix.OobePage
import com.zhizhu.zhicode.compose.ui.pages.miuix.ApiConfigPage
import com.zhizhu.zhicode.compose.ui.pages.miuix.AboutPage
import com.zhizhu.zhicode.compose.ui.pages.miuix.ChatDisplayPage
import com.zhizhu.zhicode.compose.ui.pages.miuix.ChatPage
import com.zhizhu.zhicode.compose.ui.pages.miuix.FilesPage
import com.zhizhu.zhicode.compose.ui.pages.miuix.TerminalPage
import com.zhizhu.zhicode.compose.ui.pages.miuix.McpConfigPage
import com.zhizhu.zhicode.compose.ui.pages.miuix.MemoryPage
import com.zhizhu.zhicode.compose.ui.pages.miuix.PrivacyPolicyPage
import com.zhizhu.zhicode.compose.ui.pages.miuix.PromptsPage
import com.zhizhu.zhicode.compose.ui.pages.miuix.RoleCardsPage
import com.zhizhu.zhicode.compose.ui.pages.miuix.SearchServicesPage
import com.zhizhu.zhicode.compose.ui.pages.miuix.SettingsPage
import com.zhizhu.zhicode.compose.ui.pages.miuix.SkillsPage
import com.zhizhu.zhicode.compose.ui.pages.miuix.ThemePage
import com.zhizhu.zhicode.compose.ui.pages.miuix.ThirdPartyLicensesPage
import com.zhizhu.zhicode.compose.ui.pages.miuix.UiDebugPage
import com.zhizhu.zhicode.compose.ui.pages.miuix.UpdatePage
import com.zhizhu.zhicode.compose.ui.pages.miuix.WorkspacePage

/**
 * 界面主题组的契约：**一个主题 = 一组 page.kt**。
 *
 * ## 为什么是接口 + 默认实现，而不是抽象类或枚举
 *
 * 每个页面方法都带 `@Composable` **默认实现**，指向 Miuix 组。于是：
 *
 * - 默认组（[MiuixPageSet]）**一行都不用写**，全回落 = 今天的样子；
 * - 新主题组**只覆写想改的页面**，其余自动回落，第一天就能装上看到效果；
 * - 编译器保证签名一致，不会出现「某个页面忘了实现」。
 *
 * 这条「回落」是整个设计的支点。若改成抽象方法，新主题必须先写满 16 个页面
 * 才能编译通过 —— 那等于要求一次性写完 1 万行才能验证第一个像素，不可行。
 *
 * ## 为什么参数是 `state` / `viewModel` 而不是二十个回调
 *
 * Miuix 组的页面函数各自带一长串回调（最长的 `SkillsPage` 有 **22 个**），
 * 把它们原样搬进接口有两个问题：
 *
 * 1. 接口会长到无法阅读，且每加一个动作就要改接口；
 * 2. 新主题的实现被迫透传一堆自己用不到的 lambda。
 *
 * 所以接口只传**数据 + 动作来源**（[WorkspaceViewModel]），由各组的实现自己
 * 决定怎么接。动作语义与现在完全一致 —— 默认实现里的接线是逐字照抄
 * `AppScaffold` 原有的调用点。
 *
 * ## 关于「最后一次非空状态」
 *
 * `AppScaffold` 原来用 `state.apiConfig ?: lastApiConfig` 保住退出动画期间的
 * 内容（关闭动作先把状态置空，而动画还要跑几百毫秒）。这个「保留」留在
 * **调用方**做：接口收到的是已经解析好的值，所以默认实现拿到的东西和以前一样。
 *
 * ⚠️ 新增页面时**两条都要做**：接口加一个带默认实现的方法 + `AppScaffold`
 * 里那条 `entry` 改成调用它。少了任何一条，那个页面就永远只有 Miuix 版本。
 */
interface ZhiPageSet {

    /** 落盘用的稳定 id。**改名等于让老用户的选择失效**。 */
    val id: String

    /** 设置页里显示的名字。 */
    val name: String

    // ------------------------------------------------------------------ 工作区

    @Composable
    fun Workspace(
        state: WorkspaceUiState,
        viewModel: WorkspaceViewModel,
        isDark: Boolean,
        wide: Boolean,
        glass: Glass,
        glassMain: Glass,
    ) {
        WorkspacePage(
            state = state,
            viewModel = viewModel,
            isDark = isDark,
            wide = wide,
            glass = glass,
            glassMain = glassMain,
        )
    }

    // ------------------------------------------------------------------ 设置

    @Composable
    fun Settings(
        draft: SettingsDraft?,
        viewModel: WorkspaceViewModel,
        extensions: ExtensionSummary,
    ) {
        SettingsPage(
            draft = draft,
            onChange = viewModel::applySettingsDraft,
            onDismiss = viewModel::closeSettings,
            onNavigate = viewModel::navigateFromSettings,
            extensions = extensions,
            brandSignature = LocalThemeSettings.current.topBrandSignature,
            onBrandSignatureChange = { next ->
                viewModel.updateThemeSettings { it.copy(topBrandSignature = next.trim()) }
            },
        )
    }

    @Composable
    fun Theme(theme: ThemeSettings, viewModel: WorkspaceViewModel) {
        ThemePage(
            theme = theme,
            onDismiss = viewModel::closeThemePage,
            onChange = viewModel::updateThemeSettings,
        )
    }

    @Composable
    fun ChatDisplay(theme: ThemeSettings, viewModel: WorkspaceViewModel) {
        ChatDisplayPage(
            theme = theme,
            onBack = viewModel::closeChatDisplay,
            onChange = viewModel::updateThemeSettings,
        )
    }

    @Composable
    fun Prompts(state: WorkspaceUiState, viewModel: WorkspaceViewModel) {
        PromptsPage(
            initPrompt = state.settings.initPrompt,
            summaryPrompt = state.settings.summaryPrompt,
            compactPromptHead = state.settings.compactPromptHead,
            onDismiss = viewModel::closePrompts,
            onChange = viewModel::setPromptSlot,
            onReset = viewModel::resetPromptSlot,
        )
    }

    // -------------------------------------------------------------- 设置二级页

    @Composable
    fun ApiConfig(config: ApiConfigState?, viewModel: WorkspaceViewModel) {
        ApiConfigPage(
            config = config,
            onDismiss = viewModel::closeApiConfig,
            onNew = viewModel::newApiProfile,
            onEdit = viewModel::editApiProfile,
            onSelect = viewModel::selectApiProfile,
            onDelete = viewModel::deleteApiProfile,
            onDraftChange = viewModel::updateApiProfileDraft,
            onSave = viewModel::saveApiProfile,
            onCancelForm = viewModel::cancelApiProfileForm,
            onApplyPreset = viewModel::applyApiPreset,
            onFetchDraftModels = viewModel::fetchDraftModelCatalog,
            onUpdateContextAndPrompt = viewModel::updateContextAndPromptFromApiConfig,
            onOpenPrompts = viewModel::openPrompts,
        )
    }

    @Composable
    fun SearchServices(state: SearchServicesState?, viewModel: WorkspaceViewModel) {
        SearchServicesPage(
            state = state,
            onDismiss = viewModel::closeSearchServices,
            onNew = viewModel::newSearchService,
            onEdit = viewModel::editSearchService,
            onSelect = viewModel::selectSearchService,
            onDelete = viewModel::deleteSearchService,
            onDraftChange = viewModel::updateSearchServiceDraft,
            onSave = viewModel::saveSearchService,
            onCancelForm = viewModel::cancelSearchServiceForm,
        )
    }

    @Composable
    fun McpConfig(config: McpConfigState?, viewModel: WorkspaceViewModel) {
        McpConfigPage(
            config = config,
            onDismiss = viewModel::closeMcpConfig,
            onNew = viewModel::newMcpServer,
            onEdit = viewModel::editMcpServer,
            onToggle = viewModel::toggleMcpServer,
            onDelete = viewModel::deleteMcpServer,
            onDraftChange = viewModel::updateMcpDraft,
            onSave = viewModel::saveMcpServer,
            onCancelForm = viewModel::cancelMcpForm,
            onTest = viewModel::testMcpServer,
            onToolOptionsChange = viewModel::setMcpToolOptions,
            onOpenImport = viewModel::openMcpImport,
            onImportTextChange = viewModel::updateMcpImportText,
            onImportConfirm = viewModel::importMcpJson,
            onImportCancel = viewModel::cancelMcpImport,
            onTestAll = viewModel::testAllMcpServers,
            onQueryChange = viewModel::setMcpQuery,
        )
    }

    @Composable
    fun Skills(state: SkillsState?, viewModel: WorkspaceViewModel) {
        SkillsPage(
            state = state,
            onDismiss = viewModel::closeSkills,
            onQueryChange = viewModel::setSkillQuery,
            onNew = viewModel::newSkill,
            onNewUrl = viewModel::newSkillUrl,
            onOpenDetail = viewModel::openSkillDetail,
            onCloseDetail = viewModel::closeSkillDetail,
            onEdit = viewModel::editSkill,
            onAttach = viewModel::attachSkill,
            onDelete = viewModel::deleteSkill,
            onCreateDraftChange = viewModel::updateSkillCreateDraft,
            onCreate = viewModel::createSkill,
            onCancelCreate = viewModel::cancelSkillCreate,
            onBodyChange = viewModel::updateSkillBody,
            onSave = viewModel::saveSkill,
            onCancelEdit = viewModel::cancelSkillEdit,
            onNewFile = viewModel::newSkillFile,
            onFileDraftChange = viewModel::updateSkillFileDraft,
            onSaveFile = viewModel::saveSkillFile,
            onCancelFile = viewModel::cancelSkillFileDraft,
            onImportFile = viewModel::importSkillFromUri,
            onDeleteFile = viewModel::deleteSkillFile,
            onUrlDraftChange = viewModel::updateSkillUrlDraft,
            onUrlImport = viewModel::importSkillFromUrl,
            onCancelUrl = viewModel::cancelSkillUrl,
        )
    }

    @Composable
    fun RoleCards(state: RoleCardsState?, viewModel: WorkspaceViewModel) {
        RoleCardsPage(
            state = state,
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

    @Composable
    fun Memory(state: MemoryState?, viewModel: WorkspaceViewModel) {
        MemoryPage(
            state = state,
            onDismiss = viewModel::closeMemory,
            onEdit = viewModel::editMemory,
            // 先关页再跑初始化：初始化会开一个长任务，页面留着会挡住任务卡的反馈。
            onRunInit = { viewModel.closeMemory(); viewModel.runInitFromUi() },
            onBodyChange = viewModel::updateMemoryBody,
            onSave = viewModel::saveMemory,
            onCancelEdit = viewModel::cancelMemoryEdit,
        )
    }

    // ------------------------------------------------------------------ 关于与更新

    @Composable
    fun About(update: UpdateUiState, viewModel: WorkspaceViewModel) {
        AboutPage(
            onBack = viewModel::closeAbout,
            onOpenLicenses = viewModel::openLicenses,
            onOpenPrivacy = viewModel::openPrivacy,
            onOpenUpdate = viewModel::openUpdate,
            update = update,
        )
    }

    @Composable
    fun Update(state: UpdateUiState, viewModel: WorkspaceViewModel) {
        UpdatePage(
            state = state,
            onBack = viewModel::closeUpdate,
            onCheck = { viewModel.checkForUpdates() },
            onDownload = viewModel::downloadUpdate,
            onCancelDownload = viewModel::cancelUpdateDownload,
            onInstall = viewModel::installDownloadedUpdate,
        )
    }

    @Composable
    fun Licenses(viewModel: WorkspaceViewModel) {
        ThirdPartyLicensesPage(onBack = viewModel::closeLicenses)
    }

    @Composable
    fun Privacy(viewModel: WorkspaceViewModel) {
        PrivacyPolicyPage(onBack = viewModel::closePrivacy)
    }

    // ------------------------------------------------------------------ 引导页

    @Composable
    fun Oobe(state: WorkspaceUiState, viewModel: WorkspaceViewModel) {
        val context = androidx.compose.ui.platform.LocalContext.current
        OobePage(
            state = state,
            canDismissDirectly = !OobeStore.isRequired(context),
            onRefreshPermissions = viewModel::refreshOobePermissions,
            onNextStep = viewModel::advanceOobeStep,
            onBackStep = viewModel::goBackOobeStep,
            onDismissReopened = viewModel::dismissReopenedOobe,
            onSelectRuntimeMirror = viewModel::selectUbuntuMirror,
            onInstallRuntime = viewModel::installRuntime,
            onRepairRuntime = viewModel::repairRuntime,
            onToggleRoot = viewModel::toggleRootFromOobe,
            onOpenApiConfig = viewModel::openApiConfigFromOobe,
            onSkipOrFinish = viewModel::completeOobe,
        )
    }

    // ------------------------------------------------------------------ 调试

    @Composable
    fun UiDebug(
        state: WorkspaceUiState,
        viewModel: WorkspaceViewModel,
        glass: Glass,
        onExportSession: (String) -> Unit,
        onImportSession: () -> Unit,
    ) {
        UiDebugPage(
            state = state,
            viewModel = viewModel,
            glass = glass,
            onBack = viewModel::closeUiDebug,
            onExportSession = onExportSession,
            onImportSession = onImportSession,
        )
    }

    // ---------------------------------------------------------- 工作区内容区

    /*
     * 下面四个是**工作区内部**的内容，不是整页。
     *
     * ## 为什么它们要单独成为覆写点
     *
     * `Workspace` 那一页只是外壳（顶栏 + 页签 + 侧栏 + 分栏），真正的对话流、
     * 文件树、终端是 `WorkspaceLayouts` 里按页签分发的。如果接口只到 `Workspace`
     * 这一层，那么一套新风格换完外壳之后，**里面还是原来那三个页面** ——
     * 这正是 M3E 组此前的状态：导航栏与 17 个独立页换了样子，对话区一动没动。
     *
     * ## 参数为什么是 `state` + `viewModel`
     *
     * `FilesPage` 有三十多个参数，全部从 `state` 派生、回调全挂在 `viewModel` 上。
     * 把它们摊进接口会让契约无法阅读，且每加一个文件操作就要改接口。
     * 所以这里只给数据源与动作源，由各实现自己接线（与上面 17 个整页方法同一约定）。
     *
     * ## ⚠️ 行为逻辑不要复制
     *
     * 滚动锚定、吸底、前插稳定键、多选状态这些是**行为**，不是观感。它们必须继续
     * 只有一份（在 `ChatList` 里），新风格只替换"一条消息画成什么样"。
     * 复制一份列表逻辑的后果是：每个滚动 bug 都要在两组里各修一次。
     */

    @Composable
    fun ChatContent(
        state: WorkspaceUiState,
        viewModel: WorkspaceViewModel,
        wide: Boolean,
        modifier: Modifier,
        glass: Glass,
    ) {
        ChatPage(
            state = state,
            viewModel = viewModel,
            wide = wide,
            modifier = modifier,
            glass = glass,
        )
    }

    @Composable
    fun FilesContent(
        state: WorkspaceUiState,
        viewModel: WorkspaceViewModel,
        modifier: Modifier,
    ) {
        FilesPage(
            filePath = state.filePath,
            entries = state.fileEntries,
            emptyNote = state.fileNote,
            listOptions = state.fileListOptions,
            onQueryChange = viewModel::updateFileQuery,
            onSortChange = viewModel::updateFileSort,
            onToggleSortDirection = viewModel::toggleFileSortDirection,
            onOpen = viewModel::openFile,
            onNavigate = viewModel::navigateTo,
            root = state.fileRoot,
            onSwitchRoot = viewModel::switchFileRoot,
            nameForm = state.fileNameForm,
            onNewEntry = viewModel::newFileForm,
            onRename = viewModel::renameForm,
            onNameDraftChange = viewModel::updateFileNameDraft,
            onSubmitName = viewModel::submitFileNameForm,
            onCancelName = viewModel::cancelFileNameForm,
            deletePrompt = state.fileDeletePrompt,
            onConfirmDelete = viewModel::confirmDelete,
            onCancelDelete = viewModel::cancelDelete,
            sharedStorageGranted = state.sharedStorageGranted,
            onGrantSharedStorage = viewModel::openSharedStorageSettings,
            active = state.tab == com.zhizhu.zhicode.compose.model.WorkspaceTab.FILES,
            selectionMode = state.fileSelectionMode,
            fileClipboardCount = state.fileClipboard.size,
            fileClipboardMove = state.fileClipboardMove,
            onPasteFiles = viewModel::pasteFilesIntoCurrentDirectory,
            onSetSelection = viewModel::setFileSelection,
            selection = state.fileSelection,
            onLongPressEntry = viewModel::longPressFileEntry,
            onToggleEntry = viewModel::toggleFileSelection,
            onToggleSelectAll = viewModel::toggleSelectAllFiles,
            onClearSelection = viewModel::clearFileSelection,
            onRenameSelected = viewModel::renameSelectedEntry,
            onDeleteSelected = viewModel::deleteSelectedEntries,
            onAttachSelected = viewModel::attachSelectedEntries,
            modifier = modifier,
        )
    }

    @Composable
    fun TerminalContent(
        state: WorkspaceUiState,
        viewModel: WorkspaceViewModel,
        modifier: Modifier,
    ) {
        TerminalPage(
            lines = state.terminalLines,
            projectName = state.projectName,
            runtimeReady = state.runtimeReady,
            workingDirectory = state.projectPath,
            // 终端实例由 ViewModel 持有，**不能**在页面里 remember：
            // 切走页签会让那段 Composable 离开组合，实例若建在那里就会连会话一起销毁。
            terminalHolder = { ctx -> viewModel.terminalPane(ctx) },
            installing = state.runtimeInstalling,
            progress = state.runtimeProgress,
            message = state.runtimeMessage,
            onInstall = viewModel::installRuntime,
            modifier = modifier,
        )
    }

    // ------------------------------------------------------------------ 浮层

    /*
     * 浮层**不在这个契约里**。
     *
     * 它们不是页面：没有自己的路由、叠在工作区之上、由状态驱动显隐，而且各自
     * 带一长串与「页面观感」无关的业务回调（导出格式、附件选择、权限决策……）。
     * 塞进接口会让这里立刻长出二十个和主题无关的参数，而新主题的实现被迫透传
     * 一堆自己用不到的 lambda。
     *
     * 浮层的观感差异走**令牌层**：它们读的 `ZhiRadius` / 色板 / `Glass` 本来就
     * 跟着主题变。真要整体重画时，那是 `ui/OverlayHost` 自己的事。
     */
}

/**
 * 默认组：Miuix。
 *
 * 空实现 —— 每个方法都落回接口里的默认实现，也就是**本工程今天的界面**。
 * 这不是"占位"，而是刻意让默认组与"什么都没改"严格等价：任何视觉差异都只能
 * 来自它覆写的方法。
 */
object MiuixPageSet : ZhiPageSet {
    override val id: String = "miuix"
    override val name: String = "Miuix（默认）"
}

/**
 * 界面主题组注册表。
 *
 * 与 `THEME_PRESETS`（配色预设）同一个套路：一张表 + 一个带兜底的查找。
 * 新增一个主题组 = 写一个新的 `object XxxPageSet : ZhiPageSet { … }` +
 * 在 [all] 里加一行。**界面代码一行都不用改**。
 *
 * ## [byId] 为什么必须兜底
 *
 * `pageSetId` 是落盘的字符串。老版本写下的 id 在新版本里可能已经不存在
 * （改名、删除），那时应当**回落到默认组**而不是崩或空白 —— 与
 * `presetId` / `paletteStyle` / `chatFont` 的处理方式完全一致。
 */
object ZhiPageSets {

    /**
     * 全部可用组。**第一个是默认组**，`byId` 找不到时回到它。
     *
     * 加一个新主题组只需要在这里加一行 —— 界面代码一行都不用改。
     */
    val all: List<ZhiPageSet> = listOf(MiuixPageSet, Material3ExpressivePageSet, MochiPageSet)

    val default: ZhiPageSet get() = all.first()

    fun byId(id: String?): ZhiPageSet =
        if (id == null) default else all.firstOrNull { it.id == id } ?: default
}

/**
 * 当前界面主题组。
 *
 * 用 `staticCompositionLocalOf` 而不是 `compositionLocalOf`：它只在用户换主题时
 * 变一次，而读它的地方是**每一个页面的入口**。静态版本在值变化时会让整棵子树
 * 重组 —— 那正是换主题时想要的（一屏全换），而平时没有任何逐帧读取开销。
 */
val LocalZhiPageSet = staticCompositionLocalOf<ZhiPageSet> { MiuixPageSet }
