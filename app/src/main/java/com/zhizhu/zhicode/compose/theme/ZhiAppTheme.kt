package com.zhizhu.zhicode.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.zhizhu.zhicode.compose.model.ThemeMode
import top.yukonga.miuix.kmp.theme.Colors
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
    val colors = if (isDark) darkColorScheme() else lightColorScheme().zhiLightNeutrals()
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

/**
 * 把 Miuix **浅色**方案里那几个"反了方向"的灰，收进本工程自己的中性阶。
 *
 * ## 症状与根因
 *
 * 用户在两轮反馈里说的都是同一件事：**浅色下很多组件是一块发灰的脏色**（"组件颜色都会
 * 发灰，好丑"），而深色下没有。逐个调用点换色修不掉它，因为两种灰**同时存在**：
 *
 * * 本工程自己的中性阶（[ZhiColors]）：背板 `#F1F1F3` → 面板 `#FFFFFF` →
 *   卡片 `#FAFAFC` → 卡内小块 `#F7F7F8`。**越靠近内容越亮**，与深色主题
 *   （背板 `#28282A` → 卡片 `#323235`）同一个方向：卡片是"浮起来"的。
 * * Miuix 0.9.4 浅色方案的容器色（从 `miuix-ui` 字节码里取出的真实值）：
 *
 * | Miuix 字段 | 浅色实际值 | 谁在取 |
 * | --- | --- | --- |
 * | `background` / `surface` / `surfaceVariant` / `surfaceContainer` | `#FFFFFF` | 白 |
 * | `surfaceContainerHigh` / `surfaceContainerHighest` | `#E8E8E8`（**同一个值**） | 分组容器、代码块、次级 Card |
 * | `secondary` | `#E6E6E6` | 次级填充 |
 * | `secondaryVariant` / `secondaryContainer` | `#F0F0F0` | 次级容器 |
 * | `onBackgroundVariant` | `#8C93B0` | 弱化文字/图标 |
 *
 * `#E6E6E6`~`#E8E8E8` 比本工程的**背板** `#F1F1F3` 还暗一档，于是浅色下凡是走
 * Miuix 容器色的地方（弹窗分组、Markdown 代码块、chip、次级按钮、各种 `Card`
 * 默认色）都成了**比背板更暗的灰块** —— 方向反了，看起来就是"脏"。
 *
 * ## 为什么只在入口重映射
 *
 * 逐个调用点换成 [ZhiColors] 也能修，但那有 200+ 个取值点，而且 Miuix **组件内部**
 * 取的色根本改不到（`CardDefaults` / `Switch` / `Slider` / 标签栏都读主题色板）。
 * 在这一个入口重映射则调用点一行都不用动，Miuix 组件内部也一起跟着变。
 *
 * ## 只改浅色
 *
 * 深色方案这几个字段本来就是 `#242424` 一族、压在本工程深色面板上没有"脏"的问题，
 * 用户反馈里也只提浅色。改深色只会引入未验证的风险，所以深色**原样透传**。
 *
 * ## 刻意不动的字段
 *
 * * `background`：已由 [MiuixTheme] 调用点覆写成 [ZhiColors.backdrop]，再改就是两份来源。
 * * `onSurface*` / `outline` / `dividerLine` / `disabled*`：这些是**文字与描边**色，
 *   不是灰块；Miuix 浅色给的值本身可用（`onSurface` 纯黑、`onSurfaceVariantSummary` 60% 黑）。
 * * `primary*` / `error*`：语义色，与"发灰"无关。
 */
private fun Colors.zhiLightNeutrals(): Colors = copy(
    // 原来是同一个 #E8E8E8：分组容器换到卡片档，代码块/更高强调换到卡内档。
    surfaceContainerHigh = ZhiColors.CardLight,
    surfaceContainerHighest = ZhiColors.CardInnerLight,
    // 原来是 #E6E6E6 / #F0F0F0 的实心灰。
    secondary = ZhiColors.SoftFillLight,
    secondaryVariant = ZhiColors.SoftFillLight,
    secondaryContainer = ZhiColors.SoftFillLight,
    // 原来是蓝灰 #8C93B0（整个浅色方案里唯一的彩色中性色）。
    onBackgroundVariant = ZhiColors.MutedLight,
)
