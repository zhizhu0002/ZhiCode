import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 蜘蛛沙箱宿主层的架构自检（纯源码文本断言，无需编译工程、无需设备）。
 *
 * <p>它固化的是 2026 年这次宿主层重写建立的不变式。放在 app/tests/ 下，
 * 与本工程其它结构测试同风格：{@code java app/tests/SandboxHostArchitectureTest.java <工程根>}。
 *
 * <p>覆盖的不变式：
 * <ol>
 *   <li>控制器进程名有单一来源，且引擎与宿主层都引用它，不再各自硬编码；</li>
 *   <li>引擎调用面收敛：除 ZhiSandbox 外，宿主层不得直接触碰 BlackBoxCore；</li>
 *   <li>主进程判定不得触发引擎类加载（SandboxProcess 只能依赖编译期常量）；</li>
 *   <li>guest 调试的安全边界必须保留（私有目录 .so、写入上限、目标页可写）；</li>
 *   <li>启动阶段日志按 pid 分文件，不能退回单文件覆盖写；</li>
 *   <li>AndroidX Startup 的 provider/receiver 必须被清单移除。</li>
 * </ol>
 */
public final class SandboxHostArchitectureTest {

    private static final List<String> failures = new ArrayList<>();

    private static void require(boolean ok, String message) {
        if (!ok) failures.add(message);
    }

    private static String read(Path root, String relative) throws Exception {
        Path file = root.resolve(relative);
        require(Files.isRegularFile(file), "缺少文件: " + relative);
        return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
    }

    /**
     * 去掉注释后再做断言。
     *
     * <p>本工程的注释里会<b>刻意</b>保留历史说明（例如「旧实现用的是 startup-stage.txt，
     * 多进程会互相冲掉」）。这类提及是有价值的文档，不该被当成回归；
     * 真正要拦的是代码路径重新用回旧做法。因此涉及「不得再出现」的断言一律先剥注释。
     */
    private static String code(String source) {
        StringBuilder out = new StringBuilder();
        boolean inBlock = false;
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (inBlock) {
                if (trimmed.contains("*/")) inBlock = false;
                continue;
            }
            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains("*/")) inBlock = true;
                continue;
            }
            if (trimmed.startsWith("*") || trimmed.startsWith("//")) continue;
            int lineComment = line.indexOf("//");
            if (lineComment >= 0 && !line.contains("://")) line = line.substring(0, lineComment);
            out.append(line).append('\n');
        }
        return out.toString();
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        String sandboxDir = "app/src/main/java/com/zhizhu/zhicode/sandbox";
        String contract = read(root, "Bcore/src/main/java/top/niunaijun/blackbox/SandboxContract.java");
        String core = read(root, "Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java");
        String process = read(root, sandboxDir + "/SandboxProcess.java");
        String engine = read(root, sandboxDir + "/ZhiSandbox.java");
        String stage = read(root, sandboxDir + "/SandboxStage.java");
        String debug = read(root, sandboxDir + "/SandboxGuestDebug.java");
        String manifest = read(root, "app/src/main/AndroidManifest.xml");
        String coreManifest = read(root, "Bcore/src/main/AndroidManifest.xml");

        // 1. 进程名单一来源
        require(contract.contains("CONTROLLER_PROCESS_SUFFIX = \":zhisandbox\""),
                "控制器进程名常量必须定义在 SandboxContract 且为 :zhisandbox");
        require(contract.contains("GUEST_PROCESS_COUNT"), "guest 进程池上界必须集中在 SandboxContract");
        require(contract.contains("private SandboxContract() {}"), "SandboxContract 必须是不可实例化的常量类");
        require(core.contains("SandboxContract.CONTROLLER_PROCESS_SUFFIX"),
                "引擎必须引用 SandboxContract，而不是自己硬编码进程名");
        require(!core.contains("\":iqsandbox\""), "引擎里不得再有 :iqsandbox 硬编码");
        require(!core.contains("\":zhisandbox\""), "引擎里不得直接写字面量 :zhisandbox，必须走 SandboxContract");
        require(process.contains("SandboxContract.CONTROLLER_PROCESS_SUFFIX"),
                "SandboxProcess 必须引用 SandboxContract 判定控制器进程");
        require(coreManifest.contains("android:process=\":zhisandbox\""),
                "Bcore 清单里的控制器组件必须声明在 :zhisandbox");
        require(!coreManifest.contains(":iqsandbox"), "Bcore 清单里不得再有 :iqsandbox");

        // 2. 宿主清单与引擎必须指向同一个进程名
        require(manifest.contains("android:process=\":zhisandbox\""),
                "宿主清单里的 SandboxRpcService 必须声明在 :zhisandbox");
        require(manifest.contains("${applicationId}.sandbox.control"),
                "provider authority 必须由 applicationId 派生，避免与同源构建冲突");

        // 3. 引擎调用面收敛
        try (Stream<Path> files = Files.list(root.resolve(sandboxDir))) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".java") || name.equals("ZhiSandbox.java")) continue;
                String source = Files.readString(file, StandardCharsets.UTF_8);
                // 允许 javadoc/注释里提到 BlackBoxCore（用于说明为何不引用），
                // 但不允许出现真实调用或 import。
                require(!source.contains("import top.niunaijun.blackbox.BlackBoxCore"),
                        name + " 不得 import BlackBoxCore（引擎调用面应收敛到 ZhiSandbox）");
                require(!source.contains("BlackBoxCore.get()"),
                        name + " 不得直接调用 BlackBoxCore.get()");
            }
        }

        // 4. 主进程判定不得触发引擎类加载
        require(!process.contains("import top.niunaijun.blackbox.BlackBoxCore"),
                "SandboxProcess 会在主进程被调用，不得 import BlackBoxCore"
                        + "（会触发其静态初始化，把 BlackBox 拉进主进程）");

        // 5. guest 调试安全边界
        require(debug.contains("单次最多写入"), "内存写入上限必须保留");
        require(debug.contains("目标内存页不可写"), "目标页可写检查必须保留");
        require(debug.contains("System.load(canonical)"), "受控 .so 加载必须保留");
        require(debug.contains("/proc/self/mem"), "受控内存通道必须保留");
        require(debug.contains("getPackageName()"), "私有目录白名单必须由运行时包名派生，不得写死");
        require(!debug.contains("/data/user/0/com.iqge"), "不得把其它应用的路径写死进安全边界");
        require(!debug.contains("/data/user/0/com.zhizhu"), "不得把本应用路径写死进安全边界");

        // 6. 阶段日志按 pid 分文件
        require(stage.contains("Process.myPid()"), "阶段日志必须按 pid 分文件");
        require(stage.contains("sandbox/stages"), "阶段日志目录必须是 sandbox/stages");
        require(!code(stage).contains("startup-stage.txt"), "不得退回单文件覆盖写（多进程会互相冲掉）");

        // 7. AndroidX Startup 移除
        require(manifest.contains("InitializationProvider\" tools:node=\"remove\""),
                "必须移除 AndroidX Startup 的 InitializationProvider");
        require(manifest.contains("ProfileInstallReceiver\" tools:node=\"remove\""),
                "必须移除 ProfileInstallReceiver");

        if (failures.isEmpty()) {
            System.out.println("SandboxHostArchitectureTest PASS");
        } else {
            for (String failure : failures) System.out.println("  FAIL  " + failure);
            throw new AssertionError(failures.size() + " 条架构不变式不满足");
        }
    }
}
