package com.zhizhu.zhicode.compose.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.ChatItem
import com.zhizhu.zhicode.compose.model.ChatKind
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.theme.ZhiTextScale
import com.zhizhu.zhicode.compose.ui.ZhiSmallPill
import com.zhizhu.zhicode.compose.ui.rememberFingerTracker
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    /**
     * **主体调试模式**：在真实消息上就地加调试信息（不另开页面）。
     *
     * 打开后每条消息上方多一行 `类型 · id · 正文/思考长度 · 工具数`，并给一个
     * 「Markdown 源码」开关把原始文本摊出来；工具组默认全展开。
     *
     * 之所以做在**真实列表**里而不是调试页：排版问题（折行、行高、卡片间距、
     * 长文本溢出）只有在真实滚动容器、真实宽度、真实数据下才看得出来。
     */
    debugMode: Boolean = false,
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

    // 输入法弹出/收起时把对话吸回底部：键盘顶起视口后列表可视高度骤变，
    // 原先贴底的内容被顶出屏幕；用户此刻的意图几乎总是"接着输入"，所以这里
    // 无条件回底（不改 autoFollow，避免键盘收起时把正在看历史的人拽走）。
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0) {
            val s = currentState
            val leading = if (s.transcript.isEmpty()) 1 else 0
            listState.requestScrollToItem(leading + s.transcript.size)
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
                // "想让智蛛做什么？"互相穿透、错位，看起来像渲染 bug。
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
                    // ⚠️ 调试条与消息卡必须放进**同一个 Column** 里，不能并列在 Box 下。
                    //
                    // 这个 Box 是用来给 anchoredMenu 定位的（菜单要盖在卡片上），
                    // 而 Box 的子项是**叠放**而不是竖排 —— 直接并列会让调试条被卡片盖住，
                    // 表现是"调试条的文字与消息正文糊在一起"。所以：竖排交给 Column，
                    // 叠放只留给那一个菜单。
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // ---- 主体调试模式：就地加料（不改变下面任何卡片的渲染） ----
                        if (debugMode) {
                            MessageDebugStrip(item)
                        }
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
                    } // Column（调试条 + 消息卡）
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

/**
 * 主体调试模式下每条消息上方的**调试条**。
 *
 * 内容刻意全是"一眼能对上号"的元信息：类型、id、正文/思考字数、工具数与各状态计数、
 * 上下文脚注。它的用途是回答"这条为什么长这样"——比如卡片高度异常时，先看
 * `body 1200` / `think 3400` 就能立刻分清是正文太长还是思考面板展开着。
 *
 * 「Markdown 源码」是一个**开关**而不是常显：源码往往比渲染结果长好几倍，
 * 常显会把真实排版挤走 —— 而这一模式的价值恰恰是看真实排版。
 */
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
                fontSize = ZhiTextScale.Micro,
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
                fontSize = ZhiTextScale.Micro,
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
                    fontSize = ZhiTextScale.Footnote,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
