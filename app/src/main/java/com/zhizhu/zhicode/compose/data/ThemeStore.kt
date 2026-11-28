package com.zhizhu.zhicode.compose.data

import android.content.Context
import com.termux.app.zhicode.storage.ApiSettingsStore
import com.zhizhu.zhicode.compose.model.ThemeMode
import com.zhizhu.zhicode.compose.theme.THEME_PRESETS
import com.zhizhu.zhicode.compose.theme.UI_SCALE_DEFAULT
import com.zhizhu.zhicode.compose.theme.ZhiThemeMode
import com.zhizhu.zhicode.compose.theme.clampUiScale
import com.zhizhu.zhicode.compose.theme.presetById

/** 聊天正文字体族。 */
enum class ChatFontFamily(val label: String) {
    DEFAULT("默认"),
    SERIF("衬线"),
    MONOSPACE("等宽"),
    CUSTOM("自定义"),
}

/** 主页标签栏（对话 / 终端 / 文件）摆在屏幕哪一侧。 */
enum class TabBarPosition(val label: String) {
    TOP("顶栏"),
    BOTTOM("底栏"),
}

/** 底部悬浮栏的形态。 */
enum class BottomBarStyle(val label: String) {
    DEFAULT("默认"),
    IOS("iOS 风格"),
}

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    /** 是否用 Monet（种子色生成整套色板）。关闭时沿用工程既有的固定色板。 */
    val dynamicColor: Boolean = false,
    val presetId: String = THEME_PRESETS.first().id,
    /** 自定义强调色（ARGB）。为空表示"用预设的种子色"。 */
    val customSeedArgb: Long? = null,
    val paletteStyle: String = "TonalSpot",
    val colorSpec: String = "Spec2021",
    val blurEnabled: Boolean = true,
    val floatingBar: Boolean = true,
    val liquidGlass: Boolean = true,
    val edgeSwipeBack: Boolean = true,
    /* 「预测性返回手势」字段已删除，理由见类头注释。 */
    val uiScale: Float = UI_SCALE_DEFAULT,
    val chatFont: String = ChatFontFamily.DEFAULT.name,
    /** 自定义字体的**相对路径**（相对 `filesDir`），为空表示没有导入过。 */
    val customFontPath: String = "",
    /** 自定义字体的显示名（导入时从文件名取的），仅用于界面展示。 */
    val customFontName: String = "",
    /** 聊天正文字号比例。 */
    val chatFontScale: Float = 1.0f,
    // ---- 标签栏 ----
    val tabBarPosition: String = TabBarPosition.TOP.name,
    /** 底栏形态下是否悬浮。顶栏形态下这一项没有意义（顶栏永远贴边）。 */
    val tabBarFloating: Boolean = true,
    /** 底部悬浮导航栏的玻璃模糊；仅在 tabBarFloating 与主题 blurEnabled 开启时生效。 */
    val floatingNavBlur: Boolean = true,
    // ---- 底部悬浮栏形态 ----
    /** [BottomBarStyle] 的 `name`。只在 [floatingBar] 打开时生效。 */
    val bottomBarStyle: String = BottomBarStyle.DEFAULT.name,
    // ---- 聊天正文 ----
    /** 是否用 LaTeX 渲染数学公式（`$…$` / `$$…$$` / `\(…\)` / `\[…\]`）。 */
    val latexEnabled: Boolean = false,
    // ---- 代码显示 ----
    /** 代码块长行是否自动换行。关闭时横向滚动（保持缩进对齐）。 */
    val codeWrap: Boolean = false,
    /** 代码块是否默认折叠（超过阈值行数时只显示前若干行）。 */
    val codeCollapse: Boolean = false,
    /** 代码块是否显示行号。 */
    val codeLineNumbers: Boolean = false,
    // ---- 界面主题组 ----
    /** 界面主题组 id（一个主题 = 一组 page.kt）。 */
    val pageSetId: String = "miuix",
    /** Mochi 材质：液态玻璃 + 滚动边缘模糊。 */
    val mochiGlass: Boolean = false,
    /** 顶部自定义品牌签名；空串时显示默认 "ZhiCode"。 */
    val topBrandSignature: String = "",
) {
    val resolvedTopBrand: String
        get() = topBrandSignature.trim().ifBlank { "ZhiCode" }

    val preset get() = presetById(presetId)

    /** 实际使用的种子色。 */
    val seedArgb: Long get() = customSeedArgb ?: preset.seedArgb

    /** 纯黑只看深色模式：浅色下把背板压成黑色等于整屏不可读。 */
    val darkBackdropArgb: Long? get() = preset.darkBackdropArgb

    /** [tabBarPosition] 的枚举视图。 */
    val tabBarPositionValue: TabBarPosition
        get() = TabBarPosition.entries.firstOrNull { it.name == tabBarPosition }
            ?: TabBarPosition.TOP

    /** [bottomBarStyle] 的枚举视图，非法值回落 [BottomBarStyle.DEFAULT]。 */
    val bottomBarStyleValue: BottomBarStyle
        get() = BottomBarStyle.entries.firstOrNull { it.name == bottomBarStyle }
            ?: BottomBarStyle.DEFAULT

    /** 液态玻璃是否**真的**生效。 */
    val liquidGlassEffective: Boolean get() = liquidGlass && floatingBar
}

