package com.zhizhu.zhicode.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.zhizhu.zhicode.compose.theme.LocalThemeSettings
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.theme.ZhiGlass
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun rememberGlass(): Glass {
    val isDark = ZhiColors.isDark()
    // 用户可以在「主题设置 → 显示 → 模糊」里关掉虚化。
    val enabled = LocalThemeSettings.current.blurEnabled
    val liquid = LocalThemeSettings.current.liquidGlassEffective
    // The blur setting is user-toggleable at runtime. Do not freeze its initial value in an
    // unkeyed remember: a false first composition would leave every Glass backdrop disabled.
    val supported = remember(enabled) { isRuntimeShaderSupported() && enabled }
    // 关键：不支持时**连 layerBackdrop 都不创建**。
    return if (supported) {
        // 捕获前先画一层不透明背景色：文档指出 layerBackdrop 只捕获自己的内容，
        // 若内容有透明区域，模糊会把颜色扩散进透明像素、出现明显色块。
        key(isDark) {
            val background = MiuixTheme.colorScheme.background
            // 深浅切换时必须重建 backdrop；否则 RuntimeShader 继续持有上一主题的
            // 背板快照，输入器和顶栏会出现蓝灰色旧色残留。
            val backdrop = rememberLayerBackdrop {
                drawRect(background)
                drawContent()
            }
            remember(backdrop, isDark, liquid) { Glass(backdrop, isDark, liquid) }
        }
    } else {
        remember(isDark, liquid) { Glass(null, isDark, liquid) }
    }
}

internal val NoGlass: Glass = Glass(null, false, false)

/** 由 [rememberGlass] 创建，集中承载门控逻辑。 */
class Glass internal constructor(
    internal val backdrop: LayerBackdrop?,
    private val dark: Boolean,
    /** 是否启用「液态玻璃」观感：更透 + 描边更亮。 */
    private val liquid: Boolean,
) {
    /** 当前设备/Runtime 是否支持 RuntimeShader 模糊。 */
    val supported: Boolean get() = backdrop != null

    /** 加在被模糊层压住的内容上：把它的绘制录进 layer。 */
    /** Capture only the content owner; consumers are always siblings outside this modifier. */
    fun capture(modifier: Modifier): Modifier =
        if (backdrop != null) modifier.layerBackdrop(backdrop) else modifier

    /** 加在悬浮层上：对已捕获的背景做高斯模糊。 */
    fun blur(modifier: Modifier, shape: Shape, radius: Float = ZhiGlass.FloatingBlur): Modifier =
        if (backdrop != null) {
            modifier.textureBlur(backdrop = backdrop, shape = shape, blurRadius = radius)
        } else {
            modifier
        }

    /** 悬浮层的底色。 */
    @Composable
    fun surfaceColor(color: Color): Color =
        if (backdrop != null) {
            // 浅色也必须保留透明度：alpha=1f 会把 textureBlur 的结果完全盖住
            val base = if (!dark) 0.78f else 0.72f
            val alpha = if (liquid) base - 0.12f else base
            color.copy(alpha = alpha)
        } else color

    /** 悬浮层的描边高光。 */
    @Composable
    fun edgeHighlight(): Color {
        val alpha = when {
            backdrop == null -> 0f
            liquid && dark -> 0.16f
            liquid -> 0.78f
            dark -> 0.10f
            else -> 0.62f
        }
        return Color.White.copy(alpha = alpha)
    }
}
