package com.zhizhu.zhicode.compose.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.Dp
import com.zhizhu.zhicode.compose.data.TabBarPosition
import com.zhizhu.zhicode.compose.theme.LocalThemeSettings
import androidx.compose.ui.unit.dp

internal val LocalFloatingNavInset = compositionLocalOf<Dp> { 0.dp }

/** 当前**固定底栏**占掉的底部高度（悬浮模式下是 `0.dp`，因为那时内容本来就铺到底）。 */
internal val LocalBottomBarInset = compositionLocalOf<Dp> { 0.dp }

internal val FloatingNavBarHeight = 64.dp

/** 胶囊与系统导航栏之间的呼吸缝。 */
internal val FloatingNavBarGap = 8.dp

/** 内容与胶囊之间至少留出的空隙，避免最后一行紧贴胶囊边缘。 */
internal val FloatingNavContentGap = 8.dp

internal val LocalNavBarScrollTick = compositionLocalOf<IntArray> { IntArray(1) }

internal val LocalNavBarProgress = compositionLocalOf<State<Float>> { mutableStateOf(0f) }

@Composable
internal fun floatingNavSheetInset(): Dp {
    val theme = LocalThemeSettings.current
    val tabBottom = theme.tabBarPositionValue == TabBarPosition.BOTTOM
    if (!tabBottom || !theme.tabBarFloating) return 0.dp
    val navBarBottom = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    // 胶囊 + 呼吸缝 + 系统导航栏；不再加 `FloatingNavContentGap`：
    // 抽屉自己底部还有一圈内边距，两段加起来已经把"最后一行紧贴胶囊"隔开了。
    return FloatingNavBarHeight + FloatingNavBarGap + navBarBottom
}

/** 悬浮导航隐藏进度的**目标值**（`0` = 完全可见，`1` = 完全滑出）。 */
internal val LocalNavBarTarget = compositionLocalOf<MutableFloatState> { mutableFloatStateOf(0f) }

/** 从"完全可见"滑到"完全滑出"所需的**竖直位移**（像素）。 */
internal val LocalNavBarTravelPx = compositionLocalOf<Float> { 1f }

private const val MaxTrustedScrollDeltaPx = 600f

@Composable
internal fun reportNavBarScroll(listState: LazyListState) {
    val target = LocalNavBarTarget.current
    val travelPx = LocalNavBarTravelPx.current.coerceAtLeast(1f)
    val scrollTick = LocalNavBarScrollTick.current
    LaunchedEffect(listState, travelPx) {
        // 换行时 offset 会归零重新增长，跨行必须给一个足够大的基数，
        // 否则"上一行末尾 → 下一行开头"会被算成向上滚。
        fun amount(index: Int, offset: Int): Float = index * 100_000f + offset

        var last = amount(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val now = amount(index, offset)
                val deltaPx = now - last
                last = now

                // 跳变（重置 / 回弹）：只同步基准，不动进度。
                if (kotlin.math.abs(deltaPx) > MaxTrustedScrollDeltaPx) return@collect

                if (index == 0 && offset == 0) {
                    target.floatValue = 0f
                } else if (deltaPx != 0f) {
                    target.floatValue =
                        (target.floatValue + deltaPx / travelPx).coerceIn(0f, 1f)
                    // 告诉手势"这一下我处理了"，免得它把同一段位移再加一次。
                    scrollTick[0]++
                }
            }
    }
}
