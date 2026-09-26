import java.nio.file.*;

public final class FridaGadgetConfigRegressionTest {
    private static void require(boolean c,String m){if(!c)throw new AssertionError(m);}
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args.length==0?".":args[0]).toAbsolutePath().normalize();
        String bridge=Files.readString(root.resolve("app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxFrida.java"));
        // 断言「配置名由常量给出、且与 Gadget 同基名」，而不是把构造表达式写成一种固定形态：
        // 字面量被提成命名常量属于正常改动，不该被当成回归。
        // 真正要拦的是名字变成 .config.so，或配置基名与 Gadget 基名不一致——
        // Frida 按「自己的文件名换个后缀」找配置，两者不一致 = 配置被静默忽略。
        require(bridge.contains("\"libzhifrida.config\"") && bridge.contains("\"libzhifrida.so\""),
                "disk-loaded Gadget must use libzhifrida.so with the matching libzhifrida.config");
        require(bridge.contains("new File(dir, CONFIG_FILE)"),"Gadget config object must be built from the CONFIG_FILE constant");
        // 只禁「配置名被写成带 .so 后缀的 APK 兼容名」这一件事。
        // 不全局禁 ".config.so" 字面量：说明「为什么不用它」的注释里会出现这个词，
        // 而注释不会影响运行时行为，把它一起拦掉是误报。
        require(!bridge.contains("\"libzhifrida.config.so\""),"APK-lib compatibility name must not be used for private disk load");
        require(bridge.contains("put(\"path\", script.getName())"),"Script path must be relative to Gadget directory");
        require(bridge.contains("System.load(gadget.getAbsolutePath())"),"Gadget must still be loaded by absolute library path");
        // 旧名必须仍然被认识：机器上可能已经存在改名之前下载的那几十 MB 主副本。
        String env=Files.readString(root.resolve("app/src/main/java/com/zhizhu/zhicode/sandbox/FridaEnv.java"));
        require(env.contains("\"libiqfrida.so\"")&&env.contains("legacy.renameTo(current)"),
                "the pre-rename Gadget master copy must be renamed in place, not silently lost");
        System.out.println("FridaGadgetConfigRegressionTest PASS");
    }
}
