import java.nio.file.*;

/**
 * 有界异步扫描器的**两边约定**。
 *
 * <p>这个文件名里的「Deadlock」来自当初那个真实故障：一条长扫描把整条命令队列串死，
 * Java 侧等到超时，而脚本侧还在跑 —— 表现是「frida_scan 偶发超时」。
 * 修法是把扫描做成有界、可中断、分块让出事件循环，并且**不设全局忙位**。
 *
 * <p>那些行为本身现在由 app/tests/js/frida-agent-harness.mjs 真跑着验证
 * （停止原因五种、某块读不了要返回部分结果、重叠不虚增 scanned、重复上报去重、
 * 慢命令不阻塞短命令……）。本文件里原先那一大串 `contains("…")` 钉的是**拼写**，
 * 换个变量名就红、行为坏了却不红，所以改成只守两件文本能守住的事：
 * <ol>
 *   <li>**Java 侧**的超时分层：脚本侧的上限必须低于 Java 侧的等待，否则脚本还没来得及
 *       返回一个结构化的「我超时了」，Java 就已经先超时了 —— 那样 Agent 只能看到
 *       一句 timeout，看不到 stop_reason 与部分结果。这是跨语言的两个数之间的关系，
 *       只能在文本上核对。</li>
 *   <li>工具 schema 与系统提示词必须把这件事教给 Agent（用 frida_scan、别用 scanSync、
 *       看 complete / stop_reason / errors）。</li>
 * </ol>
 */
public final class FridaDeadlockRegressionTest {
    private static void require(boolean c, String m) {
        if (!c) throw new AssertionError(m);
    }

    /** 去掉全部空白后再比较：断言关心的是标识符与先后关系，不该被空格/换行左右。 */
    private static String squash(String source) {
        return source.replaceAll("\\s+", "");
    }

