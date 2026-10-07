package com.zhizhu.zhicode.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.zhizhu.zhicode.compose.theme.ZhiColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 悬浮层的「玻璃」效果（背景虚化 + 半透明底），带运行时门控。
 *
 * ## 为什么要门控
 * Miuix 的模糊是 `miuix-blur` 里的 `Modifier.textureBlur`，底层是 **RuntimeShader**，
 * **Android 上要求 API 33+**（官方文档明确写了这是整个库的硬下限）。
 * 工程 `minSdk = 24`，所以低版本设备必须整条路径跳过，否则会崩。
 * 判定用 Miuix 自己的能力检查 `isRuntimeShaderSupported()`，
 * 而不是自己判断 `Build.VERSION.SDK_INT`，这样非 Android 目标也能正确返回 true。
 *
 * ## 用法（两步）
 * 1. 在**被压住的内容**那层调用 [capture]，让它把背景内容录进 layer；
 * 2. 在**悬浮层**上调用 [blur] + [surfaceColor]。
 *
 * 不支持模糊时两个方法都是恒等变换（[surfaceColor] 返回原色），
 * 所以调用点不需要写任何 `if`。
 */
@Composable
fun rememberGlass(): Glass {
    val isDark = ZhiColors.isDark()
    val supported = remember { isRuntimeShaderSupported() }
    // 关键：不支持时**连 layerBackdrop 都不创建**。
    // 只在 Modifier 上做恒等替换还不够 —— 光是把 blur 的代码路径执行到，
    // 在 API < 33 上就可能因为 RuntimeShader 缺失而崩。
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
            remember(backdrop, isDark) { Glass(backdrop, isDark) }
        }
    } else {
        remember(isDark) { Glass(null, isDark) }
    }
}

/** 由 [rememberGlass] 创建，集中承载门控逻辑。 */
class Glass internal constructor(internal val backdrop: LayerBackdrop?, private val dark: Boolean) {
    /** 当前设备/Runtime 是否支持 RuntimeShader 模糊。 */
    val supported: Boolean get() = backdrop != null

    /** 加在被模糊层压住的内容上：把它的绘制录进 layer。 */
    fun capture(modifier: Modifier): Modifier =
        if (backdrop != null) modifier.layerBackdrop(backdrop) else modifier

    /** 加在悬浮层上：对已捕获的背景做高斯模糊。 */
    fun blur(modifier: Modifier, shape: Shape, radius: Float = 24f): Modifier =
        if (backdrop != null) {
            modifier.textureBlur(backdrop = backdrop, shape = shape, blurRadius = radius)
        } else {
            modifier
        }

    /**
     * 悬浮层的底色。
     *
     * 能模糊时压低不透明度，否则模糊会被不透明底色完全挡住、白做；
     * 不支持时保持原来的不透明底色，观感与改动前一致。
     */
    @Composable
    fun surfaceColor(color: Color): Color =
        if (backdrop != null) {
            // 浅色模式的半透明白色会把内容和背板混成一层灰雾；让主题容器
            // 保持更高不透明度，模糊仍由 textureBlur 提供，不靠压低底色完成。
            val alpha = if (!dark) 1f else 0.72f
            color.copy(alpha = alpha)
        } else color
}
