package com.zhizhu.zhicode.compose.theme

data class ThemePreset(
    /** 落盘用的稳定 id。 */
    val id: String,
    val name: String,
    /** 种子色 ARGB。 */
    val seedArgb: Long,
    /** 预设**自带的**深色背板色；null 表示沿用本工程既有的层级色。 */
    val darkBackdropArgb: Long? = null,
) {
    /** `#RRGGBB` 形式，给预览与调试页显示用。 */
    val hex: String get() = String.format("#%06X", seedArgb and 0xFFFFFF)
}

/** 预设表。 */
val THEME_PRESETS: List<ThemePreset> = listOf(
    ThemePreset("default", "默认蓝", 0xFF3482FF),
    ThemePreset("green", "森野绿", 0xFF36D167),
    ThemePreset("purple", "暮光紫", 0xFF7C4DFF),
    ThemePreset("amber", "暖阳橙", 0xFFFFB21D),
    ThemePreset("coral", "珊瑚红", 0xFFFF5722),
    ThemePreset("sakura", "樱花粉", 0xFFE91E63),
    // 纯黑：种子仍取青色（强调色需要一个颜色），但背板被钉成 #000000。
    ThemePreset("amoled", "纯黑", 0xFF00BCD4, darkBackdropArgb = 0xFF000000L),
)

/** 找不到就回落第一个（`default`），**不抛异常**：老版本写下的 id 读不到是常态。 */
fun presetById(id: String?): ThemePreset =
    THEME_PRESETS.firstOrNull { it.id == id } ?: THEME_PRESETS.first()

/** 界面缩放的合法区间。 */
const val UI_SCALE_MIN = 0.85f
const val UI_SCALE_MAX = 1.15f

/** 默认缩放 —— 1.0 表示"就是现在这样"。 */
const val UI_SCALE_DEFAULT = 1.0f

/** 夹取缩放值。 */
fun clampUiScale(value: Float): Float = when {
    value.isNaN() -> UI_SCALE_DEFAULT
    else -> value.coerceIn(UI_SCALE_MIN, UI_SCALE_MAX)
}

/** 缩放显示成百分比（`100%`），四舍五入到整数。 */
fun formatUiScale(value: Float): String {
    val pct = (clampUiScale(value) * 100f).toInt()
    return "$pct%"
}

// ---------------------------------------------------------------- 预设色卡配色

/** 一个预设色卡上要画的四种颜色（riKKa 的 `PresetThemeButton` 同款四象限）。 */
data class ThemeSwatch(
    /** 中心那个圆点：强调色本身。 */
    val primary: Long,
    /** 左上象限。 */
    val primaryContainer: Long,
    /** 右上象限（色相 +40°）。 */
    val secondaryContainer: Long,
    /** 右下象限（色相 −50°）。 */
    val tertiaryContainer: Long,
)

fun presetSwatch(seedArgb: Long, dark: Boolean): ThemeSwatch {
    val target = if (dark) 0xFF000000L else 0xFFFFFFFFL
    // 深色下容器色与种子色只差一档（否则在深色卡上糊成一团）；
    // 浅色下要拉得很开，否则彩色方块之间看不出区别。
    val nearMix = if (dark) 0.62f else 0.80f
    val midMix = if (dark) 0.70f else 0.86f
    val farMix = if (dark) 0.74f else 0.88f
    return ThemeSwatch(
        primary = seedArgb,
        primaryContainer = mixArgb(seedArgb, target, nearMix),
        secondaryContainer = mixArgb(shiftHue(seedArgb, 40f), target, midMix),
        tertiaryContainer = mixArgb(shiftHue(seedArgb, -50f), target, farMix),
    )
}

/** 两色按 [t] 线性混合（0 = [from]，1 = [to]），逐通道、保持 alpha。 */
internal fun mixArgb(from: Long, to: Long, t: Float): Long {
    val k = t.coerceIn(0f, 1f)
    fun ch(shift: Int): Long {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + (b - a) * k).toLong().coerceIn(0L, 255L)
    }
    return 0xFF000000L or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** 旋转色相（单位：度），保持饱和度与明度。 */
internal fun shiftHue(argb: Long, degrees: Float): Long {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    val d = max - min
    if (d == 0f) return argb // 灰阶没有色相可转
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h0 = when (max) {
        r -> ((g - b) / d + if (g < b) 6f else 0f)
        g -> ((b - r) / d + 2f)
        else -> ((r - g) / d + 4f)
    } * 60f
    val h = ((h0 + degrees) % 360f + 360f) % 360f
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun ch(v: Float): Long = ((v + m) * 255f).toLong().coerceIn(0L, 255L)
    return 0xFF000000L or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
}
