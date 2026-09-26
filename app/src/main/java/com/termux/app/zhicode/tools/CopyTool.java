package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * 拷贝文件或目录树。
 *
 * <h3>为什么不直接用 {@code java.nio.file.Files.copy}</h3>
 * 它要 API 26，而本工程要跑到 Android 7。自己走流还有个附带好处：
 * 能顺手把权限与修改时间一起搬过去 —— {@code Files.copy} 得额外传
 * {@code COPY_ATTRIBUTES} 才有同样效果。
 *
 * <h3>属性为什么要显式搬</h3>
 * 拷贝出来的脚本必须仍然可执行，否则「拷了一份脚本然后跑不动」这种问题
 * 每次都要重新排查。修改时间则被 Glob 用来按新旧排序。
 */
public final class CopyTool implements ZhiTool {

    private static final int BUFFER_BYTES = 64 * 1024;

    @Override public String name() { return "Copy"; }

    @Override public String description() {
        return "Copy a file or directory tree inside the workspace. Parent directories are created automatically.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.WRITE; }

    @Override public JSONObject inputSchema() {
        try {
            JSONObject properties = new JSONObject();
            properties.put("source", ToolSchemas.string("Source path."));
            properties.put("destination", ToolSchemas.string("Destination path."));
            properties.put("overwrite", ToolSchemas.bool("Replace existing destination files."));
            return ToolSchemas.object(properties, "source", "destination");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        File source = PathPolicy.resolve(config.projectDirectory, input.getString("source"));
        File destination = PathPolicy.resolve(config.projectDirectory, input.getString("destination"));
        if (!source.exists()) return ToolExecutionResult.error("Source does not exist: " + source);
        // 同一个路径会让递归拷贝把自己拷进自己，最终耗尽磁盘。
        if (source.equals(destination)) {
            return ToolExecutionResult.error("Source and destination are the same path.");
        }
        copy(source, destination, input.optBoolean("overwrite", false));
        return ToolExecutionResult.ok("Copied " + source + " -> " + destination);
    }

    /**
     * 递归拷贝。
     *
     * @param overwrite 目标文件已存在时是否覆盖。目录**总是**合并 ——
     *        「覆盖一个目录」没有定义（要删掉里面多出来的文件吗？），
     *        而合并是递归拷贝里唯一不会造成意外丢失的语义。
     */
    static void copy(File source, File destination, boolean overwrite) throws Exception {
        if (source.isDirectory()) {
            copyDirectory(source, destination, overwrite);
        } else {
            copyFile(source, destination, overwrite);
        }
    }

    private static void copyDirectory(File source, File destination, boolean overwrite) throws Exception {
        if (destination.exists() && !destination.isDirectory()) {
            throw new IllegalArgumentException("Destination exists and is not a directory: " + destination);
        }
        if (!destination.exists() && !destination.mkdirs()) {
            throw new IllegalStateException("Cannot create directory: " + destination);
        }
        File[] children = source.listFiles();
        if (children == null) return;
        for (File child : children) {
            copy(child, new File(destination, child.getName()), overwrite);
        }
    }

    private static void copyFile(File source, File destination, boolean overwrite) throws Exception {
        if (destination.exists() && !overwrite) {
            throw new IllegalStateException("Destination already exists: " + destination);
        }
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create parent: " + parent);
        }
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(destination, false)) {
            byte[] buffer = new byte[BUFFER_BYTES];
            int count;
            while ((count = in.read(buffer)) >= 0) out.write(buffer, 0, count);
        }
        copyAttributes(source, destination);
    }

    /**
     * 搬权限位与修改时间。
     *
     * <p>返回值刻意不检查（原来的写法也没有）：Android 的应用目录通常允许这些操作，
     * 但外部存储上不行，而「属性没设上」远不到值得让整次拷贝失败的程度 ——
     * 内容已经写好了。
     */
    private static void copyAttributes(File source, File destination) {
        destination.setExecutable(source.canExecute(), true);
        destination.setReadable(source.canRead(), true);
        destination.setWritable(source.canWrite(), true);
        destination.setLastModified(source.lastModified());
    }
}
