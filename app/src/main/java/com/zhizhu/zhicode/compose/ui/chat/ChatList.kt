package com.zhizhu.zhicode.compose.ui.chat

import kotlinx.coroutines.coroutineScope
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import com.zhizhu.zhicode.compose.model.blockRangeSelection
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.clickable
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import top.yukonga.miuix.kmp.basic.Surface
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.AgentTask
import com.zhizhu.zhicode.compose.model.ChatItem
import com.zhizhu.zhicode.compose.model.ChatKind
import com.zhizhu.zhicode.compose.model.TaskState
import com.zhizhu.zhicode.compose.model.ToolGrouping
import com.termux.app.zhicode.storage.ZhiMemory
import com.zhizhu.zhicode.compose.model.TurnLayout
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.pages.LocalZhiPageSet
import com.zhizhu.zhicode.compose.ui.LocalImeMotion
import com.zhizhu.zhicode.compose.ui.ZhiHorizontalDivider
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMarkdown
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.ZhiSmallPill
import com.zhizhu.zhicode.compose.ui.debug.ZhiFrameTrace
import com.zhizhu.zhicode.compose.ui.rememberFingerTracker
import top.yukonga.miuix.kmp.anim.SinOutEasing
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 对话内容的指纹。 */
private data class ContentStamp(
    val size: Int,
    val body: Int,
    val thinking: Int,
    val tools: Int,
    val session: String,
    val status: String?,
    /** 往前补过多少页（见 `WorkspaceUiState.transcriptPrependRevision`）。 */
    val prepend: Int,
)

/** 对话流，对应原版 renderChat() + addTranscriptWindow()。 */
private fun requestScrollToBottom(listState: androidx.compose.foundation.lazy.LazyListState) {
    val total = listState.layoutInfo.totalItemsCount
    if (total > 0) listState.requestScrollToItem(total - 1)
}

private fun androidx.compose.foundation.lazy.LazyListState.hasHiddenContent(): Boolean = canScrollForward

