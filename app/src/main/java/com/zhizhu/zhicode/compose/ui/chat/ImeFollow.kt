package com.zhizhu.zhicode.compose.ui.chat

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 让对话流**跟着键盘一起动**，而不是等键盘弹完再跳一下。
 *
 * ## 为什么需要它
 *
 * 键盘弹起时，列表的可用高度在**动画的十几帧里**连续缩小。原来我们的做法是
 * 「IME 打开代数变了 → 等一帧 → `scrollToItem(最后一条)`」：
 *
 *  · 等待那一帧的时候键盘还在长，`scrollToItem` 落下时视口已经又变了；
 *  · `scrollToItem` 是**瞬间**定位，没有跟手过程 —— 视觉上就是内容先被键盘顶掉，
 *    然后"啪"地弹回来。
 *
 * 参考实现 rikkahub 的做法（`ImeAutoScroller.kt:23-36`）是记下上一次的键盘高度，
 * 每次高度变化就 `scrollBy(本次高度 - 上次高度)`。增量跟着高度曲线走，
 * 于是整个过程是连续的 —— 键盘长多少，列表就同步让出多少。
 *
 * ## 为什么必须走 `snapshotFlow`，不能在组合期读 inset
 *
 * `WindowInsets.ime` 在键盘动画期间**每帧都在变**。在组合期读它
 * （`val h = ime.getBottom(density)`）等于每帧都让这一层重组一次 ——
 * 本文件里那条「对话流狂闪」的注释记录的就是这个坑：每帧重组 → 每帧重新测量列表 →
 * 相邻两帧的内容在"重排前/重排后"之间来回，看起来就是整片在闪。
 *
 * 放进 `snapshotFlow` 之后，读取发生在协程里、**不进组合**，inset 每帧变一次也不重组。
 *
 * ## 为什么收起键盘时不反向滚回去
 *
 * 键盘收起时视口变高，原本贴底的内容上方会空出一截 —— 但那不需要补偿：
 * 视口变大只会让更多内容可见，用户的锚点（他正在看的那条）不会移动。
 * 若反向 `scrollBy`，反而会把内容往下拽、露出底部空白。
 *
 * ⚠️ 所以「高度变小就 `scrollBy(负增量)`」这件事**只对"键盘弹起"方向做**。
 * 参考实现里那个 `if/else` 两个分支算的是同一个式子（等于没分支），
 * 这里按上面的理由显式只在增高时补偿。
 *
 * @param enabled 是否允许跟随。对话页传 `composerFocused` —— 侧栏搜索框弹键盘时
 *   浮起来的也是同一个窗口级 inset，那种情况下对话流不该动。
 */
@Composable
internal fun ImeLazyListAutoScroller(
    listState: LazyListState,
    enabled: Boolean,
) {
    val ime = WindowInsets.ime
    val density = LocalDensity.current

    LaunchedEffect(listState, enabled) {
        if (!enabled) return@LaunchedEffect
        // 记录"上一次已补偿到的键盘高度"。用局部变量而不是 State：
        // 它只在协程里读写，进组合没有意义，反而会多一次重组。
        var consumedPx = 0
        snapshotFlow { ime.getBottom(density) }
            .distinctUntilChanged()
            .collect { currentPx ->
                val delta = currentPx - consumedPx
                consumedPx = currentPx
                // 键盘收起（delta < 0）不补偿，理由见上面的说明。
                if (delta <= 0) return@collect
                // `scrollBy` 在列表根本不能滚时是安全的空操作；
                // 内容不足一屏时也不该出现位移，所以这里不做额外判断。
                listState.scrollBy(delta.toFloat())
            }
    }
}
