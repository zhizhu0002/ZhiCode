package com.zhizhu.zhicode.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.dialogs.ApiConfigOverlay
import com.zhizhu.zhicode.compose.ui.dialogs.AttachFileOverlay
import com.zhizhu.zhicode.compose.ui.dialogs.ChoicePickerOverlay
import com.zhizhu.zhicode.compose.ui.dialogs.EnvironmentOverlay
import com.zhizhu.zhicode.compose.ui.dialogs.McpConfigOverlay
import com.zhizhu.zhicode.compose.ui.dialogs.MemoryOverlay
import com.zhizhu.zhicode.compose.ui.dialogs.ModelPickerOverlay
import com.zhizhu.zhicode.compose.ui.dialogs.PermissionOverlay
import com.zhizhu.zhicode.compose.ui.dialogs.PlanApprovalOverlay
import com.zhizhu.zhicode.compose.ui.dialogs.RoleCardsOverlay
import com.zhizhu.zhicode.compose.ui.dialogs.SkillsOverlay
import com.zhizhu.zhicode.compose.ui.dialogs.TaskListOverlay

/**
 * 弹窗挂载宿主：从 `ZhiCodeScreen` 原地拆出，内容逐字未改。
 *
 * ---- Overlay 系列：必须位于 Scaffold 内容里才能找到 popupHost ----
 * 弹窗打开时，先铺一层「模糊背景」：
 * Miuix 的 blur 只能在**同一个窗口**内采样背景（LayerBackdrop 录的是本窗口
 * 的 GraphicsLayer），所以没法在弹窗自己的窗口里做模糊 —— 改成在主窗口里
 * 把整个工作区糊掉，弹窗再画在它上面。视觉效果与 MIUI 的模态背景一致。
 * 长按动作菜单由**被长按的那一项**自己渲染（贴它弹出），不是居中对话框。
 * 所以它既不该进 modalOpen（那会给整个工作区再加一层背景模糊，而弹层
 * 自己已经带窗口变暗），也不该再走 ChoicePickerOverlay。
 */
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
        // 锚定菜单已经由被长按的条目自己画了，这里必须让位 ——
        // 否则长按后会出现「下拉菜单 + 居中对话框」同时可见。
        picker = if (anchoredActionMenu) null else state.choicePicker,
        onSubmit = viewModel::onSubmitSelection,
        onDismiss = viewModel::dismissChoicePicker,
        onSubmitFreeForm = viewModel::onSubmitFreeForm,
    )
    AttachFileOverlay(
        open = state.attachPickerOpen,
        query = state.attachQuery,
        hits = state.attachHits,
        onQueryChange = viewModel::updateAttachQuery,
        onPick = { hit -> viewModel.attachProjectFile(hit.path, hit.relative) },
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
    MemoryOverlay(
        state = state.memory,
        onDismiss = viewModel::closeMemory,
        onEdit = viewModel::editMemory,
        onRunInit = { viewModel.closeMemory(); viewModel.runInitFromUi() },
        onBodyChange = viewModel::updateMemoryBody,
        onSave = viewModel::saveMemory,
        onCancelEdit = viewModel::cancelMemoryEdit,
    )
}
