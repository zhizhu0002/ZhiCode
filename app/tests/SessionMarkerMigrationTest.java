import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 守住「内部续跑标记」只有一个来源。
 *
 * <p>背景：这个标记会被写进<b>持久化</b>的会话历史（JSONL），而
 * {@code SessionStore} 靠它把「模型被截断后的自动续跑指令」从人类发言里排除掉。
 * 不排除的话，它会变成会话标题，还会出现在可编辑的用户消息里。
 *
 * <p>改名之后这里只认一个标记。原来的回归测试守的是「新旧标记都识别」（为了不丢旧会话），
 * 那条兼容已经按明确决定删除了，所以测试跟着改成守现在的规则：
 * <ul>
 *   <li>标记只有一个字面量，且只在一个常量里定义；</li>
 *   <li>判定与生成都用那个常量，引擎侧不得内联；</li>
 *   <li>旧标记不得再出现 —— 否则就是兼容被悄悄加回来了。</li>
 * </ul>
 * 这类改动的失败方式很特别：编译不报错、新会话也正常，只在旧数据上表现不同，
 * 所以必须有测试盯着。
 */
public final class SessionMarkerMigrationTest {

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static String read(Path root, String relative) throws Exception {
        Path file = root.resolve(relative);
        require(Files.isRegularFile(file), "缺少文件: " + relative);
        return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        String store = read(root, "app/src/main/java/com/termux/app/zhicode/storage/SessionStore.java");
        String engine = read(root, "app/src/main/java/com/termux/app/zhicode/core/ZhiCodeEngine.java");

        // 1. 标记只定义一次，值就是当前的。
        String marker = "<zhicode_internal_continue>";
        require(store.contains("INTERNAL_CONTINUE_MARKER = \"" + marker + "\""),
                "SessionStore 必须以常量形式定义当前的内部续跑标记");

        // 2. 判定必须覆盖「当前标记」与「上下文摘要标记」两支。
        //    只检查字面量出现不够：有人可能把其中一支从判定逻辑里删掉而常量还在。
        int begin = store.indexOf("isInternalMarkerText(String text)");
        require(begin > 0, "找不到内部标记判定方法");
        String verdict = store.substring(begin, Math.min(store.length(), begin + 600));
        require(verdict.contains("INTERNAL_CONTINUE_MARKER") && verdict.contains("CONTEXT_SUMMARY_MARKER"),
                "标记判定必须同时覆盖内部续跑标记与上下文摘要标记");

        // 3. 写入端用常量，且闭标签也是当前的（只写开标签是最容易漏的一半）。
        int writer = store.indexOf("internalContinuationText(String stopReason)");
        require(writer > 0, "找不到内部续跑文本的生成方法");
        String text = store.substring(writer, Math.min(store.length(), writer + 900));
        require(text.contains("return INTERNAL_CONTINUE_MARKER"), "生成方法必须使用当前标记常量");
        require(text.contains("</zhicode_internal_continue>"), "闭标签也必须用当前标记");

        // 4. 引擎侧不得自己拼字面量：两处各写一份就是「改了一处」的起点。
        require(engine.contains("SessionStore.internalContinuationText("),
                "ZhiCodeEngine 必须通过 SessionStore 的单一来源生成续跑文本");
        require(!engine.contains(marker) && !engine.contains("</zhicode_internal_continue>"),
                "ZhiCodeEngine 不得内联续跑标记字面量");

        // 5. 旧品牌标记不得复活。这条是这次改动的核心：兼容被有意删掉了，
        //    如果哪天它以「为了老用户」的名义被加回来，这里会先失败。
        require(!store.contains("<iq_internal_continue>"),
                "旧品牌续跑标记不得再出现在 SessionStore 里");
        require(!engine.contains("<iq_internal_continue>"),
                "旧品牌续跑标记不得再出现在 ZhiCodeEngine 里");

        // 6. 本测试自身必须被 canonical suite 执行。
        String script = read(root, "test-source-no-build.sh");
        require(script.contains("SessionMarkerMigrationTest.java")
                        && script.contains("SessionMarkerMigrationTest \"$PROJECT_ROOT\""),
                "canonical source suite 必须执行本回归测试");

        System.out.println("SessionMarkerMigrationTest PASS");
    }
}
