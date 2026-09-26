package com.zhizhu.zhicode.background;

import android.content.Context;
import android.content.SharedPreferences;

import com.termux.app.zhicode.termux.TermuxShellExecutor;
import com.termux.shared.termux.TermuxConstants;
import com.zhizhu.zhicode.compose.BuildConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 保活时对系统做的两处改动，以及把它们原样撤回。
 *
 * <h3>它只做两件事，而且都是「加进白名单」这一类</h3>
 * 一是 {@code cmd deviceidle whitelist} 把本应用加入电池优化白名单；二是用
 * {@code cmd appops set} 允许后台运行。两者都是 Android 官方的调试接口，
 * 也都需要 root —— 所以整个类的前提是「用户明确打开了 root 保活开关」。
 * 它不会去改别的应用的任何设置。
 *
 * <h3>为什么必须记住「改之前是什么」</h3>
 * 这两项都是<b>环境状态</b>，不是本应用自己的状态。用户原本可能已经手工加过白名单，
 * 或者本来就允许后台运行。关掉保活时若一律写回默认值，就等于把用户原本的设置改掉了。
 * 所以 apply 之前先读一遍现状，把「原来是什么」记下来，revoke 时再写回去。
 *
 * <h3>为什么包名取自 BuildConfig</h3>
 * 这些命令是把包名交给系统命令行执行的。一旦与实际包名不符，保活会<b>静默失效</b>：
 * 命令照样返回成功，只是白名单加到了另一个（可能并不存在的）包上。所以不写字面量。
 */
public final class RootKeepAliveController {

    private static final String PREFS = TermuxConstants.BRAND_SLUG + "_keep_alive";

    // 这三个键是持久化契约：它们记的是「本应用改之前的环境状态」。
    // 改名等于丢掉撤回能力，而那会留下一个用户自己没法清理的白名单条目。
    private static final String KEY_ADDED_DEVICEIDLE = "added_deviceidle";
    private static final String KEY_PREVIOUS_RUN_IN_BACKGROUND = "previous_run_in_background";
    private static final String KEY_PREVIOUS_RUN_ANY_IN_BACKGROUND = "previous_run_any_in_background";

    /** 目标包名，见类注释。 */
    private static final String PACKAGE = BuildConfig.APPLICATION_ID;

    private static final String SETTING_RUN_IN_BACKGROUND = "RUN_IN_BACKGROUND";
    private static final String SETTING_RUN_ANY_IN_BACKGROUND = "RUN_ANY_IN_BACKGROUND";

    private static final int PROBE_TIMEOUT_MS = 15_000;
    private static final int APPLY_TIMEOUT_MS = 20_000;
    private static final int REVOKE_TIMEOUT_MS = 20_000;

    /** appops 只认这几个取值。读到别的就当作「没读到」，宁可不动它。 */
    private static final String[] KNOWN_APPOP_MODES = {"default", "allow", "ignore", "deny"};

    private RootKeepAliveController() {}

    /**
     * 加强保活。
     *
     * <p>返回的是给人看的一句话（会显示在保活通知里）。失败时也返回一句话而不是抛异常：
     * 保活失败不该让服务崩掉 —— 那会让「已经启动的保活」反而消失。
     */
    public static String apply(Context context) {
        try {
            TermuxShellExecutor shell = new TermuxShellExecutor(context);
            String workingDirectory = context.getFilesDir().getAbsolutePath();

            TermuxShellExecutor.Result identity =
                    shell.executeAsRoot("id -u", workingDirectory, PROBE_TIMEOUT_MS);
            if (identity.exitCode != 0 || identity.timedOut || !"0".equals(identity.stdout.trim())) {
                return "Root 未授予 uid 0";
            }

            TermuxShellExecutor.Result before = shell.executeAsRoot(snapshotCommand(),
                    workingDirectory, PROBE_TIMEOUT_MS);
            String snapshot = before.combined();
            boolean alreadyWhitelisted = listsThisPackage(snapshot);
            String previousRun = appOpMode(snapshot, SETTING_RUN_IN_BACKGROUND);
            String previousAny = appOpMode(snapshot, SETTING_RUN_ANY_IN_BACKGROUND);

            TermuxShellExecutor.Result applied =
                    shell.executeAsRoot(applyCommand(), workingDirectory, APPLY_TIMEOUT_MS);
            if (applied.exitCode != 0 || applied.timedOut) {
                return "Root 保活策略执行失败：" + applied.combined();
            }

            prefs(context).edit()
                    .putBoolean(KEY_ADDED_DEVICEIDLE, !alreadyWhitelisted)
                    .putString(KEY_PREVIOUS_RUN_IN_BACKGROUND, previousRun)
                    .putString(KEY_PREVIOUS_RUN_ANY_IN_BACKGROUND, previousAny)
                    .apply();
            return alreadyWhitelisted ? "Root 保活已加强（系统白名单原本已存在）" : "Root 保活已加强";
        } catch (Exception failure) {
            return "Root 保活不可用：" + (failure.getMessage() == null ? failure.toString() : failure.getMessage());
        }
    }

