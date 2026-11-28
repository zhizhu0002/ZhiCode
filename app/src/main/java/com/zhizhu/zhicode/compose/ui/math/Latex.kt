package com.zhizhu.zhicode.compose.ui.math

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.unit.TextUnit
import ru.noties.jlatexmath.JLatexMathAndroid
import ru.noties.jlatexmath.JLatexMathDrawable
import kotlin.math.ceil

object LatexRenderer {

    /** 缓存上限按**条目数**而不是字节数。 */
    private const val CacheEntries = 96

    private val cache = object : LruCache<String, Bitmap>(CacheEntries) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    /** 位图四周留白（px）。公式的下伸部（如 `y` 的尾巴）会被紧裁，需要一点余量。 */
    private const val PaddingPx = 2f

    /** 初始化。 */
    fun ensureInit(context: Context) {
        runCatching { JLatexMathAndroid.init(context.applicationContext) }
    }

    /** 渲染成位图。 */
    fun bitmap(context: Context, latex: String, textSizePx: Float, colorArgb: Int): Bitmap? {
        if (latex.isBlank() || textSizePx <= 0f) return null
        // 键里带字号（取整到 0.1px，避免浮点尾数把缓存打散）与颜色。
        val key = buildString {
            append(textSizePx.let { (it * 10).toInt() })
            append('|')
            append(colorArgb)
            append('|')
            append(latex)
        }
        cache.get(key)?.let { return it }

        ensureInit(context)
        val rendered = runCatching { rasterize(latex, textSizePx, colorArgb) }.getOrNull() ?: return null
        // 宽度或高度为 0 说明排版没产出任何东西（空公式、纯注释宏），
        // 那不是一个错误，但也没有可显示的内容。
        if (rendered.width <= 0 || rendered.height <= 0) return null
        cache.put(key, rendered)
        return rendered
    }

    /** 只问尺寸、不要位图。用于 [androidx.compose.ui.text.InlineTextContent] 的占位测量。 */
    fun measure(context: Context, latex: String, textSizePx: Float, colorArgb: Int): Pair<Int, Int>? =
        bitmap(context, latex, textSizePx, colorArgb)?.let { it.width to it.height }

    private fun rasterize(latex: String, textSizePx: Float, colorArgb: Int): Bitmap {
        val drawable = JLatexMathDrawable.builder(latex)
            .textSize(textSizePx)
            .color(colorArgb)
            .padding(PaddingPx.let { ceil(it).toInt() })
            .build()
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight
        val bitmap = Bitmap.createBitmap(
            width.coerceAtLeast(1),
            height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    /** 清空缓存。主题切换时不必调（键里已经有颜色），只在真正想释放内存时用。 */
    fun clear() = cache.evictAll()
}

/** 把一段公式画出来。 */
@Composable
fun LatexFormula(
    latex: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // 默认用正文色：公式是正文的一部分，不该自己有颜色主张。
    val resolvedColor = if (color == Color.Unspecified) MiuixTheme.colorScheme.onSurface else color
    val bitmap = remember(latex, fontSize, resolvedColor) {
        val px = with(density) { fontSize.toPx() }
        LatexRenderer.bitmap(context, latex, px, resolvedColor.toArgb())
    }
    if (bitmap == null) {
        // 渲染失败：把源码原样显示出来。用户至少能看到自己贴的是什么，
        // 也能看出是哪个宏有问题。
        top.yukonga.miuix.kmp.basic.Text(
            text = latex,
            modifier = modifier,
            color = resolvedColor,
            fontSize = fontSize,
        )
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = latex,
            modifier = modifier,
            contentScale = ContentScale.FillBounds,
        )
    }
}
