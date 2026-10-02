package com.zhizhu.zhicode.compose.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `SkillStore` 里两个**纯函数**的行为：文件名规则、从内容里解析技能名。
 *
 * 这里只测不依赖文件系统的部分 —— 路径逃逸那道防线（`fileInSkillDir`）需要真实
 * 目录才能验证，由 `app/tests/SkillsPageStructureTest.java` 从源码层面守。
 *
 * 规则本身单独测的理由：它**两处**在用（界面上的即时校验、写入前的兜底），
 * 不一致时会变成"界面说不行、其实能写进去"或者反过来。
 */
class SkillStoreNameTest {

    @Test
    fun `普通文件名合法`() {
        assertTrue(SkillStore.isValidFileName("SKILL.md"))
        assertTrue(SkillStore.isValidFileName("reference.md"))
        assertTrue(SkillStore.isValidFileName("a-b_c.1"))
    }

    @Test
    fun `点与双点被拒绝`() {
        // 正则里的 `.` 是字面点，所以 `..` 本身能通过 matches，
        // 必须靠额外判断挡住 —— 这一条就是防它被删掉。
        assertFalse(SkillStore.isValidFileName("."))
        assertFalse(SkillStore.isValidFileName(".."))
    }

    @Test
    fun `路径分隔符被拒绝`() {
        assertFalse(SkillStore.isValidFileName("../secret"))
        assertFalse(SkillStore.isValidFileName("a/b"))
        assertFalse(SkillStore.isValidFileName("a\\b"))
        assertFalse(SkillStore.isValidFileName("/etc/passwd"))
    }

    @Test
    fun `空名与超长名被拒绝`() {
        assertFalse(SkillStore.isValidFileName(""))
        assertFalse(SkillStore.isValidFileName("a".repeat(65)))
        assertTrue(SkillStore.isValidFileName("a".repeat(64)))
    }

    @Test
    fun `从内容里解析出名字`() {
        val content = """
            |---
            |name: my-skill
            |description: 说明
            |---
            |
            |正文
        """.trimMargin()
        assertEquals("my-skill", SkillStore.nameFromContent(content))
    }

    @Test
    fun `带引号的名字也认`() {
        // 占位符里示范的就是带引号写法，用户照抄时不能被判成非法。
        assertEquals("my-skill", SkillStore.nameFromContent("---\nname: \"my-skill\"\n---\n"))
        assertEquals("my-skill", SkillStore.nameFromContent("---\nname: 'my-skill'\n---\n"))
    }

    @Test
    fun `正文里的 name 不算数`() {
        // 只在 frontmatter 块里找。全文扫描会把正文里的示例当成技能名，
        // 于是目录名和内容对不上，而用户完全看不出为什么。
        val content = """
            |---
            |description: 没有 name
            |---
            |
            |示例：
            |name: 这是正文里的示例
        """.trimMargin()
        assertNull(SkillStore.nameFromContent(content))
    }

    @Test
    fun `没有 frontmatter 时返回空`() {
        assertNull(SkillStore.nameFromContent("# 只有正文\n"))
        assertNull(SkillStore.nameFromContent(""))
        // 只有开头的分隔线、没有结束的那条：不是一个完整的元数据块。
        assertNull(SkillStore.nameFromContent("---\nname: x\n"))
    }

    @Test
    fun `空名字不算解析成功`() {
        assertNull(SkillStore.nameFromContent("---\nname:\n---\n"))
        assertNull(SkillStore.nameFromContent("---\nname:   \n---\n"))
    }
}
