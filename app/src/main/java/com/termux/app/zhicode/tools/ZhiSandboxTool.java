package com.termux.app.zhicode.tools;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Base64;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.zhizhu.zhicode.sandbox.SandboxConsole;
import com.zhizhu.zhicode.sandbox.SandboxGuestHost;
import com.zhizhu.zhicode.sandbox.SandboxRpc;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;

/**
 * Agent 用来操作ZhiCode 沙箱的入口。
 *
 * <h3>这个工具只碰沙箱，不碰真机</h3>
 * install 的落点永远是沙箱；即便调用者给了一个真机 APK 路径，
 * 这里也只是把「文件」交给沙箱控制器去装进虚拟运行时。
 * 真机安装是另一条由 AndroidIntent / Root 负责的路径，两者刻意不共用入口——
 * 一旦共用，一个参数写错就会把包装到用户手机上。
 *
 * <h3>两条不同的通道</h3>
 * <ul>
 *   <li><b>管理类</b>（status/list/install/launch/stop/clear_data/uninstall/debug_snapshot）
 *       走 {@link SandboxRpc}：控制器进程直接回答，不需要 guest 存在。</li>
 *   <li><b>界面类</b>（dump_ui/screenshot/click_node/tap/swipe/input_text/back…）
 *       走 {@link SandboxGuestHost}：请求广播给持有前台 guest Activity 的那个进程，
 *       由它自己动手。工具进程无法直接操控另一个进程的视图树。</li>
 * </ul>
 */
public final class ZhiSandboxTool implements ZhiTool {

    /** 截图体积上限，与沙箱宿主侧的限制保持一致。 */
    private static final int MAX_SCREENSHOT_BYTES = 5 * 1024 * 1024;

    /** 截图私有目录，相对宿主 files 目录。 */
    private static final String SCREENSHOT_DIR = "sandbox/screenshots";

    /**
     * 界面动作的超时不同源。
     *
     * <p>截图要等前台 Activity 出现、等一次布局、等 PixelCopy 回调，
     * 秒级以内是常态；而点击/输入这类动作只要 Activity 在前台就应当立刻返回。
     * 给它们同一个超时，要么截图经常假失败，要么点击失败时用户要白等十几秒。
     */
    private static final int SCREENSHOT_TIMEOUT_MS = 12000;
    private static final int UI_ACTION_TIMEOUT_MS = 4500;

    private static final String ACTION_SCREENSHOT = "screenshot";

    private final Context context;

