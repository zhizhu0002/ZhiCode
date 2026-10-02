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

        // ---- 在飞标志必须有超时兜底（用户报「点一次之后再也点不了」）------------
        //
        // 根因：`SandboxRpc.call` 是**阻塞式** `ContentResolver.call`，**没有超时**；
        // 控制器进程一卡就永远不返回，而 `rootSettingInFlight` / `floatingLogInFlight`
        // 只在成功或失败的回调里被清掉 —— 标志永久为 true，开关再也点不动。
        //
        // 修法是看门狗：超时后复位标志、恢复可交互、并如实告知"没等到结果"。
        // 这条断言守的就是"这两个开关都有超时兜底"，而不只是"有一个"。
        require(has(dashboard, "armWatchdog(generation)")
                        && dashboard.contains("WATCHDOG_TIMEOUT_MS")
                        && dashboard.contains("watchdog.postDelayed"),
                "两个在飞标志都必须有看门狗超时兜底：SandboxRpc.call 没有超时，"
                        + "卡住时标志会永久为 true、开关再也点不动（用户报过的现象）");
        // 超时回调不得**假装**设置成功：它必须把界面恢复到"服务端确认过的值"，而不是停在乐观值。
        //
        // ⚠️ 这里要断的是**超时分支内部**的顺序（先回滚、再报超时），不能只断
        // "文本里有 setRootSwitch(previous,true) 与 超时" —— 那两样在失败分支里也有，
        // 把超时分支的回滚删掉测试照样绿（实测如此）。`has` 会压掉空白，
        // 所以相邻两句可以连着比。
        require(has(dashboard, "setRootSwitch(previous,true)toast(\"Root隐藏设置超时")
                        && has(dashboard, "setFloatingLog(!enabled,true)toast(\"日志悬浮窗设置超时"),
                "超时后必须先回滚到服务端确认的值、再如实提示，不能假装设置成功"
                        + "（两个开关都要）");
        // 代数计数：超时回调不能去复位**后来那一次**操作的标志（否则互斥失效、两次写入打架）。
        //
        // ⚠️ 两半都要断：**递增**（发起时）与**比较**（超时/完成时）。
        // 只断比较处的话，把递增删掉测试照样绿 —— 而那时两个操作会共用同一个代数，
        // 旧超时会误伤新操作。
        require(has(dashboard, "++rootSettingGeneration")
                        && has(dashboard, "++floatingLogGeneration")
                        && has(dashboard, "generation!=rootSettingGeneration")
                        && has(dashboard, "generation!=floatingLogGeneration"),
                "代数必须同时有\"发起时递增\"与\"回调时比较\"两半："
                        + "只比较不递增时两次操作共用同一代数，旧超时会复位新操作的标志，互斥就失效了");

        require(testScript.contains("SandboxRootVisibilitySettingTest.java")
                        && testScript.contains("SandboxRootVisibilitySettingTest \"$PROJECT_ROOT\""),
                "the canonical source suite must run this regression test");
        System.out.println("SandboxRootVisibilitySettingTest PASS");
    }
}
