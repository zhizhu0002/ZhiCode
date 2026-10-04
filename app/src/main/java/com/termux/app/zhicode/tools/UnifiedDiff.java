package com.termux.app.zhicode.tools;

import java.util.ArrayList;
import java.util.List;

/**
 * 生成给界面看的统一 diff。
 *
 * <h3>它不参与写入</h3>
 * 这个类**只生成展示文本**：真正的改动由各工具自己写盘，diff 只是随后被记下来
 * 给用户看。所以这里可以容忍近似（见下面的「粗粒度」分支），
 * 而如果它参与了写入，「近似」就是不可接受的。
 *
 * <h3>为什么要有一个粗粒度分支</h3>
 * 精确 diff 走的是 LCS 动态规划，空间是 {@code O(旧行数 × 新行数)}。
 * 对一个 3000 行的文件改一行，那是九百万个 int —— 上百 MB，必然 OOM。
 * 所以单元格数超过 {@value #MAX_LCS_CELLS} 就换一条路：直接取公共前后缀，
 * 把中间整段当成「改了」。展示上仍然是对的（人看得出改的是哪一段），
 * 代价只是改动统计不如精确时细。
 *
 * <h3>输出上限</h3>
 * {@value #MAX_DISPLAY_CHARS} 字符后截断。它只在工具卡里展开显示、给人扫一眼，
 * 超过这个长度的 diff 没有人会读，但它会一直留在内存里并进入会话文件。
 */
final class UnifiedDiff {

    /** LCS 单元格上限，见类注释。 */
    private static final long MAX_LCS_CELLS = 1_200_000L;
    /** 展示文本上限。 */
    private static final int MAX_DISPLAY_CHARS = 140_000;
    /** 单次改动最多展示多少行（粗粒度分支用）。 */
    private static final int MAX_CHANGED_LINES_SHOWN = 500;
    /** 删除时最多列出多少行。 */
    private static final int MAX_DELETED_LINES_SHOWN = 1200;
    /** diff 块前后的上下文行数。3 是通用 diff 的惯例。 */
    private static final int CONTEXT_LINES = 3;

    private static final String TRUNCATED = "\n@@ … diff display truncated … @@\n";

    /** 一次 diff 的结果：展示文本 + 增删行数。 */
    static final class Result {
        final String text;
        final int additions;
        final int deletions;

        Result(String text, int additions, int deletions) {
            this.text = text == null ? "" : text;
            this.additions = Math.max(0, additions);
            this.deletions = Math.max(0, deletions);
        }
    }

    /**
     * 一行 diff。
     *
     * <p>{@link #kind} 用 {@code ' '} / {@code '+'} / {@code '-'} 而不是枚举：
     * 前一个字符会被直接拼进输出文本，用枚举反而多一次映射。
     */
    private static final class Op {
        private static final char SAME = ' ';
        private static final char ADD = '+';
        private static final char REMOVE = '-';

        final char kind;
        final String text;

        Op(char kind, String text) {
            this.kind = kind;
            this.text = text == null ? "" : text;
        }
    }

    private UnifiedDiff() {}

    /**
     * 一次「修改」的 diff。
     *
     * @param existed 目标文件之前是否存在。决定左标题是 {@code a/路径} 还是
     *                {@code /dev/null} —— 后者是「新建」的通用表示，界面据此
     *                显示「新增文件」而不是「改了一个不存在的文件」
     */
    static Result create(String path, String before, String after, boolean existed) {
        String[] oldLines = lines(before);
        String[] newLines = lines(after);
        List<Op> ops = diff(oldLines, newLines);

        Counts counts = countChanges(ops, oldLines, newLines);
        // 两边都没变就返回空结果：界面据此跳过一条没有内容的变更记录，
        // 而不是显示一个标题下面什么都没有的卡片。
        if (counts.additions == 0 && counts.deletions == 0) return new Result("", 0, 0);

        String shownPath = cleanPath(path);
        StringBuilder out = new StringBuilder();
        out.append("--- ").append(existed ? "a/" + shownPath : "/dev/null").append('\n');
        out.append("+++ b/").append(shownPath).append('\n');
        appendHunks(out, ops);

        if (out.length() > MAX_DISPLAY_CHARS) {
            out.setLength(MAX_DISPLAY_CHARS);
            out.append(TRUNCATED);
        }
        return new Result(out.toString(), counts.additions, counts.deletions);
    }

    /** 一次删除的 diff。全部算删除行，不产生新增。 */
    static Result deleted(String path, String before) {
        String[] oldLines = lines(before);
        if (oldLines.length == 0) return new Result("", 0, 0);

        StringBuilder out = new StringBuilder();
        out.append("--- a/").append(cleanPath(path)).append('\n');
        out.append("+++ /dev/null\n");
        out.append("@@ -1,").append(oldLines.length).append(" +0,0 @@\n");
        int shown = Math.min(oldLines.length, MAX_DELETED_LINES_SHOWN);
        for (int i = 0; i < shown && out.length() < MAX_DISPLAY_CHARS; i++) {
            out.append(Op.REMOVE).append(oldLines[i]).append('\n');
        }
        if (shown < oldLines.length) {
            out.append("@@ … ").append(oldLines.length - shown).append(" deleted lines omitted … @@\n");
        }
        return new Result(out.toString(), 0, oldLines.length);
    }

