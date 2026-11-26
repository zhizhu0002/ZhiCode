package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.compose.runtime.Composable
import com.zhizhu.zhicode.compose.data.ThemeSettings
import com.zhizhu.zhicode.compose.model.ExtensionSummary
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
}
