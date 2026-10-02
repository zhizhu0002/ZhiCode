package com.zhizhu.zhicode.compose.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * `SkillImport` 里**纯函数**的行为：地址判别、zip 分流、有界读取、zip 解包。
 *
 * 为什么这些必须有 JVM 单测而不是只靠源码断言：它们全是"不设限就出事"的地方，
 * 而且出的事**不会编译失败**——
 * - 有界读取写成 `readBytes()` 再判长度：功能完全正常，只是限制形同虚设
 *   （内存已经在判定之前被吃光）；
 * - zip 条目数/单条大小的上限被删：正常使用一行都看不出来，
 *   只有遇到 zip bomb 才会炸；
 * - `looksLikeZip` / `looksLikeText` 判错：会把二进制塞进内容框，
 *   用户看到的是一屏乱码，而不是一条"这不是文本"的提示。
 *
 * 所以这里真的拿 byte[] 去跑，断言的是**行为**（抛不抛、抛什么、解出什么），
 * 不是源码里有没有那行字。
 */
class SkillImportTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val skillMd = """
        |---
        |name: pdf-tools
        |description: 处理 PDF
        |---
        |
        |正文
    """.trimMargin()

    // ---------- 地址判别 ----------

    @Test
    fun `只接受 http 与 https`() {
        assertTrue(SkillImport.isHttpUrl("https://example.com/SKILL.md"))
        assertTrue(SkillImport.isHttpUrl("http://example.com/a.zip"))
        assertTrue(SkillImport.isHttpUrl("  HTTPS://example.com/a.zip  "))
        // file: 与 content: 会让"从 URL 导入"变成一条任意本地读路径，必须挡住。
        assertFalse(SkillImport.isHttpUrl("file:///etc/passwd"))
        assertFalse(SkillImport.isHttpUrl("content://media/external/file/1"))
        assertFalse(SkillImport.isHttpUrl("javascript:alert(1)"))
        assertFalse(SkillImport.isHttpUrl(""))
    }

    @Test
    fun `非 http 地址下载时报错而不是去连`() {
        val result = SkillImport.download("file:///etc/passwd")
        assertTrue(result.isFailure)
        assertTrue(
            "错误信息应当说明只支持 http(s)，实际：" + result.exceptionOrNull()?.message,
            result.exceptionOrNull()?.message.orEmpty().contains("http"),
        )
    }

    // ---------- 二进制判别 ----------

    @Test
    fun `zip 魔数只认 PK 开头`() {
        assertTrue(SkillImport.looksLikeZip(zipOf("SKILL.md" to skillMd)))
        assertFalse(SkillImport.looksLikeZip(skillMd.toByteArray(Charsets.UTF_8)))
        assertFalse(SkillImport.looksLikeZip(bytes(0x50)))
    }

    @Test
    fun `含 NUL 的字节不算文本`() {
        assertTrue(SkillImport.looksLikeText(skillMd.toByteArray(Charsets.UTF_8)))
        assertTrue(SkillImport.looksLikeText(ByteArray(0)))
        // 一个 PNG 头就足够说明问题（真图片里必然有 NUL）。
        assertFalse(SkillImport.looksLikeText(bytes(0x89, 0x50, 0x4E, 0x47, 0x00, 0x1A)))
    }

    // ---------- 有界读取 ----------

    @Test
    fun `限内读取返回全部内容`() {
        val data = ByteArray(1024) { 7 }
        val read = SkillImport.readAllBounded(ByteArrayInputStream(data), limit = 1024)
        assertEquals(1024, read.size)
    }

    @Test
    fun `超限立刻抛错`() {
        val data = ByteArray(4096) { 1 }
        try {
            SkillImport.readAllBounded(ByteArrayInputStream(data), limit = 1024)
            fail("超过上限时必须抛错，否则限制形同虚设")
        } catch (e: IllegalStateException) {
            assertTrue("错误信息要带上上限，实际：" + e.message, e.message.orEmpty().contains("上限"))
        }
    }

    @Test
    fun `恰好等于上限仍然可以读`() {
        // 边界：`>` 与 `>=` 的差别会变成"正好 2MB 的文件读不了"这种莫名其妙的失败。
        val read = SkillImport.readAllBounded(ByteArrayInputStream(ByteArray(512)), limit = 512)
        assertEquals(512, read.size)
    }

    // ---------- zip 解包 ----------

    @Test
    fun `认任意深度的 SKILL_md`() {
        // 仓库压缩包的第一层永远是 repo-main/，子目录也必须认。
        val zip = zipOf(
            "repo-main/skill-a/SKILL.md" to skillMd,
            "repo-main/skill-a/reference/api.md" to "参考",
            "repo-main/readme.txt" to "无关文件",
        )
        val found = SkillImport.skillsFromZip(zip)
        assertEquals(1, found.size)
        assertEquals("pdf-tools", found.first().name)
        assertTrue(found.first().content.contains("正文"))
    }

    @Test
    fun `一包多技能时全部解出`() {
        val zip = zipOf(
            "skills/a/SKILL.md" to "---\nname: a\n---\n",
            "skills/b/SKILL.md" to "---\nname: b\n---\n",
        )
        assertEquals(listOf("a", "b"), SkillImport.skillsFromZip(zip).map { it.name })
    }

    @Test
    fun `没有 SKILL_md 时给出明确原因`() {
        val zip = zipOf("readme.md" to "什么都没有")
        try {
            SkillImport.skillsFromZip(zip)
            fail("包里没有 SKILL.md 时必须报错，而不是返回空列表让调用方以为导入成功")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("SKILL.md"))
        }
    }

    @Test
    fun `SKILL_md 缺少 name 字段时拒绝`() {
        // 猜一个目录名会建出用户没打算建的东西，所以这里必须**拒绝**并指出是哪个条目。
        val zip = zipOf("a/SKILL.md" to "---\ndescription: 没有名字\n---\n")
        try {
            SkillImport.skillsFromZip(zip)
            fail("frontmatter 里没有 name 时必须报错")
        } catch (e: IllegalStateException) {
            assertTrue("错误信息要指出是哪个条目，实际：" + e.message, e.message.orEmpty().contains("a/SKILL.md"))
        }
    }

    @Test
    fun `不是 zip 的输入被拒绝`() {
        try {
            SkillImport.skillsFromZip("这不是压缩包".toByteArray(Charsets.UTF_8))
            fail("非 zip 输入必须报错")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("zip"))
        }
    }
}
