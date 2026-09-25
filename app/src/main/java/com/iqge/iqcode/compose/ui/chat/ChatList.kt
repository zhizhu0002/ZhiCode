package com.iqge.iqcode.compose.ui.chat

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iqge.iqcode.compose.model.ChatItem
import com.iqge.iqcode.compose.model.ChatKind
import com.iqge.iqcode.compose.model.WorkspaceUiState
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter

/** 对话流，对应原版 renderChat() + addTranscriptWindow()。 */
@Composable
fun ChatList(
    state: WorkspaceUiState,
    onToggleTool: (String) -> Unit,
    onToggleGroup: (String, Boolean) -> Unit,
    onToggleThinking: (String) -> Unit,
    onMessageActions: (ChatItem) -> Unit,
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

    // 自动吸底：新增消息或工作状态变化时滚到最后一项
    LaunchedEffect(state.transcript.size, state.workingStatus) {
        val leading = if (state.transcript.isEmpty()) 1 else 0
        val lastIndex = leading + state.transcript.size
        if (lastIndex > 0) listState.animateScrollToItem(lastIndex)
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
                // 新消息淡入 + 已有消息位置变化时平滑推移
                Box(modifier = Modifier.animateItem().padding(end = 14.dp)) {
                    when (item.kind) {
                        ChatKind.USER -> UserBubble(item) { onMessageActions(item) }
                        ChatKind.ASSISTANT -> AssistantCard(
                            item = item,
                            onToggleThinking = { onToggleThinking(item.id) },
                            onLongPress = { onMessageActions(item) },
                        )
                        ChatKind.TOOL_GROUP -> ToolGroupCard(
                            item = item,
                            onToggleTool = onToggleTool,
                            onToggleGroup = { expanded -> onToggleGroup(item.id, expanded) },
                            onActions = { onMessageActions(item) },
                        )
                        ChatKind.ERROR -> ErrorCard(item)
                        ChatKind.INFO -> InfoCard(item)
                    }
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
