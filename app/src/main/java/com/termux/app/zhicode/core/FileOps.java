package com.termux.app.zhicode.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文件面板的写操作：新建 / 重命名 / 删除 / 保存。
 *
 * <h3>为什么单独一个类</h3>
 *
 * 界面上「保存」「删除」都是**不可逆**动作，而它们的边界条件全在字符串层面：
 * 名字里带 {@code /} 会写到别的目录去；{@code ..} 会越出当前目录；空名字会生成
 * 一个没有名字的文件；重名会静默覆盖掉别人的工作。这些都不是编译期能发现的。
 *
 * 所以校验与操作放在这里 —— 纯 {@code java.io}，不 import {@code android.*}，
 * 于是能被 {@code test-jvm-fast.sh} 秒级单测（见 {@code FileOpsTest}）。
 *
 * <h3>返回 String 而不是抛异常</h3>
 *
 * 每个方法返回 {@code null} 表示成功、否则返回**给用户看的**中文原因。
 * 界面要做的就是把这句原样显示出来：失败原因（名字非法 / 已存在 / 权限不足）
 * 正是用户唯一需要知道的信息，包成异常再拆开只会让它更容易在半路丢掉。
 *
 * <h3>只增不删的边界</h3>
 *
 * {@link #delete} 对目录是**递归**的。调用方必须先让用户明确确认 ——
 * 这里不做二次确认（它不认识界面），但把「删了几个文件」如实返回。
 */
public final class FileOps {

    /** 单个文件一次保存的上限。超过这个量的文本编辑不适合在手机上做。 */
    public static final int MAX_WRITE_BYTES = 8 * 1024 * 1024;

    private FileOps() { }

    /**
     * 校验一个「新建/重命名」用的名字。返回 {@code null} 表示可用。
     *
     * <p>逐条拒绝并说明理由：用户看到「名字里不能有 /」比看到「操作失败」有用得多。
     */
    public static String nameError(String name) {
        if (name == null) return "名字不能为空";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "名字不能为空";
        if (!trimmed.equals(name)) {
            // 首尾空白在文件管理器里完全看不出来，最终会变成"同一个名字有两个文件"。
            return "名字首尾不能有空格";
        }
        if (".".equals(name) || "..".equals(name)) return "这个名字是保留的";
        if (name.indexOf('/') >= 0) return "名字里不能有 /";
        if (name.indexOf('\\') >= 0) return "名字里不能有 \\";
        if (name.indexOf('\u0000') >= 0) return "名字里有非法字符";
        if (name.length() > 255) return "名字太长（最多 255 个字符）";
        return null;
    }

    /** 在 {@code dir} 下新建文件。返回 null 表示成功。 */
    public static String createFile(File dir, String name) {
        return create(dir, name, false);
    }

    /** 在 {@code dir} 下新建目录。返回 null 表示成功。 */
    public static String createDirectory(File dir, String name) {
        return create(dir, name, true);
    }

    private static String create(File dir, String name, boolean directory) {
        if (dir == null) return "目录不存在";
        String error = nameError(name);
        if (error != null) return error;
        if (!dir.isDirectory()) return "目录不存在：" + dir.getAbsolutePath();

        File target = new File(dir, name);
        if (target.exists()) return "「" + name + "」已经存在";
        boolean ok = directory ? target.mkdirs() : createEmptyFile(target);
        if (!ok) return "创建失败（可能没有写入权限）";
        return null;
    }

    /**
     * 重命名（同目录内改名）。
     *
     * <p>用 {@code renameTo} 而不是"复制+删除"：后者在目录上等于递归复制，
     * 在手机上一次误操作能卡住几十秒；而同卷 rename 是原子的。
     */
    public static String rename(File target, String newName) {
        if (target == null || !exists(target)) return "文件不存在";
        String error = nameError(newName);
        if (error != null) return error;
        if (target.getName().equals(newName)) return null;
        File parent = target.getParentFile();
        if (parent == null) return "无法确定上级目录";
        File dest = new File(parent, newName);
        if (dest.exists()) return "「" + newName + "」已经存在";
        if (!target.renameTo(dest)) {
            // 跨文件系统 rename 会失败（例如从私有目录搬到共享存储）。
            // 这里如实说清楚，而不是含糊地报"失败"。
            return "重命名失败（目标可能在不同分区，或不支持改名）";
        }
        return null;
    }

    /**
     * 删除文件或目录（目录递归）。返回 null 表示成功。
     *
     * <p>只删**这一条**，不做「保留目录里的 .git」这类聪明事 ——
     * 界面已经让用户确认过一次，这里再自作主张只会让人不敢用。
     */
    public static String delete(File target) {
        if (target == null || !exists(target)) return "文件不存在";
        String problem = deleteRecursive(target);
        if (problem != null) return problem;
        return exists(target) ? "删除失败（可能没有权限）" : null;
    }

    private static String deleteRecursive(File target) {
        if (isSymlink(target)) {
            // 符号链接只能删链接本身：跟进去删会把目标目录整个清掉
            //（`~/storage/shared` 就指着整块内部存储）。
            return target.delete() ? null : "删除失败（可能没有权限）";
        }
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    String problem = deleteRecursive(child);
                    if (problem != null) return problem;
                }
            }
        }
        return target.delete() ? null : "删除失败（可能没有权限）";
    }

    /** 保存文本。返回 null 表示成功。 */
    public static String write(File target, String text) {
        if (target == null) return "文件不存在";
        if (text == null) text = "";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_WRITE_BYTES) {
            return "内容太大（" + bytes.length + " 字节，上限 " + MAX_WRITE_BYTES + "）";
        }
        if (target.isDirectory()) return "这是一个目录，不能保存为文件";
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return "上级目录不存在：" + parent.getAbsolutePath();
        }
        // 先写临时文件再改名：中途断电/被杀不会留下一个**半截**的源文件 ——
        // 那比保存失败糟得多（用户会以为文件本来就是坏的）。
        File temp = new File(parent, "." + target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp);
             Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writer.write(text);
            writer.flush();
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return "写入失败：" + describe(e);
        }
        if (!temp.renameTo(target)) {
            // 改名失败（目标被占）时退一步直接覆盖写，别把内容丢在临时文件里。
            try (FileOutputStream out = new FileOutputStream(target);
                 Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                writer.write(text);
                writer.flush();
            } catch (IOException e) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                return "写入失败：" + describe(e);
            }
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
        return null;
    }

    /** 当前目录下与 {@code name} 冲突时的建议名：{@code a.txt} → {@code a (2).txt}。 */
    public static String suggestName(File dir, String name) {
        if (dir == null || !dir.isDirectory()) return name;
        if (!new File(dir, name).exists()) return name;
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        // 隐藏文件（.bashrc）整体当名字，不把 `.bashrc` 拆成空名字 + 扩展名。
        if (dot > 0 && dot < name.length() - 1) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = base + " (" + i + ")" + ext;
            if (!new File(dir, candidate).exists()) return candidate;
        }
        return name;
    }

    /** 目录里的项数（用于删除前的提示）。读不到时返回 -1。 */
    public static int childCount(File dir) {
        if (dir == null || !dir.isDirectory()) return -1;
        String[] children = dir.list();
        return children == null ? -1 : children.length;
    }

    /**
     * 递归清点要被删除的条目数，用于确认文案（"删除 12 项"）。
     *
     * <p>两条规则，都写在测试里（{@code countIncludesTheTargetItself}）：
     * <ul>
     *   <li><b>目标自己算一条</b> —— 删一个空目录是 1 而不是 0，那个目录确实会消失；</li>
     *   <li><b>符号链接算 1 条、不跟进去</b> —— 跟进去会把链接目标（可能指着整块内部存储）
     *       也数进来，而它根本不会被删。计数一旦虚高，确认文案就在骗人。</li>
     * </ul>
     */
    public static int countForDelete(File target) {
        if (target == null || !exists(target)) return 0;
        if (isSymlink(target) || !target.isDirectory()) return 1;
        int total = 0;
        File[] children = target.listFiles();
        if (children != null) {
            for (File child : children) total += countForDelete(child);
        }
        return total + 1;
    }

    /** 路径是否是 {@code root} 之下（或就是它）。规范化比较，符号链接会走出 root。 */
    public static boolean isInside(File root, File target) {
        if (root == null || target == null) return false;
        try {
            String r = root.getCanonicalPath();
            String t = target.getCanonicalPath();
            return t.equals(r) || t.startsWith(r + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    /** 可写性探测：能不能在这个目录里建东西。 */
    public static boolean canWrite(File dir) {
        return dir != null && dir.isDirectory() && dir.canWrite();
    }

    /** 目录里可读的子项数量（列不出来时返回 -1，界面据此区分"空"与"读不到"）。 */
    public static List<String> listNames(File dir) {
        if (dir == null || !dir.isDirectory()) return new ArrayList<>();
        String[] children = dir.list();
        if (children == null) return new ArrayList<>();
        List<String> out = new ArrayList<>(Arrays.asList(children));
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static boolean createEmptyFile(File target) {
        try {
            return target.createNewFile();
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean exists(File file) {
        return java.nio.file.Files.exists(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean isSymlink(File file) {
        return java.nio.file.Files.isSymbolicLink(file.toPath());
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }
}