    /**
     * 把多次改动合成一份。
     *
     * <p>计数是**相加**（每次改动都是独立的文件），文本之间加一个空行分隔。
     * 空文本的条目被跳过 —— 它们是没有实际改动的那些。
     */
    static Result combine(List<Result> results) {
        StringBuilder text = new StringBuilder();
        int additions = 0;
        int deletions = 0;
        if (results != null) {
            for (Result result : results) {
                if (result == null) continue;
                additions += result.additions;
                deletions += result.deletions;
                if (result.text.isEmpty()) continue;
                if (text.length() > 0) text.append('\n');
                text.append(result.text);
            }
        }
        return new Result(text.toString(), additions, deletions);
    }

    /** 增删行数。精确分支数 op，粗粒度分支用前后缀算。 */
    private static Counts countChanges(List<Op> ops, String[] oldLines, String[] newLines) {
        boolean coarse = cells(oldLines.length, newLines.length) > MAX_LCS_CELLS;
        if (!coarse) {
            Counts exact = new Counts();
            for (Op op : ops) {
                if (op.kind == Op.ADD) exact.additions++;
                else if (op.kind == Op.REMOVE) exact.deletions++;
            }
            return exact;
        }
        int prefix = commonPrefix(oldLines, newLines);
        int suffix = commonSuffix(oldLines, newLines, prefix);
        Counts coarseCounts = new Counts();
        coarseCounts.deletions = oldLines.length - prefix - suffix;
        coarseCounts.additions = newLines.length - prefix - suffix;
        return coarseCounts;
    }

    private static final class Counts {
        private int additions;
        private int deletions;
    }

    private static long cells(int oldCount, int newCount) {
        return (long) oldCount * (long) newCount;
    }

    // ------------------------------------------------------------------ 求差

