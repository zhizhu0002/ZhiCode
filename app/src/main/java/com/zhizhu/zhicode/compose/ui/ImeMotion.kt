package com.zhizhu.zhicode.compose.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsAnimationCompat

/**
 * Activity 级 IME 运动状态。
 *
 * 这里故意分成两份：
 * - [currentLiftPx] 只给布局阶段的 `Modifier.offset {}` 读取，跟着键盘动画逐帧变化；
 * - [settledLiftPx] 只在动画结束后更新，给列表底部留白和一次性吸底事件使用。
 *
 * 如果把两份合成一个 Compose 参数，键盘动画每一帧都会重组 ChatArea/ChatList，
 * 同时列表滚动协程还会被不断取消。这正是旧实现"输入框会上移但对话不稳定吸底"的根源。
 */
class ImeMotionState {
    var currentLiftPx by mutableIntStateOf(0)
        private set

    var settledLiftPx by mutableIntStateOf(0)
        private set

    /** 只在键盘从关闭到打开时递增；由 ChatList 用来安排一次吸底。 */
    var openGeneration by mutableIntStateOf(0)
        private set

    private var runningImeAnimations = 0
    private var openingSignalled = false

    fun apply(insets: WindowInsetsCompat) {
        val lift = liftOf(insets)
        val wasOpen = currentLiftPx > 0 || settledLiftPx > 0
        currentLiftPx = lift
        if (!wasOpen && lift > 0 && !openingSignalled) {
            openingSignalled = true
            openGeneration++
        }
        if (runningImeAnimations == 0) {
            settledLiftPx = lift
            if (lift == 0) openingSignalled = false
        }
    }

    fun onPrepare(animation: WindowInsetsAnimationCompat) {
        if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
            runningImeAnimations++
            if (currentLiftPx == 0 && settledLiftPx == 0 && !openingSignalled) {
                openingSignalled = true
                openGeneration++
            }
        }
    }

    fun onProgress(insets: WindowInsetsCompat): WindowInsetsCompat {
        currentLiftPx = liftOf(insets)
        return insets
    }

    fun onEnd(animation: WindowInsetsAnimationCompat) {
        if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
            runningImeAnimations = (runningImeAnimations - 1).coerceAtLeast(0)
            if (runningImeAnimations == 0) {
                settledLiftPx = currentLiftPx
                if (currentLiftPx == 0) openingSignalled = false
            }
        }
    }

    fun reset() {
        runningImeAnimations = 0
        openingSignalled = false
        currentLiftPx = 0
        settledLiftPx = 0
    }

    private fun liftOf(insets: WindowInsetsCompat): Int {
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        return (ime - navigation).coerceAtLeast(0)
    }
}

val LocalImeMotion = staticCompositionLocalOf { ImeMotionState() }
