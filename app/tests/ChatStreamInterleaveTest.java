import java.nio.file.*;
import java.util.*;

/**
 * 对话流时序的守卫：正文与工具卡必须**按时序穿插**。
 *
 * <p>这里守的是一条不会报错、只会让界面"看起来不对"的时序约定：
 * 模型说一段话 → 调一批工具 → 再说一段话，这三段在对话流里必须是
 * 「正文 · 工具卡 · 正文」的顺序。
 *
 * <h2>为什么会平掉</h2>
 *
 * {@code onEngineToolBatchStarted} 只负责开一张新的工具分组卡，
 * 它不会碰正文气泡。如果它**不**先把当前正文气泡封口（{@code streaming = false}
 * 并把 {@code streamingAssistantId} 置空），工具结束后模型继续说的那段
 * 就会追加到**工具之前**的同一个气泡里 —— 正文全在最上面、工具卡全挤在下面，
 * 时序完全是平的（真机截图里出现过）。
 *
 * <p>这类问题在代码里非常容易"看起来无害"：封不封口都能编译、单测也不会红
 * （它不改变任何数据，只改变**哪一段字落在哪个气泡里**），所以只能用静态断言钉住。
 *
 * <p>⚠️ 引擎侧的保证不在这里守：工具事件之前先 {@code flushTextNow()} 是
 * {@code ZhiEngineController} 的事（那样工具卡才不会插到还没显示的正文前面）。
 */
public final class ChatStreamInterleaveTest {

