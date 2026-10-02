package com.termux.app.zhicode.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * 文档 ID ↔ 路径映射与边界校验的判定表。
 *
 * ### 为什么每一条都值得写
 *
 * 这一段是「把 HOME 发布给文件管理器」里唯一会算错的部分，而它出错的表现
 * **全都不可诊断**：文件管理器里某一项打不开、新建报「操作失败」，
 * 或者最糟的一种 —— 越界成功了（照常读写）而没有任何人发现。
 *
 * 尤其要守的是 [DocumentTree.canonicalUnderRoot]：HOME 里就有
 * `storage/shared -> /storage/emulated/0` 这种链接，它的路径里没有 `..`、
 * 也不是私有目录名，逐段黑名单完全拦不住，只有规范化之后才看得出它指到了外面。
 */
class DocumentTreeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val home: File by lazy { tmp.newFolder("home") }

    // ---- ID ↔ 路径 ------------------------------------------------------

    @Test
    fun rootMapsToRootDocumentId() {
        assertEquals(DocumentTree.ROOT_DOCUMENT_ID, DocumentTree.documentIdFor(home, home))
        assertEquals(home.canonicalFile, DocumentTree.fileForId(home, DocumentTree.ROOT_DOCUMENT_ID))
    }

    @Test
    fun nestedFilesRoundTrip() {
        val dir = File(home, "projects/demo")
        assertTrue(dir.mkdirs())
        val file = File(dir, "a.kt")
        assertTrue(file.createNewFile())

        val id = DocumentTree.documentIdFor(home, file)
        assertEquals("home/projects/demo/a.kt", id)
        assertFalse("ID 里不该出现平台分隔符", id.contains('\\'))

        assertEquals(file.canonicalFile, DocumentTree.fileForId(home, id))
    }

    /** 目录也要能往返：文件管理器点进目录靠的就是它。 */
    @Test
    fun directoriesRoundTrip() {
        val dir = File(home, "a/b")
        assertTrue(dir.mkdirs())
        assertEquals("home/a/b", DocumentTree.documentIdFor(home, dir))
        assertEquals(dir.canonicalFile, DocumentTree.fileForId(home, "home/a/b"))
    }

    // ---- ID 的合法性 ----------------------------------------------------

    @Test
    fun malformedIdsAreRejected() {
        listOf(
            null, "", "other", "other/x", "homex", "home2/a",
            "/home/a", "HOME/a", "home_/a",
        ).forEach { bad ->
            assertThrows("应当拒绝非法 ID：$bad") { DocumentTree.fileForId(home, bad) }
        }
    }

    /** `.` / `..` 作为**整段**出现时必须拒绝，无论藏在哪个位置。 */
    @Test
    fun dotSegmentsAreRejectedAnywhereInTheId() {
        listOf(
            "home/..", "home/../etc", "home/a/../../b", "home/a/..", "home/a/./b", "home/.",
        ).forEach { bad ->
            assertThrows("应当拒绝含 . / .. 段的 ID：$bad") { DocumentTree.fileForId(home, bad) }
        }
    }

    /**
     * ⚠️ 名字里**含** `..` 不等于路径穿越。
     *
     * 这条是刻意的判据选择：用 `relative.contains("..")` 判断的话，
     * 一个叫 `a..b.txt` 的合法文件会被拒 —— 而我们自己生成的 ID 正是
     * `home/a..b.txt`，于是它**转不回来**，表现是文件管理器里这个文件打不开。
     * 所以判据是「整段就是 `..`」，不是「包含 `..`」。
     */
    @Test
    fun namesContainingDotsAreNotPathTraversal() {
        listOf("a..b.txt", "..x", "x..", "a...b", "...").forEach { name ->
            val file = File(home, name)
            assertTrue("创建失败：$name", file.createNewFile())
            val id = DocumentTree.documentIdFor(home, file)
            assertEquals("home/$name", id)
            assertEquals(
                "名字里含 .. 的合法文件必须能转回来：$name",
                file.canonicalFile,
                DocumentTree.fileForId(home, id),
            )
        }
    }

    /** 应用自己的元数据目录：按 ID 也进不去。 */
    @Test
    fun privateDirectoriesAreUnreachableById() {
        DocumentTree.privateNames.forEach { name ->
            assertThrows("不该能按 ID 访问私有目录：$name") {
                DocumentTree.fileForId(home, "home/$name")
            }
            assertThrows("私有目录出现在中间也要挡住：$name") {
                DocumentTree.fileForId(home, "home/sub/$name/x")
            }
        }
    }

    // ---- 越界：本节是重点 -----------------------------------------------

    /**
     * ⚠️ 符号链接必须被规范化检查拦住。
     *
     * HOME 里就有 `storage/shared -> /storage/emulated/0`。那条链接的路径里
     * **没有 `..`**、也不是私有目录名 —— 逐段黑名单完全拦不住，
     * 只有 `getCanonicalPath()` 会告诉你它指到了私有目录之外。
     */
    @Test
    fun symlinkEscapingHomeIsRejected() {
        val outside = tmp.newFolder("outside")
        assertTrue(File(outside, "secret.txt").createNewFile())
        Files.createSymbolicLink(File(home, "escape").toPath(), outside.toPath())

        val error = assertThrows("经符号链接越出 HOME 必须被拦住") {
            DocumentTree.fileForId(home, "home/escape/secret.txt")
        }
        assertTrue("提示要说清是越界：${error.message}", error.message!!.contains("越界"))

        assertThrows("HOME 之外的路径不该能转成文档 ID") {
            DocumentTree.documentIdFor(home, File(outside, "secret.txt"))
        }
    }

    /**
     * 前缀比较必须补分隔符。
     *
     * 只写 `startsWith(rootPath)` 会让 `/…/home-evil` 被误判成在 `/…/home` 之内
     * —— 它们是同一前缀下的两个不同目录。
     */
    @Test
    fun siblingWithSamePrefixIsNotInside() {
        val sibling = File(home.parentFile, home.name + "-evil")
        assertTrue(sibling.mkdirs())
        assertTrue(File(sibling, "x.txt").createNewFile())

        val error = assertThrows("同前缀的兄弟目录不该被算作 HOME 之内") {
            DocumentTree.canonicalUnderRoot(home, File(sibling, "x.txt"))
        }
        assertTrue(error.message!!.contains("越界"))
        assertFalse(DocumentTree.isChildOf(home, File(sibling, "x.txt")))
    }

    @Test
    fun isChildOfAcceptsSelfAndDescendants() {
        val sub = File(home, "a/b")
        assertTrue(sub.mkdirs())
        assertTrue("自己也算自己的子项（SAF 的约定）", DocumentTree.isChildOf(home, home))
        assertTrue(DocumentTree.isChildOf(home, sub))
        assertFalse(DocumentTree.isChildOf(sub, home))
        assertFalse(DocumentTree.isChildOf(null, home))
        assertFalse(DocumentTree.isChildOf(home, null))
    }

    // ---- 名字校验 -------------------------------------------------------

    @Test
    fun legalNamesPass() {
        assertEquals("a.txt", DocumentTree.safeName("a.txt"))
        assertEquals("构建产物.log", DocumentTree.safeName("构建产物.log"))
        assertEquals(".bashrc", DocumentTree.safeName(".bashrc"))
    }

    /**
     * 非法名字必须被拒。
     *
     * 带 `/` 的名字会写到**别的目录**去，而用户在原来的位置找不到时
     * 只会以为「操作失败」。
     */
    @Test
    fun pathSeparatorsAndDotNamesAreRejected() {
        listOf(
            "", "   ", null, ".", "..", "a/b.txt", "/etc/passwd",
            "sub\\f.txt", "a\u0000b", " ok ", "ok ",
        ).forEach { bad ->
            assertThrows("应当拒绝非法名称：$bad") { DocumentTree.safeName(bad) }
        }
    }

    @Test
    fun privateNamesCannotBeCreatedOrRenamedInto() {
        DocumentTree.privateNames.forEach { name ->
            assertThrows("不该允许创建/改名成私有目录：$name") { DocumentTree.safeName(name) }
        }
    }

    /**
     * 名字规则必须与 [FileOps] 的一致 —— 两边写的是**同一个目录**，
     * 口径不同的结果是"一边能建、另一边打不开"。
     *
     * 这条当初确实抓到过一次：`FileOps` 拒绝首尾空格，而 `DocumentTree`
     * 在静默 trim（于是 `" ok "` 在文件管理器里会被建成 `"ok"`）。
     */
    @Test
    fun nameRulesAgreeWithFileOps() {
        val samples = listOf(
            "a.txt", "构建 产物.log", ".bashrc", "", "..", "a/b", "sub\\x",
            "a\u0000b", " ok ", "ok ", "   ", "normal name.md",
        )
        for (sample in samples) {
            val fileOpsOk = FileOps.nameError(sample) == null
            val documentOk = runCatching { DocumentTree.safeName(sample) }.isSuccess
            assertEquals(
                "FileOps 与 DocumentTree 对「$sample」的判断必须一致",
                fileOpsOk,
                documentOk,
            )
        }
    }

    // ---- 删除不跟符号链接 -----------------------------------------------

    /**
     * ⚠️ 删符号链接不能跟进去。
     *
     * HOME 里的 `storage/shared` 指着整块内部存储 —— 跟着链接递归删，
     * 等于把用户的照片、下载、所有应用数据一起删掉。
     */
    @Test
    fun deleteTreeDoesNotFollowSymlinks() {
        val outside = tmp.newFolder("outside")
        val keep = File(outside, "照片.txt")
        Files.write(keep.toPath(), "重要".toByteArray(StandardCharsets.UTF_8))

        val link = File(home, "shared")
        Files.createSymbolicLink(link.toPath(), outside.toPath())

        assertNull(DocumentTree.deleteTree(link))

        assertFalse("链接本身该被删掉", Files.exists(link.toPath(), LinkOption.NOFOLLOW_LINKS))
        assertTrue("链接指向的目录必须原封不动", outside.isDirectory)
        assertTrue("链接指向的文件必须原封不动", keep.isFile)
    }

    @Test
    fun deleteTreeRemovesNestedContent() {
        val sub = File(home, "x/y")
        assertTrue(sub.mkdirs())
        assertTrue(File(sub, "z.txt").createNewFile())

        assertNull(DocumentTree.deleteTree(File(home, "x")))
        assertFalse(File(home, "x").exists())
        assertTrue("HOME 自己不能被误删", home.isDirectory)
    }

    @Test
    fun deleteTreeOnMissingTargetIsANoop() {
        assertNull(DocumentTree.deleteTree(File(home, "nope")))
        assertNull(DocumentTree.deleteTree(null))
    }

    // ---- 报告 -----------------------------------------------------------

    @Test
    fun describeReportsRealFacts() {
        val joined = DocumentTree.describe(home).joinToString("\n")
        assertTrue("要报真实路径：$joined", joined.contains(home.absolutePath))
        assertTrue("要报可读写：$joined", joined.contains("可读写"))
        assertTrue("要说清挡了哪些目录：$joined", joined.contains(".zhicode"))

        val missing = DocumentTree.describe(File(home, "nope")).joinToString("\n")
        assertTrue("目录不存在时不能说谎说存在：$missing", missing.contains("存在        : false"))
    }

    /** 私有目录名必须与 `TermuxConstants` 的实际取值对上（写错了等于没挡）。 */
    @Test
    fun privateNamesCoverTheRealDataDirectory() {
        val expected = "." + com.termux.shared.termux.TermuxConstants.BRAND_SLUG
        assertTrue(
            "必须挡住真正的数据目录 $expected，实际：${DocumentTree.privateNames}",
            DocumentTree.privateNames.contains(expected),
        )
    }

    // ---- 工具 -----------------------------------------------------------

    private fun assertThrows(message: String, block: () -> Unit): IOException {
        try {
            block()
        } catch (e: IOException) {
            return e
        }
        throw AssertionError(message)
    }
}
