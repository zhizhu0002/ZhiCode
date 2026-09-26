package com.zhizhu.zhicode.sandbox;

import android.content.Context;

import com.termux.app.zhicode.termux.TermuxShellExecutor;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 按需把官方 arm64 Frida Gadget 装进蜘蛛的私有存储。
 *
 * <p>来源被刻意钉死：URL 与压缩包 SHA-256 都指向 frida/frida 的官方 release，
 * 不从任意镜像取。传输与解压交给内置 Termux 的 curl + xz，
 * 但校验由 Java 侧做——脚本能改的只有文件，改不了这里算出来的摘要。
 *
 * <p>三道关卡的顺序是有意的：先校验压缩包摘要，再解压；解压后再确认产物是真 ELF。
 * 只做前者，一个「摘要正确但解压出空文件」的环境问题会以「Gadget 已安装」的形式漏过去。
 */
public final class FridaEnv {

    /** 固定的 Gadget 版本；目录名与下载缓存名都由它派生。 */
    public static final String VERSION = "17.17.0";

    /** 官方 release 资产地址。 */
    public static final String GADGET_URL =
            "https://github.com/frida/frida/releases/download/" + VERSION
                    + "/frida-gadget-" + VERSION + "-android-arm64.so.xz";

    /** 上述压缩包的 SHA-256。校验的是<b>下载到的压缩包</b>，不是解压后的 .so。 */
    public static final String GADGET_XZ_SHA256 =
            "942b66a229da11f1dda38c2c3c126bf23df76c0febc0925b6a5decc04687e871";

    /** 判定「装好了」的最小体积。真实 Gadget 远大于此，用它挡掉被截断的半个文件。 */
    private static final long MIN_GADGET_BYTES = 1024L * 1024L;

    /** auto-attach 状态文件的格式号。见 {@link #isAutoAttachEnabled} 的迁移说明。 */
    private static final int AUTO_ATTACH_FORMAT = 2;

    /** 读状态文件时的体积上限，防止一个被写坏的文件把内存吃光。 */
    private static final int MAX_STATE_BYTES = 1024 * 1024;

    private static final String DIR_ROOT = "sandbox/frida";
    private static final String GADGET_FILE = "libzhifrida.so";
    private static final String MARKER_FILE = "installed.json";
    private static final String AUTO_ATTACH_FILE = "auto-attach.json";

    private FridaEnv() {}

    // ------------------------------------------------------------- 路径与状态

    public static File root(Context context) {
        return new File(context.getFilesDir(), DIR_ROOT);
    }

    /** 主副本：安装动作的落点，也是各 guest session 的拷贝源。 */
    public static File masterGadget(Context context) {
        return new File(versionDir(context), GADGET_FILE);
    }

    /** 安装完成标记，记录压缩包摘要与时间，供事后追溯「装的是哪一份」。 */
    private static File marker(Context context) {
        return new File(versionDir(context), MARKER_FILE);
    }

    private static File versionDir(Context context) {
        return new File(root(context), "frida-" + VERSION);
    }

    /**
     * 是否已装好：存在、体积像样、且确实是 ELF。
     *
     * <p>三个条件缺一不可。只看存在会把半截文件当成可用；
     * 只看体积会把 1MB 的垃圾当成可用；ELF 头这一条才是决定性的。
     */
    public static boolean isInstalled(Context context) {
        File gadget = masterGadget(context);
        return gadget.isFile() && gadget.length() > MIN_GADGET_BYTES && isElf(gadget);
    }

    public static JSONObject status(Context context) throws Exception {
        File gadget = masterGadget(context);
        return new JSONObject()
                .put("version", VERSION)
                .put("installed", isInstalled(context))
                .put("path", gadget.getAbsolutePath())
                .put("size", gadget.isFile() ? gadget.length() : 0)
                .put("source", GADGET_URL)
                .put("asset_sha256", GADGET_XZ_SHA256);
    }

    // ----------------------------------------------------------- auto-attach

    /**
     * 该虚拟包是否要求「在 guest Application.onCreate 之前就把 Gadget 挂上」。
     *
     * <p>这道开关的意义：启动期 hook 只能靠 Gadget 在最早时机注入，
     * 等应用跑起来再 attach 就晚了。
     *
     * <p>关于 {@code __format}：早期版本写的是裸 {@code {包名: true}}，没有格式号。
     * 那种文件无法判断是「用户显式开启」还是「旧版遗留」，
     * 而后者会让下次启动在 guest 起来之前就注入——启动期崩溃最难查。
     * 因此凡是没有格式号或格式号对不上的，一律<b>当未开启</b>处理，升级后自动恢复安全默认值。
     */
    public static synchronized boolean isAutoAttachEnabled(Context context, String packageName) {
        String pkg = trimToEmpty(packageName);
        if (pkg.isEmpty()) return false;
        try {
            JSONObject state = readAutoAttach(context);
            if (state == null || state.optInt("__format", 0) != AUTO_ATTACH_FORMAT) return false;
            return state.optBoolean(pkg, false);
        } catch (Throwable unreadable) {
            // 文件坏了就按「未开启」——安全默认值，不该因为一个坏文件让 guest 无法启动。
            return false;
        }
    }