    /**
     * 精确 diff（LCS）。
     *
     * <p>回溯时的取舍规则要留意：当两个方向的后继 LCS 长度**相等**时取删除。
     * 取新增也是合法的 diff，但两种选择在同一个文件上会给出不同的输出，
     * 而输出的稳定性会让「同一处改动前后两次跑出来的 diff 不一样」——
     * 那看起来像是又改了一次。
     */
    private static List<Op> diff(String[] oldLines, String[] newLines) {
        if (cells(oldLines.length, newLines.length) > MAX_LCS_CELLS) {
            return coarseDiff(oldLines, newLines);
        }
        int[][] lcs = new int[oldLines.length + 1][newLines.length + 1];
        for (int i = oldLines.length - 1; i >= 0; i--) {
            for (int j = newLines.length - 1; j >= 0; j--) {
                lcs[i][j] = oldLines[i].equals(newLines[j])
                    ? 1 + lcs[i + 1][j + 1]
                    : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<Op> ops = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < oldLines.length || j < newLines.length) {
            if (i < oldLines.length && j < newLines.length && oldLines[i].equals(newLines[j])) {
                ops.add(new Op(Op.SAME, oldLines[i]));
                i++;
                j++;
            } else if (i < oldLines.length && (j >= newLines.length || lcs[i + 1][j] >= lcs[i][j + 1])) {
                ops.add(new Op(Op.REMOVE, oldLines[i]));
                i++;
            } else {
                ops.add(new Op(Op.ADD, newLines[j]));
                j++;
            }
        }
        return ops;
    }

    /** 粗粒度 diff：公共前后缀之外的整段算作改动。见类注释。 */
    private static List<Op> coarseDiff(String[] oldLines, String[] newLines) {
        int prefix = commonPrefix(oldLines, newLines);
        int suffix = commonSuffix(oldLines, newLines, prefix);
        List<Op> ops = new ArrayList<>();

        for (int i = Math.max(0, prefix - CONTEXT_LINES); i < prefix; i++) {
            ops.add(new Op(Op.SAME, oldLines[i]));
        }
        appendLimited(ops, Op.REMOVE, oldLines, prefix, oldLines.length - prefix - suffix);
        appendLimited(ops, Op.ADD, newLines, prefix, newLines.length - prefix - suffix);
        int trailingEnd = Math.min(oldLines.length, oldLines.length - suffix + CONTEXT_LINES);
        for (int i = oldLines.length - suffix; i < trailingEnd; i++) {
            if (i >= 0) ops.add(new Op(Op.SAME, oldLines[i]));
        }
        return ops;
    }

    /**
     * 追加最多 {@value #MAX_CHANGED_LINES_SHOWN} 行改动，超出部分用一行说明代替。
     *
     * <p>不留这行说明的话，粗粒度 diff 看起来就像「改动只有这么多」——
     * 而它其实是「只显示这么多」。
     */
    private static void appendLimited(List<Op> ops, char kind, String[] lines, int start, int count) {
        int head = Math.min(count, MAX_CHANGED_LINES_SHOWN);
        for (int i = 0; i < head; i++) ops.add(new Op(kind, lines[start + i]));
        if (head < count) {
            ops.add(new Op(Op.SAME, "… " + (count - head) + " changed lines omitted from preview …"));
        }
    }

    private static int commonPrefix(String[] a, String[] b) {
        int prefix = 0;
        while (prefix < a.length && prefix < b.length && a[prefix].equals(b[prefix])) prefix++;
        return prefix;
    }

    private static int commonSuffix(String[] a, String[] b, int prefix) {
        int suffix = 0;
        while (suffix < a.length - prefix && suffix < b.length - prefix
            && a[a.length - 1 - suffix].equals(b[b.length - 1 - suffix])) suffix++;
        return suffix;
    }

    // -------------------------------------------------------------- 输出成块

    /**
     * 把 op 序列切成带上下文与行号的 hunk。
     *
     * <p>相邻的改动要合并进同一个 hunk：它们的上下文窗口重叠
     * （见 {@code start <= lastEnd} 的判断）。不合并的话，两个相距 5 行的改动
     * 会输出两个 hunk，而它们的上下文在视觉上重复了一整段。
     */
    private static void appendHunks(StringBuilder out, List<Op> ops) {
        List<int[]> ranges = new ArrayList<>();
        int lastEnd = -1;
        for (int i = 0; i < ops.size(); i++) {
            if (ops.get(i).kind == Op.SAME) continue;
            int start = Math.max(0, i - CONTEXT_LINES);
            int end = Math.min(ops.size(), i + CONTEXT_LINES + 1);
            if (!ranges.isEmpty() && start <= lastEnd) {
                ranges.get(ranges.size() - 1)[1] = Math.max(lastEnd, end);
            } else {
                ranges.add(new int[]{start, end});
            }
            lastEnd = ranges.get(ranges.size() - 1)[1];
        }

        for (int[] range : ranges) {
            out.append(header(ops, range[0], range[1]));
            for (int i = range[0]; i < range[1]; i++) {
                Op op = ops.get(i);
                out.append(op.kind).append(op.text).append('\n');
            }
        }
    }

    /**
     * {@code @@ -旧起点,旧行数 +新起点,新行数 @@}。
     *
     * <p>两处都只能从 op 序列推出来：下标是「第几个 op」，而 hunk 头里的数字是
     * 「文件里的第几行」，两者只在一行都没被增删时才相等。
     *
     * <p>{@code from} 之前的所有 op 都要算进起始行号，而**行数只算
     * {@code [from, to)} 这一段** —— 把行数也算到序列末尾的话，
     * 每个 hunk 头都会写成「一直到文件结尾」，而正文里只有几行，
     * 编辑器按这个头跳转会跳错位置。
     */
    private static String header(List<Op> ops, int from, int to) {
        int oldStart = 1;
        int newStart = 1;
        for (int i = 0; i < from; i++) {
            char kind = ops.get(i).kind;
            if (kind != Op.ADD) oldStart++;
            if (kind != Op.REMOVE) newStart++;
        }
        int oldCount = 0;
        int newCount = 0;
        for (int i = from; i < to; i++) {
            char kind = ops.get(i).kind;
            if (kind != Op.ADD) oldCount++;
            if (kind != Op.REMOVE) newCount++;
        }
        return "@@ -" + oldStart + "," + oldCount + " +" + newStart + "," + newCount + " @@\n";
    }

    // ------------------------------------------------------------------ 解析

    /**
     * 拆行。
     *
     * <p>先把三种换行统一成 {@code \n}：同一个文件被不同工具改过之后，
     * 混着 {@code \r\n} 与 {@code \n} 是常态，不统一的话「只改了一行」
     * 会显示成「整个文件都改了」。
     *
     * <p>末尾的空行要去掉：{@code split(..., -1)} 对以换行结尾的文本会多出一个
     * 空元素，那个元素不是文件里的一行，留着会让每次改动的行数都多 1。
     */
    private static String[] lines(String value) {
        if (value == null || value.isEmpty()) return new String[0];
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        String[] raw = normalized.split("\n", -1);
        if (raw.length > 0 && raw[raw.length - 1].isEmpty()) {
            String[] withoutTrailingBlank = new String[raw.length - 1];
            System.arraycopy(raw, 0, withoutTrailingBlank, 0, withoutTrailingBlank.length);
            return withoutTrailingBlank;
        }
        return raw;
    }

    /** 标题里显示的路径：空值给 {@code file}，反斜杠统一成斜杠，去掉开头的 {@code ./}。 */
    private static String cleanPath(String path) {
        String value = path == null || path.trim().isEmpty() ? "file" : path.trim().replace('\\', '/');
        while (value.startsWith("./")) value = value.substring(2);
        return value;
    }
}
