import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SandboxRootVisibilitySettingTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    /** 去掉所有空白后再比较：检查代码形态时不该被缩进/换行影响。 */
    private static boolean has(String source, String needle) {
        return source.replaceAll("\\s+", "").contains(needle.replaceAll("\\s+", ""));
    }

    private static String read(Path root, String path) throws Exception {
        return new String(Files.readAllBytes(root.resolve(path)), StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        String store = read(root, "app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxPrefs.java");
        String engine = read(root, "app/src/main/java/com/zhizhu/zhicode/sandbox/ZhiSandbox.java");
        String provider = read(root, "app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxRpcService.java");
        // 界面已从手写 View 改成 Compose + Miuix，所以这里读**两个**文件：
        //   · SandboxBoard.kt      —— 状态与后端调用（开关值、在飞互斥位、回滚）
        //   · ZhiSandboxScreen.kt  —— 绘制（文案、Miuix 组件）
        // 断言的语义一条没松：这个开关必须存在、必须说清"会重启 Guest"、必须带回滚。
        String dashboard = read(root, "app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxBoard.kt")
                + read(root, "app/src/main/java/com/zhizhu/zhicode/compose/ui/sandbox/ZhiSandboxScreen.kt");
        String testScript = read(root, "test-source-no-build.sh");

        require(store.contains("AtomicFile") && store.contains("sandbox/settings.json"),
                "sandbox settings must use one atomic host file");
        // 断言「安全默认值」这件事本身：默认值常量必须为 true，读取时用该常量兜底，
        // 且每条失败路径都 return defaults()。刻意不钉 optBoolean("hide_root", true)
        // 这种书写形态——把键名与默认值提成命名常量是重写范围内的正常改动。
        require(has(store, "DEFAULT_HIDE_ROOT=true")
                        && store.contains("optBoolean(KEY_HIDE_ROOT, DEFAULT_HIDE_ROOT)")
                        && store.contains("return defaults();"),
                "missing or invalid settings must keep root hidden");
        require(store.contains(".startWrite()") && store.contains(".finishWrite(out)")
                        && store.contains(".failWrite(out)"),
                "settings writes must retain AtomicFile rollback semantics");

        require(has(engine, "isHideRoot(){return SandboxPrefs.isRootHidden(app);}"),
                "BlackBox root hiding must read the persistent setting");
        require(!has(engine, "isHideRoot(){return true;}"),
                "BlackBox root hiding must not remain hard-coded");
        // 顺序不变式：先把运行中的 Guest 停干净并确认停住，才允许落盘新设置。
        int stopGuests = engine.indexOf("for (ApplicationInfo info : apps) BlackBoxCore.get().stopPackage");
        int verifyStopped = engine.indexOf("仍有 Guest 进程在运行");
        int writeSetting = engine.indexOf("SandboxPrefs.setRootHidden(");
        require(stopGuests >= 0 && verifyStopped > stopGuests && writeSetting > verifyStopped,
                "running Guests must be stopped and verified before a changed setting is persisted");
        int launch = engine.indexOf("public static boolean launch(String pkg)");
        int launchLock = engine.indexOf("synchronized (LIFECYCLE_LOCK)", launch);
        require(launch >= 0 && launchLock > launch,
                "Guest launch and setting changes must share lifecycle serialization");

        require(provider.contains("case \"set_hide_root\"")
                        && provider.contains("put(\"hide_root\", ZhiSandbox.isRootHidden())"),
                "the private sandbox RPC must expose the effective setting");
        // 这里原来钉的是 `new Switch(this)` —— 那是**平台 View 的书写形态**，不是不变式。
        // 改成 Compose 之后组件换成了 Miuix 的 `SwitchPreference`（这本来就是这次改动的目的：
        // 平台 Switch 在 Material v1 主题下是青色、且被塞进 64dp 盒子压变形）。
        // 于是断言换成「这个开关用的是 Miuix 的开关组件、且文案还在」，
        // 其余三条（文案、在飞压住、失败回滚）一字不改。
        require(has(dashboard, "SwitchPreference(") && dashboard.contains("隐藏 Root"),
                "the sandbox dashboard must expose a Root hiding switch");
        // 用 squash 比较：断言的是「回滚语义」（切走时禁用并压住交互，失败时恢复），
        // 不该被 setRootSwitch(previous, false) 这类逗号后的空格写法左右。
        require(dashboard.contains("所有正在运行的 Guest 将停止")
                        && has(dashboard, "setRootSwitch(previous,false)")
                        && has(dashboard, "setRootSwitch(previous,true)"),
                "the switch must confirm restart semantics, disable in flight, and restore on failure");

        require(testScript.contains("SandboxRootVisibilitySettingTest.java")
                        && testScript.contains("SandboxRootVisibilitySettingTest \"$PROJECT_ROOT\""),
                "the canonical source suite must run this regression test");
        System.out.println("SandboxRootVisibilitySettingTest PASS");
    }
}
