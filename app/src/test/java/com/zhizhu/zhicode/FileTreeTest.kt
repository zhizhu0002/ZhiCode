package com.zhizhu.zhicode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * [FileTree.deleteRecursive] 的回归测试。
 *
 * 这一格对应真机上真实发生过的事故：内置 Termux 已经装好、能正常用，但再点一次初始化就
 * 永远失败，报 `Cannot create staging prefix: …/usr-staging`。原因是暂存目录里
 * `Os.symlink` 建出的悬空链接从未被删过（`File.exists()` 跟随链接，悬空即 false），
 * 父目录因此永远非空，`mkdirs()` 于是返回 false，错误被报成「建不出来」。
 *
 * <h3>这里测了什么、没测什么</h3>
 * 测了：递归、跳过规则、「不跟随符号链接」这三条**共用代码** —— 单测在 JVM 上跑，
 * 用 NIO 的 `NOFOLLOW_LINKS` 当判定（JVM 有真实的符号链接，所以这不是桩测试）。
 * 没测：[FileTree.ANDROID_KIND] 那一支（`Os.lstat`）在 JVM 上执行不到，
 * 它的「不跟随」语义靠的是 lstat 本身，而不是这段代码。
 */
class FileTreeTest {

    /** JVM 上的判定：与真机的 `Os.lstat` 同语义（都不跟随链接）。 */
    private val jvmKind = FileTree.Kind { file ->
        val path = file.toPath()
        when {
            !Files.exists(path, LinkOption.NOFOLLOW_LINKS) -> FileTree.ABSENT
            Files.isSymbolicLink(path) -> FileTree.SYMLINK
            Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) -> FileTree.DIRECTORY
            else -> FileTree.REGULAR
        }
    }

    private fun tempTree(): File = Files.createTempDirectory("filetree").toFile()

    private fun delete(file: File?): Boolean = FileTree.deleteRecursive(file, jvmKind)

    /** 悬空链接：老实现会漏掉它，整个目录就永远删不掉。 */
    @Test
    fun `悬空的符号链接也必须被删掉`() {
        val root = tempTree()
        val bin = File(root, "bin").apply { mkdirs() }
        File(bin, "real").writeText("x")
        val deadAbs = File(bin, "dead-abs").toPath()
        val deadRel = File(bin, "dead-rel").toPath()
        Files.createSymbolicLink(deadAbs, File("/data/user/0/com.zhizhu.code/files/usr/lib/none.so").toPath())
        Files.createSymbolicLink(deadRel, File("../../LICENSES/GPL-2.0.txt").toPath())

        // 先把「为什么会漏」钉住：这两个目录项在 File.exists() 眼里等于不存在。
        assertFalse("悬空链接的 exists() 应为 false，这正是老实现漏删的原因", deadAbs.toFile().exists())
        assertFalse(deadRel.toFile().exists())
        assertTrue(
            "但它们是真实存在的目录项",
            Files.exists(deadRel, LinkOption.NOFOLLOW_LINKS),
        )
        assertEquals("判定必须认出这是链接而不是「不存在」", FileTree.SYMLINK, jvmKind.of(File(bin, "dead-rel")))

        assertTrue("删除应当成功", delete(root))
        assertFalse("整棵树必须消失（老实现会剩下来）", Files.exists(root.toPath(), LinkOption.NOFOLLOW_LINKS))
        // 事故现场就是这一句：目录还在 → mkdirs() 返回 false → 报「建不出来」。
        assertEquals("残留必须为空，否则下一轮 mkdirs 会失败", FileTree.ABSENT, jvmKind.of(root))
    }

    /** 指向目录的链接：只能删链接，绝不能顺着它把目标目录里的东西删掉。 */
    @Test
    fun `不跟随指向目录的符号链接去删目标内容`() {
        val root = tempTree()
        val outside = tempTree()
        val payload = File(outside, "别动我.txt").apply { writeText("重要") }
        val link = File(root, "link-to-outside").toPath()
        Files.createSymbolicLink(link, outside.toPath())

        assertTrue(delete(root))
        assertFalse("链接本身应被删掉", Files.exists(link, LinkOption.NOFOLLOW_LINKS))
        assertTrue("链接指向的目录必须原样还在", outside.isDirectory)
        assertTrue("里面的文件必须原样还在", payload.isFile)
        assertEquals("重要", payload.readText())
    }

    /** 幂等：路径不存在也算成功，安装流程反复调用不会因此炸。 */
    @Test
    fun `不存在的路径返回成功`() {
        val root = tempTree()
        assertTrue(delete(File(root, "never-existed")))
        assertTrue(delete(null))
        assertTrue(delete(root))
        assertFalse(Files.exists(root.toPath(), LinkOption.NOFOLLOW_LINKS))
    }

    /** 失败时要说清「还剩什么」，这正是原报错说反了的那部分。 */
    @Test
    fun `残留摘要报出项数和名字`() {
        val root = tempTree()
        assertEquals("无残留", FileTree.describe(File(root, "nope"), jvmKind))
        File(root, "a").mkdirs()
        File(root, "b").mkdirs()
        File(root, "c").mkdirs()
        File(root, "d").mkdirs()
        val text = FileTree.describe(root, jvmKind)
        assertTrue("要报出真实项数：$text", text.contains("残留 4 项"))
        assertTrue("超过 3 项时要有省略号：$text", text.contains("等"))
    }
}
