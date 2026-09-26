package com.zhizhu.zhicode.sandbox;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import top.niunaijun.blackbox.app.BActivityThread;

/**
 * Sandbox / Debug 工具共用的跨进程控制面。
 *
 * <h3>为什么用广播 + 结果文件</h3>
 * 请求方在主进程，干活的那一方在某个 guest 进程里，两者之间没有可以直接调用的对象。
 * 这里选的通道是「有序可寻址广播 + 私有目录里的结果文件」：
 * 广播带一个 request_id 与截止时间投出去，任何自认为该处理的 guest 把结果写成
 * {@code <id>.json}，请求方轮询到这个文件就读走。相比绑定 Service，
 * 这条通道不需要宿主与 guest 之间建立连接，也不怕 guest 被回收后连接残留在半途。
 *
 * <h3>谁该处理哪个请求</h3>
 * <ul>
 *   <li>{@code proc_*}：只有被<b>显式选中 PID</b> 的那个 guest 处理。
 *       目的是让 Agent 能看模块基址、线程、内存、加载调试库，
 *       而不必去 ptrace 不相干的 Android 进程。</li>
 *   <li>UI 动作：只有持有「当前前台 guest Activity」的那个进程处理。</li>
 * </ul>
 * 不满足条件的接收者必须<b>静默返回</b>，绝不能回一个错误结果——
 * 否则先到的「不是你」会覆盖掉后到的真正答案，表现为「命令时好时坏」。
 *
 * <h3>一次请求的完整生命周期（截图）</h3>
 * 广播可能被多个 guest 收到，也可能落到一个还没有前台 Activity 的进程上，
 * 因此截图这条路径额外做三件事：用 {@code .claim} 文件做原子抢占（只允许一个进程真正执行）、
 * 在截止时间内重复广播（等 Activity 起来），以及把整个抓帧过程关进
 * {@link CaptureSession}（单次终态 + 超时 + 浮层必复原）。
 */
public final class SandboxGuestHost {

    /** 控制广播的 action；宿主与 guest 两侧必须一致。 */
    public static final String ACTION_CONTROL = "com.zhizhu.zhicode.sandbox.AGENT_CONTROL";

    // ---------------------------------------------------------- 广播协议键

    private static final String EXTRA_REQUEST_ID = "request_id";
    private static final String EXTRA_ACTION = "action";
    private static final String EXTRA_TARGET_PACKAGE = "target_package";
    private static final String EXTRA_TARGET_PID = "target_pid";
    private static final String EXTRA_DEADLINE = "deadline_uptime_ms";
    private static final String EXTRA_PAYLOAD = "payload";

    /** proc_* 动作前缀：这类动作按进程寻址，不按 Activity 寻址。 */
    private static final String PROC_PREFIX = "proc_";
    private static final String ACTION_SCREENSHOT = "screenshot";

    // ------------------------------------------------------------ 时限容量

    /** 请求超时下限。低于它连一次广播往返都盖不住。 */
    private static final long MIN_REQUEST_TIMEOUT_MS = 500;
    /** 结果文件的轮询间隔。 */
    private static final long RESULT_POLL_MS = 35;

    /**
     * 截图请求的重发间隔。
     *
     * <p>只有截图需要重发：UI 动作要求 Activity 已经在前台，广播一次就够；
     * 而截图往往在「用户刚点开应用、窗口还没布局完」时被请求，
     * 单次广播落空后如果不重发，这次请求就只能等超时。
     */
    private static final long BROADCAST_RETRY_INTERVAL_MS = 250L;

    /** 抓帧像素上限。超出则等比缩小，避免一次截图吃掉几十 MB 的位图。 */
    private static final long MAX_CAPTURE_PIXELS = 1_000_000L;
    /** 单张 PNG 的体积上限；超限视为编码异常而不是「拍到了大图」。 */
    private static final long MAX_SCREENSHOT_BYTES = 5L * 1024L * 1024L;
    /** 截图保留时长。 */
    private static final long SCREENSHOT_MAX_AGE_MS = 60L * 60L * 1000L;
    /** 同时保留的截图张数。 */
    private static final int MAX_RETAINED_SCREENSHOTS = 8;
    /** 请求中间产物（.json/.claim/.tmp）的保留时长。 */
    private static final long REQUEST_ARTIFACT_MAX_AGE_MS = 2L * 60L * 1000L;
    /** 启动抓帧前要求剩余的时间；太短的话流程会中途过期，不如直接判定为过期。 */
    private static final long MIN_SCREENSHOT_REMAINING_MS = 250L;

    // ------------------------------------------------------------ UI 抓取