    /**
     * 把源码里所有双引号字面量的内容按出现次序拼起来。
     *
     * <p>系统提示词在源码里是很多段字符串相加的，而断言要检查的是**最终输出的文字**。
     * 对源码直接 contains 会把换行位置也变成契约：重排不改变行为，却会让断言失败。
     */
    private static String literals(String source) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < source.length()) {
            if (source.charAt(i) != '"') { i++; continue; }
            i++;
            while (i < source.length()) {
                char d = source.charAt(i);
                if (d == '\\') {
                    if (i + 1 < source.length()) out.append(source.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (d == '"') { i++; break; }
                out.append(d);
                i++;
            }
        }
        return out.toString();
    }

    /** 从 Java 源码里取出某个整型常量的值（`static final int NAME = 123;`）。 */
    private static long constant(String source, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("static\\s+final\\s+int\\s+" + name + "\\s*=\\s*([0-9_]+)").matcher(source);
        if (!m.find()) throw new AssertionError("找不到常量 " + name);
        return Long.parseLong(m.group(1).replace("_", ""));
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        String bridge = Files.readString(root.resolve("app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxFrida.java"));
        String tool = Files.readString(root.resolve("app/src/main/java/com/termux/app/zhicode/tools/ZhiDebugTool.java"));
        String prompt = literals(Files.readString(root.resolve("app/src/main/java/com/termux/app/zhicode/core/SystemPromptBuilder.java")));

        // 1. Java 侧的超时必须**留出余量**：脚本侧自己会在 timeout_ms 到点时返回一个
        //    结构化响应（含 stop_reason 与部分命中），Java 侧要在它之后再放弃。
        //    两个数的关系写成断言，而不是钉某一种书写形态。
        String toolFlat = squash(tool);
        require(toolFlat.contains("fridaPayload.put(\"timeout_ms\",runtimeTimeoutMs)")
                        && toolFlat.contains("commandTimeoutMs=Math.min(FRIDA_COMMAND_MAX_TIMEOUT_MS,runtimeTimeoutMs+FRIDA_TIMEOUT_HEADROOM_MS)"),
                "Java 命令超时必须给脚本侧的结构化超时响应留出余量");
        long headroom = constant(tool, "FRIDA_TIMEOUT_HEADROOM_MS");
        require(headroom > 0, "余量必须为正数（否则脚本永远来不及返回 stop_reason）");

        // 2. 脚本侧的上限（SCAN_TIMEOUT_MAX / EVAL_TIMEOUT_MAX）必须不高于 Java 侧的等待上限。
        //    这两棵树上的数值关系是行为测试看不到的（它只加载脚本，不知道 Java 等多久）。
        long scriptMax = 0;
        for (String line : bridge.split("\n")) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("const (?:SCAN|EVAL)_TIMEOUT_MAX = ([0-9]+);").matcher(line.trim());
            if (m.find()) scriptMax = Math.max(scriptMax, Long.parseLong(m.group(1)));
        }
        require(scriptMax > 0, "脚本里找不到 SCAN_TIMEOUT_MAX / EVAL_TIMEOUT_MAX（超时上限没了）");
        long commandMax = constant(tool, "FRIDA_COMMAND_MAX_TIMEOUT_MS");
        require(commandMax >= scriptMax,
                "Java 侧等待上限（" + commandMax + "）必须不低于脚本侧上限（" + scriptMax + "）");

        // 3. 真正要守的是这一条**紧约束**：请求被钳到的上限加上余量，必须仍装得进
        //    Java 的等待上限里。装不进的话，请求一变大 Java 就会与脚本同时放弃 ——
        //    于是 Agent 只能看到一句 timeout，看不到 stop_reason 与部分命中。
        //
        //    这里纠正一处我自己写错的记录：先前这个文件里写着「请求到顶时余量恰好为 0，
        //    与脚本的截止时间同一时刻」。那是**错的** —— 请求被钳到的是
        //    FRIDA_MAX_TIMEOUT_MS（118000），不是 FRIDA_COMMAND_MAX_TIMEOUT_MS（120000）：
        //        请求 999999999 → runtime = min(118000, …) = 118000
        //                      → 等待   = min(120000, 118000+1500) = 119500   余量 1500
        //    两个数看起来只差一点，结论却相反。所以现在按**数值关系**断言，
        //    而不是像原先那样只比两个上限的大小（那一条恒真，等于没断言）。
        long requestMax = constant(tool, "FRIDA_MAX_TIMEOUT_MS");
        long waitMax = Math.min(commandMax, requestMax + headroom);
        long ceilingGap = waitMax - requestMax;
        require(ceilingGap >= headroom,
                "请求到顶时 Java 必须仍留出完整余量：请求上限 " + requestMax + " + 余量 " + headroom
                        + " = " + (requestMax + headroom) + " 必须 ≤ 等待上限 " + commandMax
                        + "（当前余量只有 " + ceilingGap + "）");

        // 3. frida_eval 的超时有一个**做不到**的一半，必须写在载荷里让人看见：
        //    JS 没有抢占，同步死循环会把 guest 卡死，连脚本自己的 deadline 都轮不到
        //    （harness 第一次跑就是这么满载 spinning 的）。所以脚本只能保证
        //    「会让出事件循环的脚本」会被打断，这一点不能只留在注释里。
        require(bridge.contains("frida_eval deadline exceeded"),
                "eval 超时错误必须写明是 deadline（否则分不清是超时还是脚本自己抛的）");
        require(bridge.contains("setImmediate"),
                "扫描循环必须分块让出事件循环：不让出的话超时机制根本没有机会触发");

        // 4. 工具 schema 与系统提示词必须把这件事教给 Agent —— 否则它会用 scanSync，
        //    而那正是当初卡死的那条路径。
        require(tool.contains("hard-capped at 2048") && tool.contains("await Zhi.scan(options)"),
                "工具 schema 必须写明命中上限与安全的 eval 扫描 API");
        require(prompt.contains("always use Debug action=frida_scan instead of Memory.scanSync")
                        && prompt.contains("await Zhi.scan(options)")
                        && prompt.contains("Legacy Memory.scanSync calls are translated")
                        && prompt.contains("complete, stop_reason, and errors"),
                "Agent 提示词必须教它有界异步扫描与停止原因");

        System.out.println("FridaDeadlockRegressionTest PASS");
    }
}
