package com.zhizhu.zhicode.compose.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * `McpStore.parseImport` 的行为：导入 JSON 配置时的两条判定。
 *
 * 为什么必须有 JVM 单测而不是只靠源码断言：这里每一个判错都不会编译失败，
 * 而且症状都很隐蔽 ——
 * - **同名覆盖**：用户已经在本机改过那份配置，导入一次就被抹掉；
 * - **认不出也加进去**：既没有 `url` 也没有 `command` 的条目存下去也跑不起来，
 *   要到 Agent 真去调用时才炸，那时错误信息离用户很远；
 * - **传输类型判反**：把只有 `command` 的条目判成 HTTP，配好之后永远连不上。
 *
 * 解析与落盘是分开的（`parseImport` 不碰磁盘），所以这些用例真的跑得起来。
 */
class McpStoreImportTest {

    private fun parse(json: String, existing: List<String> = emptyList()) =
        McpStore.parseImport(json, existing)

    @Test
    fun `基本导入_url 型按 HTTP`() {
        val parsed = parse(
            """
            |{"mcpServers":{"web":{"url":"https://example.com/mcp"}}}
            """.trimMargin()
        )
        assertEquals(listOf("web"), parsed.added.map { it.name })
        assertEquals("http", parsed.added.first().type)
        assertEquals("https://example.com/mcp", parsed.added.first().url)
        assertTrue(parsed.skipped.isEmpty() && parsed.invalid.isEmpty())
    }

    @Test
    fun `显式 type 为 sse 时用 SSE`() {
        val parsed = parse(
            """{"mcpServers":{"a":{"url":"https://x/sse","type":"sse"}}}"""
        )
        assertEquals("sse", parsed.added.first().type)
    }

    @Test
    fun `只有 command 的判成 stdio`() {
        // 判反的话配好之后永远连不上 —— 而配置文件看起来完全正常。
        val parsed = parse(
            """{"mcpServers":{"local":{"command":"npx","args":["-y","some-server"]}}}"""
        )
        assertEquals("stdio", parsed.added.first().type)
        assertEquals(listOf("-y", "some-server"), parsed.added.first().args)
    }

    @Test
    fun `同名跳过不覆盖`() {
        // 静默覆盖等于丢数据：用户可能已经在本机改过那份配置。
        val parsed = parse(
            """{"mcpServers":{"Web":{"url":"https://new"}}}""",
            existing = listOf("web"),
        )
        assertTrue("同名必须跳过，不能加进去", parsed.added.isEmpty())
        assertEquals(listOf("Web"), parsed.skipped)
    }

    @Test
    fun `同一批里的大小写变体只加一次`() {
        val parsed = parse(
            """{"mcpServers":{"A":{"url":"https://1"},"a":{"url":"https://2"}}}"""
        )
        assertEquals(1, parsed.added.size)
        assertEquals(1, parsed.skipped.size)
    }

    @Test
    fun `既没有 url 也没有 command 的被拒绝`() {
        val parsed = parse(
            """{"mcpServers":{"broken":{"name":"看起来正常"},"ok":{"url":"https://x"}}}"""
        )
        assertEquals(listOf("ok"), parsed.added.map { it.name })
        assertEquals(listOf("broken"), parsed.invalid)
    }

    @Test
    fun `空白 url 也算没有`() {
        val parsed = parse("""{"mcpServers":{"a":{"url":"   "}} }""")
        assertTrue(parsed.added.isEmpty())
        assertEquals(listOf("a"), parsed.invalid)
    }

    @Test
    fun `坏 JSON 给出可读原因`() {
        try {
            parse("{这不是 JSON")
            fail("坏 JSON 必须报错，而不是静默导入 0 个")
        } catch (e: IllegalArgumentException) {
            assertTrue("错误信息要能看懂，实际：" + e.message, e.message.orEmpty().contains("JSON"))
        }
    }

    @Test
    fun `缺少 mcpServers 字段时明确报出来`() {
        try {
            parse("""{"servers":[]}""")
            fail("缺少 mcpServers 必须报错")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("mcpServers"))
        }
    }

    @Test
    fun `空的 mcpServers 被拒绝`() {
        try {
            parse("""{"mcpServers":{}}""")
            fail("空的 mcpServers 必须报错，否则界面上会出现一次「导入成功 0 个」")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("没有任何服务器"))
        }
    }

    @Test
    fun `空文本被拒绝`() {
        try {
            parse("   ")
            fail("空文本必须报错")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().isNotBlank())
        }
    }

    @Test
    fun `env 与 headers 原样带过去`() {
        val parsed = parse(
            """{"mcpServers":{"a":{"url":"https://x","headers":{"Authorization":"Bearer t"}}}}"""
        )
        assertEquals("Bearer t", parsed.added.first().headers.optString("Authorization"))
    }

    @Test
    fun `enabled 缺省为 true`() {
        val parsed = parse("""{"mcpServers":{"a":{"url":"https://x"}}}""")
        assertTrue("导入的条目默认启用（用户写下它就是要用它）", parsed.added.first().enabled)
    }

    @Test
    fun `显式停用的条目保持停用`() {
        val parsed = parse("""{"mcpServers":{"a":{"url":"https://x","enabled":false}}}""")
        assertTrue(parsed.added.isNotEmpty())
        assertTrue(!parsed.added.first().enabled)
    }
}
