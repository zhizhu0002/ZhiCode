package com.zhizhu.zhicode.compose.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模型选择面板的搜索过滤。
 *
 * <p>这段逻辑以前内联在组合函数里（测不到）。它有两个真会出错的地方：
 * 空搜索必须返回**全部**（返回空的话一打开面板就是"没有匹配"），
 * 以及搜索串必须**大小写不敏感**、且 id 与显示名**两边**都能命中。
 */
class ModelPickerSearchTest {

    private fun picker(search: String) = ModelPickerState(
        profileName = "DeepSeek",
        currentModel = "deepseek-flash",
        search = search,
        models = listOf(
            ModelOption(id = "deepseek-flash", displayName = "DeepSeek-V4.1-Flash"),
            ModelOption(id = "deepseek-v4-pro", displayName = "DeepSeek-V4-Pro"),
            ModelOption(id = "qwen-max", displayName = "Qwen Max"),
        ),
    )

    @Test
    fun `空搜索返回全部`() {
        // 一打开面板搜索框是空的，这时必须看到完整目录。
        assertEquals(3, picker("").visibleModels.size)
    }

    @Test
    fun `只有空白的搜索也返回全部`() {
        // 用户误敲一个空格不该把目录清空 —— 而且"看不见任何模型"很难与
        // "目录没拉到"区分开。
        assertEquals(3, picker("   ").visibleModels.size)
        assertEquals(3, picker("\t").visibleModels.size)
    }

    @Test
    fun `按 id 的子串匹配`() {
        val hit = picker("v4-pro").visibleModels
        assertEquals(1, hit.size)
        assertEquals("deepseek-v4-pro", hit[0].id)
    }

    @Test
    fun `按显示名匹配`() {
        // 显示名里没有 "v4-pro" 这种片段时也要能命中（"Qwen" 只在显示名里出现）
        val hit = picker("Qwen").visibleModels
        assertEquals(1, hit.size)
        assertEquals("qwen-max", hit[0].id)
    }

    @Test
    fun `大小写不敏感`() {
        assertEquals(2, picker("DEEPSEEK").visibleModels.size)
        assertEquals(2, picker("deepseek").visibleModels.size)
        assertEquals(2, picker("DeEpSeEk").visibleModels.size)
    }

    @Test
    fun `搜索串前后空白被忽略`() {
        assertEquals(1, picker("  qwen  ").visibleModels.size)
    }

    @Test
    fun `匹配到多条时全部返回`() {
        assertEquals(2, picker("deepseek").visibleModels.size)
    }

    @Test
    fun `搜不到时返回空列表`() {
        // 界面靠这个空列表显示"没有匹配「…」的模型"，而不是留一片空白
        // （空白与"还在加载"看起来一模一样）。
        assertTrue(picker("gpt-4").visibleModels.isEmpty())
    }

    @Test
    fun `子串匹配而不是模糊匹配`() {
        // 刻意不做子序列匹配："dsp" 不是任何 id 的**连续**子串，所以不该命中。
        // 模糊匹配会让用户以为目录里没有某个模型（字符顺序不同就搜不到）。
        assertTrue(picker("dsp").visibleModels.isEmpty())
        // 但连续子串要命中
        assertEquals(2, picker("deepseek").visibleModels.size)
    }

    @Test
    fun `搜索不影响要用的模型名`() {
        // 这是把 search 与 query 分成两个字段的**核心保证**：
        // 搜索时敲的半个词绝不能变成"要用的模型名"，否则点「使用模型」
        // 就把一个不存在的名字写进了配置（服务端随后 400）。
        val state = picker("deep").copy(query = "deepseek-v4-pro")
        assertEquals("deepseek-v4-pro", state.query)
        assertEquals(2, state.visibleModels.size)
    }
}
