package com.termux.app.zhicode.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code $HOME/storage/} —— 手机共享存储的入口（Termux 的 {@code termux-setup-storage} 等价物）。
 *
 * <h3>为什么要有它</h3>
 *
 * HOME 在应用私有目录里（{@code /data/user/0/<pkg>/files/home}），而用户要处理的文件
 * 几乎都在共享存储（{@code /storage/emulated/0}，也就是「内部存储」）里：下载的 APK、
 * 截图、微信导出的文件、相机照片。没有这一层，Agent 想动这些文件只能写绝对路径，
 * 而绝对路径一旦走 {@code content://} 之外的方式就会撞上分区存储的限制。
 *
 * <h3>为什么是符号链接而不是复制</h3>
 *
 * 链接是**零成本的双向通道**：{@code ~/storage/shared/Download/a.apk} 与
 * {@code /storage/emulated/0/Download/a.apk} 是同一个文件。复制会有两份、
 * 会不同步，而且几十 GB 的媒体目录根本复制不动。
 *
 * <h3>名字与目标照抄 Termux</h3>
 *
 * 六个名字（{@code shared/dcim/downloads/movies/music/pictures}）与 Termux 官方
 * {@code termux-setup-storage} 完全一致。这不是随意的：模型见过大量 Termux 语料，
 * 写 {@code ~/storage/shared/...} 是它的肌肉记忆；另起一套名字会让它每次都猜错一次。
 *
 * <h3>为什么这个类不 import android.*</h3>
 *
 * 建链接、判断可达性全是纯 Java。不碰 {@code android.*} 才能被
 * {@code test-jvm-fast.sh} 秒级单测（见 {@code StorageLinksTest}）——
 * 这类"建了但指错地方"的问题在真机上很难看出来（链接存在、读一下就报错）。
 */
public final class StorageLinks {

    /** 共享存储根。Android 上固定是这个（多用户时前面还有 {@code /storage/emulated/<uid>}）。 */
    public static final String EXTERNAL_ROOT = "/storage/emulated/0";

    /**
     * 链接名 → 相对 {@link #EXTERNAL_ROOT} 的目标。
     *
     * <p>顺序固定（{@code shared} 在最前，它是"整个内部存储"）：{@code EnvDoctor}
     * 的报告按这个顺序打印，用户一眼能对上号。
     */
    private static final Map<String, String> LINKS = new LinkedHashMap<>();

    static {
        LINKS.put("shared", "");
        LINKS.put("dcim", "DCIM");
        LINKS.put("downloads", "Download");
        LINKS.put("movies", "Movies");
        LINKS.put("music", "Music");
        LINKS.put("pictures", "Pictures");
    }

    private StorageLinks() { }

    /** 链接名（按报告顺序）。 */
    public static List<String> linkNames() {
        return new ArrayList<>(LINKS.keySet());
    }

    /**
     * 建立（或修复）{@code home/storage} 下的全部链接。**幂等**。
     *
     * <p>只增不删：目录里不属于本表的条目一律不动。用户可能在 {@code ~/storage}
     * 下放了自己的东西，清空整个目录（官方脚本会警告并重建）在这里是没必要的破坏 ——
     * 我们只负责自己那六个。
     *
     * @param home     {@code $HOME}
     * @param external 共享存储根，通常是 {@link #EXTERNAL_ROOT}
     * @return 建好之后**确实存在且指向正确**的链接名，按 {@link #linkNames()} 的顺序
     */
    public static List<String> setup(File home, File external) {
        List<String> ok = new ArrayList<>();
        if (home == null || external == null) return ok;

        File storage = new File(home, "storage");
        if (!storage.isDirectory() && !storage.mkdirs()) return ok;

        for (Map.Entry<String, String> entry : LINKS.entrySet()) {
            String name = entry.getKey();
            File target = entry.getValue().isEmpty()
                ? external
                : new File(external, entry.getValue());
            File link = new File(storage, name);

            // 目标不存在时**尽力**建一下：DCIM / Movies 这类目录在没存过东西的手机上
            // 本来就不存在，而一个指向不存在目录的链接会让 `cd ~/storage/dcim` 失败，
            // 用户看到的是"配置没生效"。建不出来（没给 MANAGE_EXTERNAL_STORAGE，
            // 或目标只读）不算错误 —— 链接本身仍然有意义，只是暂时指不到东西。
            if (!target.exists()) {
                //noinspection ResultOfMethodCallIgnored
                target.mkdirs();
            }

            if (isCorrectLink(link, target)) {
                ok.add(name);
                continue;
            }
            // 形状不对（普通文件 / 指向别处 / 悬空）才重建。先删掉这一条，别的条目不动。
            if (exists(link) || isSymlink(link)) {
                if (!link.delete()) continue;
            }
            if (createSymlink(target, link)) ok.add(name);
        }
        return ok;
    }

    /** 链接是否存在且**指向** {@code target}（按规范化路径比，容忍尾斜杠与相对写法）。 */
    public static boolean isCorrectLink(File link, File target) {
        if (!isSymlink(link)) return false;
        try {
            Path want = target.getCanonicalFile().toPath();
            Path got = link.getCanonicalFile().toPath();
            return want.equals(got);
        } catch (IOException e) {
            return false;
        }
    }

    /** 链接是否指向一个**现在读得到**的地方（悬空链接、权限不足都算 false）。 */
    public static boolean isReachable(File link) {
        if (!isSymlink(link)) return false;
        try {
            return Files.exists(link.toPath(), LinkOption.NOFOLLOW_LINKS)
                && link.getCanonicalFile().exists()
                && link.getCanonicalFile().canRead();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 报告用的一行一条：{@code 名字 -> 规范化目标   可达=是/否}。
     *
     * <p>报告要的是**实测值**：链接在不在、指向哪、读不读得到。三件事分别可能出问题
     * （没建 / 指错 / 没权限），合成一个"OK/FAIL"就查不出是哪一种。
     */
    public static List<String> describe(File home) {
        List<String> lines = new ArrayList<>();
        File storage = new File(home, "storage");
        if (!storage.isDirectory()) {
            lines.add("  (未创建)");
            return lines;
        }
        File[] children = storage.listFiles();
        if (children == null || children.length == 0) {
            lines.add("  (空)");
            return lines;
        }
        Arrays.sort(children, (a, b) -> a.getName().compareTo(b.getName()));
        for (File child : children) {
            String target = isSymlink(child)
                ? canonicalOf(child)
                : "(普通文件/目录，非链接)";
            lines.add("  " + child.getName() + " -> " + target
                + "  可达=" + (isReachable(child) ? "是" : "否"));
        }
        return lines;
    }

    private static String canonicalOf(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return "(无法解析)";
        }
    }

    private static boolean exists(File file) {
        return Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean isSymlink(File file) {
        return Files.isSymbolicLink(file.toPath());
    }

    private static boolean createSymlink(File target, File link) {
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath());
            return true;
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            return false;
        }
    }
}
