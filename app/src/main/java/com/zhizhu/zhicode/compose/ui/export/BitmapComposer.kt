package com.zhizhu.zhicode.compose.ui.export

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * 把任意 `@Composable` 内容离线渲染成一张 Bitmap。
 *
 * ## 为什么需要它
 *
 * 「导出成图片」不能截屏：截屏拿到的是**当前视口**，装不下滚出屏幕的内容，
 * 而且会带上顶栏、输入框、悬浮卡片这些不属于对话本身的东西。
 *
 * 做法是临时建一个 `ComposeView`（**不可见**）、挂到 Activity 的 DecorView 上，
 * 让它自己测量布局，再 `drawToBitmap()` 抽出来。这样能渲染任意高度，
 * 且内容完全由我们控制。
 *
 * ## 为什么必须 postDelayed 再测一次
 *
 * 第一次 `measure`/`layout` 时，`ZhiMarkdown` 里的代码高亮、图片解码这些
 * **异步**内容还没完成，量到的高度偏小。等 ~100ms 后再测一次，高度才是最终的。
 * 只测一次会得到一张底部被裁掉的图 —— 而那种 bug 在缩略图里看不出来。
 *
 * ## 资源释放
 *
 * 容器必须在 `finally` 里从 DecorView 上移除：它是加在**窗口层级**上的，
 * 忘记移除等于 Activity 里常驻一个不可见 View（而且它握着整个 composition）。
 *
 * 对应参考实现：rikkahub 的 `ui/components/BitmapComposer.kt`。
 */
object BitmapComposer {

    private val MaxWidth = 10_000.dp
    private val MaxHeight = 10_000.dp

    /**
     * @param width   期望宽度（dp）。导出图固定宽度排版，所以这个值总要给。
     * @param height  期望高度（dp）。给 `null` 表示"由内容决定"，用 `AT_MOST` 测量。
     * @param scope   必须是 **Main 作用域**：测量与布局只能在主线程做。
     */
    suspend fun composableToBitmap(
        activity: Activity,
        width: Dp? = null,
        height: Dp? = null,
        density: Density,
        scope: CoroutineScope,
        content: @Composable () -> Unit,
    ): Bitmap = suspendCancellableCoroutine { continuation ->
        scope.launch {
            val widthPx = (density.density * (width ?: MaxWidth).value).roundToInt()
            val heightPx = (density.density * (height ?: MaxHeight).value).roundToInt()

            val container = FrameLayout(activity).apply {
                layoutParams = ViewGroup.LayoutParams(widthPx, heightPx)
                // 不可见但**参与测量**：`GONE` 会让它完全不被布局，量出来是 0。
                visibility = View.INVISIBLE
            }
            val composeView = ComposeView(activity).apply {
                // 让它随容器一起销毁，否则 composition 会活到 Activity 结束。
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent { content() }
            }
            container.addView(composeView)

            val decor = activity.window?.decorView as? ViewGroup
            if (decor == null) {
                container.removeAllViews()
                continuation.resume(createFallbackBitmap(widthPx))
                return@launch
            }
            decor.addView(container)

            try {
                val widthSpec = if (width == null) View.MeasureSpec.AT_MOST else View.MeasureSpec.EXACTLY
                val heightSpec = if (height == null) View.MeasureSpec.AT_MOST else View.MeasureSpec.EXACTLY

                fun measureAndLayout() {
                    container.measure(
                        View.MeasureSpec.makeMeasureSpec(widthPx, widthSpec),
                        View.MeasureSpec.makeMeasureSpec(heightPx, heightSpec),
                    )
                    container.layout(
                        0,
                        0,
                        container.measuredWidth.coerceAtLeast(1),
                        container.measuredHeight.coerceAtLeast(1),
                    )
                }

                Handler(Looper.getMainLooper()).post {
                    measureAndLayout()
                    // 等异步内容（代码高亮、图片解码）就位后重测一次，拿到真实高度。
                    Handler(Looper.getMainLooper()).postDelayed({
                        measureAndLayout()
                        val bitmap = runCatching { container.drawToBitmap() }
                            .getOrElse { createFallbackBitmap(widthPx) }
                        // 先移除再 resume：保证调用方拿到的图不会在容器还在窗口上时被回收。
                        decor.removeView(container)
                        composeView.disposeComposition()
                        if (continuation.isActive) continuation.resume(bitmap)
                    }, 120)
                }
            } catch (t: Throwable) {
                decor.removeView(container)
                composeView.disposeComposition()
                if (continuation.isActive) continuation.resume(createFallbackBitmap(widthPx))
            }
        }
    }

    /**
     * 兜底位图：尺寸对、全透明。
     *
     * 宁可给调用方一张能正常压缩保存的空图，也不要抛出去让整个导出流程中断 ——
     * 用户看到"导出失败"时无法自行恢复，而空白图至少能重试。
     */
    private fun createFallbackBitmap(widthPx: Int): Bitmap =
        Bitmap.createBitmap(widthPx.coerceAtLeast(1), 1, Bitmap.Config.ARGB_8888)
}