    /**
     * 撤回保活时做的改动。
     *
     * <p>「什么都没记下来」就什么都不做 —— 这是本方法最重要的一行：没有记录说明本应用
     * 从未改过环境（或者上次 apply 就没成功），此时去写任何值都是无依据的。
     *
     * <p>写回成功之后才清掉记录。提前清掉的话，一次失败的命令会让记录丢失，
     * 用户就再也没有撤回的机会了。
     */
    public static void revoke(Context context) {
        SharedPreferences prefs = prefs(context);
        boolean removeWhitelist = prefs.getBoolean(KEY_ADDED_DEVICEIDLE, false);
        String previousRun = knownAppOpMode(prefs.getString(KEY_PREVIOUS_RUN_IN_BACKGROUND, ""));
        String previousAny = knownAppOpMode(prefs.getString(KEY_PREVIOUS_RUN_ANY_IN_BACKGROUND, ""));
        if (!removeWhitelist && previousRun.isEmpty() && previousAny.isEmpty()) return;

        List<String> commands = new ArrayList<>();
        if (removeWhitelist) commands.add("cmd deviceidle whitelist -" + PACKAGE);
        if (!previousRun.isEmpty()) {
            commands.add(appOpSetCommand(SETTING_RUN_IN_BACKGROUND, previousRun));
        }
        if (!previousAny.isEmpty()) {
            commands.add(appOpSetCommand(SETTING_RUN_ANY_IN_BACKGROUND, previousAny));
        }
        // 末尾补一个恒真的命令：只要前面有任意一条失败，整条复合命令仍然返回失败，
        // 但「全部成功」时退出码必须是 0，否则记录不会被清掉。
        commands.add("true");

        try {
            TermuxShellExecutor.Result result = new TermuxShellExecutor(context).executeAsRoot(
                    join(commands), context.getFilesDir().getAbsolutePath(), REVOKE_TIMEOUT_MS);
            if (result.exitCode == 0 && !result.timedOut) {
                prefs.edit()
                        .remove(KEY_ADDED_DEVICEIDLE)
                        .remove(KEY_PREVIOUS_RUN_IN_BACKGROUND)
                        .remove(KEY_PREVIOUS_RUN_ANY_IN_BACKGROUND)
                        .apply();
            }
        } catch (Exception ignored) {
            // 撤回失败时记录留在磁盘上，用户下次关保活时可以再试一次。
        }
    }

    // ------------------------------------------------------------ 命令行

    /** 读现状。三条命令的失败都被忽略：读不到就等于「不知道」，后续按默认处理。 */
    private static String snapshotCommand() {
        return "cmd deviceidle whitelist"
                + "; cmd appops get " + PACKAGE + " " + SETTING_RUN_IN_BACKGROUND + " 2>/dev/null"
                + "; cmd appops get " + PACKAGE + " " + SETTING_RUN_ANY_IN_BACKGROUND + " 2>/dev/null";
    }

    private static String applyCommand() {
        return "cmd deviceidle whitelist +" + PACKAGE + "; "
                + appOpSetCommand(SETTING_RUN_IN_BACKGROUND, "allow") + " 2>/dev/null || true; "
                + appOpSetCommand(SETTING_RUN_ANY_IN_BACKGROUND, "allow") + " 2>/dev/null || true";
    }

    private static String appOpSetCommand(String setting, String mode) {
        return "cmd appops set " + PACKAGE + " " + setting + " " + mode;
    }

    // ------------------------------------------------------------ 输出解析

    /**
     * 输出里有没有本应用的独立一行。
     *
     * <p>必须<em>整行相等</em>而不是 {@code contains}：{@code cmd deviceidle whitelist} 会列出
     * 所有已加白的包，被包含关系一判，任何一个包名是本应用前缀的应用都会让它误判成
     * 「已经在白名单里」，于是 apply 会把 {@code added_deviceidle} 记成 false，
     * 关掉保活时就不去撤回了 —— 用户的白名单里会永远留着这一条。
     */
    private static boolean listsThisPackage(String output) {
        if (output == null) return false;
        for (String line : splitLines(output)) {
            if (line.equals(PACKAGE)) return true;
        }
        return false;
    }

    /**
     * 从 {@code cmd appops get} 的输出里取某一项当前的取值。
     *
     * <p>输出形如 {@code RUN_IN_BACKGROUND: allow}。取值在最后一个冒号之后 ——
     * 不能按第一个冒号切：该行前面可能带包名，而包名里不含冒号但别的实现可能带。
     */
    private static String appOpMode(String output, String operation) {
        if (output == null) return "";
        for (String line : splitLines(output)) {
            if (!line.contains(operation)) continue;
            int colon = line.lastIndexOf(':');
            if (colon < 0) continue;
            String tail = line.substring(colon + 1).trim();
            int space = tail.indexOf(' ');
            String mode = space < 0 ? tail : tail.substring(0, space);
            String known = knownAppOpMode(mode);
            if (!known.isEmpty()) return known;
        }
        return "";
    }

    /** 只接受 appops 认得的取值。读到别的东西时当作「没读到」，宁可不动。 */
    private static String knownAppOpMode(String mode) {
        String value = mode == null ? "" : mode.trim().toLowerCase(Locale.US);
        return Arrays.asList(KNOWN_APPOP_MODES).contains(value) ? value : "";
    }

    private static List<String> splitLines(String output) {
        return Arrays.asList(output.split("\\r?\\n"));
    }

    private static String join(List<String> commands) {
        return join(commands, "; ");
    }

    /**
     * 拼命令串。
     *
     * <p>不用 {@code String.join}：那是 Java 8 API，在 Android 上要 API 26 才有，
     * 而本工程 minSdk 24 且没开 core library desugaring —— 编译能过、
     * 真机上会 `NoSuchMethodError`。
     */
    private static String join(List<String> parts, String separator) {
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            if (joined.length() > 0) joined.append(separator);
            joined.append(part);
        }
        return joined.toString();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
