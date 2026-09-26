package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.app.zhicode.termux.TermuxShellExecutor;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 在嵌入式 Termux 里跑命令。
 *
 * <h3>它不只是「转发一次 exec」</h3>
 * 除执行之外还做四件事，每一件都对应一种真实出现过的失败：
 * <ol>
 *   <li><b>包管理器命令的网络策略。</b>apt 的默认重试次数与超时在移动网络上会
 *       反复卡在同一个坏连接上。写一份 apt 配置把重试压到 1 次、超时压到 35 秒，
 *       让失败尽快暴露出来，才有机会走第 2 步。</li>
 *   <li><b>官方源回退。</b>镜像可以回答轻量的可用性探测、却在拉一个 100 MB 的
 *       .deb 时反复断流。确认是网络类失败后，固定回官方源再重试一次。
 *       <b>不轮换到第三方镜像</b> —— 那等于把用户的软件来源交给我们猜。</li>
 *   <li><b>dpkg 半配置修复。</b>被打断的安装、或上游 .deb 里仍带旧前缀，
 *       都会让 dpkg 停在半配置状态，后续任何安装都会失败。修一次缓存里的包
 *       再重试。只对「前缀/dpkg 状态类」失败做这件事 —— 普通的 404/签名失败
 *       直接报给用户，重试一万次也不会好。</li>
 *   <li><b>超时下限。</b>见 {@link #MIN_PACKAGE_TIMEOUT_MS} 与
 *       {@link #MIN_BUILD_TIMEOUT_MS} 的注释。</li>
 * </ol>
 */
public final class BashTool implements ZhiTool {

    private static final int MIN_TIMEOUT_MS = 1000;
    private static final int MAX_TIMEOUT_MS = 2 * 60 * 60 * 1000;

    /**
     * 包管理命令的超时下限（一小时）。
     *
     * <p>OpenJDK、LLVM、Chromium 这类包单个归档就有上百 MB，而 apt 在下载期间
     * **不产生任何行输出**。按「多久没输出」判活的看门狗会把一个健康的下载杀掉。
     */
    private static final int MIN_PACKAGE_TIMEOUT_MS = 60 * 60 * 1000;

    /**
     * 构建/测试命令的超时下限（十五分钟）。
     *
     * <p>模型给 Gradle/Maven/NDK 命令的默认超时常常是 10～120 秒，
     * 而在 Android 上光是首次解析依赖就可能要几分钟。超时太小看起来像
     * 「agent 跑到一半停了」（退出码 124），很难联想到是超时。
     * 用户仍然可以取消、或者发一条纠偏消息 —— 两者都会立刻终止整棵进程树。
     */
    private static final int MIN_BUILD_TIMEOUT_MS = 15 * 60 * 1000;

    private static final int TIMEOUT_EXIT_CODE = 124;
    private static final int MIRROR_SWITCH_TIMEOUT_MS = 3 * 60 * 1000;
    private static final int REPAIR_TIMEOUT_MS = 15 * 60 * 1000;

    private static final String OFFICIAL_MAIN_REPOSITORY = "https://packages.termux.dev/apt/termux-main";
    private static final String OFFICIAL_ROOT_REPOSITORY = "https://packages.termux.dev/apt/termux-root";
    private static final String OFFICIAL_X11_REPOSITORY = "https://packages.termux.dev/apt/termux-x11";

    /** 回退目标。**只放官方源**，见类注释第 2 点。 */
    private static final List<String> MAIN_MIRROR_FALLBACKS = Arrays.asList(OFFICIAL_MAIN_REPOSITORY);

    /** 判定「网络类失败」的关键词。 */
    private static final List<String> MIRROR_FAILURE_MARKERS = Arrays.asList(
        "failed to fetch", "could not connect", "connection timed out", "connection timeout",
        "connection reset", "network is unreachable", "temporary failure resolving",
        "tls handshake", "certificate verification failed", "hash sum mismatch",
        "unexpected size", "undetermined error", "server returned", " 404 ", " 403 ");

    /** 判定「前缀/dpkg 状态类失败」的关键词，见类注释第 3 点。 */
    private static final List<String> DPKG_STATE_MARKERS = Arrays.asList(
        "/data/data/com.termux", "unable to stat './data/data/com.termux",
        "dpkg returned an error code", "dpkg was interrupted",
        "dependency problems - leaving unconfigured",
        "you must manually run 'dpkg --configure -a'");

    /**
     * 需要放宽超时的构建/测试命令（已小写）。
     *
     * <p>用「命令词」而不是「片段」来写：{@link #mentionsCommand} 会在
     * 串首、以及 {@code 空格 ; && |} 之后去找它。直接写 {@code contains("make")}
     * 会把 {@code cmake} 也算进来，而漏掉 {@code gradle assembleRelease}
     * （它以命令词开头、前面没有空格）。
     */
    private static final List<String> BUILD_COMMANDS = Arrays.asList(
        "gradlew", "gradle", "mvn", "mvnw", "cmake --build", "ndk-build", "ninja",
        "cargo build", "cargo test", "npm run build", "npm test",
        "bun run build", "bun test", "make");

    private final TermuxShellExecutor executor;
    /** 可为 null（单测里不需要意图桥）。 */
    private final AndroidIntentBridge androidIntent;

    public BashTool(TermuxShellExecutor executor) {
        this(executor, null);
    }

    public BashTool(TermuxShellExecutor executor, AndroidIntentBridge androidIntent) {
        this.executor = executor;
        this.androidIntent = androidIntent;
    }

    @Override public String name() { return "Bash"; }

    @Override public String description() {
        return "Run bash in embedded Termux for project commands, builds, tests, git, and packages.";
    }

    @Override public JSONObject inputSchema() {
        JSONObject properties = new JSONObject();
        try {
            properties.put("command", ToolSchemas.string("Shell command to execute with bash -lc."));
            properties.put("cwd", ToolSchemas.string(
                "Optional working directory. Relative paths are resolved against the active project."));
            properties.put("timeout_ms", ToolSchemas.integer("Timeout in milliseconds.", 1000));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return ToolSchemas.object(properties, "command");
    }

    @Override public PermissionKind permissionKind() { return PermissionKind.SHELL; }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        return execute(config, input, null);
    }

    @Override public ToolExecutionResult execute(SessionConfig config, JSONObject input, ProgressListener progress)
            throws Exception {
        String command = input.optString("command", "");

        // am start 必须由本应用进程发起（见 AndroidIntentTool）。这里拦下来转交，
        // 是因为模型经常会写 am start，而让它经由这里比让它失败后再纠正便宜得多。
        if (androidIntent != null) {
            ToolExecutionResult bridged = androidIntent.tryExecuteAmStart(command);
            if (bridged != null) return bridged;
        }

        String cwd = PathPolicy.resolve(config.projectDirectory,
            input.optString("cwd", config.projectDirectory)).getAbsolutePath();
        String lower = command.toLowerCase(Locale.US);
        boolean packageCommand = isPackageCommand(lower);
        int timeout = effectiveTimeout(input, lower, packageCommand);

        if (packageCommand) ensureAptNetworkPolicy();

        TermuxShellExecutor.OutputListener live = progress == null
            ? null
            : (chunk, stderr, elapsed) -> progress.onProgress(chunk, stderr, elapsed);
        TermuxShellExecutor.Result result = executor.execute(command, cwd, timeout, live);
        String output = result.combined();

        if (packageCommand && failed(result) && looksLikeMirrorFailure(output)) {
            Retry mirrorRetry = retryAgainstOfficialMirror(command, cwd, timeout, live, progress);
            if (mirrorRetry != null) {
                result = mirrorRetry.result;
                output = mirrorRetry.log;
            }
        }

        if (packageCommand && failed(result) && looksLikeDpkgStateFailure(output)) {
            Retry repairRetry = repairCachedPackagesThenRetry(command, cwd, timeout, live, progress);
            result = repairRetry.result;
            output = repairRetry.log;
        }

        if (packageCommand && result.exitCode != 0) output += packageManagerContext(result);
        return ToolExecutionResult.command(result.timedOut ? TIMEOUT_EXIT_CODE : result.exitCode, output);
    }

    // ------------------------------------------------------------------ 超时

    /** 把用户/模型给的时间夹进允许范围，再按命令类型抬到下限之上。 */
    private static int effectiveTimeout(JSONObject input, String lowerCommand, boolean packageCommand) {
        int requested = input.optInt("timeout_ms", TermuxShellExecutor.DEFAULT_TIMEOUT_MS);
        int timeout = Math.max(MIN_TIMEOUT_MS, Math.min(requested, MAX_TIMEOUT_MS));
        if (packageCommand) return Math.max(timeout, MIN_PACKAGE_TIMEOUT_MS);
        if (looksLikeBuildCommand(lowerCommand)) return Math.max(timeout, MIN_BUILD_TIMEOUT_MS);
        return timeout;
    }

    // -------------------------------------------------------------- 网络策略

    /**
     * 写一份 apt 网络配置。
     *
     * <p>{@code Pipeline-Depth 0} 关掉 HTTP 管线：某些镜像的管线实现会让
     * 大文件下载在最后一个包上永远挂住。{@code Retries 1} 是配合下面的回退用的 ——
     * 让 apt 少重试、把「这个源不行」这个结论尽快交给我们。
     */
    private static void ensureAptNetworkPolicy() throws Exception {
        File directory = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/etc/apt/apt.conf.d");
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Cannot create apt config directory: " + directory);
        }
        File config = new File(directory, "99" + TermuxConstants.BRAND_SLUG + "-network");
        String body = "Acquire::Retries \"1\";\n"
            + "Acquire::http::Timeout \"35\";\n"
            + "Acquire::https::Timeout \"35\";\n"
            + "Acquire::http::Pipeline-Depth \"0\";\n"
            + "DPkg::Lock::Timeout \"5\";\n";
        try (FileOutputStream out = new FileOutputStream(config, false)) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 依次尝试回退源；返回最后一次的结果与完整日志。 */
    private Retry retryAgainstOfficialMirror(String command, String cwd, int timeout,
                                             TermuxShellExecutor.OutputListener live,
                                             ProgressListener progress) throws Exception {
        StringBuilder log = new StringBuilder();
        TermuxShellExecutor.Result result = null;
        for (int i = 0; i < MAIN_MIRROR_FALLBACKS.size(); i++) {
            String mirror = MAIN_MIRROR_FALLBACKS.get(i);
            String notice = "\n[ZhiCode] 下载失败，正在固定 Termux 官方源 "
                + (i + 1) + "/" + MAIN_MIRROR_FALLBACKS.size() + ": " + mirror + "\n";
            log.append(notice);
            if (progress != null) progress.onProgress(notice, true, 0);

            TermuxShellExecutor.Result switched =
                executor.execute(mirrorSwitchCommand(mirror), cwd, MIRROR_SWITCH_TIMEOUT_MS, live);
            log.append(switched.combined()).append('\n');
            if (switched.exitCode != 0 || switched.timedOut) continue;

            if (progress != null) {
                progress.onProgress("\n[ZhiCode] Termux 官方源可用，重新执行原安装命令…\n", false, 0);
            }
            TermuxShellExecutor.Result retry = executor.execute(command, cwd, timeout, live);
            log.append("[retry @ ").append(mirror).append("]\n").append(retry.combined()).append('\n');
            result = retry;
            // 已经不是网络类失败就停：继续换源只会把同一个错误重放一遍。
            if (retry.exitCode == 0 || retry.timedOut || !looksLikeMirrorFailure(retry.combined())) break;
        }
        return result == null ? null : new Retry(result, log.toString());
    }

    /**
     * 固定官方源的命令。
     *
     * <p>它同时处理两种 sources 格式（老的单行 {@code sources.list} 与新的
     * {@code .sources} 风格）：注释掉前者里指向 termux-main 的行，
     * 把后者里指向它的 URI 改回官方并置 {@code Enabled: no}。
     * 不处理的话，我们写的 {@code <slug>-main.list} 会与旧条目互相冲突，
     * apt 会在两个源之间反复取到不同的版本。
     *
     * <p>原文件先备份到 {@code <slug>-backup/}：这是对用户环境的改动，
     * 得让他能回去。
     */
    private static String mirrorSwitchCommand(String mirror) {
        String slug = TermuxConstants.BRAND_SLUG;
        String quotedMirror = shellQuote(mirror);
        return "set -e; "
            + "aptroot=\"$PREFIX/etc/apt\"; listdir=\"$aptroot/sources.list.d\"; termuxdir=\"$PREFIX/etc/termux\"; "
            + "mkdir -p \"$listdir\" \"$aptroot/" + slug + "-backup\" \"$termuxdir\"; "
            + "if [ -f \"$aptroot/sources.list\" ] && [ ! -f \"$aptroot/" + slug + "-backup/sources.list\" ]; then "
            + "cp -p \"$aptroot/sources.list\" \"$aptroot/" + slug + "-backup/sources.list\"; fi; "
            + "for f in \"$aptroot/sources.list\" \"$listdir\"/*.list; do "
            + "[ -f \"$f\" ] || continue; [ \"$f\" = \"$listdir/" + slug + "-main.list\" ] && continue; "
            + "sed -i '/^[[:space:]]*deb[[:space:]].*termux-main/s/^/# ZhiCode fixed official main: /' \"$f\"; done; "
            + "for f in \"$listdir\"/*.sources; do [ -f \"$f\" ] || continue; "
            + "if grep -q 'termux-main' \"$f\"; then "
            + "sed -i 's|^[[:space:]]*URIs:.*termux-main.*|URIs: " + OFFICIAL_MAIN_REPOSITORY + "|' \"$f\"; "
            + "if grep -q '^[[:space:]]*Enabled:' \"$f\"; then sed -i 's/^[[:space:]]*Enabled:.*/Enabled: no/' \"$f\"; "
            + "else printf '\\nEnabled: no\\n' >> \"$f\"; fi; fi; done; "
            + "printf 'deb %s stable main\\n' " + quotedMirror + " > \"$listdir/" + slug + "-main.list\"; "
            // chosen_mirrors 是 Termux 自己的镜像选择记录，留着它会让 apt 又回到被判定为坏的源。
            + "rm -rf \"$termuxdir/chosen_mirrors\"; "
            + "printf 'MAIN=\"%s\"\\nROOT=\"%s\"\\nX11=\"%s\"\\nWEIGHT=1\\n' "
            + quotedMirror + " " + shellQuote(OFFICIAL_ROOT_REPOSITORY) + " "
            + shellQuote(OFFICIAL_X11_REPOSITORY) + " > \"$termuxdir/chosen_mirrors\"; "
            + "rm -f \"$PREFIX/var/cache/apt/archives/partial/\"* 2>/dev/null || true; "
            + "apt-get update -o Acquire::Retries=1 -o Acquire::http::Timeout=25 -o Acquire::https::Timeout=25";
    }

    // -------------------------------------------------------------- dpkg 修复

    /**
     * 修缓存里的包、重新配置 dpkg、然后重跑原命令。
     *
     * <p>脚本名必须由 {@link TermuxConstants#BRAND_SLUG} 派生：它由安装器装成
     * {@code bin/<slug>-patch-deb}。这里原先写的是上一个产品名，
     * 于是 {@code [ -x ... ]} 恒假、整个补丁步骤被静默跳过 ——
     * 表现为「修了但没修好」，而且没有任何报错。
     */
    private Retry repairCachedPackagesThenRetry(String command, String cwd, int timeout,
                                                TermuxShellExecutor.OutputListener live,
                                                ProgressListener progress) throws Exception {
        String patcher = "$PREFIX/bin/" + TermuxConstants.BRAND_SLUG + "-patch-deb";
        String repair = "set +e; "
            + "if [ -x \"" + patcher + "\" ]; then find \"$PREFIX/var/cache/apt/archives\" "
            + "-maxdepth 1 -type f -name '*.deb' -exec \"" + patcher + "\" {} + 2>/dev/null; fi; "
            + "dpkg --configure -a; "
            + "apt-get -f install -y";
        if (progress != null) progress.onProgress("\n[ZhiCode package compatibility repair]\n", true, 0);
        TermuxShellExecutor.Result repaired = executor.execute(repair, cwd, REPAIR_TIMEOUT_MS, live);
        if (progress != null) progress.onProgress("\n[retry]\n", false, 0);
        TermuxShellExecutor.Result retry = executor.execute(command, cwd, timeout, live);
        String log = "[ZhiCode package compatibility repair]\n" + repaired.combined()
            + "\n\n[retry]\n" + retry.combined();
        return new Retry(retry, log);
    }

    /**
     * 包管理命令失败时附上的环境上下文。
     *
     * <p>它把判断所需的三样东西（HOME、PREFIX、退出码）直接放进结果里，
     * 因为「dpkg 半配置」这类问题的排查第一步永远是确认这两个路径对不对。
     */
    private static String packageManagerContext(TermuxShellExecutor.Result result) {
        return "\n\n[Termux package-manager context]\nHOME=" + TermuxConstants.TERMUX_HOME_DIR_PATH
            + "\nPREFIX=" + TermuxConstants.TERMUX_PREFIX_DIR_PATH
            + "\nExit=" + (result.timedOut ? TIMEOUT_EXIT_CODE : result.exitCode)
            + "\nHint: the full stderr is visible in this failed Bash row."
            + " Use TermuxDoctor or TermuxRepair if dpkg is left half-configured.";
    }

    // ------------------------------------------------------------------ 判定

    private static boolean failed(TermuxShellExecutor.Result result) {
        // 超时不算「命令自己失败」：再试一次只是再等一轮超时。
        return result.exitCode != 0 && !result.timedOut;
    }

    private static boolean isPackageCommand(String lowerCommand) {
        return lowerCommand.contains("pkg ") || lowerCommand.contains("apt ")
            || lowerCommand.contains("apt-get ") || lowerCommand.contains("dpkg ")
            || lowerCommand.contains("dpkg-");
    }

    private static boolean looksLikeBuildCommand(String lowerCommand) {
        for (String command : BUILD_COMMANDS) {
            if (mentionsCommand(lowerCommand, command)) return true;
        }
        return false;
    }

    /**
     * 命令串里是否出现了某个命令词。
     *
     * <p>认三种位置：串首、空白之后、以及 {@code ; && |} 之后。
     * 这样 {@code cd x && make} 能命中，而 {@code cmake} 不会被当成 {@code make}。
     */
    private static boolean mentionsCommand(String lowerCommand, String command) {
        if (lowerCommand.startsWith(command)) return true;
        for (String prefix : new String[]{" ", ";", "&&", "|"}) {
            if (lowerCommand.contains(prefix + command)) return true;
        }
        return false;
    }

    private static boolean looksLikeMirrorFailure(String output) {
        return containsAny(output, MIRROR_FAILURE_MARKERS);
    }

    private static boolean looksLikeDpkgStateFailure(String output) {
        String lower = lower(output);
        if (containsAny(lower, DPKG_STATE_MARKERS)) return true;
        // 「permission denied」与「sub-process」都要与 dpkg 同时出现才算：
        // 单独出现时它们是别的工具的普通报错，重试无益。
        if (lower.contains("permission denied") && lower.contains("dpkg")) return true;
        return lower.contains("sub-process") && lower.contains("dpkg");
    }

    private static boolean containsAny(String output, List<String> markers) {
        String lower = lower(output);
        for (String marker : markers) {
            if (lower.contains(marker)) return true;
        }
        return false;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    /** 一次「修好之后再试一次」的结果与它给用户看的日志。 */
    private static final class Retry {
        private final TermuxShellExecutor.Result result;
        private final String log;

        private Retry(TermuxShellExecutor.Result result, String log) {
            this.result = result;
            this.log = log;
        }
    }
}
