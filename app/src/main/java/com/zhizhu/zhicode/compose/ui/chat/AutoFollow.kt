package com.zhizhu.zhicode.compose.ui.chat

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 「自动吸底」的跟/不跟决策。 */
internal object AutoFollowPolicy {

    /** @param startIndex  用户**开始拖动那一刻**的首个可见项下标 */
    fun decide(
        startIndex: Int,
        startOffset: Int,
        endIndex: Int,
        endOffset: Int,
        hysteresisPx: Int,
        sawBottomDuringDrag: Boolean,
        previous: Boolean,
        atBottomNow: Boolean,
    ): Boolean {
        // 往**旧内容**方向拖过阈值 → 用户在翻历史，停下跟随。
        val towardOlder = endIndex < startIndex ||
            (endIndex == startIndex && endOffset < startOffset - hysteresisPx)
        // 往**新内容**方向拖过阈值 → 用户在往回赶。
        val towardNewer = endIndex > startIndex ||
            (endIndex == startIndex && endOffset > startOffset + hysteresisPx)

        return when {
            towardOlder -> false
            // 光往新方向拖还不够：中途得真的到过底部才算"我要追最新"。
            towardNewer -> sawBottomDuringDrag || atBottomNow
            // 几乎没动（小于滞回阈值）：保持原状态；但如果已经贴底就恢复跟随
            // （内容长高把末尾顶出屏幕时，用户"抖一下"不该让跟随断掉）。
            else -> previous || atBottomNow
        }
    }
}

/** 「跟随最新内容」的句柄：既能读当前值，也能由界面**主动**重新打开跟随。 */
internal class AutoFollowHandle internal constructor(
    val state: MutableState<Boolean>,
) {
    /** 重新打开跟随（界面主动调用）。 */
    fun rearm() {
        state.value = true
    }
}

/** 观察 [listState]，返回「是否应该继续跟随最新内容」的**句柄**。 */
@Composable
internal fun rememberAutoFollowHandle(
    listState: LazyListState,
    forceFollowToken: Long = 0L,
    hysteresis: Dp = 8.dp,
): AutoFollowHandle {
    val autoFollow = rememberSaveable(
        listState,
        saver = Saver<MutableState<Boolean>, Boolean>(
            save = { it.value },
            restore = { mutableStateOf(it) },
        ),
    ) { mutableStateOf(true) }
    val hysteresisPx = with(LocalDensity.current) { hysteresis.roundToPx() }

    /* 令牌只对**变化**负责，不对"非零"负责。 */
    var lastForceToken by remember { mutableStateOf(forceFollowToken) }

    LaunchedEffect(forceFollowToken) {
        if (forceFollowToken != lastForceToken) {
            lastForceToken = forceFollowToken
            autoFollow.value = true
        }
    }

    LaunchedEffect(listState, hysteresisPx) {
        var dragging = false
        var sawBottom = false
        var wasFollowing = true
        var startIndex = 0
        var startOffset = 0

        // 拖动过程中持续采样"是否贴底"。
        launch {
            snapshotFlow { listState.isScrollInProgress && !listState.canScrollForward }
                .collect { atBottom -> if (atBottom) sawBottom = true }
        }

        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                dragging = true
                // 先记住拖动前的跟随状态：下面会把 autoFollow 置 false 以便拖动期间让位，
                // 直接传给 decide 的 previous 必须是拖动前的值，否则轻点暂停后若没达滞后阈值也会误判为 false。
                wasFollowing = autoFollow.value
                // 直接读一次当前位置：开始拖动时也可能已经贴底（用户拖了两下）。
                sawBottom = !listState.canScrollForward
                startIndex = listState.firstVisibleItemIndex
                startOffset = listState.firstVisibleItemScrollOffset
                autoFollow.value = false
            } else if (dragging) {
                dragging = false
                autoFollow.value = AutoFollowPolicy.decide(
                    startIndex = startIndex,
                    startOffset = startOffset,
                    endIndex = listState.firstVisibleItemIndex,
                    endOffset = listState.firstVisibleItemScrollOffset,
                    hysteresisPx = hysteresisPx,
                    sawBottomDuringDrag = sawBottom,
                    previous = wasFollowing,
                    atBottomNow = !listState.canScrollForward,
                )
            }
        }
    }
    return AutoFollowHandle(autoFollow)
}

/** 只要读的话用这个。 */
@Composable
internal fun rememberAutoFollow(
    listState: LazyListState,
    forceFollowToken: Long = 0L,
    hysteresis: Dp = 8.dp,
): State<Boolean> = rememberAutoFollowHandle(listState, forceFollowToken, hysteresis).state
