package com.zhizhu.zhicode.compose.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.termux.app.zhicode.storage.ApiSettingsStore
import com.zhizhu.zhicode.compose.model.ThemeMode

/**
 * 「现在是深色还是浅色」的**唯一**来源。
 *
 * <h3>为什么要收成一处</h3>
 *
 * 这个判断原先在三处各写了一份，而且三份**不一样**：
 *
 * | 位置 | 判据 |
 * | --- | --- |
 * | `ZhiCodeApp` | 应用设置 `ThemeMode`（对） |
 * | `MainActivity` 状态栏 | 应用设置 `ThemeMode`（对） |
 * | `SandboxBoard` 沙箱页 | `isSystemInDarkTheme()`（**错**） |
 *
 * 于是「设置里选浅色、系统是深色」时，主界面是浅色而沙箱页整片深色 ——
 * 用户的原话是「沙箱在浅色模式下仍有问题」。这类不一致的共同点是：
 * **每一处单独看都说得通，只是没人负责让它们相等**。
 *
 * 所以深/浅只在这里判一次，其余地方（Compose 用 [rememberCurrentDark]，
 * Activity 用 [appliedTo]）都是它的转发。`ThemeConsistencyTest` 钉住这条：
 * `theme/` 之外不许出现第二个 `isSystemInDarkTheme()`，也不许再手写
 * `ThemeMode.LIGHT -> false` 那种分支表。
 *
 * <h3>为什么读设置而不是读系统</h3>
 *
 * 本应用允许在设置里独立选浅色/深色。选浅色而系统是深色时，状态栏图标必须是
 * **深色**的，否则白图标压在白背板上完全看不见 —— 也就是「跟随应用主题」而不是
 * 「跟随系统」。
 */
object ZhiThemeMode {

    /** 落盘的设置值 → 枚举。读不到、或值不认识（老版本写的、手改的）都当「跟随系统」。 */
    fun stored(context: Context): ThemeMode {
        val raw = runCatching {
            ApiSettingsStore.getThemeMode(context, ThemeMode.SYSTEM.name.lowercase())
        }.getOrNull() ?: return ThemeMode.SYSTEM
        return ThemeMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: ThemeMode.SYSTEM
    }

    /**
     * 纯函数：模式 + 系统深浅 → 是否深色。
     *
     * 单独一个函数是为了能被单测钉住（[ZhiThemeMode] 的另外两半都要 Android 环境）。
     * 三种模式都必须有明确结果 —— 少一个分支的话 `when` 不穷尽、编译就过不去，
     * 但那正是这里想要的：**新增一档主题必须回来补这一处**。
     */
    fun resolve(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }

    /** 系统当前是否深色（不读应用设置）。只有本对象该调用它。 */
    @Composable
    fun systemDark(): Boolean = isSystemInDarkTheme()

    /** 应用当前是否深色 —— Compose 侧的统一入口。 */
    @Composable
    fun rememberCurrentDark(context: Context): Boolean =
        resolve(stored(context), systemDark())

    /** 应用当前是否深色 —— 已经拿到 [ThemeMode] 时用这个（例如界面状态里就有）。 */
    @Composable
    fun rememberCurrentDark(mode: ThemeMode): Boolean = resolve(mode, systemDark())

    /** 非 Compose 环境（`Activity` 的 `onCreate` / `onResume`）读系统深浅。 */
    fun systemDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /**
     * 把深浅落到**窗口**上：状态栏/导航栏透明 + 图标明暗。
     *
     * 两个 Activity（主界面、沙箱页）都调它，理由和上面一样：
     * 分头写的话「沙箱页的图标明暗没跟着主题」这种问题没人会发现 ——
     * 它只在浅色主题下才看得出来，而默认主题是跟随系统。
     *
     * ⚠️ 配合 `enableEdgeToEdge()` 使用：窗口这一层保持透明，背板由 Compose 画
     * （`ZhiColors.backdrop()`），状态栏区域才不会有一条与背板不同的色带。
     */
    fun appliedTo(activity: Activity, dark: Boolean) {
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
            // 只改图标明暗，**不**隐藏状态栏：用户明确要求「把状态栏显示出来」。
            // 这一行同时把旧版（Fullscreen 主题留下的）隐藏状态复位。
            show(WindowInsetsControllerCompat.BEHAVIOR_DEFAULT)
        }
    }

    /**
     * 在界面里跟着深浅实时应用系统栏。
     *
     * 为什么不能只在 `onResume` 里应用一次：用户**在应用内**换主题（设置页 / 顶栏那个
     * 循环按钮）之后并不会 resume —— 于是选浅色主题、系统是深色时，状态栏图标一直是
     * 白色的，压在白背板上完全看不见，要等下次回到前台才恢复。这正是「深浅色模式
     * 很不完善」里最容易被漏掉的一环：它只在"应用内切换 + 系统相反"时出现。
     */
    @Composable
    fun ApplySystemBars(dark: Boolean) {
        val context = LocalContext.current
        LaunchedEffect(dark) {
            (context.findActivity() ?: return@LaunchedEffect).let { appliedTo(it, dark) }
        }
    }
}

/**
 * 从 Compose 的 `LocalContext` 找回宿主 Activity。
 *
 * 它不一定是 Activity 本身：`LocalContext` 常见的是 `ContextThemeWrapper`，
 * 直接强转会拿到 `ClassCastException` 或者静默失效（更糟）。
 */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
