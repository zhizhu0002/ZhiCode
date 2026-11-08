package com.zhizhu.zhicode.sandbox;

import android.content.Context;
import android.util.AtomicFile;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Cross-process settings shared by the sandbox controller and Guest processes. */
final class SandboxSettingsStore {
    private static final Object WRITE_LOCK = new Object();
    private static final int VERSION = 1;
    private static final int MAX_SETTINGS_BYTES = 64 * 1024;

    private SandboxSettingsStore() {}

    static boolean isRootHidden(Context context) {
        AtomicFile settings = atomicFile(context);
        if (!settings.getBaseFile().isFile()) return true;
        try (FileInputStream in = settings.openRead(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_SETTINGS_BYTES) return true;
                out.write(buffer, 0, read);
            }
            JSONObject value = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            if (value.optInt("version", 0) != VERSION) return true;
            return value.optBoolean("hide_root", true);
        } catch (Exception ignored) {
            return true;
        }
    }

    static boolean isFloatingLogEnabled(Context context) {
        return read(context).optBoolean("show_floating_log", true);
    }

    /**
     * 宿主主题的深浅。读不到时用调用方给的 [fallback]。
     *
     * <p>**必须由宿主写进来**，不能在 guest 里现读：guest 进程的
     * `getSharedPreferences` 被 BlackBox 虚拟化，读到的永远是默认值 ——
     * 症状就是"浅色主题下悬浮条仍是一块深色"（这正是这一版之前的老问题）。
     */
    static boolean themeDark(Context context, boolean fallback) {
        JSONObject value = read(context);
        return value.has("theme_dark") ? value.optBoolean("theme_dark", fallback) : fallback;
    }

    /**
     * 由**主进程**调用：把当前主题深浅记进沙箱设置，供 guest 侧的悬浮条配色。
     *
     * <p>值没变就不写盘 —— 这个方法挂在 `onResume` 上，不做判断的话每次切回前台
     * 都会产生一次文件写。
     */
    public static void publishThemeDark(Context context, boolean dark) {
        synchronized (WRITE_LOCK) {
            try {
                JSONObject value = read(context);
                if (value.has("theme_dark") && value.optBoolean("theme_dark", dark) == dark) return;
                value.put("version", VERSION).put("theme_dark", dark);
                write(context, value);
            } catch (Exception ignored) {
                // 为主题落盘失败而打断 onResume 不值得：悬浮条顶多颜色旧一点。
            }
        }
    }

    static void setFloatingLogEnabled(Context context, boolean enabled) throws Exception {
        synchronized (WRITE_LOCK) {
            JSONObject value = read(context);
            value.put("version", VERSION).put("show_floating_log", enabled);
            write(context, value);
        }
    }

    static void setRootHidden(Context context, boolean hidden) throws Exception {
        synchronized (WRITE_LOCK) {
            JSONObject value = read(context);
            value.put("version", VERSION).put("hide_root", hidden);
            write(context, value);
        }
    }

    private static JSONObject empty() {
        JSONObject value = new JSONObject();
        try { value.put("version", VERSION); } catch (Exception ignored) {}
        return value;
    }

    private static JSONObject read(Context context) {
        AtomicFile settings = atomicFile(context);
        if (!settings.getBaseFile().isFile()) return empty();
        try (FileInputStream in = settings.openRead(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_SETTINGS_BYTES) return empty();
                out.write(buffer, 0, read);
            }
            JSONObject value = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            return value.optInt("version", 0) == VERSION ? value : empty();
        } catch (Exception ignored) {
            return empty();
        }
    }

    private static void write(Context context, JSONObject value) throws Exception {
        AtomicFile settings = atomicFile(context);
        File parent = settings.getBaseFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IllegalStateException("Failed to create sandbox settings directory: " + parent);
        }
        byte[] data = value.toString(2).getBytes(StandardCharsets.UTF_8);
        FileOutputStream out = null;
        try {
            out = settings.startWrite();
            out.write(data);
            settings.finishWrite(out);
        } catch (Exception error) {
            if (out != null) settings.failWrite(out);
            throw error;
        }
    }

    /**
     * 设置文件的**唯一真实路径**。
     *
     * <h3>为什么不能直接用 `context.getFilesDir()`</h3>
     *
     * 这个类被三个进程读：`:iqsandbox`（控制器，写）、`:black` 与 `:pN`（guest，读）。
     * 在 **guest 进程**里，`context.getFilesDir()` 已经被 BlackBox 虚拟化成
     * `…/blackbox/data/user/0/<guest 包名>/files` —— 那是 guest 自己的目录，
     * 宿主写在 `…/files/sandbox/settings.json` 的那份**根本看不到**。
     *
     * 后果不是报错，而是**静默失效**：`read()` 读不到文件 → 返回默认值
     * （`show_floating_log = true`），于是「日志悬浮窗」这个开关关掉之后
     * 悬浮球照样出现；「隐藏 Root」也一样永远按默认值走。
     *
     * 所以这里显式拼宿主路径。同 UID，读得到；BlackBox 的文件钩子只重定向
     * guest 自己那棵子树，宿主路径是直通的。
     */
    private static File settingsFile(Context context) {
        Context app = context.getApplicationContext() == null ? context : context.getApplicationContext();
        File virtual = new File(app.getFilesDir(), "sandbox/settings.json");
        if (virtual.isFile()) return virtual;
        /*
         * `/data/user/0/<包名>` 与 `/data/data/<包名>` 是同一个目录（后者是符号链接），
         * 用前者：Android 4.2 起 `getFilesDir()` 返回的就是它，不必依赖符号链接。
         *
         * 包名取 BuildConfig.APPLICATION_ID —— 它是**宿主的**编译期常量，
         * 在任何进程里都是同一个值，不会跟着 guest 的虚拟包名走。
         */
        File host = new File("/data/user/0/" + com.zhizhu.zhicode.compose.BuildConfig.APPLICATION_ID,
                "files/sandbox/settings.json");
        if (host.isFile()) return host;
        return virtual;
    }

    private static AtomicFile atomicFile(Context context) {
        return new AtomicFile(settingsFile(context));
    }
}
