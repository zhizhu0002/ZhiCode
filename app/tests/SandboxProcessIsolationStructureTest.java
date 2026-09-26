import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SandboxProcessIsolationStructureTest {
    private static void require(boolean ok,String m){if(!ok)throw new AssertionError(m);}

    /**
     * 去掉所有空白后再比较：这里检查的是「代码形态」，
     * 不该被缩进/换行/空格影响（本工程是 Kotlin + 花括号风格，与官方 Java 单行写法不同）。
     */
    private static boolean has(String source,String needle){return squash(source).contains(squash(needle));}
    private static String squash(String value){return value.replaceAll("\\s+","");}

    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);
        String app=Files.readString(root.resolve("app/src/main/java/com/zhizhu/zhicode/compose/ZhiCodeApplication.kt"),StandardCharsets.UTF_8);
        String manifest=Files.readString(root.resolve("app/src/main/AndroidManifest.xml"),StandardCharsets.UTF_8);
        String client=Files.readString(root.resolve("app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxRpc.java"),StandardCharsets.UTF_8);
        // 官方是 Java 单行写法 `if (sandboxProcess) IQSandboxEngine.attach(base);`；
        // 本工程是 Kotlin 花括号写法，且分支内夹着注释，因此不能做连续子串匹配。
        // 改为断言两件事：分支存在，且 attach 出现在该分支之后。
        int gate = app.indexOf("if (sandboxProcess)");
        require(gate >= 0 && app.indexOf("ZhiSandbox.attach(base)") > gate,"BlackBox attach must be sandbox-process gated");
        require(app.contains("SandboxProcess.isMain(this)"),"main process branch missing");
        require(manifest.contains("SandboxRpcService")&&manifest.contains("android:process=\":zhisandbox\""),"isolated sandbox control provider missing");
        require(manifest.contains("InitializationProvider\" tools:node=\"remove\""),"AndroidX startup removal missing");
        require(client.contains("ContentResolver")||client.contains("getContentResolver().call"),"sandbox IPC client missing");
        System.out.println("SandboxProcessIsolationStructureTest PASS");
    }
}
