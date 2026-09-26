import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class FridaSandboxStructureTest {
    static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
    static String read(Path r,String p)throws Exception{return new String(Files.readAllBytes(r.resolve(p)),StandardCharsets.UTF_8);}
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args.length==0?".":args[0]).toAbsolutePath().normalize();
        String manager=read(root,"app/src/main/java/com/zhizhu/zhicode/sandbox/FridaEnv.java");
        String frida=read(root,"app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxFrida.java");
        String engine=read(root,"app/src/main/java/com/zhizhu/zhicode/sandbox/ZhiSandbox.java");
        String tool=read(root,"app/src/main/java/com/termux/app/zhicode/tools/ZhiDebugTool.java");
        String bridge=read(root,"app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxGuestHost.java");
        String config=read(root,"app/src/main/java/com/termux/app/zhicode/model/SessionConfig.java");
        String core=read(root,"app/src/main/java/com/termux/app/zhicode/core/ZhiCodeEngine.java");
        String prompt=read(root,"app/src/main/java/com/termux/app/zhicode/core/SystemPromptBuilder.java");
        require(manager.contains("17.17.0")&&manager.contains("942b66a229da11f1dda38c2c3c126bf23df76c0febc0925b6a5decc04687e871"),"Frida official arm64 release must be pinned and hash verified");
        require(manager.contains("setAutoAttach")&&manager.contains("isAutoAttachEnabled"),"Frida auto-attach policy must be persistent");
        // 这一条以前钉的是一长串拼写（`async function safeScan(options,outerDeadline)`、
        // `boundedInteger(p.chunk_size,4194304,65536,8388608)`、`Zhi.scan=(options)=>…` 之类），
        // 换个变量名就红，而真正的行为漂移（某块读不了就整条命令失败、扫描上限失守、
        // 重叠把 scanned 算多）一个也拦不住。现在拆成两半：
        //   · 这里只钉「脚本确实具备这几项能力」的**API 名字**（它们对应 Frida 的宿主对象，
        //     不是我们的实现细节）与「扫描走的是 Memory.scan 而不是同步的 scanSync」；
        //   · 行为本身由 app/tests/js/frida-agent-harness.mjs 真跑着验证
        //     （163 项断言：五种停止原因、部分失败、去重、重叠、并行、jsonSafe、legacy 改写）。
        require(frida.contains("System.load") && frida.contains("File.readAllText"),
                "Gadget 必须由本进程 System.load 载入，桥脚本必须轮询信箱文件");
        require(frida.contains("Memory.scan(") && frida.contains("readVolatile")
                        && frida.contains("Memory.patchCode") && frida.contains("Interceptor")
                        && frida.contains("Stalker"),
                "桥脚本必须具备读/写/插桩这几项能力（对应的宿主 API 名字是契约，不能是别的实现）");
        require(frida.contains("Process.enumerateRanges(") && frida.contains("scanSync"),
                "扫描必须自己枚举可读映射，并且认得 legacy 的 scanSync 调用");
        require(!frida.contains("Memory.scanSync(a,n,String(p.pattern))"),
                "专用扫描路径不得退回同步的 scanSync（那正是把 guest 卡死的那条路径）");
        require(frida.contains("script.getName()")&&frida.contains("\"libzhifrida.config\"")&&!frida.contains("\"libzhifrida.config.so\"")&&frida.contains("zhi-agent.js"),"Gadget config must use canonical .config naming and script path relative to Gadget");
        require(engine.contains("beforeApplicationOnCreate")&&engine.contains("isAutoAttachEnabled")&&engine.contains("SandboxFrida.load"),"auto-attach must happen before virtual Application.onCreate");
        require(tool.contains("frida_install")&&tool.contains("frida_auto_attach")&&tool.contains("frida_eval")&&tool.contains("frida_scan")&&tool.contains("frida_write")&&tool.contains("frida_watch")&&tool.contains("frida_patch"),"Agent Debug tool must expose managed Frida operations");
        require(bridge.contains("PixelCopy.request")&&bridge.contains("capture_method")&&bridge.contains("view_draw_fallback"),"Guest screenshot must prefer PixelCopy with explicit fallback reporting");
        require(config.contains("sandboxAgentFullAccess = true"),"sandbox full-access mode must be explicit in session config");
        require(core.contains("sandboxFullAccess")&&core.contains("!\"plan\".equals(effectivePermissionMode)")&&core.contains("\"Sandbox\".equals(call.name)")&&core.contains("!\"host\".equalsIgnoreCase"),"permission bypass must be sandbox-only, preserve plan read-only mode, and must not cover host Debug");
        require(prompt.contains("frida_runtime_status")&&prompt.contains("frida_auto_attach")&&prompt.contains("PixelCopy")&&prompt.contains("sandboxAgentFullAccess"),"system prompt must teach the Agent the dynamic debug and screenshot workflow");
        System.out.println("FridaSandboxStructureTest PASS");
    }
}
