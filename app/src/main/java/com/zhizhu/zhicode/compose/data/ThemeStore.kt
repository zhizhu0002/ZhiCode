package com.zhizhu.zhicode.compose.data

import android.content.Context
import com.termux.app.zhicode.storage.ApiSettingsStore
import com.zhizhu.zhicode.compose.model.ThemeMode
import com.zhizhu.zhicode.compose.theme.THEME_PRESETS
import com.zhizhu.zhicode.compose.theme.UI_SCALE_DEFAULT
import com.zhizhu.zhicode.compose.theme.ZhiThemeMode
import com.zhizhu.zhicode.compose.theme.clampUiScale
import com.zhizhu.zhicode.compose.theme.presetById

/**
 * 聊天正文字体族。
 *
 * 与 rikkahub 的 `ChatFontFamily` 同名同义，但**去掉了它的 `CUSTOM` 依赖**：
 * 这里只放枚举，自定义字体的加载（`Typeface.createFromFile`）在 `ChatFontLoader` 里 ——
 * 枚举要能被非 Android 的调用点（主题落盘、单测）读，不该拖进 `android.graphics`。
 */
enum class ChatFontFamily(val label: String) {
    DEFAULT("默认"),
    SERIF("衬线"),
    MONOSPACE("等宽"),
    CUSTOM("自定义"),
}

/**
 * 主页标签栏（对话 / 终端 / 文件）摆在屏幕哪一侧。
 *
 * 单手握持时拇指够不到顶栏，所以底栏是很多人的实际偏好；但宽屏与平板下顶栏更省空间。
 * 两者都是真实需求，所以做成可选，而不是替用户决定。
 */
enum class TabBarPosition(val label: String) {
    TOP("顶栏"),
    BOTTOM("底栏"),
}

/**
 * 底部悬浮栏的形态。
 *
 * - [DEFAULT]：Miuix 的浮动卡片（当前形态）。
 * - [IOS]：iOS 那种**单条**胶囊，输入器与发送键内联在同一个圆角壳里。
 *
 * 只在 [ThemeSettings.floatingBar] 打开时有意义 —— 不悬浮时就没有"形态"可谈，
 * 那时两种取值都画成满宽贴边。
 */
enum class BottomBarStyle(val label: String) {
    DEFAULT("默认"),
    IOS("iOS 风格"),
}

