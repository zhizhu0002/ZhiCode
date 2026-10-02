package com.zhizhu.zhicode.compose.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模型面板里 API 切换 tab 栏的下标与可见性。
 *
 * 这两样以前会内联在组合函数里（测不到），而它们都有真会出错的边界：
 *
 * - `indexOfFirst` 找不到时返回 **-1**。把 -1 传给 Miuix 的 `TabRow` 不会有任何 tab
 *   被选中，界面上看起来就是"控件坏了"，而不是"数据不对"。
 * - tab 栏只有**一条**记录时是死控件（点了不会切到任何地方），必须不画。
 */
class ModelPickerProfileTabsTest {

    private fun picker(
        profiles: List<ModelProfileTab>,
        activeId: String,
    ) = ModelPickerState(
        profileName = "x",
        currentModel = "m",
        profiles = profiles,
        activeProfileId = activeId,
    )

    private val three = listOf(
        ModelProfileTab("a", "DeepSeek"),
        ModelProfileTab("b", "Qwen"),
        ModelProfileTab("c", "本地"),
    )

    @Test
    fun `下标跟随当前生效的那条`() {
        assertEquals(1, picker(three, "b").activeProfileIndex)
        assertEquals(2, picker(three, "c").activeProfileIndex)
    }

    @Test
    fun `找不到匹配时回落到 0 而不是 -1`() {
        // activeId 与列表不同步是会真实发生的：配置文件被外部改过、或者刚删掉那条。
        // 回落到 0 至少让第一个 tab 选中（看得见），-1 则是整条栏一个都不选中。
        assertEquals(0, picker(three, "不存在的 id").activeProfileIndex)
        assertEquals(0, picker(three, "").activeProfileIndex)
    }

    @Test
    fun `空列表也回落到 0`() {
        assertEquals(0, picker(emptyList(), "a").activeProfileIndex)
    }

    @Test
    fun `重复 id 时取第一条`() {
        // 存储层理论上不允许重复 id，但真出现了也不能让下标变成 1 之后又跳回 0。
        val duplicated = listOf(ModelProfileTab("a", "一"), ModelProfileTab("a", "二"))
        assertEquals(0, picker(duplicated, "a").activeProfileIndex)
    }

    @Test
    fun `只有一条时不显示 tab 栏`() {
        // 死控件：只有一条记录时点了不会切到任何别的地方。
        assertFalse(picker(listOf(ModelProfileTab("a", "DeepSeek")), "a").showProfileTabs)
        assertFalse(picker(emptyList(), "").showProfileTabs)
    }

    @Test
    fun `两条及以上才显示 tab 栏`() {
        assertTrue(picker(three, "a").showProfileTabs)
        assertTrue(
            picker(
                listOf(ModelProfileTab("a", "一"), ModelProfileTab("b", "二")),
                "b",
            ).showProfileTabs,
        )
    }
}