internal object ThemeStore {

    private const val PREFIX = "theme_"
    private const val DYNAMIC = PREFIX + "dynamic_color"
    private const val PRESET = PREFIX + "preset_id"
    private const val CUSTOM_SEED = PREFIX + "custom_seed_argb"
    private const val PALETTE = PREFIX + "palette_style"
    private const val SPEC = PREFIX + "color_spec"
    private const val BLUR = PREFIX + "blur_enabled"
    private const val FLOATING_BAR = PREFIX + "floating_bar"
    private const val LIQUID_GLASS = PREFIX + "liquid_glass"
    private const val EDGE_SWIPE = PREFIX + "edge_swipe_back"
    private const val UI_SCALE = PREFIX + "ui_scale"
    private const val CHAT_FONT = PREFIX + "chat_font"
    private const val CHAT_FONT_PATH = PREFIX + "chat_font_path"
    private const val CHAT_FONT_NAME = PREFIX + "chat_font_name"
    private const val CHAT_FONT_SCALE = PREFIX + "chat_font_scale"
    // 新增键一律带前缀，与上面保持同一族命名：单看一个键名就能认出它属于主题。
    private const val TAB_BAR_POSITION = PREFIX + "tab_bar_position"
    private const val TAB_BAR_FLOATING = PREFIX + "tab_bar_floating"
    private const val FLOATING_NAV_BLUR = PREFIX + "floating_nav_blur"
    private const val BOTTOM_BAR_STYLE = PREFIX + "bottom_bar_style"
    private const val LATEX = PREFIX + "latex"
    private const val CODE_WRAP = PREFIX + "code_wrap"
    private const val CODE_COLLAPSE = PREFIX + "code_collapse"
    private const val CODE_LINE_NUMBERS = PREFIX + "code_line_numbers"

    private const val PAGE_SET = PREFIX + "page_set"

    /** Mochi 的材质（液态玻璃 / 滚动边缘模糊）开关。 */
    private const val MOCHI_GLASS = PREFIX + "mochi_glass"
    private const val TOP_BRAND_SIGNATURE = PREFIX + "top_brand_signature"

    /** 聊天字号比例的合法区间。与 `UI_SCALE_MIN/MAX` 分开：正文可以拉得比界面密度更开。 */
    const val CHAT_FONT_SCALE_MIN = 0.8f
    const val CHAT_FONT_SCALE_MAX = 1.6f

    /** 把任意输入夹进合法区间。NaN / 无限大回落默认值 1.0（而不是夹成某个边界）。 */
    fun clampChatFontScale(value: Float): Float = when {
        !value.isFinite() -> 1f
        else -> value.coerceIn(CHAT_FONT_SCALE_MIN, CHAT_FONT_SCALE_MAX)
    }

