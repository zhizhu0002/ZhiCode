import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SandboxFloatingLogStructureTest {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static String read(Path root, String file) throws Exception { return new String(Files.readAllBytes(root.resolve(file)), StandardCharsets.UTF_8); }

    /**
     * 去掉注释（保留字符串字面量里的内容）。
     *
     * <p>下面的断言全是"某段代码必须存在"。直接拿原文 contains 的话，
     * <b>把它注释掉也算存在</b>——而注释掉的调用恰恰是最容易发生的事故：
     * 编译照过、行为退回旧样子、守卫还是绿的。实测过：
     * 把 `tintDialog(dialog, palette);` 改成 `// tintDialog(dialog, palette);`
     * 原先的断言照样 PASS。
     */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                int nl = src.indexOf('\n', i);
                i = nl < 0 ? src.length() : nl;
                continue;
            }
            if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '*') {
                int close = src.indexOf("*/", i + 2);
                i = close < 0 ? src.length() : close + 2;
                continue;
            }
            if (c == '"') {
                int j = i + 1;
                while (j < src.length() && src.charAt(j) != '"' && src.charAt(j) != '\n') {
                    j += src.charAt(j) == '\\' ? 2 : 1;
                }
                out.append(src, i, Math.min(j + 1, src.length()));
                i = j + 1;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    public static void main(String[] args) throws Exception {
        Path root=Paths.get(args.length==0?".":args[0]).toAbsolutePath().normalize();
        String settings=read(root,"app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxPrefs.java");
        String provider=read(root,"app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxRpcService.java");
        String engine=read(root,"app/src/main/java/com/zhizhu/zhicode/sandbox/ZhiSandbox.java");
        String floating=read(root,"app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxOverlay.java");
        String dashboard=read(root,"app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxBoard.kt")
                + read(root,"app/src/main/java/com/zhizhu/zhicode/compose/ui/sandbox/ZhiSandboxScreen.kt");
        String script=read(root,"test-source-no-build.sh");
        require(settings.contains("show_floating_log")&&settings.contains("isFloatingLogEnabled")&&settings.contains("setFloatingLogEnabled"),"floating log preference must be persisted");
        require(provider.contains("set_show_floating_log")&&provider.contains("show_floating_log"),"provider must expose floating log state");
        require(engine.contains("isFloatingLogEnabled")&&engine.contains("SandboxOverlay.detach"),"resume lifecycle must gate and remove the overlay");
        require(floating.contains("SandboxConsole.snapshot")&&floating.contains("ClipboardManager")&&floating.contains("复制全部")&&floating.contains("刷新")&&floating.contains("关闭"),"floating viewer must refresh and copy bounded logs");
        require(floating.contains("addContentView")&&floating.contains("OVERLAY_TAG"),"viewer must remain host-owned and deduplicated");

        // ---- 日志面板必须自己不依赖 guest 主题 ---------------------------------
        //
        // 真机症状（截图）：日志面板是一片**纯白**，看着像没有内容。
        //
        // 根因不是日志空，而是颜色：正文是硬编码的 `Color.WHITE`，而 `AlertDialog` 的
        // 底色跟着**当前 Activity** 的主题 —— 那是 guest 应用的（截图里是浅色的 PHIRA）
        // → 白底白字。日志一直好好地写在宿主的目录里。
        //
        // 所以这里钉：正文色必须来自我们自己的 palette（不许再写死 WHITE）；
        // 对话框的窗口底色、标题、按钮也必须由我们显式上色 —— 少任何一处都会
        // 退回"guest 主题说了算"，而 guest 主题是我们控制不了的。
        // 下面这一段的判据全部跑在**去掉注释**的源码上（见 stripComments）：
        // 这些断言问的是"这段代码在不在"，注释掉的调用不算数。
        String code = stripComments(floating);
        require(!code.contains("setTextColor(Color.WHITE)"),
                "日志正文不许再写死 Color.WHITE：对话框底色跟着 guest 主题走，"
                        + "浅色 guest 上就是白底黑字/白底白字（用户报的「日志显示也是」就是这个）");
        require(code.contains("body.setTextColor(palette.text)"),
                "日志正文必须用我们自己的主题文字色");
        // 只钉"tintDialog 这个函数存在"是不够的：把调用点注释掉，函数定义还在、
        // `setBackgroundDrawable(rounded(palette.surface…` 也还在，守卫照样绿
        // （实测如此）。所以要钉**调用点确实在 show 回调里**，且它在 comment-stripped
        // 的源码里、位于 `setOnShowListener(` 之后。
        int showAt = code.indexOf("setOnShowListener(");
        int tintAt = code.indexOf("tintDialog(dialog, palette)");
        require(tintAt >= 0 && showAt >= 0 && tintAt > showAt,
                "对话框的窗口底色与标题/按钮色必须在 show 之后显式设置（tintDialog 的**调用点**）："
                        + "只改正文的话，标题是黑的、按钮是 guest 主题的强调色（紫色）");
        require(code.contains("setBackgroundDrawable(rounded(palette.surface"),
                "对话框窗口必须换成我们自己的圆角底色：AlertDialog 默认底色是 guest 主题的");

        // ---- 日志必须从**宿主**的 context 读 -----------------------------------
        //
        // guest 的 `filesDir` 可能被引擎重定向到虚拟数据目录，用它去读
        // `SandboxConsole` 会读到另一个文件（或读不到）。而 `ZhiSandbox.attach`
        // 记下的 `appContext` 才是宿主自己的。
        require(code.contains("ZhiSandbox.hostContext()"),
                "日志必须从宿主的 context 读（ZhiSandbox.hostContext()）："
                        + "guest 的 filesDir 可能被重定向，读到的不是宿主那份 events.log");
        require(engine.contains("public static Context hostContext()"),
                "ZhiSandbox 必须暴露 hostContext()：它是宿主 context 的唯一来源");

        require(dashboard.contains("日志悬浮窗")&&dashboard.contains("set_show_floating_log")&&dashboard.contains("floatingLogInFlight"),"dashboard must provide persistent switch with rollback state");
        require(script.contains("SandboxFloatingLogStructureTest.java")&&script.contains("SandboxFloatingLogStructureTest \"$PROJECT_ROOT\""),"canonical source suite must run the floating log regression");
        System.out.println("SandboxFloatingLogStructureTest PASS");
    }
}
