package com.zhizhu.zhicode.sandbox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 主进程 → `:iqsandbox` 引擎的**第二条**通道：文件信箱 + 定向广播。
 *
 * ## 为什么还需要一条，ContentProvider 不够吗
 *
 * 不够，而且这不是理论问题 —— 它已经真实发生过：
 *
 * 当 **ZhiCode 自己被装进 IQ 沙箱**运行时（用本应用测本应用，是最常用的调试方式），
 * `SandboxHostClient` 的 `ContentResolver.call` 会直接抛
 *
 * <pre>
 *   IllegalArgumentException: Unknown authority com.zhizhu.code.sandbox.control
 * </pre>
 *
 * 原因不是权限、也不是 authority 拼错（宿主合并清单里那条是正确的）。
 * 真正的原因与导出红框那次**完全同一个**：BlackBox 把 guest 的
 * `ContentResolver` / `PackageManager` 虚拟化了 —— `resolveContentProvider`
 * 只查得到 **guest 自己安装的包**里的 provider。宿主 manifest 里声明的
 * `SandboxControlProvider` 属于宿主包，在 guest 内部看来**不存在**。
 *
 * 所以沙箱里跑的那份 ZhiCode 必须有一条不经过 ContentResolver 的路。
 *
 * ## 为什么是「文件 + 广播」而不是 Binder / AIDL / LocalSocket
 *
 * | 通道 | guest 里可用？ | 说明 |
 * |---|---|---|
 * | ContentProvider | ❌ | 就是上面那个问题 |
 * | 自定义 AIDL Binder | ❌ | 同样要靠 `bindService`，而服务解析也被虚拟化 |
 * | LocalSocket / 命名管道 | ⚠️ | 抽象命名空间在 guest 里可用性随 ROM 变化，且要自己写生命周期 |
 * | **文件 + 定向广播** | ✅ | 存粹是文件 IO + `sendBroadcast`，两者都不走包解析 |
 *
 * 文件这条还顺带解决了"结果怎么回来"：广播是单向的，结果必须落在双方都能读的位置。
 * 用**宿主**的 `filesDir/sandbox/ipc/`（guest 与宿主同 UID，目录可直接读写），
 * 与 `SandboxAgentBridge` 用的 `sandbox/agent-results/` 是同一套思路。
 *
 * ## 与 `SandboxAgentBridge` 的分工
 *
 * 那个桥是「宿主 → **guest**」（界面控制：截图、点按、dump）；
 * 这个是「主进程 → **:iqsandbox 引擎**」（装包、启停、状态）。
 * 两者方向不同、目标进程不同，不能合并。
 */
public final class SandboxControlMailbox extends BroadcastReceiver {

    /** 定向广播动作。带包名，避免与别的应用撞车。 */
    public static final String ACTION =
            "com.zhizhu.zhicode.sandbox.CONTROL_MAILBOX";

    private static volatile boolean registered;

    /** 在 `:iqsandbox` 进程注册一次。**只在引擎所在进程**有意义。 */
    public static synchronized void register(Context context) {
        if (registered) return;
        registered = true;
        IntentFilter filter = new IntentFilter(ACTION);
        Intent receiver = new Intent();
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(new SandboxControlMailbox(), filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(new SandboxControlMailbox(), filter);
        }
        SandboxDebugLog.event("控制信箱已注册（" + SandboxProcessRole.processName(context) + "）");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String id = intent.getStringExtra("request_id");
        final String action = intent.getStringExtra("action");
        final String method = intent.getStringExtra("method");
        final String payload = intent.getStringExtra("payload");
        if (id == null || id.isEmpty()) return;

        // 只有引擎所在进程处理。主进程收到自己的广播必须忽略，
        // 否则会自己给自己写一遍结果。
        if (SandboxProcessRole.isMainProcess(context)) return;
        if (!SandboxProcessRole.shouldAttachBlackBox(context)) return;

        new Thread(() -> {
            Bundle extras = new Bundle();
            extras.putString("payload", payload == null ? "{}" : payload);
            JSONObject out = SandboxControlProvider.handle(context, method, action, extras);
            writeResult(context, id, out);
        }, "zhi-sandbox-mailbox").start();
    }