/**
 * 主题与显示相关的全部设置（形状 + 落盘）。
 *
 * ## 为什么单独一个类，而不是继续塞进 `AppSettings`
 *
 * `AppSettings` 那一份是"设置页草稿"的数据源，而主题是**全局底座**：
 * 它在 `ZhiCodeApp` 组合的最外层就要用到，早于任何页面、也不经过
 * `SettingsDraft` 的生命周期。放在一起会让"打开设置→改一个值→未保存"
 * 与"立刻应用到全屏"这两件事纠缠。
 *
 * 另外它需要被**非 Compose** 的地方读到（沙箱悬浮窗的 `ZhiOverlayPalette`、
 * `EditorActivity`），而 `AppSettings` 只在 `WorkspaceUiState` 里存在。
 *
 * ## 每一项都必须真的生效
 *
 * 这个类里**没有**摆设开关。写进来之前先确认它有接入点：
 *
 * | 字段 | 接入点 |
 * | --- | --- |
 * | [mode] | `ZhiThemeMode.resolve` → `LocalZhiDark` |
 * | [dynamicColor] + [presetId] + [seedArgb] | `ZhiAppTheme` 走 `ThemeController` 的 Monet 模式 |
 * | [amoled] | 深色时背板与面板压成纯黑 |
 * | [blurEnabled] | `rememberGlass()` 关闭时**连 layerBackdrop 都不建** |
 * | [floatingBar] | `FloatingBottomShell` 关闭时满宽、去圆角与阴影 |
 * | [liquidGlass] | `Glass.surfaceColor` 的透明度与描边高光；经 [ThemeSettings.liquidGlassEffective] 受 [floatingBar] 门控 |
 * | [edgeSwipeBack] | `AppScaffold` 里 `entry(swipeDismiss = …)` |
 * | [uiScale] | `LocalDensity` 缩放 |
 * | [tabBarPosition] | `AppScaffold` 把标签栏摆顶部或底部 |
 * | [tabBarFloating] | 底栏形态下是否走悬浮胶囊容器 |
 * | [bottomBarStyle] | 底部悬浮栏的两种形态（Miuix 卡片 / iOS 单条胶囊） |
 * | [latexEnabled] | `ZhiMarkdown` 是否解析 `$…$` 等公式定界符 |
 * | [codeWrap] / [codeCollapse] / [codeLineNumbers] | `Markdown.CodeBlock` |
 *
 * ⚠️ 刻意**没有**「预测性返回手势」开关：Miuix 的 `NavDisplay` 把 predictive back
 * 做成了内置能力且没有关闭 API（核过 `NavDisplay.kt` 455–560 行），
 * 做成开关就是一个点了没用的假开关 —— 那是这个代码库反复警告过的反模式。
 */
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    /** 是否用 Monet（种子色生成整套色板）。关闭时沿用工程既有的固定色板。 */
    val dynamicColor: Boolean = false,
    val presetId: String = THEME_PRESETS.first().id,
    /** 自定义强调色（ARGB）。为空表示"用预设的种子色"。 */
    val customSeedArgb: Long? = null,
    /**
     * Monet 的调色板风格与颜色规范。
     *
     * <p>存的是 Miuix 枚举的 `name` 而不是枚举本身：这一层不该依赖 compose 侧的库类型
     * （它还要能被非 Compose 的调用点读）。非法值在 [ZhiAppTheme] 侧回落到默认。
     */
    val paletteStyle: String = "TonalSpot",
    val colorSpec: String = "Spec2021",
    val amoled: Boolean = false,
    val blurEnabled: Boolean = true,
    val floatingBar: Boolean = true,
    val liquidGlass: Boolean = true,
    val edgeSwipeBack: Boolean = true,
    val uiScale: Float = UI_SCALE_DEFAULT,
    /**
     * 聊天正文字体族。存枚举的 `name`，与 `paletteStyle` 同一个约定：
     * 这一层不该依赖 compose 的类型（它还要能被非 Compose 的调用点读）。
     *
     * 非法值在读取处回落到 [ChatFontFamily.DEFAULT] —— 老版本写下的名字读不到是常态。
     */
    val chatFont: String = ChatFontFamily.DEFAULT.name,
    /**
     * 自定义字体的**相对路径**（相对 `filesDir`），为空表示没有导入过。
     *
     * 只存相对路径不存绝对路径：绝对路径里的 `filesDir` 在重装后可能变，
     * 而 `filesDir` 本身是稳定的，相对路径跟着它一起稳定。
     */
    val customFontPath: String = "",
    /** 自定义字体的显示名（导入时从文件名取的），仅用于界面展示。 */
    val customFontName: String = "",
    /**
     * 聊天正文字号比例。
     *
     * 与 [uiScale] 是**两件事**，不要合并：
     * - [uiScale] 改的是整个界面的密度（`LocalDensity`），连按钮、间距、图标一起变；
     * - 这一项只改**阅读正文**的字号，用户想"字大一点但按钮别变大"时用它。
     */
    val chatFontScale: Float = 1.0f,
    // ---- 标签栏 ----
    /**
     * 主页标签栏位置。存枚举的 `name`，与 [chatFont] 同一约定（这一层不依赖 Compose）。
     *
     * 非法值在读取处回落到 [TabBarPosition.TOP] —— 老版本写下的名字读不到是常态。
     */
    val tabBarPosition: String = TabBarPosition.TOP.name,
    /** 底栏形态下是否悬浮。顶栏形态下这一项没有意义（顶栏永远贴边）。 */
    val tabBarFloating: Boolean = true,
    // ---- 底部悬浮栏形态 ----
    /** [BottomBarStyle] 的 `name`。只在 [floatingBar] 打开时生效。 */
    val bottomBarStyle: String = BottomBarStyle.DEFAULT.name,
    // ---- 聊天正文 ----
    /**
     * 是否用 LaTeX 渲染数学公式（`$…$` / `$$…$$` / `\(…\)` / `\[…\]`）。
     *
     * 默认**关闭**：解析与位图渲染都有成本，而绝大多数对话里没有公式。
     * 关闭时完全跳过解析，零开销 —— 不是在渲染后隐藏。
     */
    val latexEnabled: Boolean = false,
    // ---- 代码显示 ----
    /** 代码块长行是否自动换行。关闭时横向滚动（保持缩进对齐）。 */
    val codeWrap: Boolean = false,
    /** 代码块是否默认折叠（超过阈值行数时只显示前若干行）。 */
    val codeCollapse: Boolean = false,
    /** 代码块是否显示行号。 */
    val codeLineNumbers: Boolean = false,
) {
    val preset get() = presetById(presetId)

    /**
     * 实际使用的种子色。
     *
     * 自定义色优先于预设：用户在色轮上挑了一个颜色之后，再点预设色卡会**清掉**自定义
     * （那是"我改主意了"的语义），所以这里不需要处理两者冲突。
     */
    val seedArgb: Long get() = customSeedArgb ?: preset.seedArgb

    /** 纯黑只看深色模式：浅色下把背板压成黑色等于整屏不可读。 */
    val darkBackdropArgb: Long? get() = if (amoled) 0xFF000000L else preset.darkBackdropArgb

    /**
     * [tabBarPosition] 的枚举视图。
     *
     * 非法值（老版本写下的、被外部改坏的）回落到 [TabBarPosition.TOP]：
     * 顶栏是本工程一直以来的形态，回落它等于"什么都没变"，比抛异常或猜测都安全。
     */
    val tabBarPositionValue: TabBarPosition
        get() = TabBarPosition.entries.firstOrNull { it.name == tabBarPosition }
            ?: TabBarPosition.TOP

    /** [bottomBarStyle] 的枚举视图，非法值回落 [BottomBarStyle.DEFAULT]。 */
    val bottomBarStyleValue: BottomBarStyle
        get() = BottomBarStyle.entries.firstOrNull { it.name == bottomBarStyle }
            ?: BottomBarStyle.DEFAULT

    /**
     * 液态玻璃是否**真的**生效。
     *
     * 这是一个纯派生判断：液态玻璃描述的是"悬浮层更透"，而没有悬浮层时它就无所指。
     * 之前只在主题页把开关**隐藏**了，底层 `Glass.surfaceColor` 仍然照着 [liquidGlass]
     * 算 —— 于是"开关关了但效果还在"这种最难查的状态就出现了。
     * 这里把门控收进数据层，调用方只读这一个值。
     */
    val liquidGlassEffective: Boolean get() = liquidGlass && floatingBar
}

