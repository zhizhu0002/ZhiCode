package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.app.zhicode.termux.TermuxShellExecutor;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONObject;

/**
 * 显式授权后才可用的 apt/dpkg 修复入口。
 *
 * <p>用于「上次装包被打断」或「上游 .deb 里仍带旧前缀」把 dpkg 留在半配置状态的情形。
 * 顺序不能反：先把缓存里的 .deb 改好前缀，再让 dpkg 重新配置，
 * 否则 dpkg 会把同一个坏包再处理一遍。
 *
 * <h3>为什么把脚本名提出来算</h3>
 * 这个补丁脚本由 {@code RuntimeInstaller} 安装为 {@code bin/<BRAND_SLUG>-patch-deb}。
 * 这里原先写的是另一个名字（上一个产品名），于是 {@code [ -x ... ]} 恒假、
 * 整个补丁步骤被静默跳过 —— 表现为「修了但没修好」，且没有任何错误输出。
 * 现在由 {@link TermuxConstants#BRAND_SLUG} 派生，改名不会再出现第二次漂移。
 */
public final class TermuxRepairTool implements ZhiTool {

    private static final int TIMEOUT_MS = 15 * 60 * 1000;
    /** apt 中断退出码，与 shell 约定一致。 */
    private static final int EXIT_TIMED_OUT = 124;

    private final TermuxShellExecutor shell;

    public TermuxRepairTool(TermuxShellExecutor shell) {
        this.shell = shell;
    }

    @Override
    public String name() {
        return "TermuxRepair";
    }

    @Override
    public String description() {
        return "Repair the embedded Termux apt/dpkg state after an interrupted or incompatible package installation. "
                + "Patches cached deb paths, runs dpkg --configure -a, then apt-get -f install -y.";
    }

    @Override
    public JSONObject inputSchema() {
        return ToolSchemas.object(new JSONObject());
    }

    @Override
    public PermissionKind permissionKind() {
        return PermissionKind.SHELL;
    }

    @Override
    public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        String patcher = "$PREFIX/bin/" + TermuxConstants.BRAND_SLUG + "-patch-deb";
        // 最后一步 dpkg --audit 不改变状态，只把「还剩什么没配好」报出来 ——
        // 少了它，命令退出码为 0 会被当作「全好了」，而实际上可能仍有半配置包。
        String command = "set -o pipefail; "
                + "if [ -x \"" + patcher + "\" ]; then "
                + "find \"$PREFIX/var/cache/apt/archives\" -maxdepth 1 -type f -name '*.deb' "
                + "-exec \"" + patcher + "\" {} + 2>/dev/null || true; fi; "
                + "dpkg --configure -a; "
                + "apt-get -f install -y; "
                + "dpkg --audit";
        TermuxShellExecutor.Result result = shell.execute(command, config.projectDirectory, TIMEOUT_MS);
        return ToolExecutionResult.command(result.timedOut ? EXIT_TIMED_OUT : result.exitCode, result.combined());
    }
}