    /**
     * 打开/关闭某个虚拟包的 auto-attach。
     *
     * <p>整个文件读改写一次完成并加锁（synchronized）：两个 Agent 请求同时改不同包时，
     * 后写的不该把先写的冲掉。写法是「写临时文件再 rename」，
     * 因为 rename 在同一文件系统内是原子的——这样即使写到一半断电，
     * 读到的也还是旧的完整文件，而不是半个 JSON。
     */
    public static synchronized JSONObject setAutoAttach(Context context, String packageName, boolean enabled) throws Exception {
        String pkg = trimToEmpty(packageName);
        if (pkg.isEmpty()) throw new IllegalArgumentException("缺少 package 参数");

        JSONObject state = readAutoAttach(context);
        // 格式号对不上说明是旧版遗留，直接重开一份，不把旧状态携带过来。
        if (state == null || state.optInt("__format", 0) != AUTO_ATTACH_FORMAT) state = new JSONObject();
        state.put("__format", AUTO_ATTACH_FORMAT);
        if (enabled) state.put(pkg, true); else state.remove(pkg);

        writeAtomically(autoAttachFile(context), state.toString(2));
        return new JSONObject()
                .put("package", pkg)
                .put("auto_attach", enabled)
                .put("frida_installed", isInstalled(context));
    }

    private static File autoAttachFile(Context context) {
        return new File(root(context), AUTO_ATTACH_FILE);
    }

    /** 读 auto-attach 状态；文件不存在或不可解析时返回 null（调用方按「无状态」处理）。 */
    private static JSONObject readAutoAttach(Context context) {
        File file = autoAttachFile(context);
        if (!file.isFile()) return null;
        try {
            String text = readText(file, MAX_STATE_BYTES);
            if (text == null || text.isEmpty()) return null;
            return new JSONObject(text);
        } catch (Throwable malformed) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 安装

    /**
     * 下载并安装 Gadget。已装好则直接返回现状（{@code changed=false}）。
     *
     * <p>失败时抛出的异常里带上了命令输出，因为这条路失败的绝大多数原因是环境
     * （Termux runtime 未就绪、网络不通），而不是代码。
     */
    public static JSONObject install(Context context, TermuxShellExecutor shell, String cwd) throws Exception {
        if (isInstalled(context)) return status(context).put("changed", false);

        File destination = masterGadget(context);
        File gadgetDir = destination.getParentFile();
        File cacheDir = new File(root(context), "cache");
        ensureDirectory(gadgetDir, "Frida 目录");
        ensureDirectory(cacheDir, "Frida cache");

        File curl = termuxBinary("curl");
        File xz = termuxBinary("xz");
        if (!curl.isFile() || !xz.isFile()) {
            throw new IllegalStateException("内置 Termux runtime 还没就绪：安装 Frida 需要 curl 与 xz");
        }

        File archive = new File(cacheDir, "frida-gadget-" + VERSION + "-android-arm64.so.xz");
        File workDir = resolveWorkDir(cwd);

        download(shell, curl, archive, workDir);
        // 摘要算一次就够，顺带当作安装标记的内容，省掉第二遍整包读取。
        String archiveDigest = verifyArchive(archive);

        unpack(shell, xz, archive, destination, gadgetDir, workDir);
        if (!isInstalled(context)) throw new IllegalStateException("Frida Gadget 解压后不是有效 ELF");

        writeMarker(context, archiveDigest);
        SandboxConsole.event("Frida Gadget " + VERSION + " 已安装: " + destination);
        return status(context).put("changed", true);
    }

    /** 用 Termux 的 curl 取压缩包；先下到 .tmp 再 mv，避免半截文件被当成缓存命中。 */
    private static void download(TermuxShellExecutor shell, File curl, File archive, File workDir) throws Exception {
        File partial = new File(archive.getAbsolutePath() + ".tmp");
        String command = quote(curl.getAbsolutePath())
                + " -L --fail --retry 2 --connect-timeout 20"
                + " -o " + quote(partial.getAbsolutePath()) + " " + quote(GADGET_URL)
                + " && mv " + quote(partial.getAbsolutePath()) + " " + quote(archive.getAbsolutePath());
        TermuxShellExecutor.Result result = shell.execute(command, workDir.getAbsolutePath(), 240_000, null);
        if (result.exitCode != 0) throw new IllegalStateException("Frida Gadget 下载失败: " + result.combined());
    }

    /**
     * 校验下载到的压缩包摘要，成功时返回该摘要。
     *
     * <p>不匹配就地删除而不是留着：留着的话下次「已存在就复用」的优化会反复拿到坏包，
     * 而删除会让下一次重试重新走完整下载。
     */
    private static String verifyArchive(File archive) throws Exception {
        String digest = sha256(archive);
        if (GADGET_XZ_SHA256.equalsIgnoreCase(digest)) return digest;
        archive.delete();
        throw new SecurityException("Frida Gadget SHA-256 不匹配，拒绝安装。"
                + "expected=" + GADGET_XZ_SHA256 + " actual=" + digest);
    }

    /** 解压到 .tmp 并 chmod 700，最后一步 mv 就位——不给 Frida 看到半个 .so 的机会。 */
    private static void unpack(TermuxShellExecutor shell, File xz, File archive, File destination, File gadgetDir, File workDir) throws Exception {
        File partial = new File(gadgetDir, GADGET_FILE + ".tmp");
        String command = quote(xz.getAbsolutePath()) + " -dc " + quote(archive.getAbsolutePath())
                + " > " + quote(partial.getAbsolutePath())
                + " && chmod 700 " + quote(partial.getAbsolutePath())
                + " && mv " + quote(partial.getAbsolutePath()) + " " + quote(destination.getAbsolutePath());
        TermuxShellExecutor.Result result = shell.execute(command, workDir.getAbsolutePath(), 120_000, null);
        if (result.exitCode != 0) throw new IllegalStateException("Frida Gadget 解压失败: " + result.combined());
    }

    private static File termuxBinary(String name) {
        return new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, name);
    }

