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

object BitmapComposer {

    private val MaxWidth = 10_000.dp
    private val MaxHeight = 10_000.dp

    /** @param width   期望宽度（dp）。 */
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

    /** 兜底位图：尺寸对、全透明。 */
    private fun createFallbackBitmap(widthPx: Int): Bitmap =
        Bitmap.createBitmap(widthPx.coerceAtLeast(1), 1, Bitmap.Config.ARGB_8888)
}
