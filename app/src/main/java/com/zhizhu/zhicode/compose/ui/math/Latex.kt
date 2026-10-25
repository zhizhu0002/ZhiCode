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

/**
 * LaTeX 数学公式渲染。
 *
 * ## 为什么借库
 *
 * 数学排版（上下标、分式、根式、矩阵、可伸缩括号、希腊字母字形）是一个规模远超本项目的
 * 东西。自写只能做出"看起来像公式"的残缺版本，而用户贴进来的往往是真实的
 * `\frac{\partial L}{\partial w}`。所以这里借 `ru.noties:jlatexmath-android`
 * （上游 `org.scilab.forge:jlatexmath` 的 Android 移植，把 AWT 绘制换成
 * `android.graphics.Canvas`）。
 *
 * ## 为什么渲染成位图并缓存
 *
 * 该库的对外产物是 `Drawable`（`JLatexMathDrawable`），而 Compose 的画布不接受
 * Android `Drawable` 直接绘制。转成 `Bitmap` 之后就能用 [Image] 放进任意布局，
 * 也能当作 [androidx.compose.ui.text.InlineTextContent] 的占位内容塞进正文流。
 *
 * 位图必须缓存：流式输出时同一段公式会被重复测量几十次（每次重组都会问一次尺寸），
 * 而一次 `TeXFormula` 解析 + 光栅化在手机上约 1–5 ms。缓存键包含
 * **字号与颜色**——换主题或改字号后旧位图不能复用（它已经把颜色烧进像素了）。
 *
 * ## 失败一律回落到原文
 *
 * 用户贴过来的 LaTeX 经常是残缺的（少一个 `}`、用了不支持的宏）。那时
 * `JLatexMathDrawable` 会抛异常。这里的约定是**返回 null**，由调用方把原文
 * 照常显示出来 —— 一个显示成源码的公式，远好过一条因为公式而整段崩掉的回答。
 */
object LatexRenderer {

    /**
     * 缓存上限按**条目数**而不是字节数。
     *
     * 单个公式位图通常只有几十 KB，但也有整屏矩阵那种上百 KB 的；按字节数算需要每个
     * 条目都记大小、还要处理"单个条目就超上限"的退化情形。条目数上限配合足够大的值
     * 已经把实际内存控制在一两 MB 量级，换来的是实现上没有任何边界情况。
     */
    private const val CacheEntries = 96

    /**
     * ⚠️ 用 `android.util.LruCache` 而不是 `androidx.collection.LruCache`：
     * 前者线程安全（内部 synchronized），而渲染发生在 IO 线程、读取发生在组合线程。
     */
    private val cache = object : LruCache<String, Bitmap>(CacheEntries) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    /** 位图四周留白（px）。公式的下伸部（如 `y` 的尾巴）会被紧裁，需要一点余量。 */
    private const val PaddingPx = 2f

    /**
     * 初始化。库自带一个 `ContentProvider` 做同样的事，但那套机制在
     * 「provider 被合并/被裁剪」时会静默失效；显式调用一次是幂等的，也更可靠。
     */
    fun ensureInit(context: Context) {
        runCatching { JLatexMathAndroid.init(context.applicationContext) }
    }

    /**
     * 渲染成位图。返回 null 表示"这段 LaTeX 渲染不出来"，调用方应回落原文。
     *
     * @param textSizePx 字号（**像素**，不是 sp）。库内部按像素排版。
     * @param colorArgb  字形颜色。已经烧进像素，所以它进缓存键。
     */
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

/**
 * 把一段公式画出来。
 *
 * [fontSize] 用 `TextUnit` 是为了能跟正文用同一个单位书写；内部按当前密度换算成像素
 * 再交给排版器（它只认像素）。
 */
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
