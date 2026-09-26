package com.zhizhu.zhicode.sandbox;

import android.content.Context;
import android.util.AtomicFile;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 跨进程沙箱设置。
 *
 * <h3>为什么不用 SharedPreferences</h3>
 * 控制器进程与各 guest 进程都要读同一份设置。SharedPreferences 的多进程模式
 * 不保证跨进程可见性（写入方的内存缓存不会同步到别的进程），
 * 而这里「改了开关，另一个进程立刻看到」是基本要求。因此走 {@link AtomicFile}
 * 落盘：每次读都真正读文件，每次写都原子替换。
 *
 * <h3>版本与回退</h3>
 * 设置文件带 {@code version} 字段。字段增删会改变语义，而旧文件仍然躺在磁盘上，
 * 因此<b>版本不匹配一律当作没有设置</b>，回到默认值，而不是「读能读到的、忽略读不到的」——
 * 后者会让新旧字段混在同一个状态里，出现只有升级用户才复现的怪问题。
 *
 * <h3>读失败不抛异常</h3>
 * 任何读取失败都回退到安全默认值（Root 隐藏默认<b>开</b>），不往上抛：
 * 一份坏的设置文件不该让沙箱起不来。写失败则相反，必须抛出去——
 * 用户点了开关却没生效，这件事必须能被看见。
 */
final class SandboxPrefs {

    /** 串行化写入。这是个纯 Java 进程内锁；跨进程的一致性由 AtomicFile 的原子替换保证。 */
    private static final Object WRITE_LOCK = new Object();

    /** 设置文件格式版本。改字段语义时必须递增。 */
    private static final int VERSION = 1;

    /** 设置文件体积上限。设里只有几个布尔值，超过这个数说明文件已损坏。 */
    private static final int MAX_SETTINGS_BYTES = 64 * 1024;

    private static final String KEY_VERSION = "version";
    private static final String KEY_HIDE_ROOT = "hide_root";
    private static final String KEY_FLOATING_LOG = "show_floating_log";

    private static final String FILE_RELATIVE_PATH = "sandbox/settings.json";

    /** 两个开关的默认值都取「开」：隐藏 Root 更安全，浮层日志更便于诊断。 */
    private static final boolean DEFAULT_HIDE_ROOT = true;
    private static final boolean DEFAULT_FLOATING_LOG = true;

    private SandboxPrefs() {}

    // ------------------------------------------------------------ 公开读取

    /** Root 是否对 guest 隐藏。读不出来时按默认值（隐藏）处理。 */
    static boolean isRootHidden(Context context) {
        return load(context).optBoolean(KEY_HIDE_ROOT, DEFAULT_HIDE_ROOT);
    }

    /** 是否显示悬浮日志窗。 */
    static boolean isFloatingLogEnabled(Context context) {
        return load(context).optBoolean(KEY_FLOATING_LOG, DEFAULT_FLOATING_LOG);
    }

    // ------------------------------------------------------------ 公开写入

    static void setRootHidden(Context context, boolean hidden) throws Exception {
        update(context, KEY_HIDE_ROOT, hidden);
    }

    static void setFloatingLogEnabled(Context context, boolean enabled) throws Exception {
        update(context, KEY_FLOATING_LOG, enabled);
    }

    /**
     * 读改写必须在同一把锁里完成。
     *
     * <p>否则两个开关几乎同时被打开时，双方各自读到「只有对方改之前」的版本，
     * 后写的那份会把先写的改动丢掉——表现为「设置一个开关，另一个悄悄变回默认值」。
     */
    private static void update(Context context, String key, boolean value) throws Exception {
        synchronized (WRITE_LOCK) {
            JSONObject settings = load(context);
            settings.put(KEY_VERSION, VERSION);
            settings.put(key, value);
            store(context, settings);
        }
    }

    // ------------------------------------------------------------ 文件读写

    /**
     * 读设置。任何异常、体积异常、版本不匹配都返回默认状态。
     *
     * <p>体积检查放在读取循环里而不是读完之后：损坏文件可能是个巨大的文本，
     * 先读完再判断等于先把内存吃光。一旦越界立刻停止并回退。
     */
    private static JSONObject load(Context context) {
        AtomicFile file = atomicFile(context);
        if (!file.getBaseFile().isFile()) return defaults();
        try (FileInputStream in = file.openRead();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int total = 0;
            int read;
            while ((read = in.read(chunk)) != -1) {
                total += read;
                if (total > MAX_SETTINGS_BYTES) return defaults();
                buffer.write(chunk, 0, read);
            }
            JSONObject settings = new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            if (settings.optInt(KEY_VERSION, 0) != VERSION) return defaults();
            return settings;
        } catch (Exception unreadable) {
            // 文件被截断、JSON 语法坏掉、权限异常，都会走到这里。
            return defaults();
        }
    }

    /**
     * 写设置。这里刻意<b>不</b>吞异常：调用方是 UI 上的开关，
     * 写失败必须变成用户能看到的错误，否则开关会「看起来生效了」。
     */
    private static void store(Context context, JSONObject settings) throws Exception {
        AtomicFile file = atomicFile(context);
        File parent = file.getBaseFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IllegalStateException("无法创建沙箱设置目录: " + parent);
        }
        byte[] data = settings.toString(2).getBytes(StandardCharsets.UTF_8);
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(data);
            file.finishWrite(out);
        } catch (Exception failure) {
            // failWrite 会删掉写了一半的 .bak/临时文件；不调用的话下次 startWrite
            // 可能把残骸当成可恢复的旧版本。
            if (out != null) file.failWrite(out);
            throw failure;
        }
    }

    /** 默认状态。版本号一并写入，使「刚起步」与「设置过」在磁盘上形态一致。 */
    private static JSONObject defaults() {
        JSONObject settings = new JSONObject();
        try {
            settings.put(KEY_VERSION, VERSION);
        } catch (Exception ignored) {
            // JSONObject.put 只声明抛 JSONException，对 String 值不会发生。
        }
        return settings;
    }

    /**
     * 设置文件位置。
     *
     * <p>用 application context 而不是传入的那个：本类会被 Activity 调用，
     * 持有一个 Activity 引用的静态文件对象会让它泄漏。
     */
    private static AtomicFile atomicFile(Context context) {
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        return new AtomicFile(new File(app.getFilesDir(), FILE_RELATIVE_PATH));
    }
}