/**
 * 主题设置的读写。
 *
 * ## 老键兼容
 *
 * 深浅三态原本存在 `theme_mode` 这一个键里（见 `ApiSettingsStore.getThemeMode`）。
 * 那是**已经发出去的格式**，所以这里继续读它，而不是另起一个键然后让老用户的选择失效。
 * 其余字段都是新键，读不到就用默认值 —— 也就是"与今天完全一致"的行为。
 */
internal object ThemeStore {

    private const val PREFIX = "theme_"
    private const val DYNAMIC = PREFIX + "dynamic_color"
    private const val PRESET = PREFIX + "preset_id"
    private const val CUSTOM_SEED = PREFIX + "custom_seed_argb"
    private const val PALETTE = PREFIX + "palette_style"
    private const val SPEC = PREFIX + "color_spec"
    private const val AMOLED = PREFIX + "amoled"
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
    private const val BOTTOM_BAR_STYLE = PREFIX + "bottom_bar_style"
    private const val LATEX = PREFIX + "latex"
    private const val CODE_WRAP = PREFIX + "code_wrap"
    private const val CODE_COLLAPSE = PREFIX + "code_collapse"
    private const val CODE_LINE_NUMBERS = PREFIX + "code_line_numbers"

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
            amoled = prefs.getBoolean(AMOLED, false),
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
            bottomBarStyle = prefs.getString(BOTTOM_BAR_STYLE, "")?.takeIf { it.isNotBlank() }
                ?: BottomBarStyle.DEFAULT.name,
            latexEnabled = prefs.getBoolean(LATEX, false),
            codeWrap = prefs.getBoolean(CODE_WRAP, false),
            codeCollapse = prefs.getBoolean(CODE_COLLAPSE, false),
            codeLineNumbers = prefs.getBoolean(CODE_LINE_NUMBERS, false),
        )
    }

    /**
     * 落盘。
     *
     * 用 `commit()` 而不是 `apply()`：主题是"点一下就走"的那一类设置，
     * `apply()` 只保证内存可见，从最近任务里划掉进程时没人等那次后台写 ——
     * 这正是本工程此前"设置记不住"的根因（见 `ApiSettingsStore` 里那段说明）。
     * 调用方在 IO 线程上，几毫秒的写不值得省。
     */
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
                .putBoolean(AMOLED, settings.amoled)
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
                .putString(BOTTOM_BAR_STYLE, settings.bottomBarStyleValue.name)
                .putBoolean(LATEX, settings.latexEnabled)
                .putBoolean(CODE_WRAP, settings.codeWrap)
                .putBoolean(CODE_COLLAPSE, settings.codeCollapse)
                .putBoolean(CODE_LINE_NUMBERS, settings.codeLineNumbers)
                .commit()
        }
    }

    private const val PREFS_NAME = "zhi_theme"
}
