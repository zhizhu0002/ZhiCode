import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 守住许可与归属声明不会被静默删掉。
 *
 * <p>为什么要有一条测试来管「文件在不在」：这些声明是**纯附加**的 ——
 * 删掉它们，编译照过、功能照跑、所有别的测试照过。也就是说，
 * 任何一次重构都可能在毫无提示的情况下把它们弄丢，
 * 而要等到别人指出侵权时才会发现。
 *
 * <p>因此这里不检查文件内容是否「好看」，只检查三件会真实导致侵权的事：
 * <ol>
 *   <li>各目录的许可文件与 NOTICE 存在；</li>
 *   <li>IQ Code 的 MIT 原文被逐字保留（MIT 的唯一硬性要求就是这段必须随附）；</li>
 *   <li>NOTICE 里写明了 GPL 程序与 Apache-2.0 库的区分 —— 这是本工程
 *       「不必整体 GPL 化」这个结论的前提，前提写不清楚，结论就没有依据。</li>
 * </ol>
 */
public final class LicenseNoticeStructureTest {

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static String read(Path root, String relative) throws Exception {
        Path file = root.resolve(relative);
        require(Files.isRegularFile(file), "缺少文件: " + relative);
        return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();

        // 1. 顶层：自己的许可 + 第三方声明 + 许可原文目录
        String license = read(root, "LICENSE");
        String notice = read(root, "NOTICE");
        require(license.contains("MIT License"), "顶层 LICENSE 必须是 MIT（蜘蛛自身代码）");
        // 版权人必须是**具体的人/主体**。这里出过一次：两处版权行一直停在
        // 「蜘蛛 (ZhiCode) contributors」这种占位写法上 —— 占位版权行在法律上是最弱的一环，
        // 而没有任何东西在管它（编译、功能、别的测试全都照过）。
        // 所以钉住具体署名，并明确禁止占位词回来。
        require(license.contains("Copyright (c) 2026 zhizhu0002"),
                "本工程的 MIT 版权行必须写明具体版权人");
        require(!license.contains("contributors") && !license.contains("(ZhiCode)"),
                "LICENSE 里不得再出现占位版权写法（contributors / (ZhiCode)）");
        String bundledMit = read(root, "THIRD-PARTY-LICENSES/MIT.txt");
        require(!bundledMit.contains("contributors") && !bundledMit.contains("(ZhiCode)"),
                "随附的 MIT 全文里也不得再出现占位版权写法");
        // GPL 传染性是这个工程最容易踩的坑，LICENSE 里必须直接说清为什么不必 GPL 化，
        // 否则后来者会以为可以随手把 GPL 库链进来。
        require(license.contains("聚合") && license.contains("libtermux.so"),
                "LICENSE 必须说明 bootstrap 的 GPL 程序是聚合分发、且被链接的 libtermux.so 不是 GPL");

        // 2. 三个 BlackBox 模块各自带许可与修改声明
        for (String module : new String[]{"Bcore", "black-reflection", "compiler"}) {
            String moduleLicense = read(root, module + "/LICENSE");
            require(moduleLicense.contains("Apache License"),
                    module + " 必须带 Apache-2.0 许可原文");
            require(read(root, module + "/NOTICE").contains("Apache"),
                    module + " 必须带说明其来源与许可的 NOTICE");
        }
        // Apache-2.0 第 4 条要求「修改过的文件必须带显著声明」。
        // 这条必须查**模块自己的** NOTICE —— 顶层 NOTICE 里也提到同一个类名，
        // 若读顶层，模块的修改声明被删光也不会被发现（反向验证时确实漏过）。
        String bcoreNotice = read(root, "Bcore/NOTICE");
        require(bcoreNotice.contains("SandboxContract.java"),
                "Bcore/NOTICE 必须点明实际修改了哪些文件（Apache-2.0 第 4 条）");
        require(bcoreNotice.contains("BlackBoxCore.java") && bcoreNotice.contains("AndroidManifest.xml"),
                "Bcore/NOTICE 的修改清单必须完整列出可核对的改动点");

        // 3. 许可原文可自足：MIT、Apache-2.0 全文、IQ Code 的 MIT（含其版权行）
        require(read(root, "THIRD-PARTY-LICENSES/Apache-2.0.txt").contains("Version 2.0, January 2004"),
                "必须随附 Apache-2.0 全文");
        require(read(root, "THIRD-PARTY-LICENSES/MIT.txt").contains("MIT License"),
                "必须随附 MIT 全文");
        String iqCode = read(root, "THIRD-PARTY-LICENSES/IQ-Code-MIT.txt");
        // 版权行是 MIT 要求必须保留的那一条，逐字核对而不是模糊匹配。
        require(iqCode.contains("Copyright (c) 2026 IQge"),
                "IQ Code 的 MIT 版权行必须逐字保留");
        require(iqCode.contains("The above copyright notice and this permission notice shall be included in all"
                        + "\ncopies or substantial portions of the Software."),
                "IQ Code 的 MIT 原文必须完整，不得改写");

        // 4. NOTICE 要区分「链接进进程的库」与「聚合分发的程序」——
        //    这正是本项目不必整体 GPL 化的判断依据。
        require(notice.contains("libtermux.so") && notice.contains("Apache-2.0"),
                "NOTICE 必须写明 libtermux.so 的归属与其许可是 Apache-2.0");
        require(notice.contains("Terminal Emulator for Android") && notice.contains("GPLv3-only"),
                "NOTICE 必须写明 termux-app 的 GPLv3 例外条款来源");
        require(notice.contains("独立可执行") && notice.contains("聚合"),
                "NOTICE 必须说明 bootstrap 内是独立程序、属聚合分发");
        require(notice.contains("bootstrap-aarch64.zip") && notice.contains("termux-packages"),
                "NOTICE 必须指明 bootstrap 的来源以便使用者取得源码");

        // 5. 分析过程的记录必须在，且指向可重跑的命令
        String analysis = read(root, "docs/licensing.md");
        require(analysis.contains("Java_com_termux_terminal_JNI_") && analysis.contains("jackpal"),
                "docs/licensing.md 必须记录 libtermux.so 的许可判定依据");
        require(analysis.contains("tools/provenance.sh"),
                "docs/licensing.md 必须指向可重跑的归属度量命令");

        // 6. 本测试自身必须被 canonical suite 执行，否则它只是一份摆设
        String script = read(root, "test-source-no-build.sh");
        require(script.contains("LicenseNoticeStructureTest.java")
                        && script.contains("LicenseNoticeStructureTest \"$PROJECT_ROOT\""),
                "canonical source suite 必须执行本许可回归测试");

        System.out.println("LicenseNoticeStructureTest PASS");
    }
}
