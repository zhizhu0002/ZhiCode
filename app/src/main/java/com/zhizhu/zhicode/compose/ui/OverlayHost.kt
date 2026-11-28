package com.zhizhu.zhicode.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import com.zhizhu.zhicode.compose.model.ChoiceIntent
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.pages.miuix.overlays.AttachFileOverlay
import com.zhizhu.zhicode.compose.ui.pages.miuix.overlays.ChoicePickerOverlay
import com.zhizhu.zhicode.compose.ui.pages.miuix.overlays.CopySheetOverlay
import com.zhizhu.zhicode.compose.ui.pages.miuix.overlays.EnvironmentOverlay
import com.zhizhu.zhicode.compose.ui.pages.miuix.overlays.PermissionOverlay
import com.zhizhu.zhicode.compose.ui.pages.miuix.overlays.PlanApprovalOverlay
import com.zhizhu.zhicode.compose.ui.pages.miuix.overlays.TaskListOverlay
import com.zhizhu.zhicode.compose.ui.pages.m3e.M3ePermissionOverlay
import com.zhizhu.zhicode.compose.ui.pages.m3e.M3ePlanApprovalOverlay
import com.zhizhu.zhicode.compose.ui.pages.m3e.M3eOverlayHost
import com.zhizhu.zhicode.compose.theme.LocalThemeSettings

/** 弹窗挂载宿主：从 `ZhiCodeScreen` 原地拆出，内容逐字未改。 */
@Composable
internal fun ZhiOverlayHost(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    glassMain: Glass,
) {
    val anchoredActionMenu = state.choicePicker?.isActionMenu == true
    // 背景模糊层按用户要求移除：OverlayDialog 自带窗口变暗（windowDimming）
    val modalOpen = state.permissionRequest != null ||
        state.planApproval != null ||
        (state.choicePicker != null && !anchoredActionMenu) ||
        state.environmentOpen ||
        state.attachPickerOpen ||
        state.taskListOpen

    // 一次性事件：把准备要分享的内容交给系统分享面板。
    com.zhizhu.zhicode.compose.ui.export.ExportSaver(
        payload = state.pendingExport,
        onConsumed = viewModel::clearPendingExport,
        onDone = viewModel::showMessage,
    )

    if (LocalThemeSettings.current.pageSetId == "m3_expressive") {
        M3ePermissionOverlay(request = state.permissionRequest, onDecision = viewModel::resolvePermission)
        M3ePlanApprovalOverlay(
            plan = state.planApproval,
            onApprove = { viewModel.resolvePlan(true) },
            onRevise = { viewModel.resolvePlan(false) },
            onReviseWithFeedback = viewModel::resolvePlanWithFeedback,
        )
        M3eOverlayHost(state = state, viewModel = viewModel)
    } else {
        PermissionOverlay(request = state.permissionRequest, onDecision = viewModel::resolvePermission)
        PlanApprovalOverlay(
            plan = state.planApproval,
            onApprove = { viewModel.resolvePlan(true) },
            onRevise = { viewModel.resolvePlan(false) },
            onReviseWithFeedback = viewModel::resolvePlanWithFeedback,
        )
        com.zhizhu.zhicode.compose.ui.pages.miuix.overlays.ExportSheetOverlay(
            sheet = state.exportSheet,
            onDismiss = viewModel::dismissExportSheet,
            onIncludeReasoningChange = viewModel::setExportIncludeReasoning,
            onExpandReasoningChange = viewModel::setExportExpandReasoning,
            onExportMarkdown = viewModel::exportSheetAsMarkdown,
            onExportImage = viewModel::exportSheetAsImage,
            onSelectMore = viewModel::exportSelectMore,
        )
        ChoicePickerOverlay(
            // 锚定菜单已经由被长按的条目自己画了，这里必须让位 ——
            // 否则长按后会出现「下拉菜单 + 居中对话框」同时可见。
            picker = if (anchoredActionMenu) null else state.choicePicker,
            onSubmit = viewModel::onSubmitSelection,
            onDismiss = viewModel::closeChoicePicker,
            onSubmitFreeForm = viewModel::onSubmitFreeForm,
        )
        // 「自由复制」底部抽屉：把一条消息的原文摊开让人划词。
        CopySheetOverlay(
            sheet = state.copySheet,
            onDismiss = viewModel::closeCopySheet,
            onCopyAll = viewModel::copySheetAll,
        )
        AttachFileOverlay(
            open = state.attachPickerOpen,
            browser = state.attachBrowser,
            onFilterChange = viewModel::updateAttachFilter,
            onNavigate = viewModel::attachNavigateTo,
            onSwitchRoot = viewModel::attachSwitchRoot,
            onPick = { entry -> viewModel.attachProjectFile(entry.path) },
            onDismiss = viewModel::closeAttachPicker,
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
            selectedMirrorId = state.ubuntuMirrorId,
            onSelectMirror = viewModel::selectUbuntuMirror,
            onDismiss = viewModel::closeEnvironment,
            onInstall = viewModel::installRuntime,
            onRepair = viewModel::repairRuntime,
            onRefresh = viewModel::refreshEnvironmentReport,
            onCopy = { viewModel.copyEnvironmentReport() },
        )
    }
}
