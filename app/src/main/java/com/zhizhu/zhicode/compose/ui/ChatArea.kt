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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
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
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
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
            // 组键由界面按 toolId 推导（见 ToolGrouping），所以这里直接把它交给 VM ——
            // 以前要在这里先把整批的 toolIds 算出来当"要收起的成员集合"，那是把
            // 界面的分组结构算进了 ViewModel 的记账方式里。
            onToggleGroup = viewModel::toggleGroupExpanded,
            onToggleThinking = viewModel::toggleThinking,
            onMessageActions = viewModel::showMessageActions,
            // 单个工具的 ⋯ 菜单里选中了一项：动作作用在某一行上，所以把 toolId 一起传下去。
            // 传的是**消息 id**（不是 ChatItem）—— VM 只需要一个能在 transcript 里定位的键，
            // 拿整条消息进去反而会让"动作作用在旧快照上"变得可能。
            onToolAction = { item, toolId, label -> viewModel.applyToolAction(item.id, toolId, label) },
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

        // 输入法弹出时，悬浮层要往上顶多少。
        //
        // ## 为什么不能直接用 `Modifier.imePadding()`
        //
        // 键盘是从**屏幕底边**长上来的，它的高度里包含导航栏那一段；而 `AppScaffold`
        // 已经在内容容器底部让出了导航栏（`padding(bottom = padding.calculateBottomPadding())`，
        // 来源是 Miuix Scaffold 的 `contentWindowInsets = systemBars ∪ displayCutout`）。
        // 直接 `imePadding()` 等于垫两次，输入框会停在键盘上方空出**一条导航栏的缝**。
        //
        // 所以要的是差值：窗口高 H，键盘顶边 y = H − ime，内容容器底边 y = H − navBar，
        // 中间要补的空隙 = (H − navBar) − (H − ime) = ime − navBar。
        // 夹到非负是必须的 —— 键盘收起时 IME 是 0，差值为负，
        // 负数内边距会把输入框推到屏幕外面去。
        //
        // ## ⚠️ 有全屏浮层时**必须不抬**（用户实测报的 bug）
        //
        // `WindowInsets.ime` 是**窗口级**的：键盘是谁提起来的它不区分。侧栏抽屉
        // （窄屏，画在 Scaffold 之上）里有一个「搜索会话」输入框，点它提键盘时
        // IME 同样变正 —— 于是**后面那个对话输入器会一起抬起来**，从抽屉右边露出来的
        // 那条缝里就能看见它整个上移了一截（用户原话：
        // 「为什么在搜索会话打开输入法，后面的聊天发送框会自动抬起」）。
        //
        // 这里没有去猜"焦点在谁身上"（Compose 没给可靠的窗口级焦点查询），而是直接
        // 按**有没有全屏浮层盖住工作区**判断：盖住的时候，这一次 IME 变化与本输入器无关。
        // 设置页 / UI 调试页 / 环境页里的搜索框是同一类问题，所以一起排除掉。
        val coveredByFullScreenOverlay = state.sidebarOpen || state.settingsOpen ||
            state.uiDebugOpen || state.environmentOpen
        val imeLift = if (coveredByFullScreenOverlay) {
            0.dp
        } else {
            with(LocalDensity.current) {
                (WindowInsets.ime.getBottom(this) - WindowInsets.navigationBars.getBottom(this))
                    .coerceAtLeast(0)
                    .toDp()
            }
        }

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
                //
                // ⚠️ 它必须在 `padding(bottom = imeLift)` **左边**：onSizeChanged 报的是
                // 它右侧（内层）量出来的尺寸 —— 让给键盘的那一段算进去之后，
                // 上面的 bottomInset 才会跟着够到键盘上缘，于是被键盘挡住的内容还能滑上来。
                // 不然只是输入框抬起来，最后几条消息永远压在键盘后面。
                .onSizeChanged { floatingHeightPx = it.height }
                // 输入法弹出时把整块悬浮层（任务卡 + 反馈条 + 输入器）顶到键盘之上。
                //
                // ## 为什么必须自己接，而不是指望清单里的 adjustResize
                //
                // 清单里确实是 `windowSoftInputMode="adjustResize"`，但 `MainActivity`
                // 在 `onCreate` 里调了 `enableEdgeToEdge()` —— 它等价于
                // `setDecorFitsSystemWindows(false)`：DecorView 不再消费系统窗口 inset，
                // 系统那套"把窗口缩小让出键盘"也随之失效，IME 的高度只能由应用自己接。
                //
                // 参考实现（反编译版 `installKeyboardMotion()`，MainActivity.java:1043-1115）
                // 同样 `setDecorFitsSystemWindows(false)`，然后自己挂
                // `setOnApplyWindowInsetsListener` + `WindowInsetsAnimation.Callback`，
                // 把 `getInsets(Type.ime()).bottom` 一路 `applyKeyboardOffset(inset)`
                // 顶到根布局上 —— 它从来没依赖过 adjustResize。
                .padding(bottom = imeLift),
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
    val picker = state.choicePicker
    // 菜单从手指位置长出来，而退场动画期间 choicePicker 已经是 null —— 不兜住的话，
    // 面板会在退场那几帧里弹回条目左上角再消失。
    val shownPicker = rememberLastNonNull(picker) ?: return
    val open = picker != null && picker.isActionMenu && picker.anchorId == anchorId
    if (!shownPicker.isActionMenu || shownPicker.anchorId != anchorId) return
    ZhiAnchoredActionMenu(
        // 不套 `if`：退出动画需要浮层常驻组合（见 ZhiAnchoredActionMenu 的说明）。
        open = open,
        labels = shownPicker.options.map { it.label },
        onSelect = viewModel::onChoiceSelected,
        onDismiss = viewModel::dismissChoicePicker,
        // 非空时面板从**手指那一点**长出来；为空则退回贴条目锚定。
        fingerOffset = fingerOffset,
    )
}
