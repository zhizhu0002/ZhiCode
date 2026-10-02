package com.zhizhu.zhicode.compose.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
        // 底部悬浮层（任务卡 + 输入器）的真实高度，用来给对话列表留白。
        //
        // ⚠️ 以前这里是**两个写死的常量**（输入器 92dp + 任务卡 140dp）。写死的代价是
        // 它永远不会跟着内容变：输入器多长一行、挂了附件条、开了调试模式的 Markdown
        // 实时预览、或者任务卡里任务变多 —— 悬浮层就变高，而预留的留白不变，
        // 于是"滑到最底部还有内容被遮住"。
        //
        // 现在改成**实测**：悬浮那一列自己 onSizeChanged 报高度，对话列表按它留白。
        // 这不是循环依赖 —— 悬浮列在 Box 里独立于列表（列表 fillMaxSize），
        // 它的高度不受 bottomInset 影响，所以量一次就稳定。
        //
        // 初始值给一个够用的下限（首帧还没量到），避免第一帧底部贴太紧。
        var floatingHeightPx by remember { mutableStateOf(0) }
        val density = LocalDensity.current
        val bottomInset = with(density) {
            (floatingHeightPx.toDp() + FloatingBottomGap).coerceAtLeast(MinFloatingInset)
        }

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
            // 单个工具的 ⋯：动作作用在某一行上，所以把 toolId 一起传下去。
            onToolActions = viewModel::showToolActions,
            // 长按消息的动作菜单：由那一条消息自己渲染（从手指位置长出来）
            anchoredMenu = { anchorId, fingerOffset ->
                ZhiAnchoredMenuHost(state, viewModel, anchorId, fingerOffset)
            },
            modifier = glass.capture(Modifier.fillMaxSize()),
            // 底部留白 = 实测的悬浮层高度 + 一点余量：让被盖住的内容也能滑上来。
            // 顶部留白：S1 重构后顶栏在 topBar 槽位已由 Scaffold padding 处理，
            // 对话列表不再需要让出头部高度，可从 Scaffold padding 顶部起排。
            bottomInset = bottomInset,
            // 首条消息落在顶栏（含 Tab 行）下缘；列表全高，滚动时消息从顶栏 blur 下穿过
            topInset = TopBarInsetWithTabs,
            // 主体调试模式：在真实消息上就地显示类型/长度/工具计数 + Markdown 源码开关；
            // 并把每条消息的细节默认全展开、任务清单内联进对话流（见 ChatList 的同名参数）
            debugMode = state.debugAppMode,
            // 内联任务清单的数据：调试模式才传，非调试时是空列表（不渲染）。
            debugTasks = if (state.debugAppMode) state.tasks else emptyList(),
        )

        // 底部悬浮层：任务卡在上、输入器在下，两者都不占布局高度，
        // 所以对话区始终铺满，且它们不随对话滚动。
        // 对应原版把 AgentProgressView + composerHost 放进位于 chatScroll
        // 之外的 chatBottomHost（MainActivity.java:1325-1331）。
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // 实测自己的高度回报给上面的 bottomInset。
                // `onSizeChanged` 只在尺寸真的变了时回调，所以正常的打字/滚动不产生额外开销。
                .onSizeChanged { floatingHeightPx = it.height },
        ) {
            AnimatedVisibility(
                visible = floating,
                enter = slideInVertically(ZhiMotion.enterSpec) { it / 2 } +
                    fadeIn(ZhiMotion.fadeInSpec),
                exit = slideOutVertically(ZhiMotion.exitSpec) { it / 2 } +
                    fadeOut(ZhiMotion.fadeOutSpec),
            ) {
                FloatingAgentStatus(
                    status = state.workingStatus.orEmpty(),
                    tasks = state.tasks,
                    onExpand = viewModel::openTaskList,
                    wide = wide,
                    glass = glass,
                )
            }

            // 操作反馈条：夹在任务卡与输入器之间。
            //
            // 为什么放这一层而不是 SnackbarHost：Snackbar 是覆盖在内容之上的浮层，
            // 会压住底部输入器（这正是它当初被删掉的原因）；而放在这个 Column 里
            // 它参与布局，长高只会把输入器往上顶，永远不遮挡。
            MessageBar(
                message = state.message,
                isError = state.messageIsError,
                onDismiss = { viewModel.clearMessage() },
                wide = wide,
                glass = glass,
            )

            // 悬浮输入器
            ComposerHost(state = state, viewModel = viewModel, wide = wide, glass = glass)
        }
    }
}

/**
 * 悬浮层之上再留的余量。
 *
 * 不只是为了好看：输入器自带投影与圆角，内容贴着它上缘会显得被"压"住；
 * 留 8dp 之后最底部那条消息与输入器之间有一条干净的缝。
 */
private val FloatingBottomGap = 8.dp

/**
 * 实测值到手之前的兜底下限。
 *
 * 首帧 `onSizeChanged` 还没回调，若此时按 0 留白，用户会看到内容"先贴底、再弹上来"。
 * 取一个偏小的值（不是旧的 92dp）：宁可第一帧略紧，也不要一开始就凭空多出一大块空白。
 */
private val MinFloatingInset = 72.dp

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
    // 外壳走共用的 FloatingBottomShell：内缩/圆角/阴影/模糊与反馈条、输入器同源，
    // 三块在同一列上必须"同框"（形态以之前的发送栏为准，见 Common.kt）。
    // verticalPadding = 8dp：沿用任务卡原本的纵向呼吸空间。
    FloatingBottomShell(
        wide = wide,
        glass = glass,
        verticalPadding = 8.dp,
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
    // 用 GetMultipleContents 而不是 GetContent：相册里一次挑多张是常态
    // （对比截图、多页文档），只能选一张的话用户得反复进出选择器。
    //
    // 它仍然不是 OpenDocument：这里的授权只为"立刻读一次字节"服务，
    // 内容读完就进内存了，不需要跨进程重启保留的持久授权。
    // （原版用 ACTION_OPEN_DOCUMENT + takePersistableUriPermission，是因为它把 Uri
    //  留在附件列表里直到用户点发送，中间可能经历一次重组甚至进程重启。）
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris -> viewModel.attachImages(uris) }

    Composer(
        state = state,
        wide = wide,
        glass = glass,
        onTextChange = viewModel::onComposerChange,
        onSend = viewModel::send,
        onStop = viewModel::stop,
        onRemoveAttachment = { viewModel.removeAttachment(it.id) },
        // `+` 菜单的三个动作
        onAttachFile = viewModel::openAttachPicker,
        onOpenFilesTab = { viewModel.selectTab(WorkspaceTab.FILES) },
        onPickImage = { pickImage.launch("image/*") },
        // 输入器里待发图片的缩略图数据（按附件 id 取，不进 state）
        onAttachmentImage = viewModel::currentAttachmentImage,
        // 页脚的下拉：选中即生效，不再弹选择器
        onPermissionSelected = viewModel::setPermissionMode,
        onEffortSelected = viewModel::setEffort,
        onModelChip = viewModel::showModelPicker,
        onPickSlash = viewModel::pickSlashCommand,
        // 主体调试模式：输入行下方实时渲染当前输入的 Markdown（与对话流同一渲染器）
        debugMode = state.debugAppMode,
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