    public ZhiSandboxTool(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public String name() {
        return "Sandbox";
    }

    @Override
    public String description() {
        return "Control ZhiCode 沙箱 apps/UI; install targets sandbox only, never the phone.";
    }

    @Override
    public PermissionKind permissionKind() {
        return PermissionKind.SYSTEM;
    }

    @Override
    public JSONObject inputSchema() {
        try {
            JSONObject properties = new JSONObject();
            properties.put("action", ToolSchemas.string("status, list, install, launch, stop, clear_data, uninstall, debug_snapshot, clear_debug, dump_ui, screenshot, click_node, long_click_node, set_text, tap, swipe, input_text, back"));
            properties.put("path", ToolSchemas.string("Absolute .apk path for install."));
            properties.put("package", ToolSchemas.string("Sandbox package name. Required for launch/stop/clear_data/uninstall and recommended for UI control."));
            properties.put("node", ToolSchemas.string("Node path returned by dump_ui, for example 0/1/0."));
            properties.put("text", ToolSchemas.string("Text for set_text or input_text."));
            properties.put("x", ToolSchemas.integer("tap X coordinate in screenshot/window pixels.", 0));
            properties.put("y", ToolSchemas.integer("tap Y coordinate in screenshot/window pixels.", 0));
            properties.put("x1", ToolSchemas.integer("swipe start X.", 0));
            properties.put("y1", ToolSchemas.integer("swipe start Y.", 0));
            properties.put("x2", ToolSchemas.integer("swipe end X.", 0));
            properties.put("y2", ToolSchemas.integer("swipe end Y.", 0));
            properties.put("duration_ms", ToolSchemas.integer("swipe duration in milliseconds.", 80));
            return ToolSchemas.object(properties, "action");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public ToolExecutionResult execute(SessionConfig config, JSONObject in) throws Exception {
        String action = in.optString("action", "").trim().toLowerCase();
        try {
            switch (action) {
                case "status":
                    return withStatus(host("status", new JSONObject()), "ZhiCode 沙箱 状态");
                case "list":
                    return ToolExecutionResult.ok(listApps());
                case "install":
                    return install(in.optString("path", ""));
                case "launch":
                    return launch(in);
                case "stop":
                    return withStatus(host("stop", packagePayload(in)), "已停止沙箱应用: " + requirePackage(in));
                case "clear_data":
                    return withStatus(host("clear_data", packagePayload(in)), "已清除沙箱数据: " + requirePackage(in));
                case "uninstall":
                    return withStatus(host("uninstall", packagePayload(in)), "已从沙箱卸载: " + requirePackage(in));
                case "debug_snapshot":
                    return ToolExecutionResult.ok(SandboxConsole.snapshot(context));
                case "clear_debug":
                    SandboxConsole.clear();
                    return ToolExecutionResult.ok("沙箱调试日志已清空");
                case "dump_ui":
                case "screenshot":
                case "back":
                case "click_node":
                case "long_click_node":
                case "set_text":
                case "tap":
                case "swipe":
                case "input_text":
                    return control(action, in);
                default:
                    return ToolExecutionResult.error("未知 Sandbox action: " + action);
            }
        } catch (Throwable e) {
            SandboxConsole.event("Agent Sandbox 失败: " + action + " / " + e);
            return ToolExecutionResult.error("Sandbox " + action + " 失败: "
                    + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }
    }

    /**
     * 启动一个已安装的沙箱应用。
     *
     * <p>判定为成功需要两个字段都为真：{@code ok} 表示控制器处理了请求，
     * {@code success} 表示它真的找到了可启动的 Activity。
     * 只看前者会把「包存在但没有入口 Activity」报成启动成功。
     */
    private ToolExecutionResult launch(JSONObject in) throws Exception {
        String pkg = requirePackage(in);
        JSONObject response = host("launch", new JSONObject().put("package", pkg));
        if (response.optBoolean("ok") && response.optBoolean("success")) {
            return ToolExecutionResult.ok("已启动沙箱应用: " + pkg);
        }
        String fallback = "沙箱没有找到可启动 Activity: " + pkg;
        return ToolExecutionResult.error(response.optString("error", response.optString("message", fallback)));
    }

    /**
     * 把真机上的一个 APK 装进沙箱。
     *
     * <p>先在本机解析这个 APK 的包信息，是为了在把文件交给沙箱之前就挡掉两类输入：
     * 不是有效 APK 的文件（否则失败信息会来自虚拟运行时的深处，难以解释），
     * 以及蜘蛛自身（把宿主装进它自己的虚拟运行时只会得到一份互相递归的垃圾状态）。
     */
    private ToolExecutionResult install(String path) throws Exception {
        if (path == null || path.trim().isEmpty()) return ToolExecutionResult.error("install 需要 path");
        File apk = new File(path);
        if (!apk.isFile()) return ToolExecutionResult.error("找不到 APK：" + path);

        PackageInfo info = context.getPackageManager()
                .getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
        if (info == null || info.packageName == null) return ToolExecutionResult.error("不是有效普通 APK: " + path);
        if (context.getPackageName().equals(info.packageName)) {
            return ToolExecutionResult.error("不能把 ZhiCode 自身安装进 ZhiCode 沙箱");
        }

        JSONObject response = host("install", new JSONObject().put("path", apk.getAbsolutePath()));
        if (!response.optBoolean("ok")) {
            return ToolExecutionResult.error(response.optString("error", response.toString()));
        }
        if (response.optBoolean("success")) {
            return ToolExecutionResult.ok("已安装到 ZhiCode 沙箱: " + response.optString("package", info.packageName));
        }
        return ToolExecutionResult.error("沙箱安装失败: " + response.optString("message", response.toString()));
    }

    private String listApps() throws Exception {
        JSONObject response = host("list", new JSONObject());
        if (!response.optBoolean("ok")) {
            throw new IllegalStateException(response.optString("error", response.toString()));
        }
        JSONArray apps = response.optJSONArray("apps");
        int count = apps == null ? 0 : apps.length();
        StringBuilder out = new StringBuilder();
        out.append("ZhiCode 沙箱 已安装 ").append(count).append(" 个应用\n");
        for (int i = 0; i < count; i++) {
            out.append("- ").append(apps.optJSONObject(i).optString("package", "")).append('\n');
        }
        return out.toString();
    }

    /**
     * 转发一次界面动作。
     *
     * <p>payload 只搬运调用者<b>真正给出</b>的字段。给未提供的坐标塞 0 是不行的：
     * 0,0 是一个合法坐标，会把「没给坐标」变成「点左上角」。
     */
    private ToolExecutionResult control(String action, JSONObject in) throws Exception {
        JSONObject payload = new JSONObject();
        if (in.has("node")) payload.put("node", in.optString("node", ""));
        if (in.has("text")) payload.put("text", in.optString("text", ""));
        for (String key : new String[]{"x", "y", "x1", "y1", "x2", "y2", "duration_ms"}) {
            if (in.has(key)) payload.put(key, in.optInt(key));
        }

        int timeout = ACTION_SCREENSHOT.equals(action) ? SCREENSHOT_TIMEOUT_MS : UI_ACTION_TIMEOUT_MS;
        JSONObject response = SandboxGuestHost.request(context, action, in.optString("package", ""), payload, timeout);
        if (!response.optBoolean("ok", false)) {
            return ToolExecutionResult.error(response.optString("error", response.toString()));
        }
        String display = response.toString(2);
        return ACTION_SCREENSHOT.equals(action) ? screenshotResult(response, display) : ToolExecutionResult.ok(display);
    }

    /**
     * 把截到的 PNG 变成模型能看的内容。
     *
     * <p>路径来自 guest 进程的返回值，因此这里<b>必须重新校验</b>而不是信任它：
     * 只接受沙箱私有截图目录内的 .png，并且做一次规范化（getCanonicalFile）
     * 以挡住 {@code ../} 这类穿越。这是唯一一个会把文件内容读进模型的地方。
     *
     * <p>图像桥接失败时，返回的错误里仍然带上原本的 JSON 说明——
     * 截图文件本身可能已经在磁盘上，用户凭那段 JSON 还能自己去看。
     */
    private ToolExecutionResult screenshotResult(JSONObject result, String display) {
        try {
            File root = new File(context.getFilesDir(), SCREENSHOT_DIR).getCanonicalFile();
            File image = new File(result.optString("path", "")).getCanonicalFile();
            if (!image.getAbsolutePath().startsWith(root.getAbsolutePath() + File.separator)
                    || !image.getName().endsWith(".png")) {
                throw new IllegalArgumentException("截图路径不在沙箱私有目录");
            }
            if (!image.isFile() || image.length() <= 0) throw new IllegalStateException("截图文件不存在或为空");

            byte[] bytes = readScreenshot(image);
            String data = Base64.encodeToString(bytes, Base64.NO_WRAP);
            JSONObject source = new JSONObject()
                    .put("type", "base64")
                    .put("media_type", "image/png")
                    .put("data", data);
            JSONArray additional = new JSONArray().put(new JSONObject()
                    .put("type", "image")
                    .put("source", source)
                    .put("name", image.getName()));
            return ToolExecutionResult.okWithAdditionalContent(display, additional);
        } catch (Throwable e) {
            SandboxConsole.event("截图像素桥接失败: " + e);
            return ToolExecutionResult.error("截图已生成，但图像桥接失败: " + e.getMessage() + "\n" + display);
        }
    }

    /**
     * 读一张截图，并确认它没有被截断或篡改。
     *
     * <p>两次校验：文件长度必须在 (0, 5MiB] 内，且开头必须是 PNG magic。
     * 只有长度校验不够——写了一半的 PNG 长度也可能是正的，
     * 交给模型只会得到一句「无法识别的图像」。
     *
     * <p>读完后再读一次（{@code in.read() != -1}）是为了确认文件在读取期间没有被追加：
     * 那意味着我们拿到的字节数与文件当前状态不符，说明写入方还没结束。
     */
    private static byte[] readScreenshot(File image) throws Exception {
        long length = image.length();
        if (length <= 0 || length > MAX_SCREENSHOT_BYTES) {
            throw new IllegalStateException("截图大小超出 5 MiB 限制: " + length);
        }
        byte[] bytes = new byte[(int) length];
        int offset = 0;
        try (FileInputStream in = new FileInputStream(image)) {
            while (offset < bytes.length) {
                int read = in.read(bytes, offset, bytes.length - offset);
                if (read < 0) break;
                offset += read;
            }
            if (offset != bytes.length || in.read() != -1) {
                throw new IllegalStateException("截图读取期间发生变化");
            }
        }
        boolean png = bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
        if (!png) throw new IllegalStateException("截图不是有效 PNG");
        return bytes;
    }

    private static String requirePackage(JSONObject in) {
        String pkg = in.optString("package", "").trim();
        if (pkg.isEmpty()) throw new IllegalArgumentException("需要 package");
        return pkg;
    }

    private static JSONObject packagePayload(JSONObject in) throws Exception {
        return new JSONObject().put("package", requirePackage(in));
    }

    private JSONObject host(String action, JSONObject payload) {
        return SandboxRpc.call(context, action, payload);
    }

    /** 管理类动作的统一收尾：{@code ok} 为真才带成功文案，否则透出控制器给的原因。 */
    private static ToolExecutionResult withStatus(JSONObject response, String success) {
        return response.optBoolean("ok", false)
                ? ToolExecutionResult.ok(success + "\n" + response.toString())
                : ToolExecutionResult.error(response.optString("error", response.toString()));
    }
}
