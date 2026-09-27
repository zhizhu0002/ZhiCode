package com.zhizhu.zhicode.compose.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.ChatItem
import com.zhizhu.zhicode.compose.model.ChatKind
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.ui.rememberFingerTracker
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter

/**
 * 对话内容的指纹。
 *
 * 用 `snapshotFlow` 观察它时，只要它没变就不会重复触发吸底 ——
 * 所以它必须覆盖所有会让**末尾那一项变高**的信号：条数、正文长度、
 * 思考长度、工具条数，以及切会话与工作状态。
 *
 * 做成 `data class` 是为了让 `snapshotFlow` 能用 `equals` 判重，
 * 否则每次发射都会当成新值。
 */
private data class ContentStamp(
    val size: Int,
    val body: Int,
    val thinking: Int,
    val tools: Int,
    val session: String,
    val status: String?,
)

/** 对话流，对应原版 renderChat() + addTranscriptWindow()。 */
@Composable
fun ChatList(
    state: WorkspaceUiState,
    onToggleTool: (String) -> Unit,
    onToggleGroup: (String, Boolean) -> Unit,
    onToggleThinking: (String) -> Unit,
    onMessageActions: (ChatItem) -> Unit,
    /**
     * 长按动作菜单的宿主插槽。在**每一项自己的布局里**调用，菜单就会贴那一项弹出
     * （见 `ZhiAnchoredActionMenu`）。
     *
     * 第二个参数是**手指位置**（相对该项）：非空时菜单从那一点长出来，
     * 而不是固定贴条目的边。
     */
    anchoredMenu: @Composable (String, DpOffset?) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 列表底部预留的高度。悬浮的任务/状态卡会盖住列表下部，
     * 用它把内容顶到卡片上方，保证被遮住的对话仍能滑出来看到。
     */
    bottomInset: Dp = 0.dp,
    /** 顶部预留高度：顶栏改成悬浮层后，内容要能滚到它下面。 */
    topInset: Dp = 0.dp,
) {
    val listState = rememberLazyListState()
    val currentState by rememberUpdatedState(state)

    /*
     * 是否跟随最新内容（吸底）。
     *
     * ⚠️ 不能用「可见的末项是不是最后一项」来判断。内容增长本身就会把末项
     * 顶出屏幕，于是在我们来得及滚动之前条件就已经变成 false —— 表现是
     * 回复长过一屏之后跟随就断了，得手动往下滑。
     *
     * 改成只看**用户的动作**：他松手时停在底部就继续跟随，停在中途就暂停
     * （他在看历史）。
     *
     * `isScrollInProgress` 在这里只反映用户拖动 / 惯性滚动：我们用的是
     * `requestScrollToItem`，它不走挂起滚动、也不占滚动互斥锁，不会把它置真。
     *
     * 两个分支都要处理：
     * - **一开始滚动就立刻暂停** —— 否则用户按住往上拖的时候流式内容还会把他
     *   拽回底部（`autoFollow` 还是 true），手感是"手指和自动滚动在抢"。
     * - **停手时按位置决定** —— 停在底部就恢复跟随，停在中途就不跟。
     */
    var autoFollow by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            autoFollow = if (scrolling) false else !listState.canScrollForward
        }
    }

    /*
     * 上一次已处理的会话 id。
     *
     * 切会话 / 新会话时要**无条件**回到底部（新打开的会话应该从底部看起），
     * 而流式过程中只在用户本来就贴底时才跟随。
     * 两者用同一个标记区分：`activeSessionId` 变了就是“换会话了”，无条件滚。
     */
    var lastSessionId by remember { mutableStateOf(state.activeSessionId) }

    /*
     * 自动吸底。
     *
     * 原实现有两个叠在一起的问题，合起来就是“不及时”：
     *
     * 1. 用 `LaunchedEffect(内容长度…)` + `scrollToItem`。
     *    `scrollToItem` 是**挂起**函数，而流式输出下内容长度几乎每帧都在变 ——
     *    LaunchedEffect 于是不停地取消并重启协程，滚动经常在真正生效之前就被
     *    取消掉，只能等某个空档才追上，看起来就是“滚一下停一下”。
     *    改用 `requestScrollToItem`：**非挂起**，只登记一个目标下标，
     *    在**下一次测量**里生效，取消不掉。
     *
     * 2. 用“可见末项”算贴底（见 [autoFollow] 的注释）。
     *
     * 另外改成一个**长驻**的 effect + `snapshotFlow` 观察内容指纹，
     * 而不是把内容长度当 `LaunchedEffect` 的 key —— 后者每个 token 都要
     * 重建一次协程，这些开销完全没有必要。
     *
     * 日志输出时不用 `animateScrollToItem`：内容连续增长时每一步都起动画
     * 会互相打断，反而更抖。
     */
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
            )
        }.collect { stamp ->
            // 空态时它会占一项（索引 0）；末尾还有一项固定高度的占位 Box
            val leading = if (stamp.size == 0) 1 else 0
            val tailIndex = leading + stamp.size
            val switched = stamp.session != lastSessionId
            lastSessionId = stamp.session
            if (tailIndex <= 0) return@collect
            if (switched || autoFollow) listState.requestScrollToItem(tailIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(start = 14.dp),
            contentPadding = PaddingValues(top = topInset, bottom = bottomInset),
        ) {
            if (state.transcript.isEmpty()) {
                item { EmptyState() }
            }
            items(state.transcript, key = { it.id }) { item ->
                // 新消息淡入 + 已有消息位置变化时平滑推移。
                //
                // ⚠️ `fadeOutSpec = null` 是刻意的，别加回来。
                // 默认的淡出会让"被移除的项"在动画期间**继续绘制**，于是当
                // `newSession()` 把 transcript 一次清空时，那几张还没淡完的卡片
                // 会和紧接着出现的 `EmptyState()` 叠在一起 —— 表现为卡片与
                // "想让蜘蛛做什么？"互相穿透、错位，看起来像渲染 bug。
                // 删除单条消息时瞬间消失没有观感损失（用户主动触发，预期即时），
                // 换掉这个交集比留着更划算。
                // 手指位置追踪：挂在这一项的 Box 上，于是记录到的坐标就是
                // 「相对这一项」的，与 anchoredMenu 的锚点是同一个坐标系。
                // 它是**只读观察者**，不会抢走卡片自己的点击/长按与无障碍语义。
                val finger = rememberFingerTracker()
                // 长按触发的那一刻把手指位置定格下来。用 state 而不是直接读
                // finger.offset()：菜单显示期间要一直用它定位，而手指已经抬起了。
                var fingerOffset by remember { mutableStateOf<DpOffset?>(null) }
                Box(
                    modifier = Modifier
                        .animateItem(fadeOutSpec = null)
                        .padding(end = 14.dp)
                        .then(finger.modifier),
                ) {
                    when (item.kind) {
                        ChatKind.USER -> UserBubble(item) {
                            fingerOffset = finger.offset()
                            onMessageActions(item)
                        }
                        ChatKind.ASSISTANT -> AssistantCard(
                            item = item,
                            onToggleThinking = { onToggleThinking(item.id) },
                            onLongPress = {
                                fingerOffset = finger.offset()
                                onMessageActions(item)
                            },
                        )
                        ChatKind.TOOL_GROUP -> ToolGroupCard(
                            item = item,
                            onToggleTool = onToggleTool,
                            onToggleGroup = { expanded -> onToggleGroup(item.id, expanded) },
                            onActions = {
                                fingerOffset = finger.offset()
                                onMessageActions(item)
                            },
                        )
                        ChatKind.ERROR -> ErrorCard(item)
                        ChatKind.INFO -> InfoCard(item)
                    }
                    // 菜单挂在这一项自己的 Box 里，并用手指位置作偏移 ——
                    // 于是它从**手指那一点**长出来，而不是贴条目边界。
                    anchoredMenu(item.id, fingerOffset)
                }
            }
            // 任务进度与工作状态**不放在滚动区**（对应原版把它们挂在固定的
            // chatBottomHost 上），改由 AppScaffold 固定在输入器上方。
            item { Box(modifier = Modifier.size(10.dp)) }
        }
        // Miuix 滚动条：自动淡入淡出、可拖拽，比自绘指示条更贴近 HyperOS
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}
