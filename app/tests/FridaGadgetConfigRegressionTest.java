import java.nio.file.*;

public final class FridaGadgetConfigRegressionTest {
    private static void require(boolean c,String m){if(!c)throw new AssertionError(m);}
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args.length==0?".":args[0]).toAbsolutePath().normalize();
        String bridge=Files.readString(root.resolve("app/src/main/java/com/zhizhu/zhicode/sandbox/SandboxFrida.java"));
        // 断言「配置文件名必须是 libiqfrida.config，且配置对象由该常量构造」，
        // 而不是把构造表达式写成一种固定形态：字面量被提成命名常量属于重写范围内的
        // 正常改动，不该被当成回归。真正要拦的是名字变成 .config.so 或用了别的名字。
        require(bridge.contains("\"libiqfrida.config\"") && bridge.contains("new File(dir, CONFIG_FILE)"),
                "disk-loaded Gadget must use libiqfrida.config");
        require(!bridge.contains("libiqfrida.config.so"),"APK-lib compatibility name must not be used for private disk load");
        require(bridge.contains("put(\"path\", script.getName())"),"Script path must be relative to Gadget directory");
        require(bridge.contains("System.load(gadget.getAbsolutePath())"),"Gadget must still be loaded by absolute library path");
        System.out.println("FridaGadgetConfigRegressionTest PASS");
    }
}