    private static final int MAX_UI_DEPTH = 40;
    private static final int MAX_UI_NODES = 1800;
    private static final int MAX_DESC_CHARS = 240;
    private static final int MAX_TEXT_CHARS = 400;
    private static final int TAP_HOLD_MS = 45;
    private static final int SWIPE_STEPS = 12;

    // ------------------------------------------------------------ 抓帧状态

    private static final ExecutorService CAPTURE_IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "iq-sandbox-capture-io");
        t.setDaemon(true);
        return t;
    });

    /** 进程内当前正在处理的截图请求 id；非空即表示忙。 */
    private static final AtomicReference<String> ACTIVE_CAPTURE = new AtomicReference<>();

    private static volatile boolean registered;

    private SandboxGuestHost() {}

    // ---------------------------------------------------------------- 注册

    /**
     * 在当前进程注册控制接收者。
     *
     * <p>{@code RECEIVER_NOT_EXPORTED} 是必需的：这条通道只服务于本应用内的
     * 宿主↔guest 通信，广播里带着可读写的私有路径与内存操作指令，
     * 不导出让其它应用无法直接投递伪造的控制请求。
     */
    public static synchronized void register(Context context) {
        if (registered) return;
        registered = true;
        IntentFilter filter = new IntentFilter(ACTION_CONTROL);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                handle(c, i);
            }
        };
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(receiver, filter);
    }

    // ---------------------------------------------------------------- 请求

    /** 便捷重载：从 payload 里的 {@code pid} 取目标进程。 */
    public static JSONObject request(Context context, String action, String target, JSONObject payload, int timeoutMs) throws Exception {
        int pid = payload == null ? -1 : payload.optInt("pid", -1);
        return request(context, action, target, pid, payload, timeoutMs);
    }

    /**
     * 投出一次控制请求并等待结果。
     *
     * <p>即便超时也返回一个 {@code ok:false} 的 JSON，而不是抛异常：
     * 「没人响应」在这条通道上是一种预期结局（目标进程没起来、Activity 不在前台），
     * 让调用方去区分「异常」与「没响应」只会增加判断分支。
     */
    public static JSONObject request(Context context, String action, String target, int targetPid, JSONObject payload, int timeoutMs) throws Exception {
        File dir = resultDir(context);
        pruneRequestArtifacts(dir);

        String id = Long.toHexString(System.nanoTime()) + "-" + Process.myPid();
        File result = new File(dir, id + ".json");
        File claim = new File(dir, id + ".claim");
        if (result.exists()) result.delete();

        long deadline = SystemClock.uptimeMillis() + Math.max(MIN_REQUEST_TIMEOUT_MS, timeoutMs);
        final boolean screenshot = ACTION_SCREENSHOT.equals(action);
        final Intent intent = buildIntent(context, id, action, target, targetPid, deadline, payload);
        context.sendBroadcast(intent);

        long nextSend = SystemClock.uptimeMillis() + BROADCAST_RETRY_INTERVAL_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (result.isFile()) {
                String text = readFile(result);
                result.delete();
                return new JSONObject(text);
            }
            // 重发的前提是「还没被任何进程认领」。已经出现 .claim 说明有进程正在做，
            // 此时再广播只会让别的 guest 白忙一场。
            long now = SystemClock.uptimeMillis();
            if (screenshot && now >= nextSend && !claim.isFile()) {
                context.sendBroadcast(intent);
                nextSend = now + BROADCAST_RETRY_INTERVAL_MS;
            }
            Thread.sleep(RESULT_POLL_MS);
        }
        return new JSONObject().put("ok", false).put("error", timeoutExplanation(action) + "未响应；确认 Guest 已启动且 package 仍然有效");
    }

    private static Intent buildIntent(Context context, String id, String action, String target, int targetPid, long deadline, JSONObject payload) {
        Intent intent = new Intent(ACTION_CONTROL).setPackage(context.getPackageName());
        intent.putExtra(EXTRA_REQUEST_ID, id)
                .putExtra(EXTRA_ACTION, action)
                .putExtra(EXTRA_TARGET_PACKAGE, target == null ? "" : target.trim())
                .putExtra(EXTRA_TARGET_PID, targetPid)
                .putExtra(EXTRA_DEADLINE, deadline)
                .putExtra(EXTRA_PAYLOAD, payload == null ? "{}" : payload.toString());
        return intent;
    }

    /** 超时文案要说清「本该谁响应」，否则使用者只能猜是进程没起来还是动作名写错了。 */
    private static String timeoutExplanation(String action) {
        if (action != null && action.startsWith(PROC_PREFIX)) return "目标沙箱进程";
        if (ACTION_SCREENSHOT.equals(action)) return "截图桥（Guest Activity 尚未进入前台或截图超时）";
        return "前台沙箱 Activity";
    }

    // ---------------------------------------------------------------- 接收

    private static void handle(Context context, Intent intent) {
        String action = intent.getStringExtra(EXTRA_ACTION);
        String id = intent.getStringExtra(EXTRA_REQUEST_ID);
        String target = intent.getStringExtra(EXTRA_TARGET_PACKAGE);
        target = target == null ? "" : target.trim();
        int targetPid = intent.getIntExtra(EXTRA_TARGET_PID, -1);
        long deadline = intent.getLongExtra(EXTRA_DEADLINE, SystemClock.uptimeMillis() + 5000L);

        String guestPkg = safeVirtualPackage();
        if (targetPid > 0 && targetPid != Process.myPid()) return;
        if (!target.isEmpty() && !target.equals(guestPkg)) return;

        JSONObject payload;
        try {
            payload = new JSONObject(intent.getStringExtra(EXTRA_PAYLOAD));
        } catch (Throwable malformed) {
            payload = new JSONObject();
        }

        if (action != null && action.startsWith(PROC_PREFIX)) {
            handleProcessAction(context, action, id, guestPkg, payload);
            return;
        }
        handleUiAction(context, action, id, target, deadline, payload);
    }

    /**
     * 进程类动作：在<b>本进程自己</b>身上执行。
     *
     * <p>主进程必须直接忽略。它并不是虚拟运行时里的 guest，
     * 如果让它响应，Agent 就能以「调试沙箱」的名义去操作宿主自身的进程内存。
     * 这一条不是优化，是权限边界。
     */
    private static void handleProcessAction(Context context, String action, String id, String guestPkg, JSONObject payload) {
        if (guestPkg.isEmpty() || guestPkg.equals(context.getPackageName())) return;
        new Thread(() -> {
            JSONObject out;
            try {
                out = SandboxGuestDebug.dispatch(context, action, payload);
            } catch (Throwable e) {
                out = error(e);
            }
            writeResult(context, id, out);
        }, "iq-sandbox-proc-debug").start();
    }

    /** UI 类动作：只有持有前台 guest Activity 的进程处理。 */
    private static void handleUiAction(Context context, String action, String id, String target, long deadline, JSONObject payload) {
        final Activity activity = ZhiSandbox.resumedActivity();
        final String pkg = ZhiSandbox.resumedPackage();
        if (activity == null || pkg == null || pkg.isEmpty()) return;
        if (!target.isEmpty() && !target.equals(pkg)) return;

        if (ACTION_SCREENSHOT.equals(action)) {
            try {
                activity.runOnUiThread(() -> {
                    // 顺序不能颠倒：先确认窗口真的可用，再去抢 claim。
                    // 抢在前面的结果是「一个还没布局完的进程把请求认领了，然后失败」，
                    // 而请求本身明明是可以靠重发成功的那一类。
                    if (!screenshotActivityReady(activity, pkg) || SystemClock.uptimeMillis() >= deadline) return;
                    if (!claimScreenshot(context, id, deadline)) return;
                    if (!ACTIVE_CAPTURE.compareAndSet(null, id)) {
                        JSONObject busy = error(new IllegalStateException("截图桥正在处理另一个请求"));
                        CAPTURE_IO.execute(() -> writeResult(context, id, busy));
                        return;
                    }
                    new CaptureSession(activity, context, id, pkg, deadline).start();
                });
            } catch (Throwable e) {
                SandboxConsole.event("截图桥调度失败: " + id + " / " + e);
            }
            return;
        }

        activity.runOnUiThread(() -> {
            JSONObject out;
            try {
                out = runUiAction(activity, pkg, action, payload);
            } catch (Throwable e) {
                out = error(e);
            }
            writeResult(context, id, out);
        });
    }

    /** UI 动作的实际执行；全部必须在主线程调用。 */
    private static JSONObject runUiAction(Activity activity, String pkg, String action, JSONObject p) throws Exception {
        JSONObject out = new JSONObject()
                .put("ok", true)
                .put("package", pkg)
                .put("activity", activity.getClass().getName())
                .put("pid", Process.myPid());
        switch (action == null ? "" : action) {
            case "dump_ui":
                out.put("ui", dumpUi(activity));
                break;
            case "click_node":
                nodeByPath(activity, p.optString("node", "")).performClick();
                break;
            case "long_click_node":
                nodeByPath(activity, p.optString("node", "")).performLongClick();
                break;
            case "set_text":
                applyText(nodeByPath(activity, p.optString("node", "")), p.optString("text", ""));
                break;
            case "tap":
                tap(activity, (float) p.optDouble("x", 0d), (float) p.optDouble("y", 0d));
                break;
            case "swipe":
                swipe(activity,
                        (float) p.optDouble("x1", 0d), (float) p.optDouble("y1", 0d),
                        (float) p.optDouble("x2", 0d), (float) p.optDouble("y2", 0d),
                        Math.max(80, p.optInt("duration_ms", 320)));
                break;
            case "input_text":
                inputText(activity, p.optString("text", ""));
                break;
            case "back":
                activity.onBackPressed();
                break;
            case "finish":
                activity.finish();
                break;
            default:
                out.put("ok", false).put("error", "未知控制动作: " + action);
        }
        return out;
    }

    // ------------------------------------------------------------ UI 树快照

    private static JSONArray dumpUi(Activity activity) throws Exception {
        JSONArray out = new JSONArray();
        walk(activity.getWindow().getDecorView(), "0", out, 0);
        return out;
    }

    /**
     * 递归遍历视图树。
     *
     * <p>跳过带 {@link SandboxOverlay#OVERLAY_TAG} 的节点：那是我们自己的浮层，
     * 既不该被 Agent 当成 guest 界面来点，也不该出现在截图里。
     *
     * <p>同时受深度与节点数双重限制。只要节点数上限就够了吗？不够——
     * 一棵深度上千的畸形视图树会在到达节点数上限之前先把栈撑爆。
     */
    private static void walk(View v, String path, JSONArray out, int depth) throws Exception {
        if (v == null || depth > MAX_UI_DEPTH || out.length() > MAX_UI_NODES) return;
        if (SandboxOverlay.OVERLAY_TAG.equals(v.getTag())) return;

        int[] xy = new int[2];
        v.getLocationOnScreen(xy);
        JSONObject node = new JSONObject()
                .put("node", path)
                .put("class", v.getClass().getName())
                .put("bounds", xy[0] + "," + xy[1] + "," + (xy[0] + v.getWidth()) + "," + (xy[1] + v.getHeight()))
                .put("visible", v.getVisibility() == View.VISIBLE)
                .put("enabled", v.isEnabled())
                .put("clickable", v.isClickable())
                .put("focusable", v.isFocusable());
        CharSequence desc = v.getContentDescription();
        if (desc != null && desc.length() > 0) node.put("content_desc", clip(desc.toString(), MAX_DESC_CHARS));
        if (v instanceof TextView) {
            CharSequence text = ((TextView) v).getText();
            if (text != null && text.length() > 0) node.put("text", clip(text.toString(), MAX_TEXT_CHARS));
        }
        out.put(node);

        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                walk(group.getChildAt(i), path + "/" + i, out, depth + 1);
            }
        }
    }

    /**
     * 按 {@code dump_ui} 给出的路径找回节点。
     *
     * <p>路径会失效（界面一变，同一路径就指向别的控件），所以每一步都重新校验：
     * 既校验父节点还是容器，也校验下标没越界。若不校验而直接转型或取值，
     * 失败会变成 ClassCastException / IndexOutOfBounds 这类无从解释的报错，
     * 而这里抛的是「node 已失效: <路径>」——使用者据此就知道该重新 dump 一次。
     */
    private static View nodeByPath(Activity activity, String path) {
        if (path == null || path.trim().isEmpty()) throw new IllegalArgumentException("node 不能为空");
        String[] parts = path.split("/");
        View current = activity.getWindow().getDecorView();
        for (int i = 1; i < parts.length; i++) {
            if (!(current instanceof ViewGroup)) throw new IllegalArgumentException("node 路径不是容器: " + path);
            int index = Integer.parseInt(parts[i]);
            ViewGroup group = (ViewGroup) current;
            if (index < 0 || index >= group.getChildCount()) throw new IllegalArgumentException("node 已失效: " + path);
            current = group.getChildAt(index);
        }
        return current;
    }

    // ---------------------------------------------------------- 触摸与输入

    /**
     * 点按：在视图树根节点上派发一对 DOWN/UP。
     *
     * <p>用 {@code dispatchTouchEvent} 而不是 {@code performClick}，
     * 是因为按坐标操作时并不知道目标控件是谁；派发到根节点后由系统自己命中测试。
     */
    private static void tap(Activity activity, float x, float y) {
        View root = activity.getWindow().getDecorView();
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + TAP_HOLD_MS, MotionEvent.ACTION_UP, x, y, 0);
        try {
            root.dispatchTouchEvent(down);
            root.dispatchTouchEvent(up);
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    /**
     * 滑动：同样派发到根节点，但分成若干中间 MOVE。
     *
     * <p>中间步数不能省。直接 DOWN 到 UP 会被识别成「长按后松手」，
     * 而 RecyclerView / ViewPager 这类控件需要看到连续的 MOVE 才会开始滚动。
     */
    private static void swipe(Activity activity, float x1, float y1, float x2, float y2, int duration) {
        View root = activity.getWindow().getDecorView();
        long start = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, x1, y1, 0);
        root.dispatchTouchEvent(down);
        down.recycle();
        for (int i = 1; i <= SWIPE_STEPS; i++) {
            float fraction = i / (float) SWIPE_STEPS;
            long at = start + (long) (duration * fraction);
            MotionEvent move = MotionEvent.obtain(start, at,
                    i == SWIPE_STEPS ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE,
                    x1 + (x2 - x1) * fraction, y1 + (y2 - y1) * fraction, 0);
            root.dispatchTouchEvent(move);
            move.recycle();
        }
    }

    /**
     * 往指定节点写文本。
     *
     * <p>TextView 直接 setText 即可；其它控件走输入法连接——因为对 EditText 之外的
     * 自绘输入控件，setText 不会触发它真正的文本更新逻辑。
     */
    private static void applyText(View v, String text) {
        if (v instanceof TextView) {
            ((TextView) v).setText(text);
            return;
        }
        v.requestFocus();
        EditorInfo info = new EditorInfo();
        InputConnection connection = v.onCreateInputConnection(info);
        if (connection == null) throw new IllegalStateException("目标 View 不接受文本输入");
        connection.commitText(text, 1);
    }

    /** 往当前焦点控件写文本；没有焦点时给出可读原因而不是 NPE。 */
    private static void inputText(Activity activity, String text) {
        View focused = activity.getCurrentFocus();
        if (focused == null) throw new IllegalStateException("当前没有获得焦点的输入控件");
        EditorInfo info = new EditorInfo();
        InputConnection connection = focused.onCreateInputConnection(info);
        if (connection == null) throw new IllegalStateException("当前焦点不接受文本输入");
        connection.commitText(text, 1);
    }

    // ------------------------------------------------------------ 截图前置

    /**
     * 该 Activity 现在是否真的可以抓帧。
     *
     * <p>四个条件缺一不可，且都会真实发生：Activity 正在销毁、窗口已脱离、
     * 尺寸仍为 0（还没布局）、或者前台已经换成了另一个 guest。
     * 最后一条尤其重要——抓错应用的截图比抓失败更难发现。
     */
    private static boolean screenshotActivityReady(Activity activity, String pkg) {
        if (activity == null || activity.isFinishing()
                || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())
                || activity.getWindow() == null) return false;
        View root = activity.getWindow().getDecorView();
        return activity == ZhiSandbox.resumedActivity()
                && pkg.equals(ZhiSandbox.resumedPackage())
                && root.isAttachedToWindow()
                && root.getWidth() > 0
                && root.getHeight() > 0;
    }

    /**
     * 抢占本次截图请求的执行权。
     *
     * <p>{@code createNewFile()} 在同一文件系统上是原子的，因此它天然就是一个
     * 「只有一个进程能赢」的锁。用它而不是在内存里记个标志位：
     * 广播会被多个 guest 进程收到，进程之间没有共享内存。
     */
    private static boolean claimScreenshot(Context context, String id, long deadline) {
        if (id == null || id.isEmpty() || SystemClock.uptimeMillis() >= deadline) return false;
        try {
            File claim = new File(resultDir(context), id + ".claim");
            boolean won = claim.createNewFile();
            if (won) claim.setLastModified(System.currentTimeMillis());
            return won;
        } catch (Throwable e) {
            SandboxConsole.event("截图桥 claim 失败: " + id + " / " + e);
            JSONObject out = error(e);
            CAPTURE_IO.execute(() -> writeResult(context, id, out));
            return false;
        }
    }

    // -------------------------------------------------------------- 抓帧会话

    /**
     * 一次截图的完整生命周期。
     *
     * <p>它存在的唯一理由是「终态唯一」：PixelCopy 是异步回调、
     * fallback 在主线程、编码在线程池、超时在主线程 Handler——
     * 四条路径都可能先到。{@code completed} 的 CAS 保证只有第一条能写结果，
     * 其余全部退化成「回收位图然后什么都不做」。
     */
    private static final class CaptureSession {
        private final Activity activity;
        private final Context context;
        private final String id;
        private final String pkg;
        private final long deadline;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final AtomicBoolean completed = new AtomicBoolean();
        private final AtomicBoolean overlayRestored = new AtomicBoolean();
        private final Runnable timeout = () -> fail(new IllegalStateException("截图内部超时；Guest 窗口可能已暂停或失效"), "timeout");

        private View root;
        private View overlay;
        private int oldOverlayVisibility = View.VISIBLE;
        private HandlerThread pixelThread;

        CaptureSession(Activity activity, Context context, String id, String pkg, long deadline) {
            this.activity = activity;
            this.context = context;
            this.id = id;
            this.pkg = pkg;
            this.deadline = deadline;
        }

        void start() {
            try {
                if (!activityUsable()) throw new IllegalStateException("Guest Activity 已失效或不在前台");
                root = activity.getWindow().getDecorView();
                int width = root.getWidth();
                int height = root.getHeight();
                if (width <= 0 || height <= 0) throw new IllegalStateException("窗口尚未完成布局");

                long remaining = deadline - SystemClock.uptimeMillis();
                if (remaining <= MIN_SCREENSHOT_REMAINING_MS) throw new IllegalStateException("截图请求已过期");
                // 内部超时比外部截止稍早触发，这样超时原因是我们自己给出的那句文案，
                // 而不是让请求方看到一个笼统的「未响应」。
                main.postDelayed(timeout, Math.max(100L, remaining - 200L));

                overlay = root.findViewWithTag(SandboxOverlay.OVERLAY_TAG);
                oldOverlayVisibility = overlay == null ? View.VISIBLE : overlay.getVisibility();
                if (overlay != null) overlay.setVisibility(View.INVISIBLE);

                // postOnAnimation 而不是立刻抓：让本帧的浮层隐藏先生效，
                // 否则抓到的画面里还留着我们自己的浮层。
                root.postOnAnimation(this::captureFrame);
            } catch (Throwable e) {
                fail(e, "start");
            }
        }

        /**
         * 首选 PixelCopy：它拿的是合成器输出，因此能拍到 SurfaceView / GL / Vulkan / 视频层，
         * 而这些层在 {@code View.draw()} 路径上是空白的。
         */
        private void captureFrame() {
            if (completed.get()) return;
            try {
                if (!activityUsable() || root == null || !root.isAttachedToWindow()) {
                    throw new IllegalStateException("截图前 Guest 窗口已离开前台");
                }
                int sourceW = root.getWidth();
                int sourceH = root.getHeight();
                int[] size = captureSize(sourceW, sourceH);

                if (Build.VERSION.SDK_INT < 26) {
                    captureFallback("api<26", sourceW, sourceH, size[0], size[1]);
                    return;
                }

                Bitmap bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
                pixelThread = new HandlerThread("iq-sandbox-pixelcopy");
                pixelThread.start();
                try {
                    PixelCopy.request(activity.getWindow(), bitmap, result -> {
                        stopPixelThread();
                        if (completed.get()) {
                            recycle(bitmap);
                            return;
                        }
                        if (expired()) {
                            recycle(bitmap);
                            fail(new IllegalStateException("PixelCopy 在截止时间后返回"), "expired-pixelcopy");
                            return;
                        }
                        if (result == PixelCopy.SUCCESS) {
                            restoreOverlay();
                            encode(bitmap, sourceW, sourceH, size[0], size[1], "pixelcopy", null);
                        } else {
                            recycle(bitmap);
                            main.post(() -> captureFallback("PixelCopy error " + result, sourceW, sourceH, size[0], size[1]));
                        }
                    }, new Handler(pixelThread.getLooper()));
                } catch (Throwable e) {
                    recycle(bitmap);
                    stopPixelThread();
                    captureFallback(e.getClass().getSimpleName() + ": " + e.getMessage(), sourceW, sourceH, size[0], size[1]);
                }
            } catch (Throwable e) {
                fail(e, "capture");
            }
        }

        /** 退路：把视图树画进位图。拍不到硬件层，但至少能拿到界面骨架。 */
        private void captureFallback(String reason, int sourceW, int sourceH, int outW, int outH) {
            if (completed.get()) return;
            if (Looper.myLooper() != Looper.getMainLooper()) {
                main.post(() -> captureFallback(reason, sourceW, sourceH, outW, outH));
                return;
            }
            Bitmap bitmap = null;
            boolean handedOff = false;
            try {
                if (expired()) throw new IllegalStateException("fallback 已超过截图截止时间");
                if (!activityUsable() || root == null || !root.isAttachedToWindow()) {
                    throw new IllegalStateException("fallback 时 Guest 窗口已失效");
                }
                bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.scale(outW / (float) sourceW, outH / (float) sourceH);
                root.draw(canvas);
                restoreOverlay();
                SandboxConsole.event("截图桥 fallback: " + id + " / " + reason);
                encode(bitmap, sourceW, sourceH, outW, outH, "view_draw_fallback", reason);
                handedOff = true;
            } catch (Throwable e) {
                fail(e, "fallback");
            } finally {
                // encode 成功时位图的所有权已经交出去（它负责回收），这里只兜住失败路径。
                if (!handedOff) recycle(bitmap);
            }
        }

        /** 落盘与结果写入都放到后台：PNG 压缩是几十毫秒级的同步开销，不能占主线程。 */
        private void encode(Bitmap bitmap, int sourceW, int sourceH, int outW, int outH, String method, String reason) {
            CAPTURE_IO.execute(() -> {
                if (completed.get()) {
                    recycle(bitmap);
                    return;
                }
                if (expired()) {
                    recycle(bitmap);
                    fail(new IllegalStateException("截图编码开始前已过期"), "expired-encode");
                    return;
                }
                File file = null;
                try {
                    file = saveBitmap(context, id, bitmap);
                    if (expired()) {
                        file.delete();
                        fail(new IllegalStateException("截图编码完成时已过期"), "expired-encode");
                        return;
                    }
                    JSONObject out = new JSONObject()
                            .put("ok", true)
                            .put("package", pkg)
                            .put("activity", activity.getClass().getName())
                            .put("pid", Process.myPid())
                            .put("path", file.getAbsolutePath())
                            .put("width", outW)
                            .put("height", outH)
                            .put("source_width", sourceW)
                            .put("source_height", sourceH)
                            .put("capture_method", method);
                    if (reason != null) out.put("fallback_reason", reason);
                    // 写结果失败（多半是请求方已经超时放弃）时把文件删掉，
                    // 否则会在私有目录里留下一张没人引用、只能等过期清理的 PNG。
                    if (!finish(out, "success") && file.isFile()) file.delete();
                } catch (Throwable e) {
                    if (file != null) file.delete();
                    fail(e, "encode");
                }
            });
        }

        private boolean activityUsable() {
            return screenshotActivityReady(activity, pkg);
        }

        private boolean expired() {
            return SystemClock.uptimeMillis() >= deadline;
        }

        /**
         * 把浮层恢复成原样，且只恢复一次。
         *
         * <p>「只一次」很重要：抓帧失败路径与成功路径都会调它，
         * 而在恢复之后用户可能已经手动改过浮层可见性——
         * 第二次恢复会把用户的选择覆盖掉。
         */
        private void restoreOverlay() {
            if (!overlayRestored.compareAndSet(false, true)) return;
            Runnable restore = () -> {
                try {
                    if (overlay != null) overlay.setVisibility(oldOverlayVisibility);
                } catch (Throwable e) {
                    SandboxConsole.event("截图桥恢复浮层失败: " + id + " / " + e);
                }
            };
            if (Looper.myLooper() == Looper.getMainLooper()) restore.run();
            else main.post(restore);
        }

        private void stopPixelThread() {
            HandlerThread thread = pixelThread;
            pixelThread = null;
            if (thread != null) thread.quitSafely();
        }

        private void fail(Throwable error, String stage) {
            finish(SandboxGuestHost.error(error), stage);
        }

        /** 唯一的终态出口。返回 false 表示已经有别的路径先到达终态。 */
        private boolean finish(JSONObject out, String stage) {
            if (!completed.compareAndSet(false, true)) return false;
            main.removeCallbacks(timeout);
            stopPixelThread();
            restoreOverlay();
            CAPTURE_IO.execute(() -> {
                writeResult(context, id, out);
                ACTIVE_CAPTURE.compareAndSet(id, null);
                SandboxConsole.event("截图桥 " + stage + ": " + id + " / " + out.optString("error", "ok"));
            });
            return true;
        }
    }

    /**
     * 按像素上限等比缩小输出尺寸。
     *
     * <p>PixelCopy 与 fallback 共用同一个尺寸计算，否则两条路径的
     * {@code width/height} 与实际像素会不一致。
     */
    private static int[] captureSize(int width, int height) {
        if (width <= 0 || height <= 0) return new int[]{1, 1};
        long pixels = (long) width * (long) height;
        if (pixels <= MAX_CAPTURE_PIXELS) return new int[]{width, height};
        double scale = Math.sqrt(MAX_CAPTURE_PIXELS / (double) pixels);
        return new int[]{Math.max(1, (int) Math.floor(width * scale)), Math.max(1, (int) Math.floor(height * scale))};
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            try {
                bitmap.recycle();
            } catch (Throwable ignored) {
                // 已被别处回收；位图回收是幂等的，忽略即可。
            }
        }
    }

    // -------------------------------------------------------------- 文件归档

    /**
     * PNG 落盘：写 .tmp → fsync → 校验体积 → rename 就位。
     *
     * <p>fsync 不能省。请求方是<b>另一个进程</b>在读这个文件，
     * 不做 fsync 的话 rename 之后对方可能读到长度还是 0 的文件——
     * 那会被上层当成「截图损坏」，而实际上只是页缓存还没落盘。
     */
    private static File saveBitmap(Context context, String id, Bitmap bitmap) throws Exception {
        File dir = screenshotDir(context);
        pruneScreenshots(dir);
        File tmp = new File(dir, id + ".png.tmp");
        File dst = new File(dir, id + ".png");
        if (tmp.exists()) tmp.delete();
        try {
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw new IllegalStateException("PNG 编码失败");
                }
                out.flush();
                out.getFD().sync();
            }
        } catch (Throwable e) {
            tmp.delete();
            if (e instanceof Exception) throw (Exception) e;
            throw (Error) e;
        } finally {
            recycle(bitmap);
        }
        if (tmp.length() <= 0 || tmp.length() > MAX_SCREENSHOT_BYTES) {
            long size = tmp.length();
            tmp.delete();
            throw new IllegalStateException("截图 PNG 大小异常: " + size + " bytes");
        }
        if (dst.exists() && !dst.delete()) {
            tmp.delete();
            throw new IllegalStateException("旧截图文件清理失败: " + dst);
        }
        if (!tmp.renameTo(dst)) {
            tmp.delete();
            throw new IllegalStateException("截图文件原子发布失败: " + dst);
        }
        return dst;
    }

    private static File screenshotDir(Context context) {
        File dir = new File(hostContext(context).getFilesDir(), "sandbox/screenshots");
        ensureDirectory(dir);
        return dir;
    }

    private static File resultDir(Context context) {
        File dir = new File(hostContext(context).getFilesDir(), "sandbox/agent-results");
        ensureDirectory(dir);
        return dir;
    }

    private static Context hostContext(Context fallback) {
        Context host = ZhiSandbox.hostContext();
        return host == null ? fallback : host;
    }

    private static void ensureDirectory(File dir) {
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IllegalStateException("无法创建目录: " + dir);
        }
    }

    /** 截图清理：先按时间淘汰，再按张数淘汰到上限以下。 */
    private static void pruneScreenshots(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - SCREENSHOT_MAX_AGE_MS;
        int retained = 0;
        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".png.tmp")) {
                // 半截的临时文件只要过期就删；它没有任何保留价值。
                if (file.lastModified() < cutoff) file.delete();
                continue;
            }
            if (!name.endsWith(".png")) continue;
            if (file.lastModified() < cutoff) file.delete();
            else retained++;
        }
        while (retained >= MAX_RETAINED_SCREENSHOTS) {
            File[] current = dir.listFiles();
            if (current == null) break;
            File oldest = null;
            for (File file : current) {
                if (file.getName().endsWith(".png") && (oldest == null || file.lastModified() < oldest.lastModified())) oldest = file;
            }
            if (oldest == null || !oldest.delete()) break;
            retained--;
        }
    }

    /** 清掉过期的请求中间产物：结果文件、claim 文件、写了一半的临时文件。 */
    private static void pruneRequestArtifacts(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - REQUEST_ARTIFACT_MAX_AGE_MS;
        for (File file : files) {
            String name = file.getName();
            boolean disposable = name.endsWith(".claim") || name.endsWith(".tmp") || name.endsWith(".json");
            if (disposable && file.lastModified() < cutoff) file.delete();
        }
    }

    private static String readFile(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 发布结果文件，同样走「写 .tmp → fsync → rename」。
     *
     * <p>这里没有把失败往上抛，而是记一条事件：本方法会在多个异步路径上被调用
     * （有的在 Handler 回调里、有的在线程池里），抛出去也没人能接。
     * 失败的表现会是请求方超时，而事件日志里能查到真实原因。
     */
    private static void writeResult(Context context, String id, JSONObject out) {
        if (id == null || id.isEmpty()) return;
        File tmp = null;
        try {
            File dir = resultDir(context);
            tmp = new File(dir, id + ".tmp");
            File dst = new File(dir, id + ".json");
            byte[] data = out.toString().getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream stream = new FileOutputStream(tmp)) {
                stream.write(data);
                stream.flush();
                stream.getFD().sync();
            }
            if (dst.exists() && !dst.delete()) throw new IllegalStateException("旧结果清理失败: " + dst);
            if (!tmp.renameTo(dst)) throw new IllegalStateException("结果原子发布失败: " + dst);
        } catch (Throwable e) {
            if (tmp != null) tmp.delete();
            SandboxConsole.event("桥接结果写入失败: " + id + " / " + e);
        }
    }

    // ------------------------------------------------------------------ 杂项

    private static JSONObject error(Throwable e) {
        JSONObject out = new JSONObject();
        try {
            out.put("ok", false).put("error", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        } catch (Exception ignored) {
            // put 几乎不会失败；真的失败时也只剩「返回一个空对象」这一条路。
        }
        return out;
    }

    /** 当前进程所属的虚拟包名；主进程里为空串，调用方据此判断「我不是 guest」。 */
    private static String safeVirtualPackage() {
        try {
            String pkg = BActivityThread.getAppPackageName();
            return pkg == null ? "" : pkg;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
