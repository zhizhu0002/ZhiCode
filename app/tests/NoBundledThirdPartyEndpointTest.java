import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * 防止「厂商内置端点」被重新引入。
 *
 * <p>应用不再预置任何厂商地址：请求只能发往用户自己填的 Base URL。
 * 但这类东西很容易被无意带回来 —— 一次复制粘贴、一次从上游同步、
 * 甚至一句“先给个默认值方便调试”就够了，而它的后果是用户的流量被导向某个具体服务。
 *
 * <p>所以这里对<b>全部源码文本</b>做一次字面量扫描，而不是只检查某个已知常量：
 * 检查对象包括我们的源码目录与构建脚本，检查方式是子串匹配，
 * 因此注释里提到这些域名也会被抓到 —— 那正是想要的：提都不该提。
 *
 * <p>下面这几个词只出现在本文件里。这就是它们应该待的地方：
 * 描述“什么不允许出现”的地方。
 */
public final class NoBundledThirdPartyEndpointTest {

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    /** 被禁止的字面量（小写比较）。 */
    private static final String[] FORBIDDEN = {
        "ginka.cloud",   // 某个厂商的代管服务地址
        "?aff=",         // 推广/返利参数
        "ctoken.top",    // 更早版本内置过的另一个第三方地址
    };

    /** 扫描范围：源码与脚本；跳过构建产物。 */
    private static final String[] ROOTS = {
        "app/src/main/java",
        "app/src/test",
        "app/tests",
        "Bcore/src/main",
        "black-reflection/src/main",
        "compiler/src/main",
        "tools",
    };

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();

        int scanned = 0;
        List<String> hits = new ArrayList<>();
        for (String relative : ROOTS) {
            Path dir = root.resolve(relative);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path file : walk.filter(Files::isRegularFile).collect(Collectors.toList())) {
                    String name = file.getFileName().toString();
                    if (!(name.endsWith(".java") || name.endsWith(".kt")
                            || name.endsWith(".xml") || name.endsWith(".sh")
                            || name.endsWith(".gradle") || name.endsWith(".kts"))) continue;
                    // 本文件自身必须提到这些词，否则它无法描述自己禁止什么。
                    if (name.equals("NoBundledThirdPartyEndpointTest.java")) continue;
                    scanned++;
                    String text = new String(Files.readAllBytes(file), "UTF-8").toLowerCase(Locale.US);
                    for (String bad : FORBIDDEN) {
                        if (text.contains(bad)) hits.add(root.relativize(file) + " 含有 " + bad);
                    }
                }
            }
        }

        require(scanned > 100, "扫描到的文件太少（" + scanned + "），路径大概写错了——这种测试必须真的扫到东西");
        require(hits.isEmpty(), "源码里不允许出现厂商端点或推广参数，但发现：\n  " + String.join("\n  ", hits));

        // 反向确认扫描确实在工作：拿一串必然出现的文本试一次。
        // 否则「一个都没扫到」和「扫描器坏了」看起来是一样的。
        Path self = root.resolve("app/src/main/java/com/termux/app/zhicode/api/ApiUrlPolicy.java");
        require(Files.isRegularFile(self), "扫描基线文件不存在，测试无法自证");
        String baseline = new String(Files.readAllBytes(self), "UTF-8");
        require(baseline.contains("Base URL"),
                "扫描器读不到已知内容，说明读取环节坏了——此时上面那个“没有命中”的结论不可信");

        System.out.println("NoBundledThirdPartyEndpointTest PASS (扫描 " + scanned + " 个文件)");
    }
}
