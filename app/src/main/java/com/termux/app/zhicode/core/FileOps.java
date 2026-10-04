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

    /**
     * 将文件或目录复制到目标目录，目标使用原名且绝不覆盖。返回 null 表示成功。
     * 符号链接（包括目录树中的链接）一律拒绝，避免复制时越过用户可见的路径边界。
     */
    public static String copy(File source, File destinationDirectory) {
        if (source == null || !exists(source)) return "文件不存在";
        if (destinationDirectory == null || !destinationDirectory.isDirectory()) return "目标目录不存在";
        if (!destinationDirectory.canWrite()) return "目标目录不可写";
        String linkError = validateCopyTree(source);
        if (linkError != null) return linkError;

        File destination = new File(destinationDirectory, source.getName());
        if (exists(destination)) return "目标已存在：「" + source.getName() + "」";
        if (source.isDirectory() && isInside(source, destination)) return "不能把目录复制到自身或其子目录";
        if (sameFile(source, destination)) return "源文件与目标相同";

        boolean created = source.isDirectory() ? destination.mkdir() : createEmptyFile(destination);
        if (!created) return "创建目标失败：「" + source.getName() + "」";
        String error = source.isDirectory()
                ? copyChildren(source, destination)
                : copyFileContents(source, destination);
        if (error != null) {
            String cleanupError = deleteRecursive(destination);
            return cleanupError == null ? error : error + "；清理未完成的目标失败：" + cleanupError;
        }
        return null;
    }

    /** 跨目录移动。复制完整成功后才删除源；若源删除失败，会保留副本并明确报告。 */
    public static String move(File source, File destinationDirectory) {
        if (source == null || !exists(source)) return "文件不存在";
        if (destinationDirectory == null || !destinationDirectory.isDirectory()) return "目标目录不存在";
        File destination = new File(destinationDirectory, source.getName());
        if (sameFile(source, destination)) return "源文件与目标相同";
        String error = copy(source, destinationDirectory);
        if (error != null) return error;
        error = delete(source);
        if (error != null) return "已复制到目标目录，但源文件删除失败：" + error;
        return null;
    }

    private static String validateCopyTree(File source) {
        if (isSymlink(source)) return "不支持复制符号链接";
        if (!source.isDirectory()) return source.isFile() && source.canRead() ? null : "源文件不可读";
        File[] children = source.listFiles();
        if (children == null) return "无法读取目录：「" + source.getName() + "」";
        for (File child : children) {
            String error = validateCopyTree(child);
            if (error != null) return error;
        }
        return null;
    }

    private static String copyChildren(File source, File destination) {
        File[] children = source.listFiles();
        if (children == null) return "无法读取目录：「" + source.getName() + "」";
        for (File child : children) {
            if (isSymlink(child)) return "不支持复制符号链接";
            File target = new File(destination, child.getName());
            boolean created = child.isDirectory() ? target.mkdir() : createEmptyFile(target);
            if (!created) return "创建目标失败：「" + child.getName() + "」";
            String error = child.isDirectory() ? copyChildren(child, target) : copyFileContents(child, target);
            if (error != null) return error;
        }
        return null;
    }

    private static String copyFileContents(File source, File destination) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(source);
             FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            out.getFD().sync();
            return null;
        } catch (IOException e) {
            return "复制失败：「" + source.getName() + "」：" + describe(e);
        }
    }

    private static boolean sameFile(File first, File second) {
        try {
            return first.getCanonicalFile().equals(second.getCanonicalFile());
        } catch (IOException e) {
            return first.getAbsoluteFile().equals(second.getAbsoluteFile());
        }
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

    /*
     * 「建议名」`suggestName(dir, name)`（`a.txt` → `a (2).txt`）**已经删掉**。
     *
     * <p>它唯一的调用者是文件面板的「新建」表单：那里原先**预填**名字，
     * 所以需要一个避开重名的建议名。现在新建弹窗的名字框是**空的**
     * （见 `WorkspaceViewModel.newFileForm`），建什么类型也由按下的按钮决定 ——
     * 一个"还没输入就先替你起好名字"的助手没有用武之地了。
     *
     * <p>重名不再靠"提前改名"规避，而是提交时由 {@link #create} 拒绝并把原因
     * 回填进弹窗（用户会看到「「x」已经存在」）。这条路更短，也不会出现
     * "预填的名字其实已经被别人占用"那种自相矛盾的提示。
     *
     * <p>⚠️ 别再把它加回来当通用工具：没有调用者的工具函数会慢慢被人当成
     * "现有能力"引用（而它连一条测试都没有了）。真需要时再连同用例一起加。
     */

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
