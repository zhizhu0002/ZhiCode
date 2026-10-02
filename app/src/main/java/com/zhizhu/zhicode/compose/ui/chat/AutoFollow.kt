package com.zhizhu.zhicode.compose.ui.chat

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 「自动吸底」的跟/不跟决策。
 *
 * 纯逻辑、不碰 Compose，所以能被单测钉住（见 `AutoFollowPolicyTest`）：
 * 这段判定的每个分支都只影响手感，**判错不会编译失败、也不会报错**，
 * 只会让人觉得"滚动不顺"。
 */
internal object AutoFollowPolicy {

    /**
     * @param startIndex  用户**开始拖动那一刻**的首个可见项下标
     * @param startOffset 同一时刻该下标的滚动偏移（px）
     * @param endIndex    松手时的首个可见项下标
     * @param endOffset   松手时的滚动偏移（px）
     * @param hysteresisPx 判定"动了"的最小位移。小于它的拖动不改变跟/不跟 ——
     *                     手指在屏幕上抖几个像素不该切换状态。
     * @param sawBottomDuringDrag 拖动过程中是否**至少有一帧**贴到底。
     *                     不能只看松手那一刻：流式内容一直在长，松手读到的
     *                     `canScrollForward` 很可能已经是真的了。
     * @param previous    拖动前的状态
     * @param atBottomNow 松手这一刻是否贴底
     */
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
            // 否则用户在历史里往下挪 20px 就会被一把拽到底 —— 那正是
            // 「手指和自动滚动在抢」的来源。
            towardNewer -> sawBottomDuringDrag || atBottomNow
            // 几乎没动（小于滞回阈值）：保持原状态；但如果已经贴底就恢复跟随
            // （内容长高把末尾顶出屏幕时，用户"抖一下"不该让跟随断掉）。
            else -> previous || atBottomNow
        }
    }
}

/**
 * 观察 [listState]，返回"是否应该继续跟随最新内容"。
 *
 * <h3>为什么不能只看「可见末项是不是最后一项」</h3>
 * 内容增长本身就会把末项顶出屏幕，于是在我们来得及滚动之前条件就已经变成 false ——
 * 表现是回复长过一屏之后跟随就断了，得手动往下滑。
 *
 * <h3>为什么不能只在松手那一刻判断</h3>
 * 流式输出下内容每 32ms 长一次，松手读到的 `canScrollForward` 很可能已经不是
 * 用户手指的位置了。所以拖动过程中**只要有一帧贴底**就记下来（[AutoFollowPolicy.sawBottomDuringDrag]）。
 *
 * <h3>为什么要立刻暂停</h3>
 * 用户按住往上拖的时候，如果跟随还是 true，流式内容会持续把他拽回底部 ——
 * 手感是"手指和自动滚动在抢"。所以拖动一开始就暂停，松手再按位置决定。
 *
 * <h3>[forceFollowToken]：发消息时无条件恢复跟随</h3>
 * "暂停跟随"这件事住在上面那个 `mutableStateOf` 里，界面外部**没有**任何入口能
 * 把它打开。于是用户上翻历史后直接发一条消息时，气泡与新回复会全部落在屏幕外。
 * 参考实现（IQ Code `scrollChat()`）在发消息时无条件 `chatAutoFollow = true`；
 * 这里用「令牌变了就把它设回 true」达到同一件事，令牌由 ViewModel 在 send 时递增。
 * 默认 0 且只在 > 0 时生效，于是首帧那次启动不会误触发。
 */
@Composable
internal fun rememberAutoFollow(
    listState: LazyListState,
    forceFollowToken: Long = 0L,
    hysteresis: Dp = 8.dp,
): State<Boolean> {
    val autoFollow = remember(listState) { mutableStateOf(true) }
    val hysteresisPx = with(LocalDensity.current) { hysteresis.roundToPx() }

    LaunchedEffect(forceFollowToken) {
        if (forceFollowToken > 0L) autoFollow.value = true
    }

    LaunchedEffect(listState, hysteresisPx) {
        var dragging = false
        var sawBottom = false
        var startIndex = 0
        var startOffset = 0

        // 拖动过程中持续采样"是否贴底"。和下面那个流分开，是因为下面那个
        // 一旦把 index/offset 也包进去就会**每个滚动像素**都发射一次，
        // 而在那个分支里重置起点会把整段判定算成"没动"。
        launch {
            snapshotFlow { listState.isScrollInProgress && !listState.canScrollForward }
                .collect { atBottom -> if (atBottom) sawBottom = true }
        }

        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                dragging = true
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
                    previous = autoFollow.value,
                    atBottomNow = !listState.canScrollForward,
                )
            }
        }
    }
    return autoFollow
}
