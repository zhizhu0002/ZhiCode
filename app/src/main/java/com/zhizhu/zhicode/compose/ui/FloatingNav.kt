package com.zhizhu.zhicode.compose.ui

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
import androidx.compose.ui.unit.dp

/**
 * 悬浮底栏（胶囊）在屏幕底部**实际占掉的高度**。
 *
 * ## 为什么是一个 CompositionLocal，而不是各页自己算
 *
 * 悬浮底栏是**浮层**：它不占布局高度，所以页面内容可以一路铺到屏幕底边（这才是
 * KSU 的观感 —— 卡片从胶囊底下穿过）。但"能铺到最底"和"最后一行不被挡住"是
 * 两件事，后者只能靠**滚动内边距**（`LazyColumn.contentPadding`）实现，
 * 而那个值必须和胶囊的真实高度严格一致。
 *
 * 之前是三层各写一套数字：`WorkspacePage` 给内容加布局底部 padding（于是底部
 * 空出一整条背景色）、`ChatPage` 只算输入器自身高度、`FilesPage` 写死 16dp。
 * 三种算法必然互相打架 —— 表现就是"胶囊底下有一条背景色带"和"内容被挡住"。
 *
 * 现在只有一个来源：`WorkspacePage` 量出 [floatingNavInset] 并提供下来，
 * 各页把它当作**滚动留白**（而不是布局留白）使用。
 *
 * 默认 `0.dp`，也就是"没有悬浮底栏" —— 固定底栏 / 顶栏模式下各页不需要让位。
 */
internal val LocalFloatingNavInset = compositionLocalOf<Dp> { 0.dp }

/**
 * 当前**固定底栏**占掉的底部高度（悬浮模式下是 `0.dp`，因为那时内容本来就铺到底）。
 *
 * 对话页需要它来算「输入器最多能往下落到哪」：
 * 屏幕底 − 这个值 = 底栏上沿。
 *
 * 键盘弹起时输入器要往上抬，但抬起量归零（键盘收起动画末尾）时它不该落到屏幕最底 ——
 * 那样会压在 NavigationBar 上。正确的落点是 `max(键盘顶边, 底栏上沿)`，
 * 而"底栏上沿"只有这个值给得出来（对话页自己读不到 Scaffold 的 content padding）。
 */
internal val LocalBottomBarInset = compositionLocalOf<Dp> { 0.dp }

/**
 * 胶囊自身高度。与 `LiquidWorkspaceBar` 里主 Row 的 `height` 必须一致。
 *
 * 曾经从 64dp 收到 52dp，理由是"64dp 会把输入器下缘吃掉一截"。那个判断是错的：
 * 真正的原因是底部留白（`bottomInset`）在首屏还没量出真实高度时就落底了，
 * 于是内容停在按 72dp 估算的位置、被后来的悬浮层压住 —— 与胶囊高 12dp 无关
 * （差 278dp，不是 12dp）。把高度还回去，覆盖问题由留白那边解决。
 */
internal val FloatingNavBarHeight = 64.dp

/** 胶囊与系统导航栏之间的呼吸缝。 */
internal val FloatingNavBarGap = 8.dp

/** 内容与胶囊之间至少留出的空隙，避免最后一行紧贴胶囊边缘。 */
internal val FloatingNavContentGap = 8.dp

/**
 * 「列表刚刚真的滚动过」的计数器。
 *
 * 由 [reportNavBarScroll] 在每次有效滚动时自增；手势（`navBarHideOnVerticalDrag`）
 * 靠它判断"这一下由列表负责，我不该再写一次进度"。
 *
 * ## 为什么必须有它（用户报的「抬起再滑会弹一下」）
 *
 * 两个驱动同时接在同一个进度上，而它们看到的是**同一段手指位移**：
 *  · 列表驱动：`firstVisibleItemScrollOffset` 增大 → 进度 += Δ/travel；
 *  · 手势驱动：手指下移 Δy → 进度 += Δy/travel。
 *
 * 一次滑动两处各加一次，胶囊就走两倍距离；手指抬起再按下时基准又换一次，
 * 于是出现"弹一下"。用这个计数器做仲裁：**只要列表动过，手势就让路**，
 * 手势只服务"这一屏根本不能滚"的情况。
 */
internal val LocalNavBarScrollTick = compositionLocalOf<IntArray> { IntArray(1) }