    // ─────────────────────────── 客户端侧 ───────────────────────────

    /**
     * 发一次请求并等结果。
     *
     * @return 引擎的 JSON 结果；超时返回 `{ok:false, error:...}`（**不抛异常**，
     *         与 `SandboxHostClient` 的契约一致 —— 调用方都是按 `ok` 判定的）。
     */
    public static JSONObject call(Context context, String action, JSONObject payload, long timeoutMs) {
        File dir = ipcDir(context);
        String id = Long.toHexString(System.nanoTime()) + "-" + android.os.Process.myPid();
        File result = new File(dir, id + ".json");
        long deadline = SystemClock.uptimeMillis() + Math.max(1000L, timeoutMs);

        Intent intent = new Intent(ACTION).setPackage(context.getPackageName());
        intent.putExtra("request_id", id)
                .putExtra("action", action)
                .putExtra("method", "control")
                .putExtra("payload", payload == null ? "{}" : payload.toString());

        long nextSend = 0L;
        while (SystemClock.uptimeMillis() < deadline) {
            /*
             * 重发是这个通道必备的：接收方进程可能**还没起来**。
             * 广播发给一个未运行的进程会被丢弃（它不会像 Binder 那样把进程拉起来），
             * 而"第一次访问沙盒页时 :iqsandbox 尚未启动"正是最常见的情况。
             * 所以按 400ms 重发，直到收到结果或超时。
             */
            if (SystemClock.uptimeMillis() >= nextSend) {
                try {
                    context.sendBroadcast(intent);
                } catch (Throwable e) {
                    SandboxDebugLog.event("控制信箱发送失败: " + e);
                }
                nextSend = SystemClock.uptimeMillis() + 400L;
            }
            if (result.isFile()) {
                try {
                    String text = read(result);
                    return new JSONObject(text);
                } catch (Throwable e) {
                    return error("控制信箱结果不可解析: " + e.getMessage());
                } finally {
                    //noinspection ResultOfMethodCallIgnored
                    result.delete();
                }
            }
            try {
                Thread.sleep(35L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return error("等待控制信箱被中断");
            }
        }
        // 超时的文案要能指导下一步：区分"引擎没起来"与"引擎起来了但没回话"。
        return error("沙箱引擎进程（:iqsandbox）没有回话；"
                + "启动阶段见 filesDir/sandbox/startup-stage.txt");
    }

    // ─────────────────────────── 内部 ───────────────────────────

    private static File ipcDir(Context context) {
        // 优先宿主目录：guest 与宿主同 UID，且 BlackBox 不重映射 filesDir 之外的路径。
        Context host = ZhiSandboxEngine.hostContext();
        if (host == null) host = context;
        File dir = new File(host.getFilesDir(), "sandbox/ipc");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    private static void writeResult(Context context, String id, JSONObject out) {
        File dir = ipcDir(context);
        File tmp = new File(dir, id + ".tmp");
        File dst = new File(dir, id + ".json");
        try {
            try (FileOutputStream stream = new FileOutputStream(tmp)) {
                stream.write(out.toString().getBytes(StandardCharsets.UTF_8));
                stream.flush();
                stream.getFD().sync();
            }
            //noinspection ResultOfMethodCallIgnored
            dst.delete();
            if (!tmp.renameTo(dst)) {
                try (FileOutputStream stream = new FileOutputStream(dst)) {
                    stream.write(out.toString().getBytes(StandardCharsets.UTF_8));
                }
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (Throwable e) {
            SandboxDebugLog.event("控制信箱写结果失败: " + id + " / " + e);
        }
    }

    private static String read(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static JSONObject error(String message) {
        JSONObject o = new JSONObject();
        try {
            o.put("ok", false).put("error", message);
        } catch (Throwable ignored) {
        }
        return o;
    }
}
