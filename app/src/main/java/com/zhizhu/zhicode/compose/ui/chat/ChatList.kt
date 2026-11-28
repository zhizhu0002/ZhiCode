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
    //
    // 这是**主驱动**：滚多少、胶囊就退多少；滚动停住、它就停在原处。
    // `reportNavBarScroll` 把滚动像素增量累加进进度，所以动画与滚动是同一件事
    // （不是"滚动触发一次 tween"）。三屏都用同一份实现。
    //
    // 手势那条（`WorkspacePage.navBarHideOnVerticalDrag`）保留，只用来补
    // "这一屏根本不能滚"的情况，两者写同一个进度值、方向一致，不会打架。
    com.zhizhu.zhicode.compose.ui.reportNavBarScroll(listState)

    var lastScrollToken by remember { mutableStateOf(state.scrollToBottomToken) }
    LaunchedEffect(state.scrollToBottomToken) {
        if (state.scrollToBottomToken == lastScrollToken) return@LaunchedEffect
        lastScrollToken = state.scrollToBottomToken
        // ⚠️ 尾部被淘汰过时**不能**滚到底：那时列表最后一条不是最新消息，
        // 滚过去只会停在历史中间，看起来像"发了消息却没反应"。
        // 这种情况要走「回到最新」（替换整份 transcript），落底由下面那条
        // transcriptJumpRevision 的 effect 负责 —— 它才拿得到替换后的新列表。
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
            // 只是往前补了一页历史就不吸底（见 [ContentStamp.prepend]）。
            // ⚠️ 判定要在写入新值**之前**做，而且要排除切会话那一次 ——
            // 新会话的第一页同样带着一次计数变化。
            val prepended = !switched && stamp.prepend != lastPrepend
            lastPrepend = stamp.prepend
            // 尾部被淘汰过时也不吸底：那时"最后一条"不是最新消息，
            // 吸过去等于把用户钉在历史中间（而且要回到最新得整份替换，见 jump effect）。
            if (currentState.transcriptHasNewer) return@collect

            // ---- 切会话：把「落到底」记成**待办**，不是一次性动作 ----
            //
            // ⚠️ 这里原来是 `if (totalItemsCount <= 0) return@collect` 之后
            // `if (switched) requestScrollToBottom()`。两处合起来是一个 bug：
            //
            //   切会话那一帧 `transcript` 还是**空的**（消息要异步从库里读），
            //   于是 `totalItemsCount == 0` → 直接 return，**永不重试**；
            //   等消息真的填进来时 `switched` 已翻假，`autoFollow` 又因为列表
            //   停在顶部而判成"用户在看历史"，仍然不吸底。
            //   结果就是「加载会话，对话流在顶上而不是底下」。
            //
            // 改成挂待办标记：还没真正落到底之前，每次内容变化都再试一次。
            if (switched) pendingBottom = true
            if (pendingBottom) {
                if (listState.layoutInfo.totalItemsCount > 0) requestScrollToBottom(listState)
                // **不在这里清标记**：`requestScrollToItem` 只登记目标，真正生效在
                // 下一帧测量里。清早了，若那一帧条数又变（消息还在灌），会停在中间。
                // 由下面那条"已贴底"的 effect 负责清。
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

    // 输入法弹出/收起时把对话吸回底部：键盘顶起视口后列表可视高度骤变，
    // 原先贴底的内容被顶出屏幕；用户此刻的意图几乎总是"接着输入"，所以这里
    // 无条件回底（不改 autoFollow，避免键盘收起时把正在看历史的人拽走）。
    //
    // ⚠️ 这里**不能在组合期读 inset**，必须走 `snapshotFlow`。
    //
    // 原先写的是 `val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)`
    // 再 `LaunchedEffect(imeBottom)`。问题是 `WindowInsets.ime` 在**键盘动画的整个
    // 过程中每帧都在变**，而组合期读 = 每帧都让这一层（连同整个列表）重组一次。
    //
    // 三个触发点全都落在这条路径上：输入器随键盘升降、悬浮任务卡与它同列跟着动、
    // 以及**发送消息时键盘收起**（所以"发个你好、回复也会闪"）。用户报的
    // 「对话流狂闪」正是这条路径：每帧重组 → 每帧重新布局列表 →
    // 相邻两帧的内容在"重排前 / 重排后"之间来回，看起来就是整片在闪。
    //
    // 放进 `snapshotFlow` 之后，读取发生在协程里、**不进组合**，所以 inset 每帧变
    // 一次也不重组；而"要先读一次当前值"这件事由 flow 的首次发射承担，
    // 与原先那次组合期读**语义等价**。
    //
    // `distinctUntilChanged` 不是省事：键盘动画里 inset 会连着好几帧同值，
    // 去掉重复可以少发几次滚动请求（滚动是幂等的，但没必要发）。
    // 对话流切成「块」（一轮助手回合一块）。放在 `LazyColumn` **外面**算：
    // 它的 content lambda 是 `LazyListScope.() -> Unit`，不是 @Composable 上下文，
    // 在里面调 `remember` 编译不过。
    // ---- 便宜的结构指纹：代替「拿整份列表当 remember 的 key」----
    //
    // 原来这里是 `remember(state.transcript) { … }` × 2。key 是 `List<ChatItem>`，
    // 判等要逐个元素做 `data class` 深比较（含 `body: String` 与
    // `tools: List<ToolActivity>`）—— 而流式输出每 32ms 就换一次 list，
    // 于是**每帧**都要在 O(n·m) 个字符上比一遍。n 是消息数、m 是单条正文长度，
    // 长会话里这一项足以把主线程吃满。
    //
    // 现在改成只看「结构有没有变」的廉价指纹：条数、首尾 id、最后一条正文长度、
    // 最后一条的工具数。流式期间只有最后一项会变，比较是 O(1)。
    //
    // ⚠️ 指纹漏判的后果是**界面不更新**，所以它必须覆盖所有会改变渲染结果的变化。
    // 这里刻意保留了 `lastBodyLength` 与 `toolsCount`：追加 token 与追加工具行
    // 都会让它们变。而"中间某条被原地替换"只可能来自补页/替换整份 transcript，
    // 那时 `size` 或 `firstId` 一定会变。
    // ⚠️ 指纹本身**不能**包在 `remember(state.transcript)` 里 —— 那样比较的还是整份列表，
    // 等于什么都没省。它每次重组直接算：取首尾元素对 ArrayList 是 O(1)，
    // 整体是常数时间，远便宜于一次深比较。
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
    // TurnLayout only stores ids. Resolve them through one index instead of scanning the
    // complete transcript once for every member of every block (which becomes quadratic
    // for long sessions).
    //
    // ⚠️ key 必须是**含正文长度**的 [TranscriptStamp]，不能换成结构指纹。
    // 这个映射存的是 `ChatItem` **实例**：流式追加 token 时列表里那一条是**新对象**，
    // 结构没变 → 结构指纹不变 → 映射不重建 → 各 item 拿到的仍是旧实例，
    // 表现就是「回复停在半句不动」。它只是 O(n) 一次 `associateBy`，
    // 不像切块那样要分配 Entry 列表并按用户消息断开回合，代价低得多。
    val transcriptById = remember(transcriptStamp) { transcript.associateBy { it.id } }

    // 「已经见过」的消息 id —— 用来判断某个成员是**本次新出现的**还是历史。
    //
    // ## 为什么需要它
    //
    // 一轮助手回合是**一个** `LazyColumn` item（见 TurnLayout），所以 item 级的
    // `animateItem` 只在整块位置变化时触发；**回合内部**新追加的工具卡/正文
    // 不产生新 item，于是没有任何进入动画 —— 观感就是文字"蹦"出来的。
    // 用户要的「新消息整条淡入/上移」缺的正是这一层。
    //
    // ## 为什么不能无条件播
    //
    // `LazyColumn` 会**回收组合**：往上滚到历史消息时，那一条是**重新进入组合**的。
    // 无条件播进入动画，历史消息就会在滚动中不停闪 —— 那比"没有动画"糟得多。
    // 所以判据必须是"这个 id 以前没见过"，而不是"它刚进入组合"。
    //
    // 集合本身放在**文件级**（见 `seenMessageIds`），**不能**放在这里的 `remember` 里 ——
    // 这一点是本轮踩过的坑，写下来免得下一个人又挪回去：
    //
    //   原来写的是 `remember(activeSessionId) { HashSet().apply { addAll(transcript) } }`。
    //   问题在于这个 `remember` 的寿命**只到本层组合被丢弃为止**。一旦 ChatList 这层
    //   组合被重建（工作区宿主重进组合、面板被重新挂载…），这个块会**再跑一次**，
    //   而 `addAll(当前的 transcript)` 会把**刚刚新到的那条消息也登记成"已见过"** ——
    //   于是它的 `add` 返回 false，`animate` 为假，**动画静默消失**：
    //   代码看着没错、编译通过、也不报错，只是不播。
    //
    // 所以：集合放文件级，并且**按会话只播种一次**（`seenSeededSession` 判重）。
    // 重建组合时 `seenIdsFor` 只会把已有集合还回来，不再重新登记。
    //
    // ⚠️ `add` 的返回值就是"以前没见过"，所以下面用 `remember(item.id) { seenIds.add(id) }`
    // 一次搞定"判断 + 登记"。它在组合期有副作用，但幂等、只做一次集合插入 ——
    // 换成 `LaunchedEffect` 就晚了：那一帧的 `isNew` 必须**同步**拿到。
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
            // ⚠️ `PullToRefresh` 的 `contentPadding` **不是**给内容留白用的
            // （它会传给里面的 LazyColumn 吗？不会 —— 核过 0.9.4 的源码：它只做
            // `RefreshHeader(modifier = Modifier.offset(y = contentPadding.calculateTopPadding()))`,
            // 也就是**只把刷新指示器整体下移**，内容的高度与位置都不受影响）。
            //
            // 正是靠这个，指示器才不会画在 y=0 被悬浮顶栏整个盖住（用户报的
            // 「PullToRefresh 怎么被顶栏覆盖了」）。列表自己的顶部留白仍然由
            // LazyColumn 的 `contentPadding.top` 负责 —— 那是"第一条消息从顶栏下缘起排，
            // 滚动时从顶栏底下穿过"的实现方式，两处不是同一件事，别合并。
            contentPadding = PaddingValues(top = topInset),
        ) {
            LazyColumn(
                state = listState,
                // 两个 Miuix 的滚动行为，与其余九个列表（设置 hub / 各二级页 /
                // 文件页 / 主题页 …）保持一致：
                //
                // · `overScrollVertical()`  跨边界时的阻尼 —— 没有它，滚到顶/底是
                //   **硬停**，手指继续拖也不动，像撞在墙上；
                // · `scrollEndHaptic()`     到顶/底时一次轻触感。
                //
                // ⚠️ 顶部与 `PullToRefresh` 有潜在手势竞争：下拉补页由它接管，
                // 而 `overScrollVertical` 也会在 top 越界时介入。实测若出现
                // 「下拉加载更早」变得不灵敏或指示器抖动，就把 `overScrollVertical()`
                // 去掉、只留 `scrollEndHaptic()` —— 触感不参与手势判定，零冲突。
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
                    // 混在一起的话，点开一条长会话会先看到"想让 ZhiCode 做什么？"
                    // 再过一会儿突然被内容填满 —— 用户以为是空会话，差点重新输入。
                    item(key = "empty-or-loading") {
                        if (state.transcriptLoading) TranscriptLoadingState() else EmptyState()
                    }
                }
                // ---- 顶部：还有更早 / 已经到头 ----
                //
                // 两种状态都要有一条占位：
                //  · 还有更早 → `OlderLoader`（提示 + "正在取"）；
                //  · 已经到头 → `TranscriptStartHint`。
                // 少了后者时，用户滚到顶会看到这一条**消失** —— 而那与"这一页刚好到头"
                // 长得一模一样，于是会反复下拉确认。
                if (state.transcriptHasOlder) {
                    item(key = "older-loader", contentType = "older-loader") {
                        OlderLoader(loading = state.transcriptLoadingOlder)
                    }
                } else if (state.transcript.isNotEmpty()) {
                    item(key = "transcript-start", contentType = "transcript-start") {
                        TranscriptStartHint()
                    }
                }
            // ---- 按「块」渲染 -------------------------------------------------
            //
            // 一块 = 一个 `LazyColumn` item：
            //  · `USER` 自己一块（容器外）；
            //  · 其余全部并进**同一个助手回合容器**（反编译版 `beginConversation()`）——
            //    容器是**透明**的、padding 为 0，只用上下外边距把这一轮与相邻内容拉开，
            //    所以它不画框、不抢注意力，只是把"这一轮的东西"归到一起。
            //
            // 切块规则在 `TurnLayout`（纯逻辑、有单测）：判错不会编译失败，
            // 只会让间距/包裹关系不对。
            //
            // ⚠️ 每个成员仍在**容器内部**各自成一层组合（`key(item.id)`），
            // 手指追踪与长按菜单也挂在成员自己那一层 —— 于是菜单锚点与以前**完全一致**，
            // 不需要任何跨层坐标换算（那正是锚偏的常见来源）。
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
                // 新消息淡入 + 已有消息位置变化时平滑推移。
                //
                // ⚠️ `fadeOutSpec = null` 是刻意的，别加回来。
                // 默认的淡出会让"被移除的项"在动画期间**继续绘制**，于是当
                // `newSession()` 把 transcript 一次清空时，那几张还没淡完的卡片
                // 会和紧接着出现的 `EmptyState()` 叠在一起 —— 表现为卡片与
                // "想让 ZhiCode 做什么？"互相穿透、错位，看起来像渲染 bug。
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
                    // ⚠️ 调试条与消息卡必须放进**同一个 Column** 里，不能并列在 Box 下。
                    // 这个 Box 是给 anchoredMenu 定位的（菜单要盖在卡片上），而 Box 的子项
                    // 是**叠放**而不是竖排 —— 直接并列会让调试条被卡片盖住。
                    Column(modifier = Modifier.fillMaxWidth()) {
                        members.forEach { item ->
                            key(item.id) {
                                // 手指位置追踪：必须放在 key(item.id) 内，才能绑定到该消息自身的 SlotLifecycle。
                                val finger = rememberFingerTracker()
                                var fingerOffset by remember { mutableStateOf<DpOffset?>(null) }
                                // ⚠️ 每一位成员外面这一层 Box 是**必须**的，不是多余的嵌套：
                                //
                                // 1. 它承载手指追踪（`finger.modifier`），记录到的坐标因此是
                                //    「相对这一条」的；
                                // 2. `anchoredMenu` 用 `Modifier.absoluteOffset` 定位，而
                                //    `absoluteOffset` 的坐标是相对**直接父节点**的 ——
                                //    若把它挂到回合 Column 下面（成为整个回合并列的子项），
                                //    第 2 条之后的菜单会整体偏掉前面所有成员的高度。
                                //
                                // 所以「手指追踪 + 菜单锚点」必须始终贴在**自己那一条**上。
                                // 本次新出现的成员播一次「淡入 + 轻微上移」；历史成员原样直出。
                                AnimatedMember(
                                    animate = remember(item.id) {
                                        // 新出现的成员播一次「淡入 + 轻微上移」；历史成员原样直出。
                                        //
                                        // 这里曾经有一句 `ZhiFrameTrace.note("seen/item …")` 探针，
                                        // 每次组合都执行一次（实测一条对话里 268 次）。它当初是为了
                                        // 确认"新消息判据没退回按组合生命周期算"，那个结论早就验证过，
                                        // 而它带来的开销是实打实的：每次都拼字符串 + `Log.i`（debug 下
                                        // 真走 logcat IPC）+ 往写盘线程提交一个任务。
                                        //
                                        // 实测数据：`downToClick` 中位数 70ms、峰值 1116ms，而
                                        // `fromClick` 只有 7~14ms、帧均 9~12ms —— 说明渲染与组合都不慢，
                                        // 慢的是"手指按下到系统判定为点击"这一段主线程排队。这类常驻
                                        // 探针正是排队来源之一，所以删掉。
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
                                    // ⚠️ 调试条与消息卡必须放进**同一个 Column** 里，不能并列在
                                    // 上面那个 Box 下：Box 的子项是**叠放**而不是竖排，
                                    // 直接并列会让调试条被卡片盖住。
                                    Column(modifier = Modifier.weight(1f)) {
                                    // ---- 主体调试模式：就地加料（不改下面任何卡片的渲染） ----
                                    if (debugMode) {
                                        MessageDebugStrip(item)
                                    }
                                    // 调试模式把细节**默认全展开**：思考面板、工具输出、处理步骤。
                                    //
                                    // 做法是"只改这一次渲染的输入"（item.copy(...)），不动 state ——
                                    // 于是折叠按钮仍然能点（点了会写到 state，而调试模式的强制展开会把它
                                    // 覆盖回展开 —— 这是刻意的：调试模式就是"全都摊开"的视图，
                                    // 想看折叠后的样子就把它关掉）。
                                    val shown = if (debugMode) item.fullyExpanded() else item
                                    // 多选模式下长按不弹菜单：点按已经是"选/不选"，
                                    // 再弹出动作菜单会让人以为选错了目标。
                                    // 长按这里**什么都不做**（而不是再切换一次）：
                                    // 长按通常伴随手指微动，重复切换会让勾选"跳来跳去"。
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
            // ---- 主体调试模式：把**完整任务清单**内联进对话流 ----
            //
            // 悬浮任务卡只显示前 maxTasks 条（多了会盖住对话），完整清单原本只在
            // 「任务清单」窗口里。调试模式把它摊在对话流末尾，于是"任务与消息的对应
            // 关系"一眼可见，也不必再开一个窗口。
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
        shape = com.zhizhu.zhicode.compose.ui.effects.ZhiSquircle(ZhiRadius.inner),
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
            // 源码开关走 ZhiSmallPill（24dp 胶囊），不是 Miuix TextButton：
            // 后者最小高 40dp、字号走主题 button 档，放进这一行会把整条调试带撑得
            // 比消息卡还显眼，而调试带本该是"贴着卡片的一条注记"。
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
                cornerRadius = ZhiRadius.inner,
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
        // `expandedGroups` 上，所以这里按键集合给全（而不是像以前那样
        // 把每个成员的 `expanded` 置真：那个标志位管的是"这条工具的输出展开"，
        // 两件事不共用）。
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
            cornerRadius = ZhiRadius.card,
            insideMargin = PaddingValues(10.dp),
            colors = CardDefaults.defaultColors(
                color = ZhiColors.cardSurface(),
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
        // 这里曾有一行 `ZhiFrameTrace.note("seen/seed …")` 探针。结论早已确认
        // （正常一次会话只打一行），而它在**切会话时**会做一次字符串拼接 + 写盘任务，
        // 属于纯开销，已删除。
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
        shape = com.zhizhu.zhicode.compose.ui.effects.ZhiSquircle(ZhiRadius.card),
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
            // 放在「取消」左边，避免抢「导出」的视觉权重 ——
            // 它是个辅助动作，不是这一步的目的。
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
