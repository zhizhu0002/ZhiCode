package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.compose.runtime.Composable
import com.zhizhu.zhicode.compose.theme.LocalThemeSettings
import com.zhizhu.zhicode.compose.data.ThemeSettings
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

/**
 * Material 3 Expressive 主题页面组。
 *
 * 与 [com.zhizhu.zhicode.compose.ui.pages.MiuixPageSet] 并列注册在 [com.zhizhu.zhicode.compose.ui.pages.ZhiPageSets] 中，
 * 共用同一套业务状态层（[WorkspaceViewModel] / [ThemeSettings]），
 * 为工作区、设置主页、主题切换、聊天显示与关于/更新/隐私/开源许可提供原生的 MD3 Expressive 实现。
 */
object Material3ExpressivePageSet : ZhiPageSet {

    override val id: String = "m3_expressive"

    override val name: String = "MD3 Expressive"

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
    override fun Oobe(state: WorkspaceUiState, viewModel: WorkspaceViewModel) {
        M3eOobePage(state = state, viewModel = viewModel)
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