    /** 工作目录必须是真实存在的目录，否则退回 Termux HOME——否则 shell 会以失败退出。 */
    private static File resolveWorkDir(String cwd) {
        if (cwd != null && !cwd.trim().isEmpty()) {
            File candidate = new File(cwd.trim());
            if (candidate.isDirectory()) return candidate;
        }
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
    }

    private static void ensureDirectory(File dir, String label) {
        if (dir.isDirectory()) return;
        if (dir.mkdirs() || dir.isDirectory()) return;
        throw new IllegalStateException("无法创建 " + label + ": " + dir);
    }

    /** 记下本次装的是哪份压缩包，便于事后对比现场。写不成也不算失败。 */
    private static void writeMarker(Context context, String archiveSha) {
        try {
            JSONObject marker = new JSONObject()
                    .put("version", VERSION)
                    .put("compressed_sha256", archiveSha)
                    .put("installed_at", System.currentTimeMillis());
            writeAtomically(marker(context), marker.toString(2));
        } catch (Throwable ignored) {
            // 标记文件只是溯源线索，缺失不影响 Gadget 可用性。
        }
    }

    // ------------------------------------------------------------------ 文件

    /**
     * 原子写入：先写同目录的 .tmp，再 rename 覆盖。
     *
     * <p>不先删目标文件再 rename——那样在两者之间会出现「文件不存在」的窗口，
     * 而读方（另一个进程、或下一次请求）正好可能落在窗口里。
     */
    private static void writeAtomically(File target, String content) throws Exception {
        File parent = target.getParentFile();
        if (parent != null) ensureDirectory(parent, "Frida 目录");
        File tmp = new File(parent, target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        if (tmp.renameTo(target)) return;
        // 少数文件系统上 rename 到已存在的目标会失败，此时退回直接写并清理临时文件。
        try (FileOutputStream out = new FileOutputStream(target)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        } finally {
            tmp.delete();
        }
    }

    /** 有限读取文本；超过上限的部分丢弃而不是报错（状态文件总是「前面对就够了」）。 */
    private static String readText(File file, int maxBytes) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) Math.min(file.length(), maxBytes)];
            int read = in.read(buffer);
            return read <= 0 ? "" : new String(buffer, 0, read, StandardCharsets.UTF_8);
        }
    }

    /** 只看前 4 个字节：ELF magic。 */
    private static boolean isElf(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] header = new byte[4];
            return in.read(header) == 4
                    && (header[0] & 0xff) == 0x7f
                    && header[1] == 'E' && header[2] == 'L' && header[3] == 'F';
        } catch (Throwable unreadable) {
            return false;
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte b : digest.digest()) hex.append(String.format(Locale.US, "%02x", b & 0xff));
        return hex.toString();
    }

    /** 单引号包裹并转义内部的单引号，供 shell 命令拼接使用。 */
    private static String quote(String value) {
        return "'" + (value == null ? "" : value.replace("'", "'\\''")) + "'";
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