    private static final String VM = "app/src/main/java/com/zhizhu/zhicode/compose/state/WorkspaceViewModel.kt";

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(String root, String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(root, relative)),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String stripComments(String text) {
        String noBlock = text.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)//[^\\n]*", " ");
    }

    private static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    /** 取出 {@code 签名 … 配对右花括号} 之间的函数体；找不到就返回空串（调用点会立刻报错）。 */
    private static String functionBody(String code, String signature) {
        int at = code.indexOf(signature);
        if (at < 0) return "";
        int depth = 0;
        boolean seen = false;
        for (int i = at; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') { depth++; seen = true; }
            else if (c == '}') {
                depth--;
                if (seen && depth == 0) return code.substring(at, i + 1);
            }
        }
        return code.substring(at);
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";
        String vm = stripComments(read(root, VM));

        // ---- 1. 封口函数存在，并且语义是「定稿但不丢弃」 --------------------
        String seal = functionBody(vm, "private fun sealStreamingAssistant()");
        require(!seal.isEmpty(),
                VM + " 里找不到 sealStreamingAssistant —— 正文与工具卡的时序穿插没了支点");
        require(squash(seal).contains("streamingAssistantId=null"),
                "sealStreamingAssistant 必须把 streamingAssistantId 置空："
                        + "不清空的话，工具之后的第一段正文还会追加回旧气泡，时序照样是平的。");
        require(squash(seal).contains("item.copy(streaming=false)"),
                "sealStreamingAssistant 必须把气泡定稿（streaming = false）："
                        + "不置的话旧气泡还挂着流式光标，看起来像还没说完。");
        // 空壳要丢，有内容的要留 —— 这是和 finalizeStreaming(keepIfEmpty=false) 的关键差别：
        // 那个只在回合结束时跑，这里在回合中间跑，把有内容的气泡丢掉等于丢用户看过的字。
        require(squash(seal).contains("item.body.isBlank()&&item.thinking.isBlank()"),
                "sealStreamingAssistant 必须区分「空壳」与「有内容」：回合中间封口时，"
                        + "已经显示出来的正文一个字都不能丢。");
        require(!seal.contains("keepIfEmpty"),
                "sealStreamingAssistant 不应复用 finalizeStreaming 的 keepIfEmpty 形参："
                        + "那个函数在回合结束时跑、会丢空白气泡；这里在回合中间跑，语义相反。");

        // ---- 2. 批次开始时必须先封口，再开分组卡 ----------------------------
        String batch = functionBody(vm, "override fun onEngineToolBatchStarted(");
        require(!batch.isEmpty(), VM + " 里找不到 onEngineToolBatchStarted");
        int sealAt = batch.indexOf("sealStreamingAssistant()");
        int groupAt = batch.indexOf("nextId(\"g\")");
        require(sealAt >= 0,
                "onEngineToolBatchStarted 必须先调用 sealStreamingAssistant()："
                        + "批次边界同时意味着「上一段正文到此为止」。不封口的话，"
                        + "工具之后的正文会追加回工具之前的气泡 —— 界面上就是"
                        + "「正文全在上面、工具卡全在下面」。");
        require(groupAt >= 0, "onEngineToolBatchStarted 里找不到 nextId(\"g\") —— 断言过期，请更新守卫");
        require(sealAt < groupAt,
                "onEngineToolBatchStarted 把开分组卡放在了封口**之前**："
                        + "那样新分组卡会插在正文气泡前面，顺序就反了。"
                        + "守的是顺序，不是「文件里出现过 sealStreamingAssistant 这几个字」。");

        // ---- 3. 防御路径（没有批次事件就来了工具调用）也要封口 --------------
        String toolUse = functionBody(vm, "override fun onEngineToolUse(");
        require(!toolUse.isEmpty(), VM + " 里找不到 onEngineToolUse");
        require(toolUse.contains("sealStreamingAssistant()"),
                "onEngineToolUse 的防御路径（currentGroupId 为 null 时自建分组卡）"
                        + "也必须封口正文气泡 —— 这条路径同样意味着「上段正文到此为止」");

        // ---- 4. 思考不许因为「还没有气泡」而被丢掉 --------------------------
        String thinking = functionBody(vm, "override fun onEngineThinking(");
        require(!thinking.isEmpty(), VM + " 里找不到 onEngineThinking");
        require(thinking.contains("ensureStreamingAssistant()"),
                "onEngineThinking 必须走 ensureStreamingAssistant()："
                        + "工具批次结束后模型先思考再说话，思考若因「还没有气泡」直接 return，"
                        + "那一段就被静默丢掉了。");
        require(!thinking.contains("?: return"),
                "onEngineThinking 里还留着「没有气泡就 return」：工具之后的思考会被静默丢掉。"
                        + "应该用 ensureStreamingAssistant() 延迟创建气泡。");

        // ---- 5. 正文与思考共用同一个「延迟创建」入口 ------------------------
        String onText = functionBody(vm, "override fun onEngineText(");
        require(!onText.isEmpty(), VM + " 里找不到 onEngineText");
        require(onText.contains("ensureStreamingAssistant()"),
                "onEngineText 必须走 ensureStreamingAssistant()："
                        + "正文与思考共用同一个创建入口，否则两条路径会各自发明一遍气泡创建，"
                        + "以后改一处漏一处。");
        require(!onText.contains("nextId("),
                "onEngineText 里不再直接创建气泡（创建逻辑收敛到 ensureStreamingAssistant）");

        String ensure = functionBody(vm, "private fun ensureStreamingAssistant()");
        require(!ensure.isEmpty(), VM + " 里找不到 ensureStreamingAssistant");
        require(squash(ensure).contains("nextId(\"a\")"),
                "ensureStreamingAssistant 必须负责创建气泡（nextId(\"a\")）");
        require(squash(ensure).contains("streamingAssistantId=id"),
                "ensureStreamingAssistant 必须记录创建出的气泡 id");

        // ---- 6. 本测试自身必须被 canonical suite 执行 ------------------------
        String script = read(root, "test-source-no-build.sh");
        require(script.contains("ChatStreamInterleaveTest \"$PROJECT_ROOT\""),
                "canonical source suite 必须执行本守卫");

        System.out.println("ChatStreamInterleaveTest PASS"
                + "（批次边界封口正文 · 防御路径同样封口 · 思考不再被静默丢掉 · 创建入口收敛）");
    }
}
