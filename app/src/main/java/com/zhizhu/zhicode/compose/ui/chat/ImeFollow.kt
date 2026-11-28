package com.zhizhu.zhicode.compose.ui.chat

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun ImeLazyListAutoScroller(
    listState: LazyListState,
    enabled: Boolean,
    imeShrinkPx: () -> Int,
) {
    LaunchedEffect(listState, enabled) {
        if (!enabled) return@LaunchedEffect
        // 跟随布局收缩后的真实视口高度变化，确保 canScrollForward 已更新再增量滚动。
        var lastViewportHeight = listState.layoutInfo.viewportSize.height
        snapshotFlow { listState.layoutInfo.viewportSize.height }
            .distinctUntilChanged()
            .collect { currentHeight ->
                if (lastViewportHeight <= 0) {
                    lastViewportHeight = currentHeight
                    return@collect
                }
                val delta = lastViewportHeight - currentHeight
                lastViewportHeight = currentHeight
                if (delta <= 0 || imeShrinkPx() <= 0) return@collect
                listState.scrollBy(delta.toFloat())
            }
    }
}
