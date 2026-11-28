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
    // 背景模糊层按用户要求移除：OverlayDialog 自带窗口变暗（windowDimming），
    // 再叠一层整屏 blur 属于重复的视觉噪音。保留 modalOpen 判断是为了
    // 后续若要按弹窗区分遮蔽策略时有个现成挂点。
    val modalOpen = state.permissionRequest != null ||
        state.planApproval != null ||
        (state.choicePicker != null && !anchoredActionMenu) ||
        state.environmentOpen ||
        state.attachPickerOpen ||
        state.taskListOpen

    // 一次性事件：把准备要分享的内容交给系统分享面板。
    // 放在这里而不是各页面里 —— 这个宿主是全局唯一的挂载点之一。
    // 导出的落盘入口：SAF（系统文件管理器）。渲染图片要真实 Activity +
    // 独立 composition，只能在界面层做，所以整条链路都在这里。
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
        // ⚠️ 用 `closeChoicePicker` 而不是 `dismissChoicePicker`。
        //
        // 后者是"容器收起动画结束"的通知，它对会话重命名/删除确认两个意图
        // 故意什么都不做 —— 因为动画结束时新状态可能已经写进去了，
        // 无条件清空会把刚打开的重命名对话框一起关掉（那正是「点重命名没反应」的根因）。
        //
        // 但「取消」按钮和点击空白走的是**同一个**回调。如果这里也用
        // `dismissChoicePicker`，那对话框就永远关不掉了（用户报的
        // 「点击取消也无法关闭」「点空白也关不掉」）。
        // `closeChoicePicker` 是无条件关闭，正是这两个入口需要的语义。
        onDismiss = viewModel::closeChoicePicker,
        onSubmitFreeForm = viewModel::onSubmitFreeForm,
    )
    // 「自由复制」底部抽屉：把一条消息的原文摊开让人划词。
    // 放在这里而不是对话页里，与其它浮层同处一个挂载点 —— 它同样要在
    // Scaffold 的 popupHost 上渲染，而对话页在宽屏下只是三个面板之一。
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
