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

    /* 浮层**不在这个契约里**。 */
}

/** 默认组：Miuix。 */
object MiuixPageSet : ZhiPageSet {
    override val id: String = "miuix"
    override val name: String = "Miuix（默认）"
}

object ZhiPageSets {

    /** 全部可用组。 */
    val all: List<ZhiPageSet> = listOf(MiuixPageSet, Material3ExpressivePageSet, MochiPageSet)

    val default: ZhiPageSet get() = all.first()

    fun byId(id: String?): ZhiPageSet =
        if (id == null) default else all.firstOrNull { it.id == id } ?: default
}

/** 当前界面主题组。 */
val LocalZhiPageSet = staticCompositionLocalOf<ZhiPageSet> { MiuixPageSet }
