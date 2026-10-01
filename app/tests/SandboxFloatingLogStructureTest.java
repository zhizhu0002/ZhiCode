import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SandboxFloatingLogStructureTest {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static String read(Path root, String file) throws Exception { return new String(Files.readAllBytes(root.resolve(file)), StandardCharsets.UTF_8); }

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
        // 界面从手写 View 改成 Compose + Miuix：文案（「日志悬浮窗」）搬到了绘制层
        // ZhiSandboxScreen.kt，「set_show_floating_log」与「floatingLogInFlight」留在 SandboxBoard.kt。
        // 所以两处一起读：断言的不变式没变（这个开关必须存在、且带回滚互斥位）。
        require(dashboard.contains("日志悬浮窗")&&dashboard.contains("set_show_floating_log")&&dashboard.contains("floatingLogInFlight"),"dashboard must provide persistent switch with rollback state");
        require(script.contains("SandboxFloatingLogStructureTest.java")&&script.contains("SandboxFloatingLogStructureTest \"$PROJECT_ROOT\""),"canonical source suite must run the floating log regression");
        System.out.println("SandboxFloatingLogStructureTest PASS");
    }
}
