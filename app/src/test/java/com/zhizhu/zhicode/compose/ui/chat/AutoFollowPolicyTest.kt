package com.zhizhu.zhicode.compose.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「自动吸底」跟/不跟决策的判定表。
 *
 * <h3>为什么值得单独测</h3>
 *
 * 这段判定只影响手感：判错**不会编译失败、不会报错、也不会崩**，
 * 只会让人说一句"滚动不顺"。而两个方向的错都难查：
 *
 * - **该跟却不跟**：回复长过一屏之后跟随就断了，用户得手动往下滑；
 * - **不该跟却跟**：用户正在翻历史，流式内容一次次把他拽回底部 ——
 *   也就是"手指和自动滚动在抢"。
 *
 * <h3>滞回（hysteresis）为什么是这段的核心</h3>
 *
 * 内容每 32ms 长一次，松手那一刻读到的 `canScrollForward` 很可能已经不是
 * 用户手指的位置了（内容刚长高，末尾被顶出屏幕）。没有滞回的话，
 * "贴着底部松手"会被判成"停在半途"，跟随直接断掉 —— 这就是用户
 * 报的「自动滚动不顺畅」最直接的来源。
 * 所以判据里有两条：拖动过程中**到过底部**（`sawBottomDuringDrag`），
 * 以及小于阈值的位移**不改变状态**。
 */
class AutoFollowPolicyTest {

    private fun decide(
        startIndex: Int = 0,
        startOffset: Int = 0,
        endIndex: Int = 0,
        endOffset: Int = 0,
        hysteresisPx: Int = 20,
        sawBottom: Boolean = false,
        previous: Boolean = true,
        atBottomNow: Boolean = false,
    ) = AutoFollowPolicy.decide(
        startIndex = startIndex,
        startOffset = startOffset,
        endIndex = endIndex,
        endOffset = endOffset,
        hysteresisPx = hysteresisPx,
        sawBottomDuringDrag = sawBottom,
        previous = previous,
        atBottomNow = atBottomNow,
    )

    // ---- 往历史方向拖：停下跟随 -----------------------------------------

    @Test
    fun draggingTowardOlderContentPausesFollow() {
        // 同一项内往旧内容方向拖过阈值
        assertFalse(decide(startIndex = 4, startOffset = 900, endIndex = 4, endOffset = 500))
        // 跨过项边界 —— 下标减小
        assertFalse(decide(startIndex = 4, startOffset = 10, endIndex = 3, endOffset = 800))
    }

    // ---- 往最新方向拖：只有真的到过底部才恢复 ----------------------------

    @Test
    fun draggingTowardNewContentResumesOnlyIfItReachedTheBottom() {
        // 到过底部 → 恢复
        assertTrue(
            decide(
                startIndex = 3, startOffset = 800, endIndex = 4, endOffset = 900,
                sawBottom = true, previous = false,
            ),
        )
        // 松手那一刻刚好贴底也算
        assertTrue(
            decide(
                startIndex = 3, startOffset = 800, endIndex = 4, endOffset = 900,
                sawBottom = false, previous = false, atBottomNow = true,
            ),
        )
        // 在历史里往下挪了一段、但离底部还远 → 保持暂停（否则会被一把拽到底）
        assertFalse(
            decide(
                startIndex = 2, startOffset = 100, endIndex = 2, endOffset = 600,
                sawBottom = false, previous = false,
            ),
        )
    }

    // ---- 几乎没动：保持原状态（这就是「不顺畅」的那一段）-----------------

    @Test
    fun tinyDragInsideHysteresisKeepsFollowing() {
        // 用户贴底时手指抖了 3px（阈值 20px）→ 跟随不能断。
        // 旧实现读一次 canScrollForward 就把它判成"停在半途"了。
        assertTrue(decide(startIndex = 6, startOffset = 300, endIndex = 6, endOffset = 303))
        // 反向同理：抖 3px 也不该把暂停变成跟随
        assertFalse(
            decide(
                startIndex = 6, startOffset = 300, endIndex = 6, endOffset = 297,
                previous = false,
            ),
        )
    }

    @Test
    fun noMovementAtAllKeepsPreviousState() {
        assertTrue(decide(previous = true))
        assertFalse(decide(previous = false))
    }

    @Test
    fun noMovementButNowAtBottomResumesFollow() {
        // 内容长高把末尾顶出屏幕之后，最后那一帧往往"位置没变但已贴底"。
        assertTrue(decide(previous = false, atBottomNow = true))
    }

    /** 停滞在阈值边界上不能抖动：等于阈值不算"动了"。 */
    @Test
    fun exactlyAtHysteresisIsNotMovement() {
        assertTrue(decide(startIndex = 6, startOffset = 300, endIndex = 6, endOffset = 320, hysteresisPx = 20))
        assertTrue(decide(startIndex = 6, startOffset = 300, endIndex = 6, endOffset = 280, hysteresisPx = 20))
    }

    /**
     * 拖动过程中"到过底部"比松手那一刻的读数更重要 ——
     * 这是修「不顺畅」的关键，别把它简化掉。
     */
    @Test
    fun sawBottomDuringDragWinsOverTheReleaseInstantReading() {
        assertTrue(
            decide(
                startIndex = 4, startOffset = 700, endIndex = 5, endOffset = 120,
                sawBottom = true, previous = false, atBottomNow = false,
            ),
        )
    }
}
