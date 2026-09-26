package com.zhizhu.zhicode.sandbox;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.app.zhicode.json.JsonItems;
import com.termux.app.zhicode.termux.TermuxShellExecutor;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 沙箱管理界面。
 *
 * <p><b>为什么它跑在主进程而不是控制器进程。</b>BlackBox 引擎在 {@code :zhisandbox} 里，
 * 但那是个会 hook ART、替换系统服务的进程 —— 把它和界面绑在一起，意味着后端一崩界面也跟着消失，
 * 用户看到的是"点了没反应"。本 Activity 留在稳定的主进程，只通过 {@link SandboxRpc}
 * 发 IPC 指令；后端挂了它就显示失败原因与启动阶段，而不是一起死掉。
 *
 * <p><b>诊断信息的两个来源。</b>
 * <ul>
 *   <li>{@link SandboxStage} 的按 pid 阶段文件（每个进程一条，互不覆盖）；
 *   <li>引擎写的 {@code sandbox/startup-stage.txt}（单文件，兼容用途）。
 * </ul>
 * 前者是定位"卡在哪一步"的主要依据，后者保留是因为引擎仍在写它。
 *
 * <p>两个开关都做了乐观更新 + 回滚：切换时先压住交互，服务端确认后再落定；
 * 失败则回到原值并提示。{@code rootSettingInFlight} / {@code floatingLogInFlight}
 * 是这两个流程的互斥位，防止连点造成状态错乱。
 */
public final class SandboxBoard extends Activity {

    private static final int PICK_APK = 4811;
    private static final int MAX_STARTUP_RETRY = 2;
    private static final String LEGACY_STAGE_FILE = "startup-stage.txt";

    private SandboxPalette palette;
    private final java.util.concurrent.ExecutorService worker =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    private LinearLayout listView;
    private TextView statusView;
    private Switch rootHideSwitch;
    private Switch floatingLogSwitch;

    private volatile int startupRetry;
    private volatile boolean destroyed;
    private boolean syncingRootSwitch;
    private boolean rootSettingInFlight;
    private boolean syncingFloatingLog;
    private boolean floatingLogInFlight;

    // ------------------------------------------------------------------ 生命周期

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        palette = SandboxPalette.resolve(this);
        applySystemBars();
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 主题可能在别处被改过；背景色变了就整棵重建，否则只刷新数据
        SandboxPalette current = SandboxPalette.resolve(this);
        boolean themeChanged = palette == null || current.background != palette.background;
        palette = current;
        if (themeChanged || listView == null) {
            applySystemBars();
            setContentView(buildContent());
        }
        reload();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        worker.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ 视图构建

    private View buildContent() {
        LinearLayout root = column();
        root.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.setBackgroundColor(palette.background);

        root.addView(buildHeader(), fixed(-1, 48));

        statusView = label("正在连接沙箱后端…", 11, palette.success);
        statusView.setPadding(0, dp(10), 0, dp(10));
        root.addView(statusView, fixed(-1, 54));

        root.addView(buildRootSetting(), fixed(-1, 48));
        root.addView(buildFloatingLogSetting(), fixed(-1, 48));
        root.addView(buildActions(), fixed(-1, 48));

        listView = column();
        ScrollView scroll = new ScrollView(this);
        scroll.addView(listView, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    private View buildHeader() {
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = textButton("←", palette.text);
        back.setOnClickListener(v -> finish());
        header.addView(back, fixed(dp(42), dp(40)));

        TextView title = label("蜘蛛沙箱", 22, palette.text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));

        TextView diagnostics = textButton("诊断", palette.accent);
        diagnostics.setOnClickListener(v -> showDiagnostics());
        header.addView(diagnostics, fixed(dp(72), dp(40)));
        return header;
    }

    private View buildRootSetting() {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label("隐藏 Root", 13, palette.text), new LinearLayout.LayoutParams(0, dp(44), 1));

        rootHideSwitch = new Switch(this);
        rootHideSwitch.setChecked(true);
        // 未连上后端前不可交互：此时改它没有意义，反而会让服务端状态与界面不一致
        rootHideSwitch.setEnabled(false);
        rootHideSwitch.setOnCheckedChangeListener((button, hidden) -> {
            if (!syncingRootSwitch) confirmRootVisibilityChange(hidden);
        });
        row.addView(rootHideSwitch, fixed(dp(64), dp(44)));
        return row;
    }

