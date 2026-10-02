package com.termux.app.zhicode.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * {@code $HOME/storage/} 建链接的判定表。
 *
 * <h3>为什么值得单独测</h3>
 *
 * 这一段建的是**符号链接**，而链接出错的方式全都静默：
 *
 * <ul>
 *   <li><b>指错地方</b>：{@code ~/storage/shared} 指到别处，Agent 往那儿写文件 ——
 *       写"成功"了，但用户在「内部存储」里找不到；</li>
 *   <li><b>普通文件挡路</b>：目标名已经被一个普通文件/目录占了，直接
 *       {@code createSymbolicLink} 会抛 {@code FileAlreadyExistsException}，而那条异常
 *       很容易被 {@code runCatching} 吞掉 —— 结果是"配置看起来跑了，链接没建"；</li>
 *   <li><b>重复执行</b>：这里在每次启动的修复路径上都会跑，第二次必须仍然正确、
 *       且不能把已经对的链接删了重建（重建有窗口期，用户恰好在那一刻访问就会失败）。</li>
 * </ul>
 *
 * <p>纯 Java 就能跑（不碰 android.*），所以挂在 {@code test-jvm-fast.sh} 上。
 */
public class StorageLinksTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File newDir(String name) throws Exception {
        File dir = new File(tmp.getRoot(), name);
        assertTrue(dir.mkdirs() || dir.isDirectory());
        return dir;
    }

    /** 六个名字必须与 Termux 官方一致 —— 模型写 {@code ~/storage/shared/...} 是肌肉记忆。 */
    @Test
    public void linkNamesMatchTermux() {
        List<String> names = StorageLinks.linkNames();
        assertEquals(
            "[shared, dcim, downloads, movies, music, pictures]",
            names.toString());
        assertEquals("shared 必须排在最前（它是「整个内部存储」）",
                "shared", names.get(0));
    }

    @Test
    public void setupCreatesAllSixLinks() throws Exception {
        File home = newDir("home");
        File external = newDir("external");

        List<String> created = StorageLinks.setup(home, external);
        assertEquals(6, created.size());
        assertEquals(StorageLinks.linkNames(), created);

        for (String name : StorageLinks.linkNames()) {
            File link = new File(new File(home, "storage"), name);
            assertTrue(name + " 应当是符号链接", Files.isSymbolicLink(link.toPath()));
        }
        // 目标必须真的指对：shared 指向根，其余指向对应子目录。
        assertTrue(StorageLinks.isCorrectLink(
                new File(new File(home, "storage"), "shared"), external));
        assertTrue(StorageLinks.isCorrectLink(
                new File(new File(home, "storage"), "downloads"),
                new File(external, "Download")));
        assertTrue(StorageLinks.isCorrectLink(
                new File(new File(home, "storage"), "dcim"),
                new File(external, "DCIM")));
    }

    /** 目标目录不存在（没存过照片的手机）时，链接仍然要建出来并指向正确的位置。 */
    @Test
    public void missingTargetsAreCreatedBestEffort() throws Exception {
        File home = newDir("home");
        File external = newDir("external");
        assertFalse(new File(external, "Movies").exists());

        StorageLinks.setup(home, external);

        assertTrue("目标目录应当被尽力建出来", new File(external, "Movies").isDirectory());
        assertTrue(StorageLinks.isCorrectLink(
                new File(new File(home, "storage"), "movies"),
                new File(external, "Movies")));
    }

    /** 第二次执行必须幂等：结果一样，且不重建已经正确的链接。 */
    @Test
    public void setupIsIdempotentAndKeepsGoodLinks() throws Exception {
        File home = newDir("home");
        File external = newDir("external");

        StorageLinks.setup(home, external);
        File shared = new File(new File(home, "storage"), "shared");
        Object before = Files.readAttributes(
                shared.toPath(), java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS).fileKey();

        List<String> second = StorageLinks.setup(home, external);
        assertEquals(6, second.size());
        Object after = Files.readAttributes(
                shared.toPath(), java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS).fileKey();
        assertEquals("已经正确的链接不该被删了重建（重建有窗口期）", before, after);
    }

    /**
     * 名字被**普通文件**占住时必须重建（删掉再建），而不是抛异常后静默放弃。
     *
     * <p>这个场景真的会发生：早期版本或用户手工 {@code touch} 过同名文件之后，
     * {@code createSymbolicLink} 会抛 {@code FileAlreadyExistsException} ——
     * 而它如果被吞掉，用户看到的就是"配置跑了但没生效"。
     */
    @Test
    public void plainFileInTheWayIsReplaced() throws Exception {
        File home = newDir("home");
        File external = newDir("external");
        File storage = new File(home, "storage");
        assertTrue(storage.mkdirs());
        File blocker = new File(storage, "downloads");
        assertTrue(blocker.createNewFile());
        assertFalse(Files.isSymbolicLink(blocker.toPath()));

        List<String> created = StorageLinks.setup(home, external);

        assertTrue("挡路的普通文件必须被换成链接", created.contains("downloads"));
        assertTrue(Files.isSymbolicLink(blocker.toPath()));
        assertTrue(StorageLinks.isCorrectLink(blocker, new File(external, "Download")));
    }

    /** 指错地方的链接必须被修好（指向了别的目录，而不是"不存在"）。 */
    @Test
    public void wrongLinkTargetIsRepaired() throws Exception {
        File home = newDir("home");
        File external = newDir("external");
        File elsewhere = newDir("elsewhere");
        File storage = new File(home, "storage");
        assertTrue(storage.mkdirs());
        Path dcim = new File(storage, "dcim").toPath();
        Files.createSymbolicLink(dcim, elsewhere.toPath());

        StorageLinks.setup(home, external);

        assertTrue(StorageLinks.isCorrectLink(
                new File(storage, "dcim"), new File(external, "DCIM")));
    }

    /** 用户自己放在 {@code ~/storage} 下的东西一律不动 —— 只增不删。 */
    @Test
    public void unrelatedEntriesAreLeftAlone() throws Exception {
        File home = newDir("home");
        File external = newDir("external");
        File storage = new File(home, "storage");
        assertTrue(storage.mkdirs());
        File mine = new File(storage, "我的备份");
        assertTrue(mine.mkdirs());

        StorageLinks.setup(home, external);

        assertTrue("不属于本表的条目不该被清掉", mine.isDirectory());
    }

    /** 报告要逐条实测：链接在不在、指向哪、读不读得到。 */
    @Test
    public void describeReportsEachLinkWithReachability() throws Exception {
        File home = newDir("home");
        File external = newDir("external");
        StorageLinks.setup(home, external);

        List<String> lines = StorageLinks.describe(home);

        assertEquals(6, lines.size());
        // 按名字排序：dcim 在最前（describe 是给人看的，按字母序更好找）
        assertTrue(lines.get(0).contains("dcim ->"));
        boolean sawShared = false;
        for (String line : lines) {
            assertTrue("每行都要带可达性：" + line, line.contains("可达="));
            if (line.startsWith("  shared ->")) {
                sawShared = true;
                assertTrue("shared 应当指向共享存储根：" + line,
                        line.contains(external.getCanonicalPath()));
                assertTrue("刚建出来的链接应当可达：" + line, line.contains("可达=是"));
            }
        }
        assertTrue("报告里必须有 shared 那一行", sawShared);
    }

    /** 目录还没建时报告不能说谎说"有链接"。 */
    @Test
    public void describeSaysNotCreatedWhenMissing() throws Exception {
        File home = newDir("home");
        List<String> lines = StorageLinks.describe(home);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("未创建"));
    }

    /** 普通文件也算"不可达"，不能因为路径存在就说可达。 */
    @Test
    public void plainFileIsNotReachable() throws Exception {
        File home = newDir("home");
        File storage = new File(home, "storage");
        assertTrue(storage.mkdirs());
        File plain = new File(storage, "shared");
        assertTrue(plain.createNewFile());
        assertFalse(StorageLinks.isReachable(plain));
        assertFalse(StorageLinks.isCorrectLink(plain, new File("/storage/emulated/0")));
    }

    /** null 入参不能抛 —— 安装路径上拿不到外部存储根时不该让整个安装炸掉。 */
    @Test
    public void nullArgumentsAreSafe() {
        assertTrue(StorageLinks.setup(null, new File("/tmp")).isEmpty());
        assertTrue(StorageLinks.setup(new File("/tmp"), null).isEmpty());
    }
}
