package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.LocalThemeSettings
import com.zhizhu.zhicode.compose.data.ThemeSettings
import com.zhizhu.zhicode.compose.editor.EditorViewModel
import com.zhizhu.zhicode.compose.model.ExtensionSummary
import com.zhizhu.zhicode.compose.model.MemoryState
import com.zhizhu.zhicode.compose.model.RoleCardsState
import com.zhizhu.zhicode.compose.model.SearchServicesState
import com.zhizhu.zhicode.compose.model.McpConfigState
import com.zhizhu.zhicode.compose.model.SettingsDraft
import com.zhizhu.zhicode.compose.model.UpdateUiState
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.Glass
import com.zhizhu.zhicode.compose.ui.pages.ZhiPageSet

/** M3E 主题页面组，与 MiuixPageSet 并列注册。 */
object Material3ExpressivePageSet : ZhiPageSet {

    override val id: String = "m3_expressive"

    override val name: String = "MD3 Expressive"

    /** M3E 的卡片圆角取 `ShapeTokens.CornerExtraLarge`。 */
    override val cardCorner: Dp = 28.dp

    /** 内嵌块取 `CornerLarge`，与 28 − 16 内边距的同心值一致。 */
    override val innerCorner: Dp = 16.dp

    /** 卡片底色取 M3E 的容器角色，而不是麻薯组的定值。 */
    @Composable
    override fun cardSurface(): Color = MaterialTheme.colorScheme.surfaceContainer

    @Composable
    override fun Workspace(
        state: WorkspaceUiState,
        viewModel: WorkspaceViewModel,
        isDark: Boolean,
        wide: Boolean,
        glass: Glass,
        glassMain: Glass,
    ) {
        M3eWorkspacePage(
            state = state,
            viewModel = viewModel,
            isDark = isDark,
            wide = wide,
            glass = glass,
            glassMain = glassMain,
        )
    }

