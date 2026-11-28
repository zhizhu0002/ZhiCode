package com.zhizhu.zhicode.compose.theme

import android.app.Activity
import android.os.Build
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

object ZhiThemeMode {

    /** 落盘的设置值 → 枚举。读不到、或值不认识（老版本写的、手改的）都当「跟随系统」。 */
    fun stored(context: Context): ThemeMode {
        val raw = runCatching {
            ApiSettingsStore.getThemeMode(context, ThemeMode.SYSTEM.name.lowercase())
        }.getOrNull() ?: return ThemeMode.SYSTEM
        return ThemeMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: ThemeMode.SYSTEM
    }

    /** 纯函数：模式 + 系统深浅 → 是否深色。 */
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

    fun appliedTo(activity: Activity, dark: Boolean) {
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        // Android 10+ otherwise inserts an opaque contrast scrim behind the gesture area.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }


    @Composable
    fun ApplySystemBars(dark: Boolean) {
        val context = LocalContext.current
        LaunchedEffect(dark) {
            (context.findActivity() ?: return@LaunchedEffect).let { appliedTo(it, dark) }
        }
    }
}

/** 从 Compose 的 `LocalContext` 找回宿主 Activity。 */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