/**
 * 悬浮导航隐藏进度的**显示值**（已补间，`0` = 完全可见，`1` = 完全滑出）。
 *
 * 消费者只在**布局/绘制**阶段读它（`graphicsLayer { }`、`offset { }`），
 * 所以每帧变化不会触发重组。
 *
 * ⚠️ **绝不能**拿它去算列表的 `contentPadding`。那会形成回路：
 * 进度变 → 留白变 → 列表重新测量 → `scrollOffset` 变 → 又写回进度，
 * 表现就是用户报的「偶尔下滑或上滑，组件都会弹一下」。
 * 列表留白必须用**稳定值**（[LocalFloatingNavInset]），只有悬浮层本身才跟着进度位移。
 */
internal val LocalNavBarProgress = compositionLocalOf<State<Float>> { mutableStateOf(0f) }

/**
 * 悬浮导航隐藏进度的**目标值**（`0` = 完全可见，`1` = 完全滑出）。
 *
 * 由滚动（[reportNavBarScroll]）与手势直接写入，是**原始**目标；
 * 平滑后的显示值由 `WorkspacePage` 用一条短 tween 从它推出来。
 */
internal val LocalNavBarTarget = compositionLocalOf<MutableFloatState> { mutableFloatStateOf(0f) }

/**
 * 从"完全可见"滑到"完全滑出"所需的**竖直位移**（像素）。
 *
 * 由 `WorkspacePage` 提供（= 胶囊高度 + 间隙 + 系统栏 + 阴影余量）。
 * [reportNavBarScroll] 用它把滚动的像素量换算成进度，所以两者必须同源 ——
 * 否则"滚一小段就全藏了"或"怎么滚都藏不完"。
 */
internal val LocalNavBarTravelPx = compositionLocalOf<Float> { 1f }

/**
 * 一次滚动事件最多被采信的竖直位移（像素）。
 *
 * 超过它就当成"列表被重置了"，**不累加**，只同步基准。
 *
 * ## 为什么必须有这条上限（这是"划到最底下就闪"的直接原因）
 *
 * 到列表底部那一刻会发生三件会让 `firstVisibleItemIndex/ScrollOffset`
 * **大跨度跳变**的事，而它们都不是"用户在滚"：
 *
 *  1. 自动跟随会 `requestScrollToItem(total - 1)`，index 可能从 39 直接到 43；
 *  2. 底部留白（`bottomInset`）变化会重新测量，滚动位置被重新吸附；
 *  3. 越界阻尼回弹让 offset 先冲高再弹回。
 *
 * 把它们的差值当滚动量累加，进度就会"撞到 1 再弹回"，肉眼看到就是闪一下。
 * 正常的滚动事件每帧只有几个像素到几十像素，所以 600px 这条线足够宽松，
 * 不会误伤真的快速滑动。
 */
private const val MaxTrustedScrollDeltaPx = 600f

/**
 * 把 `LazyListState` 的滚动量接到悬浮导航的隐藏进度上。
 *
 * ## 为什么是"累加滚动量"而不是"判断方向"
 *
 * 上一版这里只置一个布尔：越过阈值就 `true`、反向越过就 `false`。那样得到的
 * 只有两个状态，动画无处安放，而且完全跟不上手指 —— 用户的原话是
 * "动画停止与播放根据对话流的滚动来弄最好"。
 *
 * 现在把每次滚动的**像素增量**除以 [LocalNavBarTravelPx] 累加进进度：
 *  · 往下翻（`scrollOffset` 增大 = 内容向上走）→ 进度增大 → 胶囊往下退；
 *  · 往上翻 → 进度减小 → 胶囊回来；
 *  · 停手 → 目标不动 → 胶囊停在原处。
 *
 * ## ⚠️ 两个坑（已修，别写回去）
 *
 * 1. **不要在 `collect` 里重新读 `listState`**。上一版在块内又调了一次
 *    `scrollAmount()`，那是**新的读取**，比发射值更新 —— 两次读数可能来自
 *    不同帧，增量算出巨大偏差，进度瞬间跳到 0 或 1。
 *    现在只用发射出来的那一对 `(index, offset)`。
 *
 * 2. **不要无条件累加**。见 [MaxTrustedScrollDeltaPx]：底部/重置造成的跳变
 *    必须被挡掉，否则"划到最底下"几乎每次都闪。
 *
 * ## 回到顶部一定完全显示
 *
 * `index == 0 && offset == 0` 时把进度清零。这条不能省：用户在历史里翻了很久、
 * 胶囊早就藏完了，此时滑回开头却看不到导航，会直接以为它坏了。
 */
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
