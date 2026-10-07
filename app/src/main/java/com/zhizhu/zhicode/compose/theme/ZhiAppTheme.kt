package com.zhizhu.zhicode.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.zhizhu.zhicode.compose.model.ThemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 应用的唯一主题栈：深浅判定 + Miuix 配色 + 文字字阶。
 *
 * ## 为什么要从 `ZhiCodeApp` 里抽出来
 *
 * 这一段原先长在 `ZhiCodeApp` 内部，于是**只有主界面那一棵组合树**在主题里。
 * 第二个 Activity（先有沙箱页、后有编辑器页）各自抄了一份 —— 抄漏一处就是
 * 「设置里选浅色、这一屏还是深色」那类问题；沙箱页当初正是这么错的
 * （它读的是 `isSystemInDarkTheme()` 而不是应用设置）。
 *
 * 现在只有这一个入口，两个 Activity（[com.zhizhu.zhicode.compose.MainActivity] 与
 * [com.zhizhu.zhicode.compose.editor.EditorActivity]）都调它。
 *
 * ## 三件事的顺序不能换
 *
 * 1. 先算深浅（[ZhiThemeMode.rememberCurrentDark] —— 全局唯一的判定处）；
 * 2. 再 `CompositionLocalProvider(LocalZhiDark)`。**必须早于任何 `ZhiColors` 取值**：
 *    那些层级色靠 `LocalZhiDark` 判断深浅，读早了会拿到默认值 `false`（浅色），
 *    于是深色模式下背板被设成浅灰、弹窗整片发白；
 * 3. 最后 `MiuixTheme`。`background` 被换成 `ZhiColors.backdrop()`：Miuix 浅色方案里
 *    background / surface / surfaceContainer 都是 `#FFFFFF`，纯白铺满后板块分不出来。
 *
 * `textStyles` 也必须在这里给：Miuix 组件读的是主题样式，逐个传 fontSize 够不到它们。
 */
@Composable
fun ZhiAppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val isDark = ZhiThemeMode.rememberCurrentDark(mode)
    val colors = if (isDark) darkColorScheme() else lightColorScheme()
    // 应用内换主题时（设置页/顶栏那个循环按钮）状态栏图标要跟着换：onResume 那条路径
    // 只在回到前台时补，期间图标会一直是相反的明暗。
    ZhiThemeMode.ApplySystemBars(isDark)

    CompositionLocalProvider(LocalZhiDark provides isDark) {
        MiuixTheme(
            colors = colors.copy(background = ZhiColors.backdrop()),
            textStyles = zhiTextStyles(),
            content = content,
        )
    }
}
