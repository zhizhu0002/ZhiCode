package com.zhizhu.zhicode.compose.theme

import com.zhizhu.zhicode.compose.model.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「模式 + 系统深浅 → 是否深色」的判定表。
 *
 * <h3>为什么值得单独测</h3>
 *
 * 这一处判错过一次，而且是**最不容易发现的那种错**：沙箱页原先读的是
 * `isSystemInDarkTheme()` 而不是应用设置，于是「设置里选浅色、系统是深色」时，
 * 主界面是浅色而沙箱页整片深色。默认主题是「跟随系统」，所以在开发机上永远
 * 看不出来 —— 只有用户把主题改成浅色（或深色）并且系统相反时才会出现。
 *
 * 现在深浅判定收在 [ZhiThemeMode] 一处（`ThemeConsistencyTest` 钉住这条），
 * 这里钉住它本身的三档语义。
 */
class ZhiThemeModeTest {

    @Test
    fun lightModeIsLightEvenWhenSystemIsDark() {
        assertFalse(ZhiThemeMode.resolve(ThemeMode.LIGHT, systemDark = true))
        assertFalse(ZhiThemeMode.resolve(ThemeMode.LIGHT, systemDark = false))
    }

    @Test
    fun darkModeIsDarkEvenWhenSystemIsLight() {
        assertTrue(ZhiThemeMode.resolve(ThemeMode.DARK, systemDark = false))
        assertTrue(ZhiThemeMode.resolve(ThemeMode.DARK, systemDark = true))
    }

    @Test
    fun systemModeFollowsTheSystem() {
        assertTrue(ZhiThemeMode.resolve(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(ZhiThemeMode.resolve(ThemeMode.SYSTEM, systemDark = false))
    }

    /**
     * 「应用设置优先于系统」是这个函数存在的全部意义。
     *
     * 少了它，两个方向都会有人"顺手简化"成直接读系统 —— 那正是沙箱页曾经的样子。
     */
    @Test
    fun explicitModeOverridesTheSystem() {
        val cases = ThemeMode.entries.filter { it != ThemeMode.SYSTEM }
        for (mode in cases) {
            val forced = ZhiThemeMode.resolve(mode, systemDark = !ZhiThemeMode.resolve(mode, systemDark = false))
            assertTrue(
                "选了 $mode 之后系统深浅不该改变结果",
                forced == ZhiThemeMode.resolve(mode, systemDark = false),
            )
        }
    }

    /** 每一档都必须有明确结果（`when` 穷尽），而且不能"都返回同一个值"。 */
    @Test
    fun everyModeIsCoveredAndTheyDiffer() {
        val byMode = ThemeMode.entries.associateWith { ZhiThemeMode.resolve(it, systemDark = false) }
        assertTrue("跟随系统在系统浅色时应判为浅色", byMode.getValue(ThemeMode.SYSTEM) == false)
        assertTrue("深色档必须判为深色", byMode.getValue(ThemeMode.DARK) == true)
        assertTrue("浅色档必须判为浅色", byMode.getValue(ThemeMode.LIGHT) == false)
        assertTrue("三档不能退化成同一个结果", byMode.values.toSet().size >= 2)
    }
}
