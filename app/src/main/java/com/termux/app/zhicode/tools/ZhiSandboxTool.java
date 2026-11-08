package com.termux.app.zhicode.tools;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Base64;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.zhizhu.zhicode.sandbox.SandboxAgentBridge;
import com.zhizhu.zhicode.sandbox.SandboxHostClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;

/**
 * 沙箱（BlackBox 虚拟 Android 运行时）的 Agent 控制面。
 *
 * <h3>这个工具为什么是「两个后端」</h3>
 * 动作分成两类，走的通道完全不同：
 *
 * <table>
 *   <tr><th>类别</th><th>通道</th><th>例子</th></tr>
 *   <tr>
 *     <td>引擎级</td>
 *     <td>{@link SandboxHostClient} —— 同 UID 的 ContentProvider
 *         （`:iqsandbox` 进程）</td>
 *     <td>install / launch / stop / list</td>
 *   </tr>
 *   <tr>
 *     <td>界面级</td>
 *     <td>{@link SandboxAgentBridge} —— 定向广播 + 结果文件
 *         （**guest 自己的进程**）</td>
 *     <td>screenshot / dump_ui / tap</td>
 *   </tr>
 * </table>
 *
 * 界面动作必须由**持有那个 guest Activity 的进程**执行：`View` 树不在
 * `:iqsandbox` 里，更不在主进程里。这就是为什么不能统一成一个通道。
 *
 * <h3>给识图模型的两件事</h3>
 * 1. {@code screenshot} 的 {@code annotate} —— 在截图上给每个可交互节点叠编号框，
 *    模型可以说"点 3 号"而不用猜像素坐标；
 * 2. {@code inspect} —— 一次拿到截图 + UI 树 + 前台 Activity + 进程列表。
 *    分开调三次会让模型在第一张图之后就开始幻觉（它记不住三份 JSON 的对应关系）。
 *
 * <p>权限分类是 {@link PermissionKind#SYSTEM}：它会往沙箱里装包、启动别人的代码。
 */
final class ZhiSandboxTool implements ZhiTool {

    /** 截图 PNG 上限。超过就当异常 —— 一张手机截图不可能有 5 MiB。 */
    private static final int MAX_SCREENSHOT_BYTES = 5 * 1024 * 1024;

    private final Context context;