@Composable
fun ChatList(
    state: WorkspaceUiState,
    onToggleTool: (String) -> Unit,
    onToggleGroup: (String, String) -> Unit,
    onToggleThinking: (String) -> Unit,
    onMessageActions: (ChatItem) -> Unit,
    /** 某一行的 `⋯` 菜单里选中了一项。 */
    onToolAction: (ChatItem, String, String) -> Unit,
    /** 多选模式下切换某条消息的勾选状态。 */
    onToggleSelect: (String) -> Unit,
    /** 多选模式下**一次性替换**整个选中集合（拖动对选用）。 */
    onRangeSelect: (Set<String>) -> Unit,
    /** 多选模式下取消整次选择。 */
    onCancelSelect: () -> Unit,
    /** 多选模式下导出并分享已勾选的消息。 */
    onExportSelect: () -> Unit,
    /** 全选 / 取消全选。 */
    onToggleSelectAll: () -> Unit,
    /** 长按动作菜单的宿主插槽。 */
    anchoredMenu: @Composable (String, DpOffset?) -> Unit,
    /** 列表滚到顶、而上面还有没读进来的历史时调用一次。 */
    onLoadOlder: () -> Unit = {},
    /** 「回到最新」：常驻窗口的尾部被淘汰过、最新消息不在内存里时调用。 */
    onLoadLatest: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** 列表底部预留的高度。 */
    bottomInset: Dp = 0.dp,
    occupiedBottomPx: Int = 0,
    suppressImeResize: Boolean = false,
    /** Activity 级 IME 从关闭到打开的代数；只在输入框真实聚焦时用于一次吸底。 */
    imeOpenGeneration: Int = 0,
    /** 发送框是否真实取得焦点；侧栏搜索框弹键盘时必须为 false。 */
    composerFocused: Boolean = false,
    /** 顶部预留高度：顶栏改成悬浮层后，内容要能滚到它下面。 */
    topInset: Dp = 0.dp,
    /** **主体调试模式**：在真实消息上就地加调试信息（不另开页面）。 */
    debugMode: Boolean = false,
    /** 与 [debugMode] 配套：对话流末尾内联的任务清单。 */
    debugTasks: List<AgentTask> = emptyList(),
) {
    ZhiFrameTrace.countRecompose("ChatList")
    val listState = rememberLazyListState()
    // 拖动对选的边缘自动滚动要用它起协程（见 LazyColumn 的 pointerInput）。
    val scope = rememberCoroutineScope()
    val currentState by rememberUpdatedState(state)
    // 分页回调也走 rememberUpdatedState：那个长驻的 snapshotFlow effect 不该因为
    // 宿主每帧传进来一个新的 lambda 就被重启（重启会丢掉"上次观察到的三元组"）。
    val onLoadOlderState by rememberUpdatedState(onLoadOlder)
    val onLoadLatestState by rememberUpdatedState(onLoadLatest)
    // 顶部下拉的刷新指示器状态。`remember` 一次即可：它只在内部驱动那圈动画。
    val pullToRefreshState = rememberPullToRefreshState()

    // 长驻 effect 读取 State 的最新值，不能冻结首次组合时的 Boolean。
    val autoFollowState = rememberAutoFollowHandle(listState, forceFollowToken = state.scrollToBottomToken)

    // 底部悬浮导航「跟着对话流滚动一起走」。
    com.zhizhu.zhicode.compose.ui.reportNavBarScroll(listState)

    var lastScrollToken by remember { mutableStateOf(state.scrollToBottomToken) }
    LaunchedEffect(state.scrollToBottomToken) {
        if (state.scrollToBottomToken == lastScrollToken) return@LaunchedEffect
        lastScrollToken = state.scrollToBottomToken
        if (currentState.transcriptHasNewer) onLoadLatestState() else requestScrollToBottom(listState)
    }

    /* 上一次已处理的会话 id。 */
    var lastSessionId by remember { mutableStateOf(state.activeSessionId) }

    /* 上一次看到的"往前补历史"计数。 */
    var lastPrepend by remember { mutableStateOf(state.transcriptPrependRevision) }
    /** 锚定 effect 专用的版本号，与吸底 effect 的 `lastPrepend` **互不干扰**（见那段注释）。 */
    /** 锚定 effect 专用的版本号，与吸底 effect 的 `lastPrepend` **互不干扰**（见那段注释）。 */
    var lastAnchoredPrepend by remember { mutableStateOf(state.transcriptPrependRevision) }
    var prependAnchorId by remember { mutableStateOf<String?>(null) }
    /** key 只作兜底：按 id 找不到时才用它（例如那一块的消息被尾部淘汰了）。 */
    var prependAnchorKey by remember { mutableStateOf<Any?>(null) }
    var prependAnchorOffset by remember { mutableStateOf(0) }

    val blockCache = remember { BlockCache() }

    /** 「该落到底」的待办标记。 */
    var pendingBottom by remember { mutableStateOf(false) }

    /** 刚进入组合的第一轮。 */
    var justRecomposed by remember { mutableStateOf(true) }

    /* 「回到最新」之后显式落底。 */
    var lastJump by remember { mutableStateOf(state.transcriptJumpRevision) }
    LaunchedEffect(state.transcriptJumpRevision) {
        if (state.transcriptJumpRevision == lastJump) return@LaunchedEffect
        lastJump = state.transcriptJumpRevision
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .first { it > 0 }
        withFrameNanos { }
        requestScrollToBottom(listState)
    }

    var loadedAtTop by remember { mutableStateOf(false) }

    val loadOlderWithAnchor: () -> Unit = remember(listState) {
        {
            val anchor = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index > 0 }
            // 头部恒定占一项（`older-loader` / `transcript-start`），所以 LazyColumn
            // 下标减 1 才是块表下标。空会话没有头部项，但那时也不会触发补页。
            val headerCount = if (currentState.transcript.isEmpty()) 0 else 1
            val blockIndex = (anchor?.index ?: -1) - headerCount
            val block = blockCache.previous.getOrNull(blockIndex)
            prependAnchorId = when (block) {
                is TurnLayout.Block.Standalone -> block.id
                is TurnLayout.Block.Turn -> block.ids.firstOrNull()
                null -> null
            }
            prependAnchorKey = anchor?.key
            prependAnchorOffset = anchor?.offset ?: 0
            onLoadOlderState()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .collect { atTop ->
                // 离开顶端就重新上膛：再滚回来时可以再补一页。
                if (!atTop) {
                    loadedAtTop = false
                    return@collect
                }
                if (loadedAtTop || !currentState.transcriptHasOlder || currentState.transcriptLoadingOlder) {
                    return@collect
                }
                loadedAtTop = true
                loadOlderWithAnchor()
            }
    }

    val imeMotion = LocalImeMotion.current
    val imeShrinkPx = {
        if (suppressImeResize) 0 else (imeMotion.imeBottomPx - occupiedBottomPx).coerceAtLeast(0)
    }
    ImeLazyListAutoScroller(
        listState = listState,
        enabled = composerFocused && !suppressImeResize,
        imeShrinkPx = imeShrinkPx,
    )

    // 内容观察器必须常驻：加载会话、前插历史、流式输出都在这里处理。
    LaunchedEffect(listState) {
        snapshotFlow {
            val s = currentState
            val last = s.transcript.lastOrNull()
            ContentStamp(
                size = s.transcript.size,
                body = last?.body?.length ?: -1,
                thinking = last?.thinking?.length ?: -1,
                tools = last?.tools?.size ?: -1,
                session = s.activeSessionId,
                status = s.workingStatus,
                prepend = s.transcriptPrependRevision,
            )
        }.collect { stamp ->
            // LazyColumn 实际画的是 blocks，不能由 transcript.size 推算索引；
            // 多条消息合并为一个回合时，真实尾项只由 totalItemsCount 决定。
            val switched = stamp.session != lastSessionId
            lastSessionId = stamp.session
            val prepended = !switched && stamp.prepend != lastPrepend
            lastPrepend = stamp.prepend
            // 尾部被淘汰过时也不吸底：那时"最后一条"不是最新消息，
            // 吸过去等于把用户钉在历史中间（而且要回到最新得整份替换，见 jump effect）。
            if (currentState.transcriptHasNewer) return@collect

            if (switched) pendingBottom = true
            if (pendingBottom) {
                if (listState.layoutInfo.totalItemsCount > 0) requestScrollToBottom(listState)
                // **不在这里清标记**：`requestScrollToItem` 只登记目标，真正生效在
                return@collect
            }

            if (justRecomposed) {
                justRecomposed = false
                // 恢复后第一轮：只重新判定一次跟随意图，不主动滚动。
                return@collect
            }

            if (autoFollowState.state.value && !prepended) requestScrollToBottom(listState)
        }
    }

    /* 「下面还有没露出来的内容吗」——给悬浮的"回到底部"按钮用。 */
    val atBottom by remember {
        derivedStateOf { !listState.hasHiddenContent() }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.hasHiddenContent() to listState.isScrollInProgress
        }.collect { (hidden, scrolling) ->
            if (!hidden || scrolling) return@collect
            // 用户没打算跟随最新内容（在翻历史）—— 绝不拽他。
            if (!autoFollowState.state.value) return@collect
            // 尾部被淘汰时"最后一条"不是最新消息，滚动只会停在历史中间。
            // 那条路径由 transcriptJumpRevision 的 effect 负责（整份替换后落底）。
            if (currentState.transcriptHasNewer) return@collect
            // 前插那一帧不动：视口由 transcriptPrependRevision 的 effect 显式还原。
            if (currentState.transcriptLoadingOlder) return@collect
            if (listState.layoutInfo.totalItemsCount <= 0) return@collect
            requestScrollToBottom(listState)
        }
    }

    LaunchedEffect(listState, bottomInset) {
        if (!autoFollowState.state.value) return@LaunchedEffect
        repeat(4) {
            withFrameNanos { }
            val total = listState.layoutInfo.totalItemsCount
            if (total <= 0) return@LaunchedEffect
            if (!listState.canScrollForward) return@LaunchedEffect
            listState.scrollToItem(total - 1)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && !listState.canScrollForward
        }
            .distinctUntilChanged()
            .collect { atBottom -> if (atBottom) pendingBottom = false }
    }
    /* `pendingBottom` 的**超时清除**。 */
    LaunchedEffect(listState) {
        snapshotFlow { pendingBottom }
            .distinctUntilChanged()
            .collect { pending ->
                if (!pending) return@collect
                kotlinx.coroutines.delay(PendingBottomTtlMillis)
                // 超时后仍然为真 → 说明这次没能贴底（被打断），丢掉它。
                if (pendingBottom) pendingBottom = false
            }
    }

    val transcript = state.transcript
    val transcriptStamp = TranscriptStamp(
        size = transcript.size,
        firstId = transcript.firstOrNull()?.id,
        lastId = transcript.lastOrNull()?.id,
        lastBodyLength = transcript.lastOrNull()?.body?.length ?: -1,
        lastToolCount = transcript.lastOrNull()?.tools?.size ?: -1,
        // 补页与"回到最新"是两次**原地替换**，条数可能恰好不变 ——
        // 所以这两个版本号必须进指纹。
        prepend = state.transcriptPrependRevision,
        jump = state.transcriptJumpRevision,
        session = state.activeSessionId,
        revision = state.transcriptRevision,
    )
    val structureStamp = StructureStamp(
        size = transcript.size,
        firstId = transcript.firstOrNull()?.id,
        lastId = transcript.lastOrNull()?.id,
        lastToolCount = transcript.lastOrNull()?.tools?.size ?: -1,
        prepend = state.transcriptPrependRevision,
        jump = state.transcriptJumpRevision,
        session = state.activeSessionId,
        revision = state.transcriptRevision,
    )

    val blocks = remember(structureStamp) {
        val entries = transcript.map {
            TurnLayout.Entry(it.id, isUser = it.kind == ChatKind.USER)
        }
        val sessionChanged = state.activeSessionId != blockCache.lastSession
        val prependHappened = state.transcriptPrependRevision != blockCache.lastPrepend
        // 只切一次块：`prependHappened` 时先算一份无 pin 的给 `pinForPrepend` 比对，
        // 否则直接算最终结果。之前**无条件**切两次块，对几千条消息是白算一遍。
        val next = if (prependHappened && !sessionChanged) {
            val unpinned = TurnLayout.blocks(entries)
            TurnLayout.blocks(entries, TurnLayout.pinForPrepend(blockCache.previous, unpinned))
        } else {
            TurnLayout.blocks(entries)
        }
        // 记下这一帧，供下一次前插对齐；顺手把版本号推进到当前。
        blockCache.previous = next
        blockCache.lastPrepend = state.transcriptPrependRevision
        blockCache.lastSession = state.activeSessionId
        next
    }
    LaunchedEffect(state.transcriptPrependRevision) {
        if (state.transcriptPrependRevision == lastAnchoredPrepend) return@LaunchedEffect
        lastAnchoredPrepend = state.transcriptPrependRevision
        val anchorId = prependAnchorId
        val key = prependAnchorKey
        if (anchorId == null && key == null) {
            ZhiMemory.log("prependAnchor NO-KEY rev=${state.transcriptPrependRevision}")
            return@LaunchedEffect
        }
        val desiredOffset = prependAnchorOffset
        // 头部恒定 1 项：`transcriptHasOlder` 为真时是 `older-loader`，
        // 否则（非空会话）是 `transcript-start`。两者都占一个 LazyColumn item。
        val headerCount = if (state.transcript.isEmpty()) 0 else 1
        /* 先按**消息 id** 找那一块，找不到再退回按 key 找。 */
        val blockIndex = anchorId?.let { id ->
            blocks.indexOfFirst { TurnLayout.containsMessage(it, id) }
        }?.takeIf { it >= 0 }
            ?: blocks.indexOfFirst { it.key == key }
        if (blockIndex < 0) {
            // 锚点那一块已经不在了（会话被换掉 / 被尾部淘汰）。没有可复现的目标，
            // 与其猜一个位置，不如什么都不做 —— 交给 `LazyColumn` 自己的锚定。
            ZhiMemory.log("prependAnchor SKIP id=$anchorId key=$key（块已不在块表里）")
            return@LaunchedEffect
        }
        val targetIndex = headerCount + blockIndex
        // 立即登记一次：新内容还没测量完时这一条也会被尊重。
        listState.requestScrollToItem(targetIndex, desiredOffset)
        // 再连续校验若干帧 —— 每次重新测量都可能把位置夹走。
        var stableFrames = 0
        var corrections = 0
        repeat(PrependAnchorMaxFrames) {
            if (listState.firstVisibleItemIndex == targetIndex &&
                listState.firstVisibleItemScrollOffset == desiredOffset
            ) {
                if (++stableFrames >= 2) {
                    ZhiMemory.log(
                        "prependAnchor OK id=$anchorId target=$targetIndex off=$desiredOffset fix=$corrections",
                    )
                    return@LaunchedEffect
                }
            } else {
                stableFrames = 0
                corrections++
                listState.requestScrollToItem(targetIndex, desiredOffset)
            }
            withFrameNanos { }
        }
        ZhiMemory.log(
            "prependAnchor GAVE-UP id=$anchorId target=$targetIndex" +
                " got=${listState.firstVisibleItemIndex} fix=$corrections",
        )
    }
    val transcriptById = remember(transcriptStamp) { transcript.associateBy { it.id } }

    val seenIds = remember(state.activeSessionId) {
        seenIdsFor(state.activeSessionId, state.transcript)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 布局期同步按键盘高度缩小列表视口，保持外层 glass.capture 全屏不抖。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val maxH = (constraints.maxHeight - imeShrinkPx()).coerceAtLeast(0)
                    val placeable = measurable.measure(
                        constraints.copy(
                            minHeight = constraints.minHeight.coerceAtMost(maxH),
                            maxHeight = maxH,
                        ),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(0, 0)
                    }
                },
        ) {
            PullToRefresh(
            isRefreshing = state.transcriptLoadingOlder,
            onRefresh = loadOlderWithAnchor,
            pullToRefreshState = pullToRefreshState,
            // 文案里带具体条数：只说"更早的消息"时用户不知道一次补多少。
            refreshTexts = listOf(
                "下拉加载更早的 ${com.zhizhu.zhicode.compose.data.SessionReader.PAGE_MESSAGES} 条",
                "松开加载",
                "正在加载…",
            ),
            contentPadding = PaddingValues(top = topInset),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 14.dp)
                    .overScrollVertical()
                    .scrollEndHaptic()
                    .pointerInput(state.multiSelectMode, blocks, state.transcript.isEmpty(), state.transcriptHasOlder) {
                        if (!state.multiSelectMode || blocks.isEmpty()) return@pointerInput
                        // 块 → 成员 id。手势里要用它把一个块下标翻译成"要选哪些消息"。
                        val blockIds = blocks.map { block ->
                            when (block) {
                                is TurnLayout.Block.Standalone -> listOf(block.id)
                                is TurnLayout.Block.Turn -> block.ids
                            }
                        }
                        // blocks 之前有 header item（empty-or-loading / older-loader / transcript-start），
                        // 必须减去 headerCount 才是 blockIds 的真实下标，否则拖选会整体错位 1 块。
                        val headerCount = (if (state.transcript.isEmpty()) 1 else 0) +
                            (if (state.transcriptHasOlder || state.transcript.isNotEmpty()) 1 else 0)
                        fun indexAt(y: Float): Int = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { y >= it.offset && y < it.offset + it.size }
                            ?.let { it.index - headerCount }
                            ?.takeIf { it in blockIds.indices }
                            ?: -1

                        /* 这几个是**普通变量**，不是 State：它们只在手势协程内部读写 */
                        var anchor = -1
                        var base: Set<String> = emptySet()
                        var selecting = false
                        var lastY = 0f
                        var dragging = false

                        coroutineScope {
                        val scroller = launch {
                            val edge = 48.dp.toPx()
                            while (true) {
                                if (dragging && anchor >= 0) {
                                    val height = listState.layoutInfo.viewportEndOffset.toFloat()
                                    val delta = when {
                                        lastY < edge ->
                                            -((edge - lastY) / edge).coerceIn(0f, 1f) * 12.dp.toPx()
                                        lastY > height - edge ->
                                            ((lastY - height + edge) / edge).coerceIn(0f, 1f) * 12.dp.toPx()
                                        else -> 0f
                                    }
                                    if (delta != 0f) {
                                        listState.scrollBy(delta)
                                        val visible = listState.layoutInfo.visibleItemsInfo
                                        val edgeBlock = (if (delta < 0f) visible.firstOrNull()?.index
                                            else visible.lastOrNull()?.index)
                                            ?.minus(headerCount)
                                            ?.coerceIn(0, blockIds.lastIndex)
                                        val end = indexAt(lastY).takeIf { it >= 0 } ?: edgeBlock
                                        if (end != null) {
                                            onRangeSelect(
                                                blockRangeSelection(blockIds, anchor, end, base, selecting),
                                            )
                                        }
                                    }
                                }
                                delay(16)
                            }
                        }
                        try {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    lastY = offset.y
                                    val hit = indexAt(offset.y)
                                    if (hit < 0 || hit >= blockIds.size) {
                                        anchor = -1
                                        dragging = false
                                    } else {
                                        anchor = hit
                                        // 起点未选中 = 这次拖动是"选中"；已选中 = "取消"。
                                        // 与文件管理器一致：从哪儿开始就能反向取消。
                                        base = state.multiSelectedIds
                                        selecting = blockIds[hit].none { it in base }
                                        dragging = true
                                        // 先划上起点那一块，给用户立即反馈。
                                        onRangeSelect(
                                            blockRangeSelection(blockIds, hit, hit, base, selecting),
                                        )
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (anchor >= 0) {
                                        lastY = change.position.y
                                        change.consume()
                                        val end = indexAt(change.position.y)
                                        if (end >= 0) {
                                            onRangeSelect(
                                                blockRangeSelection(blockIds, anchor, end, base, selecting),
                                            )
                                        }
                                    }
                                },
                                onDragEnd = {
                                    anchor = -1
                                    dragging = false
                                },
                                onDragCancel = {
                                    anchor = -1
                                    dragging = false
                                },
                            )
                        } finally {
                            scroller.cancel()
                        }
                        }
                    },
                contentPadding = PaddingValues(top = topInset, bottom = bottomInset),
            ) {
                if (state.transcript.isEmpty()) {
                    // 三态而不是两态：空列表可能是「新会话」也可能是「正在载入」。
                    item(key = "empty-or-loading") {
                        if (state.transcriptLoading) TranscriptLoadingState() else EmptyState()
                    }
                }
                // ---- 顶部：还有更早 / 已经到头 
                if (state.transcriptHasOlder) {
                    item(key = "older-loader", contentType = "older-loader") {
                        OlderLoader(loading = state.transcriptLoadingOlder)
                    }
                } else if (state.transcript.isNotEmpty()) {
                    item(key = "transcript-start", contentType = "transcript-start") {
                        TranscriptStartHint()
                    }
                }
            items(
                items = blocks,
                key = { it.key },
                contentType = { block ->
                    when (block) {
                        is TurnLayout.Block.Standalone -> "message-user"
                        is TurnLayout.Block.Turn -> "message-turn"
                    }
                },
            ) { block ->
                val memberIds = when (block) {
                    is TurnLayout.Block.Standalone -> listOf(block.id)
                    is TurnLayout.Block.Turn -> block.ids
                }
                val members = remember(memberIds, transcriptById) {
                    memberIds.mapNotNull { transcriptById[it] }
                }
                if (members.isEmpty()) return@items
                Box(
                    modifier = Modifier
                        .animateItem(fadeOutSpec = null)
                        .padding(end = 14.dp)
                        // 回合容器：反编译版是 `margins(0, dp(10), 0, dp(14))`。
                        .then(
                            if (block is TurnLayout.Block.Turn) {
                                Modifier.padding(top = 10.dp, bottom = 14.dp)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        members.forEach { item ->
                            key(item.id) {
                                // 手指位置追踪：必须放在 key(item.id) 内，才能绑定到该消息自身的 SlotLifecycle。
                                val finger = rememberFingerTracker()
                                var fingerOffset by remember { mutableStateOf<DpOffset?>(null) }
                                AnimatedMember(
                                    animate = remember(item.id) {
                                        seenIds.add(item.id)
                                    },
                                ) {
                                Box(modifier = Modifier.fillMaxWidth().then(finger.modifier)) {
                                    /* 多选模式下，每个成员左侧多一个勾选框，整行点击切换勾选 */
                                    val selecting = state.multiSelectMode
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                /* 多选模式下**整行可点**（切换勾选）。 */
                                                if (selecting) {
                                                    Modifier.clickable { onToggleSelect(item.id) }
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        if (selecting) {
                                            SelectionCheckbox(
                                                checked = item.id in state.multiSelectedIds,
                                                onToggle = { onToggleSelect(item.id) },
                                            )
                                        }
                                    Column(modifier = Modifier.weight(1f)) {
                                    // ---- 主体调试模式：就地加料（不改下面任何卡片的渲染） ----
                                    if (debugMode) {
                                        MessageDebugStrip(item)
                                    }
                                    // 调试模式把细节**默认全展开**：思考面板、工具输出、处理步骤。
                                    val shown = if (debugMode) item.fullyExpanded() else item
                                    // 多选模式下长按不弹菜单：点按已经是"选/不选"
                                    val longPress = if (selecting) {
                                        { }
                                    } else {
                                        {
                                            fingerOffset = finger.offset()
                                            onMessageActions(item)
                                        }
                                    }
                                    when (shown.kind) {
                                        ChatKind.USER -> UserBubble(shown, longPress)
                                        ChatKind.ASSISTANT -> AssistantCard(
                                            item = shown,
                                            onToggleThinking = { onToggleThinking(item.id) },
                                            onLongPress = longPress,
                                        )
                                        ChatKind.TOOL_GROUP -> ToolBatch(
                                            item = shown,
                                            // 传**原始** id：折叠动作要写到 state 上，用 shown 的 id 会指向同一条
                                            // （id 不变），但语义上更清楚的是"动的是哪一条消息"。
                                            onToggleTool = onToggleTool,
                                            // 组键由界面从 toolId 推导（见 ToolGrouping），所以这里传的是
                                            // **哪一组**，而不是"整批的成员列表要收起"。
                                            onToggleGroup = { groupKey -> onToggleGroup(item.id, groupKey) },
                                            // `⋯` 是**单个工具**的操作，所以传的是那一行的 id，
                                            // 而不是整组（以前传整组，于是点单行弹出整组菜单）。
                                            onToolAction = { toolId, label -> onToolAction(item, toolId, label) },
                                        )
                                        ChatKind.ERROR -> ErrorCard(shown)
                                        ChatKind.INFO -> InfoCard(shown)
                                    }
                                    } // Column（调试条 + 消息卡，竖排）
                                    } // Row（多选勾选框 + Column）
                                    // 菜单挂在**成员自己的 Box** 里，并用手指位置作偏移 ——
                                    // 于是它从手指那一点长出来，且坐标空间与手指追踪同源。
                                    anchoredMenu(item.id, fingerOffset)
                                } // Box（手指追踪 + 菜单锚点）
                                } // AnimatedMember（一次性进入动画）
                            }
                        }
                    } // Column（整个回合：各成员依次竖排）
                }
            }
            // ---- 主体调试模式：把**完整任务清单**内联进对话流 
            if (debugMode && debugTasks.isNotEmpty()) {
                item(key = "debug-tasks", contentType = "debug-task-list") {
                    InlineTaskList(debugTasks)
                }
            }
            // 尾部被淘汰过：末尾给一个明确的"回到最新"，否则用户往下滚只会停在历史中间。
            if (state.transcriptHasNewer) {
                item(key = "jump-to-latest", contentType = "jump-to-latest") {
                    JumpToLatest(onJumpToLatest = onLoadLatestState)
                }
            }
            // 任务进度与工作状态**不放在滚动区**（对应原版把它们挂在固定的
            // chatBottomHost 上），改由 AppScaffold 固定在输入器上方。
            item(key = "chat-bottom-spacer", contentType = "chat-spacer") {
                Box(modifier = Modifier.size(10.dp))
            }
            }
        }
        }
        // Miuix 滚动条：自动淡入淡出、可拖拽，比自绘指示条更贴近 HyperOS

        /* 多选模式的顶部操作条。 */
        AnimatedVisibility(
            visible = state.multiSelectMode,
            enter = fadeIn(ZhiMotion.fadeInSpec) + slideInVertically(ZhiMotion.enterSpec) { -it },
            exit = fadeOut(ZhiMotion.fadeOutSpec) + slideOutVertically(ZhiMotion.exitSpec) { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topInset + 6.dp, start = 14.dp, end = 14.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            SelectionBar(
                count = state.multiSelectedIds.size,
                total = state.transcript.size,
                onToggleAll = onToggleSelectAll,
                onCancel = onCancelSelect,
                onExport = onExportSelect,
            )
        }

        AnimatedVisibility(
            visible = !atBottom && state.transcript.isNotEmpty(),
            enter = fadeIn(ZhiMotion.fadeInSpec) + scaleIn(ZhiMotion.scaleEnterSpec, initialScale = 0.85f),
            exit = fadeOut(ZhiMotion.fadeOutSpec) + scaleOut(ZhiMotion.scaleExitSpec, targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset { IntOffset(0, -imeShrinkPx()) }
                .padding(end = 18.dp, bottom = bottomInset + 8.dp),
        ) {
            ScrollToBottomPill(
                onClick = {
                    if (currentState.transcriptHasNewer) {
                        onLoadLatestState()
                    } else {
                        autoFollowState.rearm()
                        requestScrollToBottom(listState)
                    }
                },
            )
        }
    }
}

