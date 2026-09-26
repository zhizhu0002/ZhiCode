import java.nio.file.*;

public final class FridaGadgetConfigRegressionTest {
    private static void require(boolean c,String m){if(!c)throw new AssertionError(m);}
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args.length==0?".":args[0]).toAbsolutePath().normalize();
        String bridge=Files.readString(root.resolve("app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxFrida.java"));
        String env=Files.readString(root.resolve("app/src/main/java/com/zhizhu/zhicode/sandbox/FridaEnv.java"));

        // 断言「配置名由常量给出、且与 Gadget 同基名」，而不是把构造表达式写成一种固定形态：
        // 字面量被提成命名常量属于正常改动，不该被当成回归。
        // 真正要拦的是名字变成 .config.so，或配置基名与 Gadget 基名不一致——
        // Frida 按「自己的文件名换个后缀」找配置，两者不一致 = 配置被静默忽略。
        require(bridge.contains("\"libzhifrida.config\"") && bridge.contains("\"libzhifrida.so\""),
                "disk-loaded Gadget must use libzhifrida.so with the matching libzhifrida.config");
        require(bridge.contains("new File(dir, CONFIG_FILE)"),"Gadget config object must be built from the CONFIG_FILE constant");
        // 只禁「配置名被写成带 .so 后缀的 APK 兼容名」这一件事。
        require(!bridge.contains("\"libzhifrida.config.so\""),"APK-lib compatibility name must not be used for private disk load");
        require(bridge.contains("put(\"path\", script.getName())"),"Script path must be relative to Gadget directory");
        require(bridge.contains("System.load(gadget.getAbsolutePath())"),"Gadget must still be loaded by absolute library path");

        // 工程里不允许再出现旧品牌文件名（libiqfrida.so / libiqfrida.config / iq-agent.js）。
        // 曾经为了不重下那几十 MB 主副本而做过一次就地改名迁移；现在不做兼容了，
        // 所以这条断言要守的是「旧名彻底不在代码里」，而不是「旧名被迁移」。
        String both = env + bridge;
        require(!both.contains("libiqfrida"), "旧品牌库名不得出现在源码里");
        require(!both.contains("iq-agent.js"), "旧品牌脚本名不得出现在源码里");

        System.out.println("FridaGadgetConfigRegressionTest PASS");
    }
}
