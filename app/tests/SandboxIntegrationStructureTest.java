import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SandboxIntegrationStructureTest {
    static void require(boolean v,String m){if(!v)throw new AssertionError(m);} static String read(Path r,String p)throws Exception{return new String(Files.readAllBytes(r.resolve(p)),StandardCharsets.UTF_8);}
    /**
     * 把源码里所有双引号字面量的内容按出现次序拼起来。
     *
     * <p>断言要检查的是「这个文件最终会输出什么文字」，不是「这些字被打在了哪几行」。
     * 直接对源码做 contains 会把**换行位置**也变成契约：任何重排都会让断言失败，
     * 而重排不改变行为。所以先把字面量还原成内容再比较。
     */
    static String literals(String source){
        StringBuilder out=new StringBuilder(); int i=0;
        while(i<source.length()){
            if(source.charAt(i)!='"'){i++; continue;}
            i++;
            while(i<source.length()){
                char d=source.charAt(i);
                if(d=='\\'){ if(i+1<source.length()) out.append(source.charAt(i+1)); i+=2; continue; }
                if(d=='"'){ i++; break; }
                out.append(d); i++;
            }
        }
        return out.toString();
    }
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args.length==0?".":args[0]).toAbsolutePath().normalize();
        String manifest=read(root,"app/src/main/AndroidManifest.xml"), registry=read(root,"app/src/main/java/com/termux/app/zhicode/tools/ToolRegistry.java"), prompt=read(root,"app/src/main/java/com/termux/app/zhicode/core/SystemPromptBuilder.java");
        String tool=read(root,"app/src/main/java/com/termux/app/zhicode/tools/ZhiSandboxTool.java"), bridge=read(root,"app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxGuestHost.java"), engine=read(root,"app/src/main/java/com/zhizhu/zhicode/sandbox/ZhiSandbox.java"), overlay=read(root,"app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxOverlay.java"), gradle=read(root,"app/build.gradle");
        require(manifest.contains(".ZhiCodeApplication")&&manifest.contains("SandboxBoard")&&manifest.contains("SandboxKeeper"),"sandbox host components must be registered");
        require(registry.contains("new ZhiSandboxTool(context)"),"Sandbox Agent tool must be registered");
        require(tool.contains("dump_ui")&&tool.contains("screenshot")&&tool.contains("click_node")&&tool.contains("tap")&&tool.contains("swipe")&&tool.contains("input_text"),"agent must be able to inspect and operate guest UI");
        require(bridge.contains("ACTION_CONTROL")&&bridge.contains("registerReceiver")&&bridge.contains("OVERLAY_TAG")&&bridge.contains("dispatchTouchEvent"),"cross-process guest control bridge must remain real");
        require(engine.contains("SandboxKeeper.start")&&engine.contains("BlackBoxCore.get().launchApk"),"guest launch must keep 蜘蛛 alive and use Bcore");
        require(overlay.contains("activity.addContentView")&&overlay.contains("日志")&&overlay.contains("返回")&&overlay.contains("停止"),"guest must expose a return/debug control without leaving the virtual Activity unmanaged");
        // 检查的是提示词**内容**（literals），而不是它在源码里的换行位置。
        String promptText=literals(prompt);
        require(promptText.contains("AskUserQuestion")&&promptText.contains("pm install -r")&&promptText.contains("AndroidIntent operation=install_apk")&&promptText.contains("Sandbox action=install")&&promptText.contains("Never silently choose the real phone"),"APK install destination policy must be baked into the system prompt");
        require(gradle.contains("implementation project(':Bcore')"),"Bcore must be integrated as a library and stable signing retained");
        System.out.println("SandboxIntegrationStructureTest PASS");
    }
}