/** 悬浮的「回到底部」胶囊。 */
@Composable
private fun ScrollToBottomPill(onClick: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Surface(
        onClick = onClick,
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.primary,
        shape = com.zhizhu.zhicode.compose.ui.effects.ZhiSquircle(LocalZhiPageSet.current.innerCorner),
    ) {
        Text(
            text = "回到底部",
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun MessageDebugStrip(item: ChatItem) {
    val scheme = MiuixTheme.colorScheme
    var showSource by remember(item.id) { mutableStateOf(false) }
    val running = item.tools.count { !it.completed && !it.awaitingPermission }
    val failed = item.tools.count { it.failed }
    val awaiting = item.tools.count { it.awaitingPermission }

    Column(modifier = Modifier.fillMaxWidth().padding(start = 2.dp, bottom = 1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.kind.name,
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                fontWeight = FontWeight.Bold,
                color = when (item.kind) {
                    ChatKind.ERROR -> ZhiColors.red()
                    ChatKind.INFO -> ZhiColors.amber()
                    else -> scheme.primary
                },
            )
            Text(
                text = buildString {
                    append("  ")
                    append(item.body.length).append('/').append(item.thinking.length)
                    if (item.tools.isNotEmpty()) {
                        append(" t").append(item.tools.size)
                        if (running > 0) append(" ▶").append(running)
                        if (failed > 0) append(" ✗").append(failed)
                        if (awaiting > 0) append(" ⧗").append(awaiting)
                    }
                    if (item.streaming) append(" …")
                    if (item.contextTokens >= 0) append(" ctx").append(item.contextTokens)
                    append("  ").append(item.id)
                },
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                fontFamily = FontFamily.Monospace,
                color = scheme.onSurfaceVariantSummary,
                // 元信息是**单行且不换行**的：它不该把消息卡往下推，
                // 超长时截断即可（要看全的在调试浮层的「对话条目」里）。
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 源码开关走 ZhiSmallPill（24dp 胶囊），不是 Miuix TextButton
            ZhiSmallPill(
                label = if (showSource) "收起源码" else "源码",
                highlighted = showSource,
                onClick = { showSource = !showSource },
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (showSource) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                cornerRadius = LocalZhiPageSet.current.innerCorner,
                insideMargin = PaddingValues(8.dp),
                colors = CardDefaults.defaultColors(
                    color = ZhiColors.cardInnerSurface(),
                    contentColor = scheme.onSurfaceVariantSummary,
                ),
            ) {
                Text(
                    text = item.body,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/** 调试模式下的"全展开"视图。 */
private fun ChatItem.fullyExpanded(): ChatItem = when (kind) {
    ChatKind.ASSISTANT -> copy(thinkingExpanded = true, streaming = streaming)
    ChatKind.TOOL_GROUP -> copy(
        groupCompleted = true,
        // 折叠组默认收起，调试模式要把**每一组**都摊开 —— 展开态记在
        expandedGroups = ToolGrouping
            .group(tools.map { ToolGrouping.Entry(it.id, it.toolName) })
            .filterIsInstance<ToolGrouping.Segment.Group>()
            .map { it.key }
            .toSet(),
        // 只有"已完成且有输出"的工具才有可展开的内容 —— 与卡片自身的判断保持一致，
        // 否则运行中/等待授权的行会被强行展开，露出一片空白。
        tools = tools.map { tool ->
            if (tool.completed && tool.output.isNotBlank()) tool.copy(expanded = true) else tool
        },
    )
    else -> this
}

/** 内联的**完整**任务清单（调试模式专用）。 */
@Composable
private fun InlineTaskList(tasks: List<AgentTask>) {
    val scheme = MiuixTheme.colorScheme
    val done = tasks.count { it.state == TaskState.DONE }
    Column(
        modifier = Modifier.fillMaxWidth().padding(end = 14.dp, top = 6.dp, bottom = 4.dp),
    ) {
        Text(
            text = "TASK_LIST  $done/${tasks.size}  （调试模式 · 完整清单）",
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            cornerRadius = LocalZhiPageSet.current.cardCorner,
            insideMargin = PaddingValues(10.dp),
            colors = CardDefaults.defaultColors(
                color = LocalZhiPageSet.current.cardSurface(),
                contentColor = scheme.onSurface,
            ),
        ) {
            Column {
                tasks.forEachIndexed { index, task ->
                    if (index > 0) {
                        ZhiHorizontalDivider(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = when (task.state) {
                                TaskState.DONE -> ZhiIcons.done
                                TaskState.RUNNING -> ZhiIcons.pending
                                TaskState.PENDING -> ZhiIcons.awaiting
                            },
                            contentDescription = task.state.name,
                            tint = when (task.state) {
                                TaskState.DONE -> ZhiColors.green()
                                TaskState.RUNNING -> scheme.primary
                                TaskState.PENDING -> scheme.onSurfaceVariantSummary
                            },
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = task.title,
                            fontSize = MiuixTheme.textStyles.body1.fontSize,
                            fontWeight = if (task.state == TaskState.RUNNING) FontWeight.Medium else FontWeight.Normal,
                            modifier = Modifier.padding(start = 7.dp).weight(1f),
                        )
                        Text(
                            text = task.state.name,
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            fontFamily = FontFamily.Monospace,
                            color = scheme.onSurfaceVariantSummary,
                        )
                    }
                    // 详情是 Markdown（真实任务清单窗口里也是这么渲染的），
                    // 所以这里用同一个渲染器，长内容/表格/代码块都能照原样看到。
                    if (task.detail.isNotBlank()) {
                        ZhiMarkdown(
                            source = task.detail,
                            bodyFontSize = MiuixTheme.textStyles.body2.fontSize,
                            modifier = Modifier.padding(start = 20.dp, top = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

private var seenMessageIds = HashSet<String>()

/** 上一次播种 [seenMessageIds] 的会话 id；`null` = 还没有播种过。 */
private var seenSeededSession: String? = null

private fun seenIdsFor(
    sessionId: String?,
    transcript: List<ChatItem>,
): MutableSet<String> {
    if (seenSeededSession != sessionId) {
        seenSeededSession = sessionId
        seenMessageIds = HashSet()
        seenMessageIds.addAll(transcript.map { it.id })
        // 这里曾有一行 `ZhiFrameTrace.note("seen/seed …")` 探针。
    }
    return seenMessageIds
}

/** 新成员进入时上移的距离。 */
private val MemberEnterRise = 8.dp

@Composable
private fun AnimatedMember(
    animate: Boolean,
    content: @Composable () -> Unit,
) {
    if (!animate) {
        content()
        return
    }
    val progress = remember { Animatable(0f) }
    // 动画结束才翻的一次性标志：避免为了判断"要不要摘 layer"而在组合期读进度。
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            animationSpec = tween(ZhiMotion.FADE_IN_MILLIS, easing = SinOutEasing),
        )
        done = true
    }
    // 位移量在组合期换算（density 几乎不会变）；进度只在绘制期的 lambda 里读。
    val rise = with(LocalDensity.current) { MemberEnterRise.toPx() }
    Box(
        modifier = if (done) {
            Modifier
        } else {
            Modifier.graphicsLayer {
                // ⚠️ 这一行是整个组件性能的关键：绘制期读，动画期间不触发重组。
                val v = progress.value
                alpha = v
                translationY = (1f - v) * rise
            }
        },
    ) {
        content()
    }
}

private data class StructureStamp(
    val size: Int,
    val firstId: String?,
    val lastId: String?,
    val lastToolCount: Int,
    val prepend: Int,
    val jump: Int,
    val session: String,
    val revision: Int,
)

private data class TranscriptStamp(
    val size: Int,
    val firstId: String?,
    val lastId: String?,
    val lastBodyLength: Int,
    val lastToolCount: Int,
    val prepend: Int,
    val jump: Int,
    val session: String,
    val revision: Int,
)

/** 多选模式下每条消息左侧的勾选框。 */
@Composable
private fun SelectionCheckbox(checked: Boolean, onToggle: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .padding(start = 2.dp, top = 14.dp, end = 4.dp)
            .size(22.dp)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        // 用 Miuix Checkbox，与输入器底排、选择器里的行首控件同一套视觉。
        // 26dp 是库里的固定尺寸，这里缩到 0.84 让它不把消息卡挤得太窄。
        top.yukonga.miuix.kmp.basic.Checkbox(
            state = if (checked) androidx.compose.ui.state.ToggleableState.On
            else androidx.compose.ui.state.ToggleableState.Off,
            onClick = onToggle,
            modifier = Modifier.graphicsLayer {
                val s = 0.84f
                scaleX = s
                scaleY = s
            },
        )
    }
}

/** 多选模式的顶部操作条。 */
@Composable
private fun SelectionBar(
    count: Int,
    total: Int,
    onToggleAll: () -> Unit,
    onCancel: () -> Unit,
    onExport: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        shape = com.zhizhu.zhicode.compose.ui.effects.ZhiSquircle(LocalZhiPageSet.current.cardCorner),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (count == 0) "选择要导出的消息" else "已选 $count / $total 条",
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            // 全选 / 取消全选：同一个入口来回切。
            Text(
                text = if (total > 0 && count >= total) "取消全选" else "全选",
                color = scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                modifier = Modifier
                    .clickable(enabled = total > 0, onClick = onToggleAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                text = "取消",
                color = scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                modifier = Modifier
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                text = "导出",
                // 没勾选时用禁用色而不是隐藏：用户要知道"这一步可以做，只是还没满足条件"。
                color = if (count == 0) scheme.onSurfaceVariantSummary else scheme.primary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable(enabled = count > 0, onClick = onExport)
                    .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

/** 前插锚定最多校验多少帧。 */
private const val PrependAnchorMaxFrames = 60

/** 切块表的草稿缓存（见 `blocks` 那段注释）。 */
private class BlockCache {
    var previous: List<TurnLayout.Block> = emptyList()
    var lastPrepend: Int = Int.MIN_VALUE
    var lastSession: String = ""
}

/** `pendingBottom` 的最长存活时间。 */
private const val PendingBottomTtlMillis = 1200L