    fun read(context: Context): ThemeSettings {
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull() ?: return ThemeSettings()
        return ThemeSettings(
            // 深浅三态继续用**老键**（`ApiSettingsStore` 的 `theme_mode`）：
            // 那是已经发出去的格式，另起一个键等于让老用户的选择失效。
            mode = runCatching { ZhiThemeMode.stored(context) }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs.getBoolean(DYNAMIC, false),
            presetId = prefs.getString(PRESET, "")?.takeIf { it.isNotBlank() } ?: THEME_PRESETS.first().id,
            // 0 表示"没有自定义"（合法颜色的 ARGB 永远非 0）。
            customSeedArgb = prefs.getLong(CUSTOM_SEED, 0L).takeIf { it != 0L },
            paletteStyle = prefs.getString(PALETTE, "")?.takeIf { it.isNotBlank() } ?: "TonalSpot",
            colorSpec = prefs.getString(SPEC, "")?.takeIf { it.isNotBlank() } ?: "Spec2021",
            blurEnabled = prefs.getBoolean(BLUR, true),
            floatingBar = prefs.getBoolean(FLOATING_BAR, true),
            liquidGlass = prefs.getBoolean(LIQUID_GLASS, true),
            edgeSwipeBack = prefs.getBoolean(EDGE_SWIPE, true),
            uiScale = clampUiScale(prefs.getFloat(UI_SCALE, UI_SCALE_DEFAULT)),
            chatFont = prefs.getString(CHAT_FONT, "")?.takeIf { it.isNotBlank() }
                ?: ChatFontFamily.DEFAULT.name,
            customFontPath = prefs.getString(CHAT_FONT_PATH, "") ?: "",
            customFontName = prefs.getString(CHAT_FONT_NAME, "") ?: "",
            chatFontScale = clampChatFontScale(prefs.getFloat(CHAT_FONT_SCALE, 1f)),
            tabBarPosition = prefs.getString(TAB_BAR_POSITION, "")?.takeIf { it.isNotBlank() }
                ?: TabBarPosition.TOP.name,
            tabBarFloating = prefs.getBoolean(TAB_BAR_FLOATING, true),
            floatingNavBlur = prefs.getBoolean(FLOATING_NAV_BLUR, true),
            bottomBarStyle = prefs.getString(BOTTOM_BAR_STYLE, "")?.takeIf { it.isNotBlank() }
                ?: BottomBarStyle.DEFAULT.name,
            latexEnabled = prefs.getBoolean(LATEX, false),
            codeWrap = prefs.getBoolean(CODE_WRAP, false),
            codeCollapse = prefs.getBoolean(CODE_COLLAPSE, false),
            codeLineNumbers = prefs.getBoolean(CODE_LINE_NUMBERS, false),
            pageSetId = prefs.getString(PAGE_SET, "")?.takeIf { it.isNotBlank() } ?: "miuix",
            mochiGlass = prefs.getBoolean(MOCHI_GLASS, false),
            topBrandSignature = prefs.getString(TOP_BRAND_SIGNATURE, "")?.trim() ?: "",
        )
    }

    /** 落盘。 */
    fun write(context: Context, settings: ThemeSettings) {
        runCatching {
            // 深浅三态也回到老键上：`ZhiThemeMode.stored` 是它唯一的读方。
            ApiSettingsStore.setThemeMode(context, settings.mode.name.lowercase())
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(DYNAMIC, settings.dynamicColor)
                .putString(PRESET, settings.presetId)
                .putLong(CUSTOM_SEED, settings.customSeedArgb ?: 0L)
                .putString(PALETTE, settings.paletteStyle)
                .putString(SPEC, settings.colorSpec)
                .putBoolean(BLUR, settings.blurEnabled)
                .putBoolean(FLOATING_BAR, settings.floatingBar)
                .putBoolean(LIQUID_GLASS, settings.liquidGlass)
                .putBoolean(EDGE_SWIPE, settings.edgeSwipeBack)
                .putFloat(UI_SCALE, clampUiScale(settings.uiScale))
                .putString(CHAT_FONT, settings.chatFont)
                .putString(CHAT_FONT_PATH, settings.customFontPath)
                .putString(CHAT_FONT_NAME, settings.customFontName)
                .putFloat(CHAT_FONT_SCALE, clampChatFontScale(settings.chatFontScale))
                .putString(TAB_BAR_POSITION, settings.tabBarPositionValue.name)
                .putBoolean(TAB_BAR_FLOATING, settings.tabBarFloating)
                .putBoolean(FLOATING_NAV_BLUR, settings.floatingNavBlur)
                .putString(BOTTOM_BAR_STYLE, settings.bottomBarStyleValue.name)
                .putBoolean(LATEX, settings.latexEnabled)
                .putBoolean(CODE_WRAP, settings.codeWrap)
                .putBoolean(CODE_COLLAPSE, settings.codeCollapse)
                .putBoolean(CODE_LINE_NUMBERS, settings.codeLineNumbers)
                .putString(PAGE_SET, settings.pageSetId)
                .putBoolean(MOCHI_GLASS, settings.mochiGlass)
                .putString(TOP_BRAND_SIGNATURE, settings.topBrandSignature.trim())
                .commit()
        }
    }

    private const val PREFS_NAME = "zhi_theme"
}
