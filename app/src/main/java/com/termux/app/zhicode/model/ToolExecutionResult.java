package com.termux.app.zhicode.model;

import org.json.JSONArray;

/**
 * 一次工具执行的结果。
 *
 * <p>字段全部公开且只读：结果是「工具 → 引擎 → 界面」的单向数据，
 * 引擎不会回头改它。
 *
 * <h3>关于 {@link #diff} 与 {@link #addedLines}/{@link #deletedLines}</h3>
 * 这三项只给**界面**用（工具卡上的 +N −M 徽章、以及展开时看的 diff 文本），
 * **不会**作为工具输出发给模型：
 * 模型需要的是「改了什么」，一段统一 diff 既占 token、又不如工具自述的摘要准确。
 *
 * <h3>关于附加内容</h3>
 * {@link #okWithAdditionalContent} 携带的是**结构化**的附加内容块
 * （例如界面画布要渲染的文档）。它由引擎追加到工具结果消息里，
 * 与给模型看的 {@link #content} 文本是两回事。
 */
public final class ToolExecutionResult {

    /** 是否算失败。决定引擎给模型的消息里带不带 {@code is_error}。 */
    public final boolean isError;

    /** 给模型看的文本。 */
    public final String content;

    /** 命令类工具的退出码；非命令类工具是 0（成功）或 -1（错误）。 */
    public final int exitCode;

    /** 统一 diff，仅供界面显示，不发给模型。 */
    public final String diff;

    public final int addedLines;
    public final int deletedLines;

    private final JSONArray additionalContent;

    private ToolExecutionResult(boolean isError, String content, int exitCode,
                                String diff, int addedLines, int deletedLines, JSONArray additionalContent) {
        this.isError = isError;
        this.content = content == null ? "" : content;
        this.exitCode = exitCode;
        this.diff = diff == null ? "" : diff;
        this.addedLines = Math.max(0, addedLines);
        this.deletedLines = Math.max(0, deletedLines);
        this.additionalContent = copyOf(additionalContent);
    }

    /** 成功，没有附加内容和 diff。 */
    public static ToolExecutionResult ok(String content) {
        return plain(false, content, 0);
    }

    /** 成功，并携带结构化附加内容。 */
    public static ToolExecutionResult okWithAdditionalContent(String content, JSONArray additionalContent) {
        return new ToolExecutionResult(false, content, 0, "", 0, 0, additionalContent);
    }

    /** 成功，并带上供界面显示的变更统计。 */
    public static ToolExecutionResult okWithDiff(String content, String diff, int addedLines, int deletedLines) {
        return new ToolExecutionResult(false, content, 0, diff, addedLines, deletedLines, null);
    }

    /**
     * 命令类工具的结果：**退出码非 0 即为失败**。
     *
     * <p>退出码原样保留（不折叠成 0/1）：模型常常据此判断该重试还是改命令。
     */
    public static ToolExecutionResult command(int exitCode, String content) {
        return plain(exitCode != 0, content, exitCode);
    }

    /** 工具自己判定失败，且没有可用的退出码。 */
    public static ToolExecutionResult error(String content) {
        return plain(true, content, -1);
    }

    /**
     * 没有 diff、没有附加内容时的最短路径。
     *
     * <p>单独抽出来是因为它是绝大多数结果走的路径（读文件、grep、命令成功等）。
     */
    private static ToolExecutionResult plain(boolean isError, String content, int exitCode) {
        return new ToolExecutionResult(isError, content, exitCode, "", 0, 0, null);
    }

    /**
     * 结构化附加内容。
     *
     * <p>返回的是**内部数组本身**，不是副本：这次调用发生在每一次工具结果上，
     * 而调用方（引擎）只遍历不修改。谁要改它请自己先复制 ——
     * 构造时已经做过一次防御性拷贝，副本的修改不会影响这里。
     */
    public JSONArray additionalContent() {
        return additionalContent;
    }

    /**
     * 深拷一份附加内容。
     *
     * <p>空与缺失都归到空数组：调用方遍历时不必判空。
     * 内容非法（不是合法 JSON 结构）时直接抛错，而不是静默丢掉 ——
     * 那会让一次本该显示出来的界面更新凭空消失。
     */
    private static JSONArray copyOf(JSONArray content) {
        if (content == null || content.length() == 0) return new JSONArray();
        try {
            return new JSONArray(content.toString());
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid additional tool content", invalid);
        }
    }
}