    private View buildFloatingLogSetting() {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label("日志悬浮窗", 13, palette.text), new LinearLayout.LayoutParams(0, dp(44), 1));

        floatingLogSwitch = new Switch(this);
        floatingLogSwitch.setChecked(true);
        floatingLogSwitch.setEnabled(false);
        floatingLogSwitch.setOnCheckedChangeListener((button, enabled) -> {
            if (!syncingFloatingLog) applyFloatingLog(enabled);
        });
        row.addView(floatingLogSwitch, fixed(dp(64), dp(44)));
        return row;
    }

    private View buildActions() {
        LinearLayout row = row();
        Button install = actionButton("导入 APK");
        install.setOnClickListener(v -> pickApk());
        row.addView(install, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button refresh = actionButton("刷新");
        refresh.setOnClickListener(v -> reload());
        row.addView(refresh, new LinearLayout.LayoutParams(0, dp(44), 1));
        return row;
    }

    // ------------------------------------------------------------------ 数据加载

    private void reload() {
        if (destroyed) return;
        worker.execute(() -> {
            JSONObject status = SandboxRpc.call(this, "status", new JSONObject());
            if (destroyed) return;
            if (!status.optBoolean("ok")) {
                retryOrShow(status.optString("error", "沙箱后端不可用"));
                return;
            }
            JSONObject listing = SandboxRpc.call(this, "list", new JSONObject());
            if (destroyed) return;
            if (!listing.optBoolean("ok")) {
                retryOrShow(listing.optString("error", "读取沙箱应用失败"));
                return;
            }
            startupRetry = 0;
            List<String> packages = collectPackages(listing);
            String summary = status.optString("status", "沙箱后端在线");
            boolean hideRoot = status.optBoolean("hide_root", true);
            boolean showLog = status.optBoolean("show_floating_log", false);
            runOnUiThread(() -> {
                if (destroyed) return;
                statusView.setText(summary + " · " + packages.size() + " 个应用");
                setRootSwitch(hideRoot, !rootSettingInFlight);
                setFloatingLog(showLog, !floatingLogInFlight);
                render(packages);
            });
        });
    }

    private static List<String> collectPackages(JSONObject listing) {
        List<String> packages = new ArrayList<>();
        JSONArray apps = listing.optJSONArray("apps");
        if (apps != null) {
            for (JSONObject app : JsonItems.of(apps)) {
                String pkg = app.optString("package", "");
                if (!pkg.isEmpty()) packages.add(pkg);
            }
        }
        packages.sort(String.CASE_INSENSITIVE_ORDER);
        return packages;
    }

    /**
     * 后端还没起来时重试，其余情况直接报错。
     *
     * <p>只在错误文本确实表示"还没初始化好"时才重试 —— 无条件重试会把真正的配置错误
     * 也拖成两轮无谓等待，反而更难看清原因。
     */
    private void retryOrShow(String error) {
        if (destroyed) return;
        String message = error == null ? "" : error;
        boolean transientFailure = message.contains("初始化")
                || message.contains("timeout")
                || message.contains("超时")
                || message.contains("Application.onCreate");
        if (startupRetry < MAX_STARTUP_RETRY && transientFailure) {
            startupRetry++;
            runOnUiThread(() -> {
                if (!destroyed) statusView.setText("沙箱后端启动中… 自动重试 " + startupRetry + "/" + MAX_STARTUP_RETRY);
            });
            try {
                Thread.sleep(450L * startupRetry);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            reload();
            return;
        }
        showBackendError(message);
    }

    private void showBackendError(String error) {
        if (destroyed) return;
        String stage = readStartupStage();
        runOnUiThread(() -> {
            if (destroyed) return;
            statusView.setText("沙箱后端异常");
            if (rootHideSwitch != null) rootHideSwitch.setEnabled(false);
            listView.removeAllViews();
            TextView detail = label(
                    "BlackBox 后端没有正常响应。\n\n" + error
                            + "\n\n最后启动阶段：\n" + stage
                            + "\n\n点右上角「诊断」可查看完整 Spider 沙箱事件与各进程阶段。",
                    12, palette.danger);
            detail.setTextIsSelectable(true);
            detail.setPadding(dp(8), dp(16), dp(8), dp(16));
            listView.addView(detail, fixed(-1, -2));
        });
    }

    // ------------------------------------------------------------------ 设置项

    private void confirmRootVisibilityChange(boolean hidden) {
        if (destroyed || rootSettingInFlight) return;
        boolean previous = !hidden;
        setRootSwitch(previous, true);

        String title = hidden ? "开启 Root 隐藏？" : "关闭 Root 隐藏？";
        String effect = hidden
                ? "Guest 将隐藏常见 Root 路径和管理包。"
                : "Guest 将可以发现并请求设备上的 su，授权仍由设备 Root 管理器决定。";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(effect + "\n\n所有正在运行的 Guest 将停止，重新启动后生效。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> applyRootVisibility(hidden, previous))
                .show();
    }

    private void applyRootVisibility(boolean hidden, boolean previous) {
        if (destroyed) return;
        rootSettingInFlight = true;
        setRootSwitch(previous, false);
        worker.execute(() -> {
            try {
                JSONObject response = SandboxRpc.call(this, "set_hide_root",
                        new JSONObject().put("hide_root", hidden));
                if (!response.optBoolean("ok")) {
                    throw new IllegalStateException(response.optString("error", "设置失败"));
                }
                boolean effective = response.optBoolean("hide_root", hidden);
                runOnUiThread(() -> {
                    if (destroyed) return;
                    rootSettingInFlight = false;
                    setRootSwitch(effective, true);
                    toast("Root 隐藏已" + (effective ? "开启" : "关闭"));
                    reload();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    rootSettingInFlight = false;
                    setRootSwitch(previous, true);
                    toast("Root 隐藏设置失败: " + error.getMessage());
                });
            }
        });
    }

    private void setRootSwitch(boolean hidden, boolean interactive) {
        if (rootHideSwitch == null) return;
        syncingRootSwitch = true;
        try {
            rootHideSwitch.setChecked(hidden);
            rootHideSwitch.setEnabled(interactive);
        } finally {
            syncingRootSwitch = false;
        }
    }

    private void applyFloatingLog(boolean enabled) {
        if (destroyed || floatingLogInFlight) return;
        floatingLogInFlight = true;
        // 乐观更新：先按用户意图显示，服务端确认后再以实际值落定
        setFloatingLog(!enabled, false);
        worker.execute(() -> {
            try {
                JSONObject response = SandboxRpc.call(this, "set_show_floating_log",
                        new JSONObject().put("show_floating_log", enabled));
                if (!response.optBoolean("ok")) {
                    throw new IllegalStateException(response.optString("error", "设置失败"));
                }
                boolean effective = response.optBoolean("show_floating_log", enabled);
                runOnUiThread(() -> {
                    if (destroyed) return;
                    floatingLogInFlight = false;
                    setFloatingLog(effective, true);
                    toast("日志悬浮窗已" + (effective ? "开启" : "关闭"));
                    reload();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    floatingLogInFlight = false;
                    setFloatingLog(!enabled, true);
                    toast("日志悬浮窗设置失败: " + error.getMessage());
                });
            }
        });
    }

    private void setFloatingLog(boolean enabled, boolean interactive) {
        if (floatingLogSwitch == null) return;
        syncingFloatingLog = true;
        try {
            floatingLogSwitch.setChecked(enabled);
            floatingLogSwitch.setEnabled(interactive);
        } finally {
            syncingFloatingLog = false;
        }
    }

    // ------------------------------------------------------------------ 列表渲染

    private void render(List<String> packages) {
        listView.removeAllViews();
        if (packages.isEmpty()) {
            TextView empty = label("还没有沙箱应用。\n导入一个 APK，或让 Agent 调用 Sandbox install。",
                    12, palette.muted);
            empty.setGravity(Gravity.CENTER);
            listView.addView(empty, fixed(-1, dp(120)));
            return;
        }
        for (String pkg : packages) listView.addView(buildAppCard(pkg), fixed(-1, -2));
    }

    private View buildAppCard(String pkg) {
        LinearLayout card = column();
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(rounded(palette.surface, 16));

        TextView name = label(pkg, 14, palette.text);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(name, fixed(-1, dp(34)));

        LinearLayout lifecycle = row();
        addWeighted(lifecycle, lifecycleButton("运行", palette.text,
                () -> rpcAction("launch", pkg, "已启动")));
        addWeighted(lifecycle, lifecycleButton("停止", palette.text,
                () -> rpcAction("stop", pkg, "已停止")));
        addWeighted(lifecycle, lifecycleButton("清数据", palette.text,
                () -> confirm("清除沙箱数据？", () -> rpcAction("clear_data", pkg, "已清除"))));
        addWeighted(lifecycle, lifecycleButton("卸载", palette.danger,
                () -> confirm("从蜘蛛沙箱卸载？", () -> rpcAction("uninstall", pkg, "已卸载"))));
        card.addView(lifecycle, fixed(-1, dp(44)));

        LinearLayout debugging = row();
        addWeighted(debugging, lifecycleButton("进程 / SO 基址", palette.text,
                () -> showProcesses(pkg)));
        addWeighted(debugging, lifecycleButton("Frida 动态调试", palette.text,
                () -> showFrida(pkg)));
        card.addView(debugging, fixed(-1, dp(42)));

        LinearLayout.LayoutParams params = fixed(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private Button lifecycleButton(String text, int color, Runnable onClick) {
        Button button = actionButton(text);
        button.setTextColor(color);
        button.setOnClickListener(v -> onClick.run());
        return button;
    }

    private void addWeighted(LinearLayout parent, Button button) {
        parent.addView(button, new LinearLayout.LayoutParams(0, dp(40), 1));
    }

    private void rpcAction(String action, String pkg, String successText) {
        worker.execute(() -> {
            try {
                JSONObject response = SandboxRpc.call(this, action, new JSONObject().put("package", pkg));
                if (!response.optBoolean("ok")) {
                    throw new IllegalStateException(response.optString("error", "操作失败"));
                }
                // launch 的 ok 只代表 RPC 成功，是否真的拉起要看 success
                if ("launch".equals(action) && !response.optBoolean("success", true)) {
                    throw new IllegalStateException("没有可启动 Activity");
                }
                runOnUiThread(() -> {
                    toast(successText);
                    reload();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> toast(action + ": " + error.getMessage()));
            }
        });
    }

    // ------------------------------------------------------------------ APK 导入

    private void pickApk() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/vnd.android.package-archive");
        startActivityForResult(intent, PICK_APK);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_APK || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        worker.execute(() -> installFromUri(uri));
    }

    /**
     * 先把选中的 APK 复制到自己的 cache，再交给后端安装。
     *
     * <p>必须先复制：SAF 给的 content:// 授权是给本进程的，控制器进程未必能读；
     * 而且本地路径也能让后面校验与安装用同一份字节。
     *
     * <p>安装前用 {@code getPackageArchiveInfo} 验一遍，是为了把"选了张图片"这类
     * 用户错误在界面侧就挡掉，不至于把后端拖进一次注定失败的解析。
     */
    private void installFromUri(Uri uri) {
        try {
            File directory = new File(getCacheDir(), "sandbox-import");
            directory.mkdirs();
            String displayName = displayName(uri).replaceAll("[^A-Za-z0-9._-]", "_");
            File staged = new File(directory, System.currentTimeMillis() + "-" + displayName);

            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(staged)) {
                if (in == null) throw new IllegalArgumentException("无法读取 APK");
                byte[] buffer = new byte[65536];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }

            PackageInfo info = getPackageManager()
                    .getPackageArchiveInfo(staged.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
            if (info == null || info.packageName == null) {
                throw new IllegalArgumentException("不是有效普通 APK");
            }
            if (getPackageName().equals(info.packageName)) {
                throw new IllegalArgumentException("不能导入蜘蛛自身");
            }

            JSONObject response = SandboxRpc.call(this, "install",
                    new JSONObject().put("path", staged.getAbsolutePath()));
            if (!response.optBoolean("ok") || !response.optBoolean("success")) {
                throw new IllegalStateException(response.optString("error",
                        response.optString("message", "安装失败")));
            }
            String installed = response.optString("package", info.packageName);
            runOnUiThread(() -> {
                toast("已安装到沙箱: " + installed);
                reload();
            });
        } catch (Throwable error) {
            runOnUiThread(() -> toast("安装失败: " + error.getMessage()));
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Throwable ignored) {
            // 拿不到就退回默认名
        }
        return "imported.apk";
    }

    // ------------------------------------------------------------------ 进程与 Frida

    private List<JSONObject> fetchProcesses(String pkg) throws Exception {
        JSONObject response = SandboxRpc.call(this, "process_list", new JSONObject().put("package", pkg));
        if (!response.optBoolean("ok")) {
            throw new IllegalStateException(response.optString("error", "读取进程失败"));
        }
        List<JSONObject> processes = new ArrayList<>();
        JSONArray rows = response.optJSONArray("processes");
        if (rows != null) {
            for (JSONObject row : JsonItems.of(rows)) processes.add(row);
        }
        return processes;
    }

    private void showProcesses(String pkg) {
        worker.execute(() -> {
            try {
                StringBuilder text = new StringBuilder();
                List<JSONObject> processes = fetchProcesses(pkg);
                if (processes.isEmpty()) text.append("当前没有运行进程。先启动这个 Guest。\n");
                for (JSONObject process : processes) {
                    int pid = process.optInt("pid", -1);
                    text.append("PID ").append(pid).append(" · ")
                            .append(process.optString("process")).append('\n');
                    text.append(describeModules(pkg, pid));
                    text.append('\n');
                }
                String body = text.toString();
                runOnUiThread(() -> showMonospaceDialog(pkg + " · 进程 / SO 基址", body));
            } catch (Throwable error) {
                runOnUiThread(() -> toast("读取进程失败: " + error.getMessage()));
            }
        });
    }

    /** 列出 guest 进程里已映射的 .so / apk 基址，供后续 Frida 或手动调试参照。 */
    private String describeModules(String pkg, int pid) {
        StringBuilder text = new StringBuilder();
        try {
            JSONObject response = SandboxGuestHost.request(this, "proc_modules", pkg, pid,
                    new JSONObject().put("pid", pid).put("max_modules", 80), 4000);
            if (!response.optBoolean("ok")) {
                return "  [调试桥] " + response.optString("error", "未响应") + "\n";
            }
            JSONArray modules = response.optJSONArray("modules");
            if (modules == null) return "";
            int shown = 0;
            for (int i = 0; i < modules.length() && shown < 24; i++) {
                JSONObject module = modules.optJSONObject(i);
                if (module == null) continue;
                String path = module.optString("path", "");
                if (!(path.endsWith(".so") || path.contains(".apk"))) continue;
                text.append("  ").append(module.optString("base", "?"))
                        .append("  ").append(new File(path).getName()).append('\n');
                shown++;
            }
        } catch (Throwable error) {
            text.append("  [调试桥] ").append(error.getMessage()).append('\n');
        }
        return text.toString();
    }

    private void showFrida(String pkg) {
        worker.execute(() -> {
            try {
                List<JSONObject> processes = fetchProcesses(pkg);
                if (processes.isEmpty()) {
                    runOnUiThread(() -> toast("先运行 Guest，再加载 Frida"));
                    return;
                }
                // 优先选主进程（process == 包名），否则退而取第一个
                JSONObject chosen = processes.get(0);
                for (JSONObject process : processes) {
                    if (pkg.equals(process.optString("process"))) {
                        chosen = process;
                        break;
                    }
                }
                int pid = chosen.optInt("pid", -1);
                if (pid <= 0) throw new IllegalStateException("无有效 PID");

                if (!FridaEnv.isInstalled(this)) {
                    runOnUiThread(() -> new AlertDialog.Builder(this)
                            .setTitle("安装 Frida Gadget " + FridaEnv.VERSION)
                            .setMessage("首次使用需要从 Frida 官方发布页下载 arm64 Gadget，"
                                    + "并校验固定的 SHA-256。")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("安装并加载",
                                    (dialog, which) -> worker.execute(() -> installAndLoadFrida(pkg, pid)))
                            .show());
                    return;
                }
                loadFrida(pkg, pid);
            } catch (Throwable error) {
                runOnUiThread(() -> toast("Frida: " + error.getMessage()));
            }
        });
    }

    private void installAndLoadFrida(String pkg, int pid) {
        try {
            runOnUiThread(() -> statusView.setText("正在安装 Frida Gadget " + FridaEnv.VERSION + "…"));
            FridaEnv.install(this, new TermuxShellExecutor(this), TermuxConstants.TERMUX_HOME_DIR_PATH);
            loadFrida(pkg, pid);
        } catch (Throwable error) {
            runOnUiThread(() -> toast("Frida 安装失败: " + error.getMessage()));
        }
    }

    private void loadFrida(String pkg, int pid) {
        try {
            JSONObject response = SandboxGuestHost.request(this, "proc_frida_load", pkg, pid,
                    new JSONObject().put("pid", pid), 15000);
            runOnUiThread(() -> showMonospaceDialog(pkg + " · Frida", response.toString()));
        } catch (Throwable error) {
            runOnUiThread(() -> toast("Frida 加载失败: " + error.getMessage()));
        }
    }

    // ------------------------------------------------------------------ 诊断

    private void showDiagnostics() {
        if (statusView != null) statusView.setText("正在读取诊断信息…");
        worker.execute(() -> {
            StringBuilder text = new StringBuilder();
            text.append(SandboxConsole.snapshot(this));
            text.append("\n===== 各进程启动阶段 =====\n");
            text.append(fetchStagesFromBackend());
            text.append("\n===== 引擎阶段文件（startup-stage.txt）=====\n");
            text.append(readStartupStage());
            String body = text.toString();
            runOnUiThread(() -> {
                if (!destroyed) showMonospaceDialog("蜘蛛沙箱诊断", body);
            });
        });
    }

    /**
     * 向控制器索取按 pid 分的阶段。
     *
     * <p>这是定位"卡在哪一步"的主要依据：每个进程一条记录，不会被别的进程覆盖。
     * 拿不到就说明控制器本身没起来 —— 那正是最需要知道的信息，所以把错误原样返回。
     */
    private String fetchStagesFromBackend() {
        try {
            JSONObject response = SandboxRpc.call(this, "stages", new JSONObject());
            if (!response.optBoolean("ok")) {
                return "(控制器未响应: " + response.optString("error", "未知原因") + ")\n";
            }
            return response.optString("stages", "(无阶段记录)\n");
        } catch (Throwable error) {
            return "(读取失败: " + error + ")\n";
        }
    }

    private String readStartupStage() {
        File file = new File(getFilesDir(), "sandbox/" + LEGACY_STAGE_FILE);
        if (!file.isFile()) return "(尚无后端启动记录)\n";
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) Math.min(file.length(), 8192)];
            int read = in.read(buffer);
            return read <= 0 ? "(空)\n" : new String(buffer, 0, read, StandardCharsets.UTF_8).trim() + "\n";
        } catch (Throwable error) {
            return "读取失败: " + error + "\n";
        }
    }

    private void showMonospaceDialog(String title, String body) {
        TextView view = label(body, 10, palette.text);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(dp(12), dp(8), dp(12), dp(8));
        scroll.addView(view);
        new AlertDialog.Builder(this).setTitle(title).setView(scroll)
                .setPositiveButton("关闭", null).show();
    }

    // ------------------------------------------------------------------ 小工具

    private void applySystemBars() {
        getWindow().setStatusBarColor(palette.background);
        getWindow().setNavigationBarColor(palette.background);
        if (android.os.Build.VERSION.SDK_INT < 23) return;
        int flags = getWindow().getDecorView().getSystemUiVisibility()
                & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (android.os.Build.VERSION.SDK_INT >= 26) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        if (palette.lightBackground) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (android.os.Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void confirm(String title, Runnable onConfirm) {
        new AlertDialog.Builder(this).setTitle(title)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> onConfirm.run())
                .show();
    }

    private void toast(String message) {
        Toast.makeText(this, message == null ? "" : message, Toast.LENGTH_SHORT).show();
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private TextView label(String text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView textButton(String text, int color) {
        TextView view = label(text, 11, color);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(10);
        button.setAllCaps(false);
        button.setTextColor(palette.text);
        button.setBackground(rounded(palette.cardButton, 10));
        return button;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams fixed(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
