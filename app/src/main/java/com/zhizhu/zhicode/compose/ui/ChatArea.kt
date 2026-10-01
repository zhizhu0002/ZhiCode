package com.zhizhu.zhicode.compose.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.AgentTask
import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.chat.AgentProgressCard
import com.zhizhu.zhicode.compose.ui.chat.ChatList
import com.zhizhu.zhicode.compose.ui.composer.Composer
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** （从 `AppScaffold.kt` 原地拆出，内容逐字未改。） */
@Composable
internal fun ChatArea(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    wide: Boolean,
    modifier: Modifier = Modifier,
    glass: Glass,
) {
    // 这张卡是「任务进度」，所以**只在真有任务时**才出现。
    //
    // 历史与取舍（两次改动方向相反，别再来回改）：
    //  1. 最初是 `workingStatus != null || tasks.isNotEmpty()` —— 于是 AI 只要用
    //     TaskCreate 列过一次清单，答完之后卡还会一直挂着。
    //  2. 改成只看 `workingStatus != null` —— 又走到另一个极端：单纯「正在思考…」
    //     时也会弹出一张「任务进度 0 / 0」，一个任务都没有却叫"任务进度"，是噪音。
    //  3. 现在只看 `tasks.isNotEmpty()`：没任务就不显示，列出任务后显示，与原版的
    //     多步进度能力一致。
    //
    // 注意 `workingStatus` 因此不再有专门的展示位（它是"正在思考…/正在执行 X…"这类
    // 进行时文案）。当前是否在跑由输入器右侧的停止键表达。
    val floating = state.tasks.isNotEmpty()

    // 对话区**不加框**（改过两次，别再加回来）。
    //
    // 原来这里套了一层 `Surface(border = BorderStroke(1.dp, dividerLine))`，
    // 外加 6dp 四周留白，理由是"纯色背板上没有边界会让对话与面板糊成一片"。
    // 实际看下来这条线是**多余的视觉噪音**：面板底色与消息卡片本身已经有对比，
    // 而这条线紧贴屏幕边缘，看着像一个白加的容器。去掉后对话直接铺满面板。
    //
    // ⚠️ 不要顺手把 6dp 留白搬到下面的 Box 上：那个留白原本只为了让线不贴边，
    // 线没了留白就没意义了（`ChatFramePadding` 已一并删掉）。
    Box(modifier = modifier.fillMaxSize()) {
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
            // 长按消息的动作菜单：由那一条消息自己渲染（从手指位置长出来）
            anchoredMenu = { anchorId, fingerOffset ->
                ZhiAnchoredMenuHost(state, viewModel, anchorId, fingerOffset)
            },
            modifier = glass.capture(Modifier.fillMaxSize()),
            // 底部预留出悬浮层的高度，让被盖住的内容也能滑上来；
            // 顶部留白：S1 重构后顶栏在 topBar 槽位已由 Scaffold padding 处理，
            // 对话列表不再需要让出头部高度，可从 Scaffold padding 顶部起排。
            bottomInset = if (floating) ComposerInset + TaskCardInset else ComposerInset,
            topInset = 0.dp,
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
                enter = slideInVertically(tween(ZhiMotion.MEDIUM, easing = EaseOutCubic)) { it / 2 } +
                    fadeIn(tween(ZhiMotion.MEDIUM)),
                exit = slideOutVertically(tween(ZhiMotion.FAST)) { it / 2 } +
                    fadeOut(tween(ZhiMotion.FAST)),
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

/** 悬浮输入器占位高度（含底部外边距），供对话列表留白使用。 */
private val ComposerInset = 92.dp

/** 悬浮任务卡占位高度（含外边距），仅在任务运行时参与留白计算。 */
private val TaskCardInset = 140.dp

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
    val shape = RoundedCornerShape(ZhiRadius.floating)
    FloatingToolbar(
        modifier = Modifier.fillMaxWidth().then(glass.blur(Modifier, shape, radius = 24f)),
        color = glass.surfaceColor(scheme.surfaceContainer),
        cornerRadius = ZhiRadius.floating,
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
        onRemoveAttachment = { viewModel.removeAttachment(it.id) },
        // `+` 菜单的四个动作
        onAttachFile = viewModel::openAttachPicker,
        onOpenSkills = viewModel::openSkills,
        onOpenFilesTab = { viewModel.selectTab(WorkspaceTab.FILES) },
        onPickImage = { pickImage.launch("image/*") },
        // 页脚的下拉：选中即生效，不再弹选择器
        onPermissionSelected = viewModel::setPermissionMode,
        onEffortSelected = viewModel::setEffort,
        onModelChip = viewModel::showModelPicker,
        onPickSlash = viewModel::pickSlashCommand,
    )
}

/**
 * 长按动作菜单的**宿主插槽**：交给被长按的那一项去调用。
 *
 * 每一项在**自己的布局里**调用 `anchoredMenu(自己的 id)`，菜单就会锚在那一项上
 * 弹出（见 [ZhiAnchoredActionMenu]）。这里集中做三件事，避免每个调用点各写一遍：
 *
 * 1. 只有 `anchorId` 与传进来的 id 相同的那一项才认领这份菜单 —— 否则列表里
 *    每一条都会弹一个；
 * 2. 不是动作菜单（`isActionMenu` 为假）时什么都不画，交回 `ChoicePickerOverlay`；
 * 3. 点击走**原有的** [WorkspaceViewModel.onChoiceSelected]（按下标分发，详见
 *    该方法里的 intent 分支）—— 动作语义、目标记忆、错误提示全部复用。
 */
@Composable
internal fun ZhiAnchoredMenuHost(
    state: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    anchorId: String,
    fingerOffset: DpOffset?,
) {
    val picker = state.choicePicker ?: return
    if (!picker.isActionMenu || picker.anchorId != anchorId) return
    ZhiAnchoredActionMenu(
        labels = picker.options.map { it.label },
        onSelect = viewModel::onChoiceSelected,
        onDismiss = viewModel::dismissChoicePicker,
        // 非空时面板从**手指那一点**长出来；为空则退回贴条目锚定。
        fingerOffset = fingerOffset,
    )
}
