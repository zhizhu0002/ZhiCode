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
        require(frida.contains("System.load")&&frida.contains("async function safeScan(options,outerDeadline)")&&frida.contains("Memory.scan(base,size,pattern")&&frida.contains("Process.enumerateRanges({protection:'r--',coalesce:false})")&&frida.contains("Zhi.scan=(options)=>safeScan(options||{})")&&!frida.contains("Memory.scanSync(a,n,String(p.pattern))")&&frida.contains("Memory.patchCode")&&frida.contains("readVolatile")&&frida.contains("watch_start")&&frida.contains("Interceptor")&&frida.contains("Stalker")&&frida.contains("File.readAllText"),"Frida Gadget bridge must execute bounded in-process instrumentation and live memory watch");
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
