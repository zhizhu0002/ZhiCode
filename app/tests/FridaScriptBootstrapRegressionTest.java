import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gadget 桥脚本的**引导契约**与**跨语言名字一致性**。
 *
 * <p>这个文件以前钉的是拼写：`contains("rpc.exports={")`、`contains("init(){let I=\"")`、
 * `contains("setInterval(tick,30);}};")`、`contains("File.writeAllText(READY,'1')")`。
 * 那样的断言等于「不许改」—— 换个变量名、拆个函数就红，而真正的行为漂移
 * （不写 ready 标记、忘了留下信箱定时器、少认一个 op、解析失效）它一个都拦不住，
 * 因为那些都是**行为**，文本匹配看不见。
 *
 * <p>所以现在分成两半，各管各的：
 * <ul>
 *   <li><b>这里</b>只钉两件事：① 信箱协议的名字（Java 侧与脚本侧按名字对齐，
 *       改一侧必须同步改另一侧）；② 脚本必须认得 Agent 工具能发出的每一个 op
 *       （漏一个 = 运行时报 "unknown Frida op"，而那是只在真机上才看得见的错）。</li>
 *   <li><b>行为</b>交给 app/tests/js/frida-agent-harness.mjs：它把载荷抽出来真加载、
 *       用桩替换 Frida 宿主对象、按信箱协议发命令读响应。那一份才是「改坏了会红」的守卫。</li>
 * </ul>
 */
public final class FridaScriptBootstrapRegressionTest {
    private static void require(boolean c, String m) {
        if (!c) throw new AssertionError(m);
    }

    private static String read(Path root, String relative) throws Exception {
        return Files.readString(root.resolve(relative));
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        String bridge = read(root, "app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxFrida.java");
        String tool = read(root, "app/src/main/java/com/termux/app/zhicode/tools/ZhiDebugTool.java");

        // 1. 信箱协议：这一组名字是 Java 侧与脚本侧的边界，必须都在。
        //    它们不是「实现细节」—— 少一个就不会有响应文件出现，而表现是「命令超时」，
        //    看不出是名字写错了。
        require(bridge.contains("rpc.exports"), "Gadget Script 模式必须导出 rpc.exports（否则 Gadget 不会执行它）");
        require(bridge.contains("command.json"), "脚本必须轮询 command.json 信箱");
        require(bridge.contains("ready.json"), "脚本必须写 ready 标记（否则 Java 侧会判成「脚本没跑起来」）");
        require(bridge.contains("events.log"), "脚本必须把异步事件追加进 events.log");
        require(bridge.contains("response-") && bridge.contains(".json"),
                "脚本必须写 response-<id>.json（Java 侧按 id 找它，名字不一致就永远超时）");
        require(bridge.contains("ok") && bridge.contains("error") && bridge.contains("result"),
                "响应必须带 ok / error / result（Java 侧就按这三个字段解析）");
        // 脚本必须自己留下信箱轮询，否则 init 结束后就没人消费命令了。
        require(bridge.contains("setInterval"), "脚本必须留下信箱轮询（否则 init 返回后命令再也不会被消费）");

        // 2. 跨语言名字一致性：工具能发出的每一个 op，脚本都必须认得。
        //    这是**两棵树之间**的不变式，任何一边单独看都不成立 —— 也正是文本匹配
        //    唯一还能比行为测试多做的一件事（行为测试只能测它自己写下的那几个 op）。
        Matcher m = Pattern.compile("ops\\.put\\(\"frida_[a-z_]+\",\\s*\"([a-z_]+)\"\\)").matcher(tool);
        int checked = 0;
        while (m.find()) {
            String op = m.group(1);
            checked++;
            require(bridge.contains("'" + op + "'"),
                    "脚本里没有 op '" + op + "'：Agent 工具能发出它，发出去会在运行时报 unknown Frida op");
        }
        require(checked >= 10, "没有从 ZhiDebugTool 里解析出 op 映射（正则或代码结构变了，这条断言会静默失去作用）");

        // 3. 历史缺陷的守卫：曾经有一版 Gadget 的 DEX 尾部被截断，脚本里留下了一段
        //    未闭合的字面量。这个形态一旦回来，载荷会整个语法错误。
        require(!bridge.contains("frida:'  "), "截断/未闭合的旧版 DEX 尾巴不得回来");

        // 4. 行为测试必须真的被执行：不然上面这些名字断言全绿，而脚本早就跑不起来了。
        String suite = read(root, "test-source-no-build.sh");
        require(suite.contains("frida-agent-harness"),
                "canonical suite 必须执行 app/tests/js/frida-agent-harness.mjs（载荷的行为守卫）");
        require(Files.isRegularFile(root.resolve("app/tests/js/frida-agent-harness.mjs")),
                "载荷的行为测试文件不见了：文本断言不能替代它");

        System.out.println("FridaScriptBootstrapRegressionTest PASS");
    }
}
