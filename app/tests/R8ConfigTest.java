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
 *
 * <p>另外两组是「全局开 R8」这次改动带进来的：
 *
 * <ul>
 *   <li><b>Bcore 的 minify 与它自己的 -dontwarn 必须同时在场。</b>只开 minify 会让
 *       构建直接失败（编译期 android.jar 里没有 ActivityThread 这类隐藏 API），
 *       而下一个人为了"让它编过"最省事的做法就是把 minify 关回去。</li>
 *   <li><b>baseline-prof.txt 的规则必须带 flags。</b>ART 的 profile 每条方法规则都要有
 *       H/S/P 之一，漏了构建会以 {@code At least one of flags 'H', 'S', 'P' must be
 *       specified} 失败 —— 而这份文件是手写的，这种错一次就会犯。</li>
 * </ul>
 */
public final class R8ConfigTest {

    private static final String APP_GRADLE = "app/build.gradle";
    private static final String APP_RULES = "app/proguard-rules.pro";
    private static final String BCORE_CONSUMER = "Bcore/consumer-rules.pro";
    private static final String BCORE_GRADLE = "Bcore/build.gradle";
    private static final String BCORE_RULES = "Bcore/proguard-rules.pro";
    private static final String BASELINE_PROFILE = "app/src/main/baseline-prof.txt";

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

        // ---- 5. Bcore 也要开 R8（用户要求「全局开 R8」） ---------------------
        //
        // 这里守的是**两件事不能同时丢**：
        //   a) Bcore 的 release 开着 minify；
        //   b) 它自己那份 -dontwarn 列表还在。
        // 只做 a 会让构建直接失败（编译期 android.jar 里没有 ActivityThread 这类
        // 隐藏 API，R8 报 Missing class 即中断），于是下一个人为了"让它编过"
        // 最省事的做法就是把 minify 关回去 —— 那这次改动就白做了。所以两条一起守。
        String bcoreGradle = stripComments(read(root, BCORE_GRADLE));
        require(bcoreGradle.contains("minifyEnabled true"),
                "Bcore 的 release 必须开 minifyEnabled true（用户要求全模块开 R8）。"
                        + "⚠️ 如果它是因为「Missing class」编不过而被关回去的，"
                        + "正确做法是在 Bcore/proguard-rules.pro 补 -dontwarn，"
                        + "而不是关掉 R8。");
        require(!bcoreGradle.contains("minifyEnabled false"),
                "Bcore 的 release 又关回 minifyEnabled false 了");

        String bcoreRules = stripComments(read(root, BCORE_RULES));
        for (String hidden : new String[]{
                "-dontwarn android.**", "-dontwarn dalvik.**", "-dontwarn mirror.**",
                "-dontwarn com.android.**"}) {
            require(bcoreRules.contains(hidden),
                    "Bcore/proguard-rules.pro 缺少 " + hidden + "：开 R8 后这些类是**故意**"
                            + "在编译期找不到的（Bcore 要 hook 的正是 android.jar 里不存在的东西），"
                            + "少了它会以 Missing class 中断构建。");
        }
        // Bcore 自己的 keep 一条都不能少 —— 沙箱宿主全靠注解驱动反射。
        for (String keep : new String[]{
                "-keep class top.niunaijun.blackbox.**",
                "-keep class top.niunaijun.blackreflection.**",
                "-keep @top.niunaijun.blackreflection.annotation.BClass class * {*;}"}) {
            require(bcoreRules.contains(keep),
                    "Bcore/proguard-rules.pro 缺少 `" + keep + "`："
                            + "开 R8 之后这类 keep 没了就是**运行期**失效"
                            + "（沙箱打不开或反射返回 null），不是编译错误。");
        }

        // ---- 6. Baseline Profile 要真的接上 ---------------------------------
        //
        // 手写规则里最容易犯的错是**漏掉 flags**：ART 的 profile 每条方法规则
        // 都必须带 H / S / P 至少一个（Hot / Startup / PostStartup），
        // 不带的话构建会以 "At least one of flags 'H', 'S', 'P' must be specified"
        // 失败。这条断言让那个失败在**提交前**就暴露，而不是等某次 release 构建。
        String profile = read(root, BASELINE_PROFILE);
        StringBuilder offenders = new StringBuilder();
        int ruleCount = 0;
        for (String rawLine : profile.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            ruleCount++;
            if (!line.startsWith("HSP") && !line.startsWith("HS") && !line.startsWith("HP")
                    && !line.startsWith("SP") && !line.startsWith("H") && !line.startsWith("S")
                    && !line.startsWith("P")) {
                offenders.append("  ").append(line).append("\n");
            }
        }
        require(offenders.length() == 0,
                "app/src/main/baseline-prof.txt 里有方法规则没带 flags（H/S/P 至少要有一个），"
                        + "构建会直接失败：\n" + offenders);
        require(ruleCount >= 20,
                "baseline-prof.txt 只剩 " + ruleCount + " 条规则 —— 冷启动路径被删得差不多了。"
                        + "它是手写的，删掉不会有人报错，只会让首屏少几百毫秒的收益悄悄消失。");
        // 只写本工程的类：Compose / Miuix / AndroidX 各自随包发布了自己的 profile
        // （构建产物里的 baselineProfiles/{0,1} 就是它们），在这里重复一遍没用，
        // 还会让这份手写清单失去「可核对」这个唯一价值。
        //
        // ⚠️ 判据是**被 profile 的那个类**（剥掉 flags 之后的 L…;），
        // 不是整行里出现了 Landroidx/ 这样的子串 —— 方法描述符里本来就满是
        // `Landroidx/compose/runtime/Composer;` 这类参数类型（那是我们自己方法的签名，
        // 治不了也不该治）。按整行 contains 判的话，这条断言会因为一行**正确**的规则
        // 而红，然后被人删掉 —— 那就成了"守卫自己把自己删了"。
        StringBuilder foreignRules = new StringBuilder();
        for (String rawLine : profile.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String body = line.replaceAll("^[HSP]+", "");
            if (!body.startsWith("Lcom/zhizhu/")) {
                foreignRules.append("  ").append(line).append("\n");
            }
        }
        require(foreignRules.length() == 0,
                "baseline-prof.txt 里出现了非本工程的类"
                        + "（被 profile 的那个类必须以 Lcom/zhizhu/ 开头）：\n" + foreignRules
                        + "第三方库的 profile 由它们自己随包发布、AGP 会自动合并，"
                        + "手写这里只会与上游的维护脱钩。");

        // ProfileInstaller 必须在依赖里：assets/dexopt/baseline.prof 要被装到设备上，
        // 靠的是它注册的那个 Startup Initializer。没有它，profile 只是白白打进包里。
        require(gradle.contains("androidx.profileinstaller:profileinstaller:"),
                "app/build.gradle 必须显式依赖 androidx.profileinstaller:profileinstaller"
                        + "（API 26 需要它才能把 assets/dexopt/baseline.prof 装到设备上；"
                        + "API 28+ 由系统自己处理）。少了它，baseline-prof.txt 只是白占包体。");

        // ---- 7. 本测试自身必须被 canonical suite 执行 ------------------------
        String script = read(root, "test-source-no-build.sh");
        require(script.contains("R8ConfigTest \"$PROJECT_ROOT\""),
                "canonical source suite 必须执行本守卫");

        System.out.println("R8ConfigTest PASS"
                + "（app 与 Bcore 的 R8 都开着 · JNI 名字绑定守住 · 注解与 blackreflection"
                + " 规则齐备 · baseline profile 规则合法且已接上）");
    }
}
