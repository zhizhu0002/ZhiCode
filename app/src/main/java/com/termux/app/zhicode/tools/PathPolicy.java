package com.termux.app.zhicode.tools;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 把工具收到的路径字符串解析成本地 {@link File}。
 *
 * <h3>为什么必须走 getCanonicalFile()</h3>
 * {@code ..} 与符号链接都能指到别处。不规范化的话，
 * 「{@code ../../../…/etc/passwd}」这类路径在字符串层面看不出问题，
 * 而所有基于路径的判断（比如 {@link DeleteTool} 的保护区检查）都会失效。
 *
 * <h3>{@code ~} 的展开</h3>
 * 用户与模型都会写 {@code ~}。这里展开成 HOME，而不是交给 shell ——
 * 文件类工具不经过 shell，不展开的话会得到一个名叫 {@code ~} 的相对路径。
 */
final class PathPolicy {

    /** 读取会永久阻塞或无限输出的设备文件。 */
    private static final Set<String> BLOCKING_DEVICES = Set.of(
        "/dev/zero", "/dev/random", "/dev/urandom", "/dev/full",
        "/dev/stdin", "/dev/stdout", "/dev/stderr", "/dev/tty", "/dev/console");

    /** {@code /proc/<pid>/fd/0|1|2}：指向某个进程的标准流，读它会挂住。 */
    private static final Pattern PROCESS_STDIO = Pattern.compile("^/proc/[^/]+/fd/[012]$");

    private PathPolicy() {}

    /**
     * 解析成绝对、规范化、存在的「形状」上可直接使用的路径。
     *
     * @throws IllegalArgumentException 路径为空 —— 调用方通常希望这是一次可读的工具错误，
     *         而不是在更深处抛一个 NPE
     */
    static File resolve(String projectDirectory, String raw) throws IOException {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("file path is required");
        }
        String path = expandHome(raw.trim());
        File file = new File(path);
        if (!file.isAbsolute()) file = new File(projectDirectory, path);
        return file.getCanonicalFile();
    }

    /** {@code ~} 与 {@code ~/…} 展开成 HOME；其余原样。 */
    private static String expandHome(String path) {
        String home = TermuxConstants.TERMUX_HOME_DIR_PATH;
        if (path.equals("~")) return home;
        if (path.startsWith("~/")) return home + path.substring(1);
        return path;
    }

    /**
     * 是否是「读一下就完蛋」的设备文件。
     *
     * <p>这些路径在文件系统上确实存在、也确实可读，但读的行为是阻塞或无限输出：
     * {@code /dev/zero} 会一直吐零字节，{@code /proc/self/fd/0} 会等标准输入。
     * 用户在提示里看不到任何反馈，引擎表现为卡死。
     */
    static boolean isDangerousPseudoFile(File file) {
        String path = file.getAbsolutePath();
        return BLOCKING_DEVICES.contains(path) || PROCESS_STDIO.matcher(path).matches();
    }
}
