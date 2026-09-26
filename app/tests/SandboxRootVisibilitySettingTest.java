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
        String dashboard = read(root, "app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxBoard.java");
        String testScript = read(root, "test-source-no-build.sh");

        require(store.contains("AtomicFile") && store.contains("sandbox/settings.json"),
                "sandbox settings must use one atomic host file");
        require(store.contains("optBoolean(\"hide_root\", true)") && store.contains("catch (Exception ignored)"),
                "missing or invalid settings must keep root hidden");
        require(store.contains("settings.startWrite()") && store.contains("settings.finishWrite(out)")
                        && store.contains("settings.failWrite(out)"),
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
        require(dashboard.contains("new Switch(this)") && dashboard.contains("隐藏 Root"),
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
