import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 守住「内部续跑标记」的改名迁移。
 *
 * <p>背景：这个标记会被写进<b>持久化</b>的会话历史（JSONL），而
 * {@code SessionStore} 靠它把「模型被截断后的自动续跑指令」从人类发言里排除掉。
 * 不排除的话，它会变成会话标题，还会出现在可编辑的用户消息里。
 *
 * <p>标签名从旧品牌标识换成了当前标识。这类改名的失败方式很特别：
 * <ul>
 *   <li>只认新标记 → 用户打开<b>旧会话</b>时看到一堆
 *       "Previous model output stopped because of…" 混在正常对话里；</li>
 *   <li>只写旧标记 → 标记永远不会退出历史舞台，改名等于没做；</li>
 *   <li>两处各写一份字面量 → 改了一处就会出现上面第一个问题。</li>
 * </ul>
 * 这三种都不会让编译失败、也不会让新会话出问题，只在旧数据上复现，
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

        // 1. 读取端必须同时识别新旧标记 —— 这是旧会话还能正确显示的前提。
        require(store.contains("\"<zhicode_internal_continue>\""),
                "SessionStore 必须定义当前的内部续跑标记");
        require(store.contains("\"<iq_internal_continue>\""),
                "SessionStore 必须继续识别旧的内部续跑标记，否则旧会话里的续跑指令会变成人类发言");

        // 2. 判定必须同时覆盖两者。单靠检查字面量出现不够：
        //    有人可能把其中一支从判定逻辑里删掉而常量还在。
        int begin = store.indexOf("isInternalMarkerText(String text)");
        require(begin > 0, "找不到内部标记判定方法");
        String verdict = store.substring(begin, Math.min(store.length(), begin + 600));
        require(verdict.contains("INTERNAL_CONTINUE_MARKER")
                        && verdict.contains("LEGACY_INTERNAL_CONTINUE_MARKER")
                        && verdict.contains("CONTEXT_SUMMARY_MARKER"),
                "标记判定必须同时覆盖当前标记、旧标记与上下文摘要标记");

        // 3. 写入端只产出新标记，且闭标签也是新的（只会写开标签是最容易漏的一半）。
        int writer = store.indexOf("internalContinuationText(String stopReason)");
        require(writer > 0, "找不到内部续跑文本的生成方法");
        String text = store.substring(writer, Math.min(store.length(), writer + 900));
        require(text.contains("return INTERNAL_CONTINUE_MARKER"), "生成方法必须使用当前标记常量");
        require(text.contains("</zhicode_internal_continue>"), "闭标签也必须用当前标记");
        require(!text.contains("</iq_internal_continue>"), "生成方法不得再写出旧闭标签");

        // 4. 引擎侧不得自己拼字面量：两处各写一份就是「改了一处」的起点。
        require(engine.contains("SessionStore.internalContinuationText("),
                "ZhiCodeEngine 必须通过 SessionStore 的单一来源生成续跑文本");
        require(!engine.contains("<iq_internal_continue>") && !engine.contains("<zhicode_internal_continue>"),
                "ZhiCodeEngine 不得内联续跑标记字面量");

        // 5. 写入端产出的文本必须能被读取端判定为内部内容 —— 端到端一致性。
        //    这里按源码拼接语义核对：新标记的起始串必须与判定用的常量一致。
        String currentMarkerLiteral = "<zhicode_internal_continue>";
        require(store.contains("INTERNAL_CONTINUE_MARKER = \"" + currentMarkerLiteral + "\""),
                "当前标记常量的值必须与判定逻辑预期的一致");

        // 6. 本测试自身必须被 canonical suite 执行。
        String script = read(root, "test-source-no-build.sh");
        require(script.contains("SessionMarkerMigrationTest.java")
                        && script.contains("SessionMarkerMigrationTest \"$PROJECT_ROOT\""),
                "canonical source suite 必须执行本回归测试");

        System.out.println("SessionMarkerMigrationTest PASS");
    }
}
