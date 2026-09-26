package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONObject;

import java.io.File;

/**
 * 移动或重命名。
 *
 * <h3>为什么需要「拷贝 + 删源」这条退路</h3>
 * {@code File.renameTo} 在 Java 上对**跨文件系统**的移动会直接失败（返回 false，
 * 且不抛异常）。工程目录与被移动的文件常常不在同一个挂载点上
 * （内部存储 vs. 外部存储、或不同分区），所以只靠 renameTo 会让这个工具
 * 在某些路径上莫名其妙地失败。
 *
 * <p>退路里两次写盘的分工是：先拷（内容已经在目标处了），再删源。
 * 如果删源失败，**不能**报成功 —— 那会留下两份副本，而用户以为已经移动完了。
 * 所以那时报错，并且错误信息里说清「目标已写好、源还在」，
 * 让人知道该手工删什么。
 *
 * <h3>目标已存在时的处理</h3>
 * 默认拒绝（不覆盖），要求显式 {@code overwrite=true}。覆盖前先删目标，
 * 是因为 {@code renameTo} 到已存在路径的行为依实现而异（有的覆盖、有的失败），
 * 先清掉才能让两条路径（rename 与 copy）表现一致。
 */
public final class MoveTool implements ZhiTool {

    @Override public String name() { return "Move"; }

    @Override public String description() {
        return "Move or rename a file or directory inside the workspace.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.WRITE; }

    @Override public JSONObject inputSchema() {
        try {
            JSONObject properties = new JSONObject()
                .put("source", ToolSchemas.string("Source path."))
                .put("destination", ToolSchemas.string("Destination path."))
                .put("overwrite", ToolSchemas.bool("Replace an existing destination file."));
            return ToolSchemas.object(properties, "source", "destination");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        File source = PathPolicy.resolve(config.projectDirectory, input.getString("source"));
        File destination = PathPolicy.resolve(config.projectDirectory, input.getString("destination"));
        if (!source.exists()) return ToolExecutionResult.error("Source does not exist: " + source);
        if (source.equals(destination)) {
            return ToolExecutionResult.error("Source and destination are the same path.");
        }

        String conflict = clearDestination(destination, input.optBoolean("overwrite", false));
        if (conflict != null) return ToolExecutionResult.error(conflict);

        File parent = destination.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        if (!source.renameTo(destination)) {
            String fallback = moveAcrossFilesystems(source, destination);
            if (fallback != null) return ToolExecutionResult.error(fallback);
        }
        return ToolExecutionResult.ok("Moved " + source + " -> " + destination);
    }

    /** 处理「目标已存在」；返回错误文案，或把目标清空后返回 {@code null}。 */
    private static String clearDestination(File destination, boolean overwrite) {
        if (!destination.exists()) return null;
        if (!overwrite) return "Destination already exists: " + destination;
        if (!removeRecursively(destination)) return "Cannot replace destination: " + destination;
        return null;
    }

    /** {@code renameTo} 失败时的退路：拷贝 + 删源。返回错误文案或 {@code null}。 */
    private static String moveAcrossFilesystems(File source, File destination) throws Exception {
        CopyTool.copy(source, destination, true);
        if (!removeRecursively(source)) {
            return "Copied destination but could not remove source: " + source;
        }
        return null;
    }

    private static boolean removeRecursively(File target) {
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!removeRecursively(child)) return false;
                }
            }
        }
        return target.delete();
    }
}
