import java.nio.file.*;
import java.util.*;

/**
 * R8 配置的守卫：混淆开着、且喂给 R8 的规则里那几条「非它不可」的 keep 还在。
 *
 * <p>为什么要专门守这个 —— R8 出问题**全都不是编译错误**：
 *
 * <ol>
 *   <li><b>原生方法按名字绑定</b>。{@code app/src/main/jniLibs/arm64-v8a/libtermux.so}
 *       是预编译产物，导出符号写死成 {@code Java_com_termux_terminal_JNI_createSubprocess}
 *       这种形式（实测 {@code nm}/{@code grep} 可见 5 个）。R8 一旦改名，终端永远起不来，
 *       报的是运行时 {@link UnsatisfiedLinkError}。本地实测：开 R8 后终端正常出
 *       {@code bash-5.3$} 并能执行命令，靠的就是这条 keep。</li>
 *   <li><b>注解驱动的反射</b>。Bcore 的 black-reflection 按 {@code @BClass} /
 *       {@code @BMethod(name = "…")} 反射成员。这几条原先**只写在
 *       {@code Bcore/proguard-rules.pro}** 里 —— 那个文件只作用于 Bcore 自己的构建，
 *       作为库被依赖时传给使用方的是 {@code consumer-rules.pro}。缺口已补，这里守住
 *       别再漏回去。</li>
 * </ol>
 *
 * <p>这两类问题的共同点是「过了编译、过了单测、启动也正常，只在某个具体功能上炸」，
 * 所以必须用静态断言钉住配置本身。
 */
public final class R8ConfigTest {

    private static final String APP_GRADLE = "app/build.gradle";
    private static final String APP_RULES = "app/proguard-rules.pro";
    private static final String BCORE_CONSUMER = "Bcore/consumer-rules.pro";

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(String root, String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get(root, relative)),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 去掉注释：否则「代码删了、注释里还写着」会让断言静默通过。 */
    private static String stripComments(String text) {
        String noBlock = text.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)//[^\\n]*", " ");
    }

    /** 取出 {@code release { ⋯ }} 这一段；找不到就报错，而不是悄悄整文件匹配。 */
    private static String releaseBlock(String gradle) {
        int start = gradle.indexOf("minifyEnabled");
        require(start >= 0, "app/build.gradle 里找不到 minifyEnabled —— R8 是不是被关掉了？");
        return gradle.substring(start, Math.min(gradle.length(), start + 800));
    }

    public static void main(String[] args) throws Exception {
        String root = args.length > 0 ? args[0] : ".";
        String gradleRaw = read(root, APP_GRADLE);
        String gradle = stripComments(gradleRaw);

        // ---- 1. R8 与资源压缩确实开着 ---------------------------------------
        String release = releaseBlock(gradle);
        require(release.contains("minifyEnabled true"),
                "release 必须开 minifyEnabled true（当前这段里没有）：\n" + release);
        require(release.contains("shrinkResources true"),
                "release 必须开 shrinkResources true（当前这段里没有）：\n" + release);
        require(release.contains("proguardFiles getDefaultProguardFile('proguard-android-optimize.txt')"),
                "release 必须挂上 AGP 的 optimize 默认规则，否则 R8 会丢掉一批默认 keep");
        require(release.contains("'proguard-rules.pro'"),
                "release 必须挂上本模块的 proguard-rules.pro");

        // 一个反例：别用 -dontoptimize / -dontshrink 之类把 R8 变成空转
        String rulesRaw = read(root, APP_RULES);
        String rules = stripComments(rulesRaw);
        for (String sabotage : new String[]{"-dontobfuscate", "-dontshrink", "-dontoptimize"}) {
            require(!rules.contains(sabotage),
                    "app/proguard-rules.pro 里出现了 " + sabotage
                            + " —— 这等于把 R8 关掉，体积会立刻涨回去");
        }

        // ---- 2. 原生方法的名字必须保住 --------------------------------------
        require(rules.contains("-keepclasseswithmembernames class *"),
                "app/proguard-rules.pro 缺少 -keepclasseswithmembernames class * —— "
                        + "JNI 名字绑定会失效");
        require(rules.contains("native <methods>;"),
                "缺 native <methods> 这条：libtermux.so 按名字绑定 Java_com_termux_terminal_JNI_*，"
                        + "改名后终端必崩（UnsatisfiedLinkError，不是编译错误）");
        require(rules.contains("-keep class com.termux.terminal.JNI"),
                "必须整类 keep com.termux.terminal.JNI：它的方法只被原生侧符号引用，"
                        + "Java 代码里看不到调用者，R8 会当死代码删掉");

        // ---- 3. 注解属性要留到运行期 ----------------------------------------
        require(rules.contains("RuntimeVisibleAnnotations"),
                "app/proguard-rules.pro 必须保留 RuntimeVisibleAnnotations —— "
                        + "black-reflection 靠运行期注解决定反射哪个成员");

        // ---- 4. Bcore 的 consumer 规则不能漏掉 blackreflection --------------
        String consumer = stripComments(read(root, BCORE_CONSUMER));
        require(consumer.contains("-keep class top.niunaijun.blackreflection."),
                "Bcore/consumer-rules.pro 缺少 blackreflection 的 keep。"
                        + "注意它**不能**只写在 Bcore/proguard-rules.pro 里 —— 那个文件"
                        + "只作用于 Bcore 自身构建，使用方拿到的是 consumer-rules.pro");
        require(consumer.contains("annotation.BClass class * {*;}"),
                "Bcore/consumer-rules.pro 缺少 @BClass 的 keep 规则");
        require(consumer.contains("annotation.BMethod.* <methods>;"),
                "Bcore/consumer-rules.pro 缺少 @BMethod.* 的 keep 规则");

        // ---- 5. 本测试自身必须被 canonical suite 执行 ------------------------
        String script = read(root, "test-source-no-build.sh");
        require(script.contains("R8ConfigTest \"$PROJECT_ROOT\""),
                "canonical source suite 必须执行本守卫");

        System.out.println("R8ConfigTest PASS"
                + "（R8 开启 · JNI 名字绑定守住 · 注解保留 · blackreflection consumer 规则齐备）");
    }
}
