package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨多个文件一次做多处精确替换，全部校验通过才落盘。
 *
 * <h3>「原子」指的是校验，不是写盘</h3>
 * 这个工具保证的是：只要有一处编辑校验不过（文本找不到、出现多次而没指定
 * {@code replace_all}），**一个文件都不会被改**。写盘阶段则做不到真正的事务
 * （Android 上没有跨文件的原子替换），所以退而求其次：写失败时
 * 把已经写过的文件恢复成原内容（见 {@link #commit}）。
 *
 * <p>没有这层保护时最糟的情况是「前三个文件改好了、第四个失败」——
 * 用户拿到一个半成品改动，而错误信息只说第四处没找到，看不出前三个已经改了。
 *
 * <h3>为什么按文件聚合而不是按编辑顺序</h3>
 * 同一个文件被多次编辑时不能每次都读盘写盘：第二次编辑必须看到第一次的结果，
 * 否则两处编辑互相覆盖。所以先把每个文件的最终内容在内存里算出来
 * （{@link #working}），最后每个文件只写一次。
 *
 * <h3>计数语义</h3>
 * 报的替换次数是**编辑条数**而不是实际替换处数：一处 {@code replace_all}
 * 可能替换几百处，而用户关心的是「我提交了 5 条编辑、动了 3 个文件」。
 */
final class MultiEditTool implements ZhiTool {

    private static final long MAX_BYTES = 20L * 1024L * 1024L;

    @Override public String name() { return "MultiEdit"; }

    @Override public String description() {
        return "Apply multiple exact old_text -> new_text edits across one or more files atomically"
            + " after validating every requested match. Useful for coordinated refactors.";
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.WRITE; }

    @Override public JSONObject inputSchema() {
        try {
            JSONObject editProperties = new JSONObject();
            editProperties.put("path", ToolSchemas.string("File path."));
            editProperties.put("old_text", ToolSchemas.string("Exact text that must exist."));
            editProperties.put("new_text", ToolSchemas.string("Replacement text."));
            editProperties.put("replace_all",
                ToolSchemas.bool("Replace every occurrence instead of exactly one."));
            JSONObject editSchema = ToolSchemas.object(editProperties, "path", "old_text", "new_text");
            JSONObject properties = new JSONObject().put("edits", new JSONObject()
                .put("type", "array").put("minItems", 1).put("items", editSchema));
            return ToolSchemas.object(properties, "edits");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        JSONArray edits = input.getJSONArray("edits");
        Batch batch = new Batch();

        for (int i = 0; i < edits.length(); i++) {
            String rejection = batch.apply(config, edits.getJSONObject(i), i + 1);
            if (rejection != null) return ToolExecutionResult.error(rejection);
        }

        List<UnifiedDiff.Result> diffs = batch.diffOfEachFile();
        commit(batch);
        UnifiedDiff.Result combined = UnifiedDiff.combine(diffs);
        return ToolExecutionResult.okWithDiff(
            "Applied " + edits.length() + " edit(s) across " + batch.fileCount() + " file(s).",
            combined.text, combined.additions, combined.deletions);
    }

    /**
     * 在内存里累积改动。三个 Map 都以文件为键、保持插入顺序：
     * 输出里的文件顺序与用户提交编辑的顺序一致，报告才读得懂。
     */
    private static final class Batch {
        /** 改动前的内容，恢复时用。 */
        private final Map<File, String> originals = new LinkedHashMap<>();
        /** 当前累积的内容。 */
        private final Map<File, String> working = new LinkedHashMap<>();
        /** 用户写下的路径，用于 diff 标题（绝对路径在里面太长了）。 */
        private final Map<File, String> displayPaths = new LinkedHashMap<>();

        /**
         * 应用一条编辑。
         *
         * @param ordinal 编辑序号（从 1 起），只用于错误信息 ——
         *        用户与模型都需要知道「是第几条」没成功
         * @return 错误文案；成功返回 {@code null}
         */
        private String apply(SessionConfig config, JSONObject edit, int ordinal) throws Exception {
            String requestedPath = edit.getString("path");
            File file = PathPolicy.resolve(config.projectDirectory, requestedPath).getCanonicalFile();
            if (!file.isFile()) return "Not a file: " + file;
            if (PathPolicy.isDangerousPseudoFile(file)) return "Refusing pseudo-file: " + file;
            if (file.length() > MAX_BYTES) {
                return "Refusing to edit a file larger than 20 MiB: " + file;
            }

            // 第一次碰到这个文件时才读盘：之后再编辑它要看的是累积结果。
            if (!working.containsKey(file)) {
                String source = TextFiles.readText(file);
                originals.put(file, source);
                working.put(file, source);
                displayPaths.put(file, requestedPath);
            }

            String source = working.get(file);
            String oldText = edit.getString("old_text");
            String newText = edit.getString("new_text");
            if (oldText.isEmpty()) return "old_text must not be empty for edit #" + ordinal;

            int occurrences = countOccurrences(source, oldText);
            if (occurrences == 0) return "old_text not found in " + file + " for edit #" + ordinal;

            boolean replaceAll = edit.optBoolean("replace_all", false);
            if (!replaceAll && occurrences != 1) {
                return "old_text appears " + occurrences + " times in " + file
                    + "; make edit #" + ordinal + " more specific or set replace_all=true";
            }
            working.put(file, replaceAll
                ? source.replace(oldText, newText)
                : replaceFirst(source, oldText, newText));
            return null;
        }

        private List<UnifiedDiff.Result> diffOfEachFile() {
            List<UnifiedDiff.Result> diffs = new ArrayList<>();
            for (Map.Entry<File, String> entry : working.entrySet()) {
                diffs.add(UnifiedDiff.create(displayPaths.get(entry.getKey()),
                    originals.get(entry.getKey()), entry.getValue(), true));
            }
            return diffs;
        }

        private int fileCount() {
            return working.size();
        }
    }

    /**
     * 落盘，失败时回滚已写过的文件。
     *
     * <p>回滚本身也可能失败（磁盘满、权限被改），那时**没有**可靠的做法 ——
     * 所以吞掉回滚的异常，把最初的失败抛出去。原始的失败信息才是用户需要的，
     * 用回滚时的第二个异常覆盖它只会让人看不出到底哪里坏了。
     */
    private static void commit(Batch batch) throws Exception {
        List<File> written = new ArrayList<>();
        try {
            for (Map.Entry<File, String> entry : batch.working.entrySet()) {
                TextFiles.writeText(entry.getKey(), entry.getValue());
                written.add(entry.getKey());
            }
        } catch (Exception failure) {
            for (File file : written) {
                try {
                    TextFiles.writeText(file, batch.originals.get(file));
                } catch (Exception ignored) {
                    // 见上：保留最初的失败。
                }
            }
            throw failure;
        }
    }

    /** 不重叠地数出现次数：按 needle 长度步进，否则 {@code "aa"} 在 {@code "aaaa"} 里会数成 3。 */
    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) return count;
            count++;
            from = at + needle.length();
        }
    }

    private static String replaceFirst(String source, String oldText, String newText) {
        int at = source.indexOf(oldText);
        return source.substring(0, at) + newText + source.substring(at + oldText.length());
    }
}
