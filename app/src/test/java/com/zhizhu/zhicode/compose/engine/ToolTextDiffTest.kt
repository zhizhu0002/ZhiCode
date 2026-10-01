package com.zhizhu.zhicode.compose.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「这条工具输出该不该按 diff 着色」的断言。
 *
 * <h3>为什么值得单独测</h3>
 *
 * 判错的两个方向都很难看，而且都**不会编译失败**：
 *
 * - **该着色没着色**：写文件的输出是一坨没有颜色的等宽文本，＋/− 全靠肉眼找；
 * - **不该着色却着色**：写入**失败**时输出是一行错误文本
 *   （`Refusing to overwrite a file larger than 20 MiB: …`），
 *   若按工具名就着色，那一行会被画成 diff 的配色 —— 错误信息看起来像"文件内容"，
 *   比不着色更误导。
 *
 * 所以判据是**两个条件同时成立**：工具确实是写文件的，**而且**输出真的是 diff。
 */
class ToolTextDiffTest {

    /** 一份真实的 `UnifiedDiff.create` 产出（结构照抄字节码里的拼装顺序）。 */
    private val realDiff = """
        --- a/app/Main.kt
        +++ b/app/Main.kt
        @@ -1,7 +1,5 @@
         package com.zhizhu
        -import a.b
        -import a.c
        +import a.d
         
         fun main() {}
    """.trimIndent()

    @Test
    fun `写文件的 diff 输出要着色`() {
        for (tool in listOf("Write", "Edit", "MultiEdit", "Delete", "WRITE", "edit")) {
            assertTrue("$tool 的 diff 输出应当按 diff 渲染", ToolText.isFileDiff(tool, realDiff))
        }
    }

    @Test
    fun `非写入类工具一律不着色`() {
        for (tool in listOf("Read", "Grep", "Bash", "Glob", "GitStatus", null)) {
            assertFalse("$tool 的输出即便含 @@ 也不是 diff（那是文件内容）",
                ToolText.isFileDiff(tool, realDiff))
        }
    }

    @Test
    fun `写入失败的错误文本不着色`() {
        // 这是 WriteTool 真实的失败输出形状：没有 hunk 头、没有文件头
        val failure = "Refusing to overwrite a file larger than 20 MiB: /x/y.bin"
        assertFalse("写入失败的错误文本不能画成 diff 配色", ToolText.isFileDiff("Write", failure))
    }

    @Test
    fun `空输出不着色`() {
        assertFalse(ToolText.isFileDiff("Write", ""))
        assertFalse(ToolText.isFileDiff("Write", "\n\n"))
    }

    @Test
    fun `新建文件的 diff（差头是 dev null）也要着色`() {
        val created = """
            --- /dev/null
            +++ b/app/New.kt
            @@ -0,0 +1,2 @@
            +package com.zhizhu
            +fun x() {}
        """.trimIndent()
        assertTrue(ToolText.isFileDiff("Write", created))
    }

    @Test
    fun `删除文件的 diff 也要着色`() {
        val deleted = """
            --- a/app/Gone.kt
            +++ /dev/null
            @@ -1,2 +0,0 @@
            -package com.zhizhu
            -fun gone() {}
        """.trimIndent()
        assertTrue(ToolText.isFileDiff("Delete", deleted))
    }

    @Test
    fun `只有文件头也要着色（截断后可能只剩它）`() {
        val headerOnly = "--- a/x\n+++ b/x\n"
        assertTrue(ToolText.isFileDiff("Edit", headerOnly))
    }

    @Test
    fun `普通文本里出现加号减号不算 diff`() {
        val source = """
            package com.zhizhu
            + 这不是 diff，只是以加号开头的源码行
            - 也不是
        """.trimIndent()
        assertFalse("没有 hunk 头、也没有成对文件头，就不能当 diff",
            ToolText.isFileDiff("Write", source))
    }
}