    @Composable
    override fun Settings(
        draft: SettingsDraft?,
        viewModel: WorkspaceViewModel,
        extensions: ExtensionSummary,
    ) {
        M3eSettingsPage(
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
    override fun Theme(theme: ThemeSettings, viewModel: WorkspaceViewModel) {
        M3eThemePage(
            theme = theme,
            onDismiss = viewModel::closeThemePage,
            onChange = viewModel::updateThemeSettings,
        )
    }

    @Composable
    override fun ChatDisplay(theme: ThemeSettings, viewModel: WorkspaceViewModel) {
        M3eChatDisplayPage(
            theme = theme,
            onBack = viewModel::closeChatDisplay,
            onChange = viewModel::updateThemeSettings,
        )
    }

    @Composable
    override fun Prompts(state: WorkspaceUiState, viewModel: WorkspaceViewModel) {
        M3ePromptsPage(
            initPrompt = state.settings.initPrompt,
            summaryPrompt = state.settings.summaryPrompt,
            compactPromptHead = state.settings.compactPromptHead,
            onDismiss = viewModel::closePrompts,
            onChange = viewModel::setPromptSlot,
            onReset = viewModel::resetPromptSlot,
        )
    }

    @Composable
    override fun ApiConfig(config: com.zhizhu.zhicode.compose.model.ApiConfigState?, viewModel: WorkspaceViewModel) {
        M3eApiConfigPage(
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
    override fun Memory(state: MemoryState?, viewModel: WorkspaceViewModel) {
        M3eMemoryPage(
            state = state,
            onDismiss = viewModel::closeMemory,
            onEdit = viewModel::editMemory,
            onRunInit = { viewModel.closeMemory(); viewModel.runInitFromUi() },
            onBodyChange = viewModel::updateMemoryBody,
            onSave = viewModel::saveMemory,
            onCancelEdit = viewModel::cancelMemoryEdit,
        )
    }

    @Composable
    override fun RoleCards(state: RoleCardsState?, viewModel: WorkspaceViewModel) {
        M3eRoleCardsPage(
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
    override fun SearchServices(state: SearchServicesState?, viewModel: WorkspaceViewModel) {
        M3eSearchServicesPage(
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
    override fun McpConfig(config: McpConfigState?, viewModel: WorkspaceViewModel) {
        M3eMcpConfigPage(
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
    override fun Skills(state: com.zhizhu.zhicode.compose.model.SkillsState?, viewModel: WorkspaceViewModel) {
        M3eSkillsPage(
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
    override fun About(update: UpdateUiState, viewModel: WorkspaceViewModel) {
        M3eAboutPage(
            update = update,
            onBack = viewModel::closeAbout,
            onOpenLicenses = viewModel::openLicenses,
            onOpenPrivacy = viewModel::openPrivacy,
            onOpenUpdate = viewModel::openUpdate,
        )
    }

    @Composable
    override fun Update(state: UpdateUiState, viewModel: WorkspaceViewModel) {
        M3eUpdatePage(
            state = state,
            onBack = viewModel::closeUpdate,
            onCheck = { viewModel.checkForUpdates() },
            onDownload = viewModel::downloadUpdate,
            onCancelDownload = viewModel::cancelUpdateDownload,
            onInstall = viewModel::installDownloadedUpdate,
        )
    }

    @Composable
    override fun Licenses(viewModel: WorkspaceViewModel) {
        M3eLicensesPage(onBack = viewModel::closeLicenses)
    }

    @Composable
    override fun Privacy(viewModel: WorkspaceViewModel) {
        M3ePrivacyPage(onBack = viewModel::closePrivacy)
    }

    @Composable
    override fun WideTopBar(
        state: WorkspaceUiState,
        viewModel: WorkspaceViewModel,
        onOpenSidebar: () -> Unit,
        onSettings: () -> Unit,
        modifier: Modifier,
    ) {
        M3eWideTopBar(
            state = state,
            viewModel = viewModel,
            onOpenSidebar = onOpenSidebar,
            onSettings = onSettings,
            modifier = modifier,
        )
    }

    @Composable
    override fun Oobe(state: WorkspaceUiState, viewModel: WorkspaceViewModel) {
        M3eOobePage(state = state, viewModel = viewModel)
    }

    @Composable
    override fun ChatContent(
        state: WorkspaceUiState,
        viewModel: WorkspaceViewModel,
        wide: Boolean,
        modifier: Modifier,
        glass: Glass,
    ) {
        M3eChatContent(
            state = state,
            viewModel = viewModel,
            wide = wide,
            modifier = modifier,
            glass = glass,
        )
    }

    @Composable
    override fun FilesContent(
        state: WorkspaceUiState,
        viewModel: WorkspaceViewModel,
        modifier: Modifier,
    ) {
        M3eFilesContent(state = state, viewModel = viewModel, modifier = modifier)
    }

    @Composable
    override fun TerminalContent(
        state: WorkspaceUiState,
        viewModel: WorkspaceViewModel,
        modifier: Modifier,
    ) {
        M3eTerminalContent(state = state, viewModel = viewModel, modifier = modifier)
    }

    @Composable
    override fun SandboxBoard(onExit: () -> Unit) {
        M3eSandboxBoardPage(onExit = onExit)
    }

    @Composable
    override fun Editor(viewModel: EditorViewModel, onExit: () -> Unit) {
        M3eEditorPage(viewModel = viewModel, onExit = onExit)
    }

    @Composable
    override fun UiDebug(
        state: WorkspaceUiState,
        viewModel: WorkspaceViewModel,
        glass: Glass,
        onExportSession: (String) -> Unit,
        onImportSession: () -> Unit,
    ) {
        M3eUiDebugPage(
            state = state,
            viewModel = viewModel,
            onBack = viewModel::closeUiDebug,
            onExportSession = onExportSession,
            onImportSession = onImportSession,
        )
    }
}
