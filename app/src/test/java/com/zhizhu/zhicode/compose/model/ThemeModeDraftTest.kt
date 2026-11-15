package com.zhizhu.zhicode.compose.model

import com.zhizhu.zhicode.compose.data.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `SettingsDraft` 与 `ThemeSettings` 这两个"真假源"之间的一致性。
 *
 * ## 为什么这条值得单独一个测试类
 *
 * 深浅色**有两份存储**：`WorkspaceUiState.themeMode`（缓存）与
 * `themeSettings.mode`（真源），此外 `SettingsDraft` 里**还有一份**。
 * 用户报的现象是：
 *
 * > 在主题页选好深浅 → 退出 → 滑动几下就恢复原来的颜色，
 * > 并且再进设置页看到的是**旧值**。
 *
 * 根因就在这三份之间：`SettingsDraft.applyTo()` 会把 draft 里的 `themeMode`
 * 写回 state（连同 `themeSettings.mode`）。而改主题的那两个入口
 * （`updateThemeSettings` / `cycleThemeMode`）原先**只改前两份、不动 draft**。
 * 于是只要之后发生任意一次 `applySettingsDraft()`（设置页里点个开关就会走），
 * 旧值就把用户刚选的新值覆盖回去 —— 而且是真的写回 state，所以设置页也显示旧值。
 *
 * 这里钉住两件事：
 * 1. `applyTo` 确实会覆盖主题（这是它的职责，不能悄悄改掉）；
 * 2. `SettingsDraft.from(state)` 取的是**当下**的 `state.themeMode`（所以新建 draft 是对的）。
 *
 * 真正防回归的是 `WorkspaceViewModel` 里那两处同步 `settingsDraft` 的写法 ——
 * 它们没有纯函数形态，只能靠 review + 真机验证。这个测试至少保证
 * "draft 一旦是旧的，`applyTo` 就会把它盖回去"这个前提被写清楚。
 */
class ThemeModeDraftTest {

    @Test
    fun `applyTo 会用 draft 里的深浅覆盖 state`() {
        val state = WorkspaceUiState(
            themeMode = ThemeMode.DARK,
            themeSettings = ThemeSettings(mode = ThemeMode.DARK),
        )
        // 一个**旧**的 draft（用户切到深色之前抓的）
        val staleDraft = SettingsDraft.from(state).copy(themeMode = ThemeMode.LIGHT)

        val applied = staleDraft.applyTo(state, keepDraft = true)

        // 这就是"被盖回去"的现场：state 里的深色变成了浅色，
        // 而且 themeSettings.mode 一起变了 —— 所以界面和设置页都显示旧值。
        assertEquals(ThemeMode.LIGHT, applied.themeMode)
        assertEquals(ThemeMode.LIGHT, applied.themeSettings.mode)
    }

    @Test
    fun `from 取的是当下值所以新建的 draft 不会覆盖`() {
        val state = WorkspaceUiState(
            themeMode = ThemeMode.DARK,
            themeSettings = ThemeSettings(mode = ThemeMode.DARK),
        )
        // 从最新 state 抓 draft，再 apply 回去：值必须原样保留。
        val applied = SettingsDraft.from(state).applyTo(state, keepDraft = true)
        assertEquals(ThemeMode.DARK, applied.themeMode)
        assertEquals(ThemeMode.DARK, applied.themeSettings.mode)
    }

    @Test
    fun `没有 draft 时 applyTo 仍然自洽`() {
        // `settingsDraft` 为 null 是常态（用户没打开过设置页）。
        // 这条防的是"为了修上面那个 bug 而在 ViewModel 里对 null 硬解包"。
        val state = WorkspaceUiState(themeMode = ThemeMode.SYSTEM)
        val applied = SettingsDraft.from(state).applyTo(state, keepDraft = false)
        assertEquals(ThemeMode.SYSTEM, applied.themeMode)
        assertEquals(null, applied.settingsDraft)
    }
}