    ZhiSandboxTool(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public String name() {
        return "Sandbox";
    }

    @Override
    public String description() {
        return "Control the IQ Sandbox (embedded BlackBox virtual Android runtime): install/launch/stop guest APKs, "
                + "then drive and observe their UI. 'inspect' returns a screenshot + UI tree + foreground activity "
                + "in one shot (prefer it for debugging); 'screenshot annotate=true' numbers every tappable node so "
                + "you can click by index; 'find_node' searches nodes by text. install targets the sandbox only, "
                + "never the real phone.";
    }

    @Override
    public PermissionKind permissionKind() {
        return PermissionKind.SYSTEM;
    }

    @Override
    public JSONObject inputSchema() {
        JSONObject p = new JSONObject();
        put(p, "action", ToolSchemas.enumString(
                "Engine: status / list / install / launch / stop / clear_data / uninstall / debug_snapshot / clear_debug. "
                        + "UI: inspect / screenshot / dump_ui / find_node / click_node / long_click_node / set_text / "
                        + "tap / swipe / input_text / back / finish.",
                "status", "list", "install", "launch", "stop", "clear_data", "uninstall",
                "debug_snapshot", "clear_debug",
                "inspect", "screenshot", "dump_ui", "find_node",
                "click_node", "long_click_node", "set_text", "tap", "swipe", "input_text", "back", "finish"));
        put(p, "path", ToolSchemas.string("Absolute .apk path for install."));
        put(p, "package", ToolSchemas.string(
                "Sandbox package name. Required for launch/stop/clear_data/uninstall; recommended for UI actions "
                        + "(without it the action goes to whichever guest is in front)."));
        put(p, "node", ToolSchemas.string("Node path from inspect/dump_ui/find_node, e.g. 0/1/0."));
        put(p, "text", ToolSchemas.string("Text for set_text or input_text; also the search keyword for find_node."));
        put(p, "match", ToolSchemas.string(
                "find_node: what to match against — text (default), content_desc, or class."));
        put(p, "annotate", ToolSchemas.bool(
                "screenshot/inspect: draw a numbered box over every clickable node so you can refer to nodes by index."));
        put(p, "with_ui", ToolSchemas.bool(
                "screenshot: also return the condensed UI tree next to the image (default false; inspect always does)."));
        put(p, "full_tree", ToolSchemas.bool(
                "inspect/dump_ui: return every node instead of only the ones with text/desc or clickable."));
        put(p, "x", ToolSchemas.integer("tap X coordinate in screenshot/window pixels.", 0));
        put(p, "y", ToolSchemas.integer("tap Y coordinate in screenshot/window pixels.", 0));
        put(p, "x1", ToolSchemas.integer("swipe start X.", 0));
        put(p, "y1", ToolSchemas.integer("swipe start Y.", 0));
        put(p, "x2", ToolSchemas.integer("swipe end X.", 0));
        put(p, "y2", ToolSchemas.integer("swipe end Y.", 0));
        put(p, "duration_ms", ToolSchemas.integer("swipe duration in milliseconds.", 80));
        return ToolSchemas.object(p, "action");
    }

    @Override
    public ToolExecutionResult execute(SessionConfig config, JSONObject in) throws Exception {
        String action = in.optString("action", "").trim().toLowerCase();
        if (action.isEmpty()) return ToolExecutionResult.error("Sandbox 需要 action");

        try {
            switch (action) {
                case "status":
                    return describe(host("status", new JSONObject()), "IQ Sandbox 状态");
                case "list":
                    return ToolExecutionResult.ok(listApps());
                case "install":
                    return install(in.optString("path", ""));
                case "launch": {
                    String pkg = requirePackage(in);
                    JSONObject r = host("launch", new JSONObject().put("package", pkg));
                    if (!r.optBoolean("ok")) return ToolExecutionResult.error(errorText(r));
                    return r.optBoolean("success")
                            ? ToolExecutionResult.ok("已启动沙箱应用: " + pkg)
                            : ToolExecutionResult.error("沙箱没有找到可启动的 Activity: " + pkg);
                }
                case "stop": {
                    String pkg = requirePackage(in);
                    return describe(host("stop", new JSONObject().put("package", pkg)), "已停止沙箱应用: " + pkg);
                }
                case "clear_data": {
                    String pkg = requirePackage(in);
                    return describe(host("clear_data", new JSONObject().put("package", pkg)), "已清除沙箱数据: " + pkg);
                }
                case "uninstall": {
                    String pkg = requirePackage(in);
                    return describe(host("uninstall", new JSONObject().put("package", pkg)), "已从沙箱卸载: " + pkg);
                }
                case "debug_snapshot":
                    return ToolExecutionResult.ok(com.zhizhu.zhicode.sandbox.SandboxDebugLog.snapshot(context));
                case "clear_debug":
                    com.zhizhu.zhicode.sandbox.SandboxDebugLog.clear();
                    return ToolExecutionResult.ok("沙箱调试日志已清空");
                default:
                    return control(action, in);
            }
        } catch (Throwable e) {
            return ToolExecutionResult.error("Sandbox " + action + " 失败: "
                    + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }
    }

    // ─────────────────────────── 引擎级（ContentProvider） ───────────────────────────

    private JSONObject host(String action, JSONObject payload) {
        return SandboxHostClient.call(context, action, payload);
    }

    private String listApps() throws Exception {
        JSONObject r = host("list", new JSONObject());
        if (!r.optBoolean("ok")) throw new IllegalStateException(errorText(r));
        JSONArray apps = r.optJSONArray("apps");
        int n = apps == null ? 0 : apps.length();
        StringBuilder b = new StringBuilder();
        b.append("沙箱已安装 ").append(n).append(" 个应用\n");
        for (int i = 0; i < n; i++) {
            b.append("- ").append(apps.optJSONObject(i).optString("package", "")).append('\n');
        }
        return b.toString();
    }

    private ToolExecutionResult install(String path) throws Exception {
        if (path == null || path.trim().isEmpty()) return ToolExecutionResult.error("install 需要 path");
        File apk = new File(path.trim());
        if (!apk.isFile()) return ToolExecutionResult.error("APK 不存在: " + path);

        PackageInfo pi = context.getPackageManager()
                .getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
        if (pi == null || pi.packageName == null) return ToolExecutionResult.error("不是有效普通 APK: " + path);

        /*
         * 拒绝把宿主自己装进沙箱。
         *
         * 这不只是"没意义"：ZhiCode 自己带 Termux 用户空间与 libtermux.so，
         * 在虚拟运行时里跑会有两条互不相让的 PTY 初始化路径。
         */
        if (context.getPackageName().equals(pi.packageName)) {
            return ToolExecutionResult.error("不能把 ZhiCode 自己安装进沙箱");
        }

        JSONObject r = host("install", new JSONObject().put("path", apk.getAbsolutePath()));
        if (!r.optBoolean("ok")) return ToolExecutionResult.error(errorText(r));
        return r.optBoolean("success")
                ? ToolExecutionResult.ok("已安装到沙箱: " + r.optString("package", pi.packageName))
                : ToolExecutionResult.error("沙箱安装失败: " + r.optString("message", r.toString()));
    }

    // ─────────────────────────── 界面级（guest 广播桥） ───────────────────────────

    private ToolExecutionResult control(String action, JSONObject in) throws Exception {
        JSONObject payload = new JSONObject();
        for (String k : new String[]{"node", "text", "match"}) {
            if (in.has(k)) payload.put(k, in.optString(k, ""));
        }
        for (String k : new String[]{"x", "y", "x1", "y1", "x2", "y2", "duration_ms"}) {
            if (in.has(k)) payload.put(k, in.optInt(k));
        }
        if (in.has("annotate")) payload.put("annotate", in.optBoolean("annotate", false));
        if (in.has("with_ui")) payload.put("with_ui", in.optBoolean("with_ui", false));
        if (in.has("full_tree")) payload.put("full_tree", in.optBoolean("full_tree", false));

        boolean wantsImage = "screenshot".equals(action) || "inspect".equals(action);
        JSONObject r = SandboxAgentBridge.request(
                context, action, in.optString("package", ""), payload, wantsImage ? 12000 : 4500);

        if (!r.optBoolean("ok", false)) return ToolExecutionResult.error(errorText(r));

        String display = r.toString(2);
        return wantsImage && r.has("path") ? imageResult(r, display) : ToolExecutionResult.ok(display);
    }

    /**
     * 把 guest 侧落盘的 PNG 读回来，作为结构化附加内容交给界面与模型。
     *
     * <h3>两道检查都是必要的</h3>
     * 1. **路径必须在宿主私有目录内**。`path` 来自广播的另一端，虽然同 UID，
     *    但一个"读任意文件并 base64 发给模型"的原语不该存在 —— 越界即失败。
     * 2. **PNG 魔数**。截图的产出者是 `PixelCopy` 或 `View.draw`，
     *    两者都可能因为窗口失效产出空文件；按魔数判定比按后缀可靠。
     */
    private ToolExecutionResult imageResult(JSONObject result, String display) {
        try {
            File root = new File(context.getFilesDir(), "sandbox/screenshots").getCanonicalFile();
            File image = new File(result.optString("path", "")).getCanonicalFile();
            if (!image.getAbsolutePath().startsWith(root.getAbsolutePath() + File.separator)
                    || !image.getName().endsWith(".png")) {
                throw new IllegalArgumentException("截图路径不在沙箱私有目录");
            }
            if (!image.isFile() || image.length() <= 0) {
                throw new IllegalStateException("截图文件不存在或为空");
            }

            byte[] bytes = readScreenshot(image);
            String data = Base64.encodeToString(bytes, Base64.NO_WRAP);
            JSONObject source = new JSONObject()
                    .put("type", "base64")
                    .put("media_type", "image/png")
                    .put("data", data);
            JSONArray additional = new JSONArray()
                    .put(new JSONObject().put("type", "image").put("source", source).put("name", image.getName()));
            return ToolExecutionResult.okWithAdditionalContent(display, additional);
        } catch (Throwable e) {
            return ToolExecutionResult.error("截图已生成，但图像桥接失败: " + e.getMessage() + "\n" + display);
        }
    }

    private static byte[] readScreenshot(File image) throws Exception {
        long length = image.length();
        if (length <= 0 || length > MAX_SCREENSHOT_BYTES) {
            throw new IllegalStateException("截图大小超出 5 MiB 限制: " + length);
        }
        byte[] bytes = new byte[(int) length];
        int offset = 0;
        try (FileInputStream in = new FileInputStream(image)) {
            while (offset < bytes.length) {
                int n = in.read(bytes, offset, bytes.length - offset);
                if (n < 0) break;
                offset += n;
            }
            if (offset != bytes.length || in.read() != -1) {
                throw new IllegalStateException("截图读取期间发生变化");
            }
        }
        if (bytes.length < 8 || (bytes[0] & 0xff) != 0x89
                || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G') {
            throw new IllegalStateException("截图不是有效 PNG");
        }
        return bytes;
    }

    // ─────────────────────────── 结果与参数 ───────────────────────────

    private static ToolExecutionResult describe(JSONObject r, String success) {
        return r.optBoolean("ok", false)
                ? ToolExecutionResult.ok(success + "\n" + r.toString())
                : ToolExecutionResult.error(errorText(r));
    }

    /**
     * 失败的文案要**能指导下一步**。
     *
     * 原版只回 `error` 字段，模型看到"未响应"只能瞎猜是没启动、还是截图超时。
     * 这里把引擎带回来的 `hint` / `stage` 一起拼上（有就拼，没有不多话）。
     */
    private static String errorText(JSONObject r) {
        String error = r.optString("error", "");
        if (error.isEmpty()) error = r.toString();
        StringBuilder b = new StringBuilder(error);
        String stage = r.optString("stage", "");
        if (!stage.isEmpty()) b.append("\n阶段: ").append(stage);
        String hint = r.optString("hint", "");
        if (!hint.isEmpty()) b.append("\n建议: ").append(hint);
        return b.toString();
    }

    private static String requirePackage(JSONObject in) {
        String p = in.optString("package", "").trim();
        if (p.isEmpty()) throw new IllegalArgumentException("需要 package");
        return p;
    }

    /** {@link JSONObject#put} 的受检异常在这里不可能发生（键是字面量）。 */
    private static void put(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
