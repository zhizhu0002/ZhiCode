package com.termux.app.zhicode.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 文件面板写操作的判定表。
 *
 * <h3>为什么每一条都值得写</h3>
 *
 * 这些操作在界面上都是**不可逆**的，而失败方式全都静默：
 *
 * <ul>
 *   <li>名字里带 {@code /} → 文件落到**别的目录**里，用户在当前目录找不到，
 *       以为"保存失败"；</li>
 *   <li>名字是 {@code ..} → 越出当前目录；</li>
 *   <li>重名 → 覆盖掉别人（或 Agent）的工作，而且**不报错**；</li>
 *   <li>删符号链接时跟进去 → {@code ~/storage/shared} 指着整块内部存储，
 *       会把用户的照片一起删掉；</li>
 *   <li>保存写了一半被杀 → 留下一个半截的源文件，比保存失败糟得多。</li>
 * </ul>
 */
public class FileOpsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // ---- 名字校验：每一条都对应一种"落到别处去了" ------------------------

    @Test
    public void legalNamesAreAccepted() {
        assertNull(FileOps.nameError("a.txt"));
        assertNull(FileOps.nameError("构建产物.log"));
        assertNull(FileOps.nameError(".bashrc"));
        assertNull(FileOps.nameError("a b c.md"));
        assertNull(FileOps.nameError("a-b_c+d(1).kt"));
    }

    @Test
    public void pathSeparatorsAreRejected() {
        assertNotNull("名字里带 / 会写到别的目录去", FileOps.nameError("a/b.txt"));
        assertNotNull(FileOps.nameError("/etc/passwd"));
        assertNotNull(FileOps.nameError("..\\..\\windows"));
        assertNotNull("反斜杠同样危险",
                FileOps.nameError("sub\\file.txt"));
    }

    @Test
    public void dotNamesAreRejected() {
        assertNotNull(FileOps.nameError("."));
        assertNotNull(FileOps.nameError(".."));
    }

    @Test
    public void emptyAndWhitespaceNamesAreRejected() {
        assertNotNull(FileOps.nameError(""));
        assertNotNull(FileOps.nameError("   "));
        assertNotNull(FileOps.nameError(null));
        // 首尾空白在文件管理器里看不出来，最终会变成"同名两个文件"
        assertNotNull(FileOps.nameError(" a.txt"));
        assertNotNull(FileOps.nameError("a.txt "));
    }

    @Test
    public void overlyLongNamesAreRejected() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 256; i++) sb.append('a');
        assertNotNull(FileOps.nameError(sb.toString()));
    }

    // ---- 新建 -----------------------------------------------------------

    @Test
    public void createFileAndDirectory() throws Exception {
        File dir = tmp.newFolder("w");

        assertNull(FileOps.createFile(dir, "a.txt"));
        assertTrue(new File(dir, "a.txt").isFile());

        assertNull(FileOps.createDirectory(dir, "sub"));
        assertTrue(new File(dir, "sub").isDirectory());
    }

    /** 重名必须**报错**而不是覆盖 —— 覆盖是静默的，用户会发现工作没了。 */
    @Test
    public void createRefusesToOverwrite() throws Exception {
        File dir = tmp.newFolder("w");
        FileOps.write(new File(dir, "a.txt"), "原有内容");

        String error = FileOps.createFile(dir, "a.txt");
        assertNotNull("同名文件必须被拒绝", error);
        assertTrue("提示里要说清是重名", error.contains("已经存在"));
        assertEquals("原有内容", read(new File(dir, "a.txt")));
    }

    /**
     * 非法名字必须被**校验**拦下，而不是靠"创建时恰好失败"。
     *
     * <p>⚠️ 这条一开始写弱了：只在空目录里试 `a/b.txt`，那时父目录不存在，
     * `createNewFile` 本来就会失败 —— 于是即使把名字校验整个删掉，测试照样绿。
     * 现在先把 `sub/` **建出来**，让非法名字真的能落进别的目录：
     * 这时的判据是"目标目录里**没有多出文件**"，校验一被删就会红。
     */
    @Test
    public void createValidatesTheName() throws Exception {
        File dir = tmp.newFolder("w");
        File trap = new File(dir, "sub");
        assertTrue(trap.mkdirs());

        String error = FileOps.createFile(dir, "sub/evil.txt");
        assertNotNull("名字里带 / 必须被校验拦下", error);
        assertEquals("提示要说清是名字的问题，而不是含糊的创建失败",
                "名字里不能有 /", error);
        assertFalse("文件绝不能落进子目录：" + trap.getAbsolutePath(),
                new File(trap, "evil.txt").exists());
        assertEquals("子目录必须保持为空", 0, trap.list().length);
    }

    /*
     * 这里原本有一条 `suggestNameAvoidsCollisions`，随 `FileOps.suggestName` 一起删了：
     * 那个函数唯一的调用者是「新建」表单，而它现在**不预填名字**（见 FileOps 里的注释）。
     * 留着一条给死代码用的用例，只会让"这个能力还在被用"看起来成立。
     */

    // ---- 重命名 ---------------------------------------------------------

    @Test
    public void renameWorks() throws Exception {
        File dir = tmp.newFolder("w");
        File file = new File(dir, "a.txt");
        FileOps.write(file, "x");

        assertNull(FileOps.rename(file, "b.txt"));
        assertFalse(file.exists());
        assertEquals("x", read(new File(dir, "b.txt")));
    }

    @Test
    public void renameRefusesToOverwriteAndKeepsOriginal() throws Exception {
        File dir = tmp.newFolder("w");
        FileOps.write(new File(dir, "a.txt"), "A");
        FileOps.write(new File(dir, "b.txt"), "B");

        String error = FileOps.rename(new File(dir, "a.txt"), "b.txt");

        assertNotNull(error);
        assertEquals("A", read(new File(dir, "a.txt")));
        assertEquals("B", read(new File(dir, "b.txt")));
    }

    @Test
    public void renameToSameNameIsANoop() throws Exception {
        File dir = tmp.newFolder("w");
        File file = new File(dir, "a.txt");
        FileOps.write(file, "x");
        assertNull(FileOps.rename(file, "a.txt"));
        assertTrue(file.isFile());
    }

    @Test
    public void renameValidatesTheName() throws Exception {
        File dir = tmp.newFolder("w");
        File file = new File(dir, "a.txt");
        FileOps.write(file, "x");
        assertNotNull(FileOps.rename(file, "sub/b.txt"));
        assertTrue("原文件必须留在原处", file.isFile());
    }

    // ---- 删除 -----------------------------------------------------------

    @Test
    public void deleteRemovesFilesAndDirectories() throws Exception {
        File dir = tmp.newFolder("w");
        FileOps.write(new File(dir, "a.txt"), "x");
        File sub = new File(dir, "sub/deep");
        assertTrue(sub.mkdirs());
        FileOps.write(new File(sub, "b.txt"), "y");

        assertNull(FileOps.delete(new File(dir, "a.txt")));
        assertFalse(new File(dir, "a.txt").exists());

        assertNull(FileOps.delete(new File(dir, "sub")));
        assertFalse(new File(dir, "sub").exists());
    }

    /**
     * ⚠️ 删符号链接**不能**跟进去。
     *
     * <p>{@code ~/storage/shared} 指着整块内部存储 —— 跟着链接递归删，
     * 等于把用户的照片、下载、所有应用的数据一起删掉。
     */
    @Test
    public void deleteDoesNotFollowSymlinks() throws Exception {
        File outside = tmp.newFolder("outside");
        File keep = new File(outside, "照片.txt");
        FileOps.write(keep, "重要");

        File dir = tmp.newFolder("w");
        File link = new File(dir, "shared");
        Files.createSymbolicLink(link.toPath(), outside.toPath());

        assertNull(FileOps.delete(link));

        assertFalse("链接本身应当被删掉", Files.exists(link.toPath()));
        assertTrue("链接指向的目录必须原封不动", outside.isDirectory());
        assertTrue("链接指向的文件必须原封不动", keep.isFile());
        assertEquals("重要", read(keep));
    }

    @Test
    public void deleteMissingFileReportsIt() throws Exception {
        File dir = tmp.newFolder("w");
        assertNotNull(FileOps.delete(new File(dir, "nope.txt")));
    }

    /**
     * 删除前要能说清"会删掉多少条"，否则确认框只能写一句含糊的「确定吗」。
     *
     * <p>计数规则：**要删掉的对象本身也算一条**。所以删一个空目录是 1 而不是 0 ——
     * 那个目录确实会消失。删一个文件也是 1。这两条要一起看才说明白，
     * 只测一种会让"到底算不算自己"变成看代码才知道的事。
     */
    @Test
    public void countForDeleteIsRecursiveButDoesNotFollowLinks() throws Exception {
        File outside = tmp.newFolder("outside");
        FileOps.write(new File(outside, "x.txt"), "x");

        File dir = tmp.newFolder("w");
        FileOps.write(new File(dir, "a.txt"), "a");
        File sub = new File(dir, "sub");
        assertTrue(sub.mkdirs());
        FileOps.write(new File(sub, "b.txt"), "b");
        Files.createSymbolicLink(new File(dir, "link").toPath(), outside.toPath());

        // dir 自己 + a.txt + sub + sub/b.txt + link = 5。
        // 链接算 1 条、**不跟进去**（跟进去会多算 outside 与 outside/x.txt，
        // 而它们根本不会被删 —— 计数一旦虚高，确认文案就在骗人）。
        assertEquals(5, FileOps.countForDelete(dir));
        assertEquals(1, FileOps.countForDelete(new File(dir, "a.txt")));
        assertEquals(2, FileOps.countForDelete(sub));
    }

    /** 「算不算自己」这条规则要显式钉住：空目录也是 1，不是 0。 */
    @Test
    public void countIncludesTheTargetItself() throws Exception {
        File dir = tmp.newFolder("w");
        File empty = new File(dir, "empty");
        assertTrue(empty.mkdirs());

        assertEquals("空目录被删掉时也是 1 条，不是 0", 1, FileOps.countForDelete(empty));
        assertEquals("不存在的目标算 0 条", 0, FileOps.countForDelete(new File(dir, "nope")));
    }

    // ---- 复制与移动 -----------------------------------------------------

    @Test
    public void copyRecursivelyPreservesContentsAndDoesNotRemoveSource() throws Exception {
        File sourceParent = tmp.newFolder("copy-source");
        File source = new File(sourceParent, "tree");
        File nested = new File(source, "nested");
        assertTrue(nested.mkdirs());
        FileOps.write(new File(source, "a.txt"), "甲");
        FileOps.write(new File(nested, "b.txt"), "乙");
        File destination = tmp.newFolder("copy-destination");

        assertNull(FileOps.copy(source, destination));
        assertEquals("甲", read(new File(destination, "tree/a.txt")));
        assertEquals("乙", read(new File(destination, "tree/nested/b.txt")));
        assertTrue("复制不能删除源目录", source.isDirectory());
    }

    @Test
    public void copyRefusesExistingTargetAndDirectoryIntoItself() throws Exception {
        File parent = tmp.newFolder("copy-boundary");
        File source = new File(parent, "source");
        assertTrue(source.mkdir());
        FileOps.write(new File(source, "a.txt"), "source");
        File existing = new File(parent, "existing");
        assertTrue(existing.mkdir());
        FileOps.write(new File(existing, "source"), "keep");

        assertNotNull("不能覆盖目标", FileOps.copy(source, existing));
        assertEquals("keep", read(new File(existing, "source")));
        assertNotNull("不能将目录复制到自身", FileOps.copy(source, parent));
        assertTrue(source.isDirectory());
    }

    @Test
    public void copyRejectsSymlinksWithoutFollowingThem() throws Exception {
        File outside = tmp.newFolder("copy-outside");
        File keep = new File(outside, "keep.txt");
        FileOps.write(keep, "keep");
        File parent = tmp.newFolder("copy-link");
        File source = new File(parent, "source");
        assertTrue(source.mkdir());
        Files.createSymbolicLink(new File(source, "link").toPath(), outside.toPath());
        File destination = tmp.newFolder("copy-link-destination");

        assertNotNull(FileOps.copy(source, destination));
        assertFalse("失败的部分复制必须清理", new File(destination, "source").exists());
        assertEquals("keep", read(keep));
    }

    @Test
    public void moveCopiesThenRemovesSourceAndRefusesOverwrite() throws Exception {
        File sourceParent = tmp.newFolder("move-source");
        File source = new File(sourceParent, "a.txt");
        FileOps.write(source, "move me");
        File destination = tmp.newFolder("move-destination");

        assertNull(FileOps.move(source, destination));
        assertFalse(source.exists());
        assertEquals("move me", read(new File(destination, "a.txt")));

        File occupied = new File(sourceParent, "occupied.txt");
        FileOps.write(occupied, "original");
        FileOps.write(new File(destination, "occupied.txt"), "target");
        assertNotNull(FileOps.move(occupied, destination));
        assertEquals("original", read(occupied));
        assertEquals("target", read(new File(destination, "occupied.txt")));
    }

    // ---- 保存 -----------------------------------------------------------

    @Test
    public void writeCreatesAndOverwrites() throws Exception {
        File dir = tmp.newFolder("w");
        File file = new File(dir, "a.txt");

        assertNull(FileOps.write(file, "第一版"));
        assertEquals("第一版", read(file));

        assertNull(FileOps.write(file, "第二版"));
        assertEquals("第二版", read(file));
    }

    /** 中文与换行必须原样保住（UTF-8 + 不替换行尾）。 */
    @Test
    public void writePreservesUtf8AndLineEndings() throws Exception {
        File dir = tmp.newFolder("w");
        File file = new File(dir, "a.md");
        String text = "# 标题\r\n\r\n第二行 · 中文\n结尾";

        assertNull(FileOps.write(file, text));

        byte[] bytes = Files.readAllBytes(file.toPath());
        assertEquals(text, new String(bytes, StandardCharsets.UTF_8));
        assertTrue("CRLF 必须原样保留", new String(bytes, StandardCharsets.UTF_8).contains("\r\n"));
    }

    /** 保存不能留下临时文件（`.a.txt.tmp`）—— 那会出现在文件列表里。 */
    @Test
    public void writeLeavesNoTemporaryFile() throws Exception {
        File dir = tmp.newFolder("w");
        File file = new File(dir, "a.txt");
        FileOps.write(file, "x");

        String[] left = dir.list();
        assertNotNull(left);
        assertEquals("目录里只该有那一个文件，实际：" + String.join(",", left), 1, left.length);
        assertEquals("a.txt", left[0]);
    }

    @Test
    public void writeRejectsDirectoriesAndHugeContent() throws Exception {
        File dir = tmp.newFolder("w");
        File sub = new File(dir, "sub");
        assertTrue(sub.mkdirs());
        assertNotNull("目录不能当文件保存", FileOps.write(sub, "x"));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < FileOps.MAX_WRITE_BYTES / 4 + 16; i++) sb.append("abcd");
        assertNotNull("超过上限要拒绝，而不是把界面拖死", FileOps.write(new File(dir, "big.txt"), sb.toString()));
    }

    /** 保存到还不存在的目录时要建出来（新建文件夹后立刻往里存）。 */
    @Test
    public void writeCreatesMissingParentDirectory() throws Exception {
        File dir = tmp.newFolder("w");
        File target = new File(dir, "new/deep/a.txt");
        assertNull(FileOps.write(target, "x"));
        assertEquals("x", read(target));
    }

    // ---- 边界与可写性 ---------------------------------------------------

    @Test
    public void isInsideUsesCanonicalPaths() throws Exception {
        File root = tmp.newFolder("root");
        File inside = new File(root, "a/b.txt");
        assertTrue(FileOps.isInside(root, inside));
        assertFalse(FileOps.isInside(root, tmp.newFolder("other")));

        // 符号链接会走出 root —— 这正是要拦住的情况
        File link = new File(root, "out");
        Files.createSymbolicLink(link.toPath(), tmp.newFolder("outside").toPath());
        assertFalse("链接指向 root 之外时不能算 inside",
                FileOps.isInside(root, new File(link, "x.txt")));
    }

    @Test
    public void canWriteAndListNames() throws Exception {
        File dir = tmp.newFolder("w");
        assertTrue(FileOps.canWrite(dir));
        assertFalse(FileOps.canWrite(new File(dir, "nope")));

        FileOps.createFile(dir, "b.txt");
        FileOps.createFile(dir, "a.txt");
        assertEquals("[a.txt, b.txt]", FileOps.listNames(dir).toString());
        assertTrue(FileOps.listNames(new File(dir, "nope")).isEmpty());
        assertTrue(FileOps.listNames(null).isEmpty());
    }

    @Test
    public void nullArgumentsAreSafe() {
        assertNotNull(FileOps.createFile(null, "a.txt"));
        assertNotNull(FileOps.rename(null, "a.txt"));
        assertNotNull(FileOps.delete(null));
        assertNotNull(FileOps.write(null, "x"));
        assertNull(FileOps.write(newFileInTemp("nullarg.txt"), null));
        assertEquals(-1, FileOps.childCount(null));
        assertEquals(0, FileOps.countForDelete(null));
    }

    private File newFileInTemp(String name) {
        try {
            return new File(tmp.newFolder("nullarg"), name);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
