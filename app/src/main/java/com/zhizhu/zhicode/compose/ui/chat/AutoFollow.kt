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
    /*
     * ⚠️ 必须是 `rememberSaveable`，不能是普通 `remember`。
     *
     * 切页签（对话 / 终端 / 文件）时，离场的那个面板会被 `AnimatedContent` **销毁**
     * （面板外面套了 `SaveableStateHolder` 用来保住滚动位置，见 WorkspaceLayouts）。
     * 普通 `remember` 的值跟着组合一起没了，于是切回来时这个标志又变回 `true` ——
     * 而下面那个吸底 effect 的 `snapshotFlow` 一订阅就会先发射一次当前值，
     * 于是**立刻**把列表拽到底部，用户刚翻到的历史位置白翻了。
     * 用户的原话：「切换页面，对话老是回到最低端」。
     *
     * 存的是"用户是否还在跟随最新内容"这件事本身，所以它就该跟着面板状态一起活下来。
     */
    val autoFollow = rememberSaveable(
        listState,
        saver = Saver<MutableState<Boolean>, Boolean>(
            save = { it.value },
            restore = { mutableStateOf(it) },
        ),
    ) { mutableStateOf(true) }
    val hysteresisPx = with(LocalDensity.current) { hysteresis.roundToPx() }

    /*
     * 令牌只对**变化**负责，不对"非零"负责。
     *
     * 原来写的是 `if (forceFollowToken > 0L) autoFollow.value = true` ——
     * `LaunchedEffect(forceFollowToken)` 在**每次重新进入组合**时都会重跑一遍，
     * 而令牌在发过一次消息之后就一直 > 0，于是切回页签时它又把跟随打开、
     * 顺手把列表拉到底部（这是"老是回到最低端"的第二个来源）。
     *
     * 记下已处理过的令牌，只在**真的变了**（= 用户按了发送）时才恢复跟随。
     */
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
