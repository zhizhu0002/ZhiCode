package com.zhizhu.zhicode.compose.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsAnimationCompat

/** Activity 级 IME 运动状态。 */
class ImeMotionState {
    var currentLiftPx by mutableIntStateOf(0)
        private set

    var settledLiftPx by mutableIntStateOf(0)
        private set

    /** Raw window IME bottom inset in pixels (from the screen bottom edge). */
    var imeBottomPx by mutableIntStateOf(0)
        private set

    /** Actual bottom navigation inset, kept separate from the IME lift. */
    var navigationBarInsetPx by mutableIntStateOf(0)
        private set

    /** 只在键盘从关闭到打开时递增；由 ChatList 用来安排一次吸底。 */
    var openGeneration by mutableIntStateOf(0)
        private set

    var imeVisible by androidx.compose.runtime.mutableStateOf(false)
        private set

    private var runningImeAnimations = 0
    private var openingSignalled = false

    fun apply(insets: WindowInsetsCompat) {
        val rawIme = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        val lift = (rawIme - nav).coerceAtLeast(0)
        navigationBarInsetPx = nav
        val wasOpen = imeBottomPx > 0 || settledLiftPx > 0
        imeBottomPx = rawIme
        currentLiftPx = lift
        if (!wasOpen && lift > 0 && !openingSignalled) {
            openingSignalled = true
            openGeneration++
        }
        if (runningImeAnimations == 0) {
            settledLiftPx = lift
            imeVisible = rawIme > 0
            if (rawIme == 0) openingSignalled = false
        }
    }

    fun onPrepare(animation: WindowInsetsAnimationCompat) {
        if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
            runningImeAnimations++
            // 动画**一开始**就翻真：让位量必须立即归零，否则键盘动画期间
            // 扩展键栏 / 输入器与键盘之间会裂开一条缝（用户截图）。
            imeVisible = true
            if (imeBottomPx == 0 && settledLiftPx == 0 && !openingSignalled) {
                openingSignalled = true
                openGeneration++
            }
        }
    }

    fun onProgress(insets: WindowInsetsCompat): WindowInsetsCompat {
        val rawIme = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        imeBottomPx = rawIme
        currentLiftPx = (rawIme - nav).coerceAtLeast(0)
        navigationBarInsetPx = nav
        return insets
    }

    fun onEnd(animation: WindowInsetsAnimationCompat) {
        if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
            runningImeAnimations = (runningImeAnimations - 1).coerceAtLeast(0)
            if (runningImeAnimations == 0) {
                settledLiftPx = currentLiftPx
                imeVisible = imeBottomPx > 0
                if (imeBottomPx == 0) openingSignalled = false
            }
        }
    }

    fun reset() {
        runningImeAnimations = 0
        openingSignalled = false
        imeBottomPx = 0
        currentLiftPx = 0
        settledLiftPx = 0
        navigationBarInsetPx = 0
        imeVisible = false
    }

    /** Composer offset to the bottom edge of the app content, excluding the IME only. */
    fun composerLiftPx(insets: WindowInsetsCompat): Int =
        (insets.getInsets(WindowInsetsCompat.Type.ime()).bottom -
            insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom).coerceAtLeast(0)
}

val LocalImeMotion = staticCompositionLocalOf { ImeMotionState() }
