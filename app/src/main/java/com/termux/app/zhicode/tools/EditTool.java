package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONObject;

import java.io.File;

/**
 * 精确字符串替换。
 *
 * <h3>为什么默认要求「只出现一次」</h3>
 * 这是这个工具最重要的约束。模型给一段待替换文本时，如果它在文件里出现多次，
 * 「替换哪一个」是**无从判断**的 —— 猜一个就有 50% 概率改错地方，
 * 而改错地方的后果往往编译还通过、行为却变了。所以这里宁可报错，
 * 让模型补足上下文再来（报错信息里带上了实际出现次数，它据此就能判断要补多少）。
 *
 * <p>{@code replace_all=true} 是显式的逃生口：批量重命名之类的场景确实要全换，
 * 但那必须是模型**说出来的**意图，而不是它没注意到时的默认行为。
 *
 * <h3>为什么不用正则</h3>
 * 替换串里出现反斜杠、{@code $} 是常态（Java 代码、shell 脚本）。
 * 走正则就得先转义替换串，而转义规则对模型来说是不透明的 ——
 * 它看到的是「我明明写了 {@code $1}，结果文件里变成了别的」。
 */
final class EditTool implements ZhiTool {

    private static final long MAX_BYTES = 20L * 1024L * 1024L;

    @Override public String name() { return "Edit"; }

    @Override public String description() {
        return "Replace an exact string in a UTF-8 text file. By default the old string must occur"
            + " exactly once; set replace_all to replace every occurrence.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.WRITE; }

    @Override public JSONObject inputSchema() {
        JSONObject properties = new JSONObject();
        try {
            properties.put("file_path", ToolSchemas.string("Absolute or project-relative file path."));
            properties.put("old_string", ToolSchemas.string("Exact text to replace."));
            properties.put("new_string", ToolSchemas.string("Replacement text."));
            properties.put("replace_all",
                ToolSchemas.bool("Replace every occurrence instead of requiring exactly one."));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return ToolSchemas.object(properties, "file_path", "old_string", "new_string");
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        File file = PathPolicy.resolve(config.projectDirectory, input.optString("file_path", ""));
        if (!file.isFile()) return ToolExecutionResult.error("File does not exist: " + file);
        if (file.length() > MAX_BYTES) {
            return ToolExecutionResult.error("Refusing to edit a file larger than 20 MiB.");
        }

        String oldString = input.optString("old_string", "");
        String newString = input.optString("new_string", "");
        // 空 old_string 必须单独挡住：它在 String.indexOf 上会命中位置 0，
        // 于是「替换一次」变成在文件开头插入一段文本 —— 一个静默的错误。
        if (oldString.isEmpty()) return ToolExecutionResult.error("old_string must not be empty");

        String original = TextFiles.readText(file);
        int occurrences = countOccurrences(original, oldString);
        if (occurrences == 0) {
            return ToolExecutionResult.error("old_string was not found in " + file.getAbsolutePath());
        }

        boolean replaceAll = input.optBoolean("replace_all", false);
        if (!replaceAll && occurrences != 1) {
            return ToolExecutionResult.error("old_string occurs " + occurrences
                + " times; provide more context or set replace_all=true");
        }

        String updated = replaceAll
            ? original.replace(oldString, newString)
            : replaceFirst(original, oldString, newString);
        int replaced = replaceAll ? occurrences : 1;

        UnifiedDiff.Result diff = UnifiedDiff.create(input.optString("file_path", file.getPath()),
            original, updated, true);
        TextFiles.writeText(file, updated);
        return ToolExecutionResult.okWithDiff(
            "Updated " + file.getAbsolutePath() + " (" + replaced + " replacement(s))",
            diff.text, diff.additions, diff.deletions);
    }

    /**
     * 统计不重叠的出现次数。
     *
     * <p>步进是 {@code needle.length()} 而不是 1：按 1 步进的话，
     * {@code "aa"} 在 {@code "aaaa"} 里会被数成 3 次，而实际能替换的只有 2 处。
     * 报错信息里的次数会因此偏大，模型据此补的上下文也就偏多。
     */
    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) return count;
            count++;
            from = at + needle.length();
        }
    }

    /** 只替换第一处。调用前已确保它存在。 */
    private static String replaceFirst(String text, String oldString, String newString) {
        int at = text.indexOf(oldString);
        return text.substring(0, at) + newString + text.substring(at + oldString.length());
    }
}
