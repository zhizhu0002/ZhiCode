package com.termux.app.zhicode.tools;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.shared.termux.TermuxConstants;
import com.zhizhu.zhicode.ZhiFileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 从蜘蛛自己的应用进程里启动 Android 组件。
 *
 * <h3>为什么不能让 shell 去干这件事</h3>
 * 内置 Termux 里的二进制是 Linux 子进程。从那里调用 Android 的 {@code am}
 * 实现时，系统的调用方身份判定会看到一个不属于本应用 UID 的进程 ——
 * 后果是「谁能启动这个组件」的判定、以及 FileProvider 的 URI 授权都算在错的
 * UID 上。所以这里刻意在蜘蛛的 Java 进程里做
 * {@link Context#startActivity}。
 *
 * <h3>{@link #tryExecuteAmStart} 是给 Bash 工具用的后门</h3>
 * 模型会习惯性地写 {@code am start -a ...}。与其让那次调用失败再纠正它，
 * 不如把「单一、无 shell 控制符」的 {@code am start} 就地翻译成一次意图调用。
 * 「无 shell 控制符」这条限定不能省：{@code am start ... && rm -rf ...}
 * 是一个复合命令，把它当成一次意图调用会**静默丢掉后半段**。
 */
public final class AndroidIntentBridge {

    private static final String APK_MIME = "application/vnd.android.package-archive";

    /** 待安装 APK 的记忆位置（跨越「去系统设置授权」这一步）。 */
    private static final String INSTALL_PREFS = TermuxConstants.BRAND_SLUG + "_pending_install";
    private static final String PENDING_APK_URI = "apk_uri";
    /** 进程内的那一份。系统可能在授权回来后杀掉并重建本应用，所以两个都要。 */
    private static final AtomicReference<Intent> PENDING_APK_INSTALL = new AtomicReference<>();

    /** 主线程投递的超时。超过它说明主线程被卡住了（而不是意图启动失败）。 */
    private static final int START_TIMEOUT_SECONDS = 8;
    /** 分享给安装器的 URI 用的标签，只影响系统界面上的显示。 */
    private static final String APK_CLIP_LABEL = "蜘蛛 APK";
    /** Android 8 起安装未知来源应用需要单独授权。 */
    private static final int API_UNKNOWN_SOURCES = 26;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());

    public AndroidIntentBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    // ================================================================== 入口

    /** 执行一次意图操作。所有异常都转成工具错误，不往外抛。 */
    public ToolExecutionResult execute(JSONObject input) {
        try {
            String operation = input.optString("operation", "intent").trim().toLowerCase(Locale.US);
            Intent intent = isLaunchOperation(operation)
                ? launchIntent(input)
                : genericIntent(input, operation);
            return isApkInstallIntent(intent) ? installApk(intent) : start(intent);
        } catch (IntentRequestException invalid) {
            // 参数有误：错误文案在构造点给出（「请提供 package」「无效 component」等），
            // 这里不再包一层前缀，免得变成「Android Intent 失败：请提供 package …」。
            return ToolExecutionResult.error(invalid.getMessage());
        } catch (Throwable failure) {
            return ToolExecutionResult.error("Android Intent 失败：" + readable(failure));
        }
    }

    private static boolean isLaunchOperation(String operation) {
        // open_app 是 launch_app 的同义词，模型两种写法都会用。
        return "launch_app".equals(operation) || "open_app".equals(operation);
    }

    /**
     * 启动一个应用。
     *
     * <p>只给 {@code app_name} 时按界面标签去找包名 —— 用户与模型都更容易记住
     * 「微信」而不是 {@code com.tencent.mm}。
     */
    private Intent launchIntent(JSONObject input) throws Exception {
        String packageName = input.optString("package", "").trim();
        String appName = input.optString("app_name", input.optString("app", "")).trim();
        if (packageName.isEmpty() && !appName.isEmpty()) {
            packageName = findLaunchablePackageByLabel(appName);
        }
        if (packageName.isEmpty()) {
            return fail("请提供 package 或 app_name");
        }
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            return fail("找不到可启动的应用："
                + (appName.isEmpty() ? packageName : appName + " (" + packageName + ")"));
        }
        return intent;
    }

    /** 构造一个通用意图。 */
    private Intent genericIntent(JSONObject input, String operation) throws Exception {
        String action = input.optString("action", Intent.ACTION_VIEW).trim();
        if (action.isEmpty()) action = Intent.ACTION_VIEW;
        // path 是给安装 APK 用的（本地文件系统路径），uri / data 是别名。
        String data = input.optString("path",
            input.optString("uri", input.optString("data", ""))).trim();
        String mime = input.optString("mime_type", input.optString("type", "")).trim();
        if ("install_apk".equals(operation)) {
            action = Intent.ACTION_VIEW;
            mime = APK_MIME;
        }

        Intent intent;
        if (!data.isEmpty() && !mime.isEmpty()) intent = new Intent(action).setDataAndType(Uri.parse(data), mime);
        else if (!data.isEmpty()) intent = new Intent(action, Uri.parse(data));
        else intent = new Intent(action);

        String packageName = input.optString("package", "").trim();
        if (!packageName.isEmpty()) intent.setPackage(packageName);

        String component = input.optString("component", "").trim();
        if (!component.isEmpty()) {
            ComponentName parsed = ComponentName.unflattenFromString(normalizeComponent(component));
            if (parsed == null) return fail("无效 component：" + component);
            intent.setComponent(parsed);
        }

        JSONArray categories = input.optJSONArray("categories");
        if (categories != null) {
            for (int i = 0; i < categories.length(); i++) {
                String category = categories.optString(i, "").trim();
                if (!category.isEmpty()) intent.addCategory(category);
            }
        }
        JSONObject extras = input.optJSONObject("extras");
        if (extras != null) putExtras(intent, extras);
        int flags = input.optInt("flags", 0);
        if (flags != 0) intent.addFlags(flags);
        return intent;
    }

    /**
     * 返回 {@code null} 会让 {@link #execute} 把它当成「构造失败」——
     * 但那不是我们要的语义，所以这里改用异常传递错误。
     * 见 {@link #fail}。
     */
    private static Intent fail(String message) {
        throw new IntentRequestException(message);
    }

    /** 意图参数有误。与「启动失败」区分开，因为两者的错误文案不同。 */
    private static final class IntentRequestException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        IntentRequestException(String message) {
            super(message);
        }
    }

    // ============================================================ APK 安装

    /**
     * 安装一个 APK。
     *
     * <h3>三步，顺序不能变</h3>
     * <ol>
     *   <li>把路径变成我们自己 FileProvider 的 content URI（系统安装器读不了
     *       应用私有目录里的 {@code file://}）。</li>
     *   <li>Android 8 起需要「安装未知来源应用」的授权。没有就先打开授权页，
     *       并把这次安装记下来 —— 用户授权后返回本应用，见
     *       {@link #resumePendingApkInstall}。</li>
     *   <li>启动系统安装器。</li>
     * </ol>
     *
     * <h3>为什么要先自己打开一次 URI</h3>
     * 系统安装器读不到文件时的报错是笼统的失败。先用
     * {@code openAssetFileDescriptor} 试一次，能在**交给别人之前**给出
     * 「APK 不存在」这种可操作的错误。
     */
    private ToolExecutionResult installApk(Intent source) {
        try {
            Intent install = new Intent(source);
            Uri uri = install.getData();
            if (uri == null) return ToolExecutionResult.error("安装 APK 需要 path 或 uri");

            Uri shared = toShareableUri(uri);
            if (shared == null) return ToolExecutionResult.error("APK 路径为空");
            uri = shared;

            install.setAction(Intent.ACTION_VIEW);
            install.setDataAndType(uri, APK_MIME);
            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            install.setClipData(ClipData.newRawUri(APK_CLIP_LABEL, uri));

            if (needsUnknownSourcesGrant()) return requestUnknownSourcesGrant(install);
            return start(install);
        } catch (IntentRequestException invalid) {
            return ToolExecutionResult.error(invalid.getMessage());
        } catch (Throwable failure) {
            return ToolExecutionResult.error("安装 APK 失败：" + readable(failure));
        }
    }

    /**
     * 把 {@code file:} 或裸路径变成我们 FileProvider 的 content URI；
     * 已经是我们的 content URI 时先自检一次。其它 scheme 原样返回（系统会拒绝，
     * 但那时的错误信息是系统给的，比我们猜一个更有用）。
     */
    private Uri toShareableUri(Uri uri) throws Exception {
        String scheme = uri.getScheme();
        if (scheme == null || scheme.isEmpty() || "file".equalsIgnoreCase(scheme)) {
            String path = "file".equalsIgnoreCase(scheme) ? uri.getPath() : uri.toString();
            if (path == null || path.trim().isEmpty()) return null;
            return ZhiFileProvider.uriForFile(context, new File(path));
        }
        if (!"content".equalsIgnoreCase(scheme)) return uri;
        if (!ZhiFileProvider.authority(context).equals(uri.getAuthority())) return uri;
        // 见方法注释：在交给安装器之前先自己读一次。
        try (android.content.res.AssetFileDescriptor probe =
                 context.getContentResolver().openAssetFileDescriptor(uri, "r")) {
            if (probe == null) throw new IntentRequestException("无法读取 APK：" + uri);
            return uri;
        }
    }

    private boolean needsUnknownSourcesGrant() {
        return Build.VERSION.SDK_INT >= API_UNKNOWN_SOURCES
            && !context.getPackageManager().canRequestPackageInstalls();
    }

    /** 记下这次安装，然后打开系统的授权页。授权失败时把记录清掉。 */
    private ToolExecutionResult requestUnknownSourcesGrant(Intent install) throws Exception {
        rememberPendingApk(install);
        Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + context.getPackageName()));
        settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ToolExecutionResult opened = start(settings);
        if (opened.isError) {
            PENDING_APK_INSTALL.set(null);
            clearRememberedApk();
            return ToolExecutionResult.error("无法打开“安装未知应用”授权页：" + opened.content);
        }
        return ToolExecutionResult.ok(
            "已打开“允许来自此来源的应用”授权页；开启后返回蜘蛛，将自动继续安装 APK。");
    }

    /**
     * 从授权页返回之后继续安装。由 {@code MainActivity.onResume} 调用。
     *
     * @return {@code null} 表示「没有待继续的安装」，或「授权还没给」—— 两种情况
     *         调用方都什么都不用做
     */
    public static ToolExecutionResult resumePendingApkInstall(Context context) {
        if (context == null) return null;
        Intent pending = PENDING_APK_INSTALL.get();
        if (pending == null) pending = restorePendingApkIntent(context);
        if (pending == null) return null;
        // 用户可能只是返回了，还没真的授权。
        if (Build.VERSION.SDK_INT >= API_UNKNOWN_SOURCES
            && !context.getPackageManager().canRequestPackageInstalls()) {
            return null;
        }
        // 进程内那份优先：它比从 prefs 还原出来的更完整（prefs 只存了 URI）。
        Intent inMemory = PENDING_APK_INSTALL.getAndSet(null);
        if (inMemory != null) pending = inMemory;
        clearRememberedApk(context);
        try {
            ToolExecutionResult result = new AndroidIntentBridge(context).start(pending);
            if (result.isError) return result;
            return ToolExecutionResult.ok("已获得安装权限，正在打开 Android 系统安装器");
        } catch (Throwable failure) {
            return ToolExecutionResult.error("继续安装 APK 失败：" + readable(failure));
        }
    }

    // -------------------------------------------------- 待安装记忆（两份）

    /**
     * 记住这次待安装。
     *
     * <p>只在我们自己的 content URI 上记 prefs：记别的 URI 没有意义
     * （还原时也要靠 authority 校验，别的 authority 会被拒），
     * 而这个 prefs 键会被后续的 resume 读到。
     */
    private void rememberPendingApk(Intent install) {
        PENDING_APK_INSTALL.set(new Intent(install));
        Uri uri = install.getData();
        if (uri == null) return;
        if (!ZhiFileProvider.authority(context).equals(uri.getAuthority())) return;
        installPrefs().edit().putString(PENDING_APK_URI, uri.toString()).apply();
    }

    /**
     * 从 prefs 还原一次待安装。
     *
     * <p>authority 不匹配时顺手清掉这条记录：它必定是旧版本或别的构建留下的，
     * 留着只会在每次 onResume 时被重新拒绝一遍。
     */
    private static Intent restorePendingApkIntent(Context context) {
        String value = context.getSharedPreferences(INSTALL_PREFS, Context.MODE_PRIVATE)
            .getString(PENDING_APK_URI, "");
        if (value == null || value.trim().isEmpty()) return null;
        Uri uri = Uri.parse(value);
        if (!ZhiFileProvider.authority(context).equals(uri.getAuthority())) {
            clearRememberedApk(context);
            return null;
        }
        Intent install = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME);
        install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        install.setClipData(ClipData.newRawUri(APK_CLIP_LABEL, uri));
        return install;
    }

    private android.content.SharedPreferences installPrefs() {
        return context.getSharedPreferences(INSTALL_PREFS, Context.MODE_PRIVATE);
    }

    private void clearRememberedApk() {
        installPrefs().edit().remove(PENDING_APK_URI).apply();
    }

    private static void clearRememberedApk(Context context) {
        context.getSharedPreferences(INSTALL_PREFS, Context.MODE_PRIVATE)
            .edit().remove(PENDING_APK_URI).apply();
    }

    /**
     * 这是不是一个「要装 APK」的意图。
     *
     * <p>三种判断都要有：显式的安装 action、APK 的 MIME、以及路径以
     * {@code .apk} 结尾。模型常常只给一个路径，那时前两条都不成立。
     */
    private static boolean isApkInstallIntent(Intent intent) {
        if (intent == null) return false;
        if (Intent.ACTION_INSTALL_PACKAGE.equals(intent.getAction())) return true;
        if (APK_MIME.equalsIgnoreCase(intent.getType())) return true;
        Uri data = intent.getData();
        String path = data == null ? "" : String.valueOf(data.getPath()).toLowerCase(Locale.US);
        return path.endsWith(".apk");
    }

    // ======================================================= am start 拦截

    /**
     * 把一条简单的 {@code am start} 命令翻译成意图调用。
     *
     * <p>三条提前返回的 {@code null} 各自防一种情况：
     * 不是 {@code am start}（别的命令）、带 shell 控制符（复合命令，
     * 见类注释）、参数少到解析不出东西。
     */
    public ToolExecutionResult tryExecuteAmStart(String command) {
        if (command == null) return null;
        String trimmed = command.trim();
        if (!trimmed.matches("(?s)^am\\s+(?:start|start-activity)\\b.*")) return null;
        if (containsShellControl(trimmed)) return null;
        try {
            List<String> words = shellWords(trimmed);
            if (words.size() < 2) return null;
            ToolExecutionResult result = execute(parseAmStartArguments(words));
            if (result.isError) return result;
            return ToolExecutionResult.ok(
                "[ZhiCode Android Intent bridge]\n已将 Termux `am start` 转交给本应用进程执行。\n"
                    + result.content);
        } catch (Throwable failure) {
            return ToolExecutionResult.error(
                "[ZhiCode Android Intent bridge]\n无法解析 am start：" + readable(failure));
        }
    }

    /**
     * 把 {@code am start} 的参数翻译成一个意图入参对象。
     *
     * <p>短选项与长选项都要认（模型两种都写）。带值的选项都已经过
     * {@link #shellWords}，所以这里只需按位置取下一个词。
     *
     * <p>末尾的裸参数按 URI 处理 —— {@code am start https://…} 是合法写法。
     * 判断条件是「含冒号或以斜杠开头」，而不是「无条件当成 URI」：
     * 那会把 {@code am start com.foo/.Bar} 这种简写也错当成 URI。
     */
    private static JSONObject parseAmStartArguments(List<String> words) throws Exception {
        JSONObject input = new JSONObject().put("operation", "intent");
        JSONArray categories = new JSONArray();
        JSONObject extras = new JSONObject();

        int i = 2;
        while (i < words.size()) {
            String word = words.get(i++);
            // 只影响等待行为或选择用户的选项，与意图内容无关。
            if ("-W".equals(word) || "--wait".equals(word)) continue;
            if ("--user".equals(word)) {
                if (i < words.size()) i++;
                continue;
            }
            if (isOption(word, "-a", "--action") && i < words.size()) input.put("action", words.get(i++));
            else if (isOption(word, "-d", "--data") && i < words.size()) input.put("uri", words.get(i++));
            else if (isOption(word, "-t", "--type") && i < words.size()) input.put("mime_type", words.get(i++));
            else if (isOption(word, "-p", "--package") && i < words.size()) input.put("package", words.get(i++));
            else if (isOption(word, "-n", "--component") && i < words.size()) input.put("component", words.get(i++));
            else if (isOption(word, "-c", "--category") && i < words.size()) categories.put(words.get(i++));
            else if (isOption(word, "-f", "--flags") && i < words.size()) input.put("flags", parseInt(words.get(i++)));
            else if ("--es".equals(word) && i + 1 < words.size()) extras.put(words.get(i++), words.get(i++));
            else if (("--ei".equals(word) || "--el".equals(word)) && i + 1 < words.size()) {
                extras.put(words.get(i++), Long.parseLong(words.get(i++)));
            } else if ("--ez".equals(word) && i + 1 < words.size()) {
                extras.put(words.get(i++), Boolean.parseBoolean(words.get(i++)));
            } else if (!word.startsWith("-") && !input.has("uri")
                && (word.contains(":") || word.startsWith("/"))) {
                input.put("uri", word);
            }
        }
        if (categories.length() > 0) input.put("categories", categories);
        if (extras.length() > 0) input.put("extras", extras);
        return input;
    }

    private static boolean isOption(String word, String shortForm, String longForm) {
        return shortForm.equals(word) || longForm.equals(word);
    }

    // ============================================================ 标签找包名

    /**
     * 按界面标签找一个可启动的包名。
     *
     * <p>两级匹配：先找**完全**等于标签或包名的，找不到再用「包含」关系。
     * 两级是必要的 —— 单用「包含」时「音乐」会命中一堆名字带「音乐」的应用，
     * 而先精确匹配能让常见情况得到确定的结果。
     *
     * <p>{@link #fuzzy} 只记住第一个命中项，不做排序：用户装了哪些应用不由我们决定，
     * 而「有歧义就报错要求给包名」比「猜一个」更不容易出错。
     */
    private String findLaunchablePackageByLabel(String wanted) {
        String needle = wanted == null ? "" : wanted.trim().toLowerCase(Locale.US);
        if (needle.isEmpty()) return "";
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = context.getPackageManager().queryIntentActivities(probe, 0);
        if (apps == null) apps = Collections.emptyList();
        String fuzzy = "";
        for (ResolveInfo info : apps) {
            if (info == null || info.activityInfo == null) continue;
            CharSequence label = info.loadLabel(context.getPackageManager());
            String labelText = label == null ? "" : label.toString().trim();
            String packageName = info.activityInfo.packageName == null
                ? "" : info.activityInfo.packageName.trim();
            if (packageName.isEmpty()) continue;
            String lowerLabel = labelText.toLowerCase(Locale.US);
            String lowerPackage = packageName.toLowerCase(Locale.US);
            if (lowerLabel.equals(needle) || lowerPackage.equals(needle)) return packageName;
            if (fuzzy.isEmpty() && (lowerLabel.contains(needle) || needle.contains(lowerLabel))) {
                fuzzy = packageName;
            }
        }
        return fuzzy;
    }

    // ============================================================ 启动

    /**
     * 启动一个意图。
     *
     * <h3>为什么要在主线程上做，还要等</h3>
     * 部分设备上 {@code startActivity} 从后台线程调用会出问题，所以统一投递到主线程。
     * 但工具调用是同步 API，必须等出结果 —— 于是用闩等。
     * 有超时是因为主线程可能正卡在别的地方，而工具不能永远挂着。
     *
     * <p>已经在主线程时**直接跑**而不是 post：post 到主线程再等在主线程上
     * 会立刻死锁（等的就是自己）。
     */
    private ToolExecutionResult start(final Intent intent) throws Exception {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        Runnable task = () -> {
            try {
                context.startActivity(intent);
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) task.run();
        else main.post(task);

        if (!done.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return ToolExecutionResult.error("Android Intent 启动超时");
        }
        Throwable error = failure.get();
        if (error != null) {
            // 「没有应用能处理」是常见且可操作的结论，单独给一句；它通常意味着
            // 用户需要装一个应用，或者 URI 的 scheme 写错了。
            if (error instanceof ActivityNotFoundException) {
                return ToolExecutionResult.error(
                    "没有应用可以处理这个 Android Intent：" + intent.toUri(0));
            }
            return ToolExecutionResult.error("Android Intent 失败：" + readable(error));
        }
        return ToolExecutionResult.ok("已通过蜘蛛应用进程启动：" + intent.toUri(0));
    }

    // ============================================================ 小工具

    /**
     * 写 extras。
     *
     * <p>{@link JSONObject} 的数字都是 {@code Integer}/{@code Long}/{@code Double}，
     * 而 {@link Intent#putExtra} 对它们的重载不同 —— 逐个判类型保住了原始类型。
     * 其余类型（含嵌套对象/数组）一律转成字符串：意图 extras 只支持基本类型，
     * 转字符串至少让调用方拿得到内容，而不是一个被丢掉的键。
     */
    private static void putExtras(Intent intent, JSONObject extras) {
        JSONArray names = extras.names();
        if (names == null) return;
        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i, "");
            Object value = extras.opt(key);
            if (key.isEmpty() || value == null || value == JSONObject.NULL) continue;
            if (value instanceof Boolean) intent.putExtra(key, (Boolean) value);
            else if (value instanceof Integer) intent.putExtra(key, (Integer) value);
            else if (value instanceof Long) intent.putExtra(key, (Long) value);
            else if (value instanceof Double) intent.putExtra(key, (Double) value);
            else intent.putExtra(key, String.valueOf(value));
        }
    }

    /**
     * 补全组件名的简写。
     *
     * <p>{@code am} 接受 {@code com.foo/.Bar} 这种简写（以点号开头的类名相对包名），
     * 而 {@link ComponentName#unflattenFromString} 不认 —— 它要求完整的类名。
     * 这里把 {@code pkg/.Cls} 展开成 {@code pkg/pkg.Cls}。
     */
    private static String normalizeComponent(String raw) {
        String value = raw.trim();
        int slash = value.indexOf('/');
        boolean relativeClass = slash > 0 && slash + 1 < value.length() && value.charAt(slash + 1) == '.';
        if (!relativeClass) return value;
        return value.substring(0, slash + 1) + value.substring(0, slash) + value.substring(slash + 1);
    }

    /**
     * 命令里是否有 shell 控制符（引号外）。
     *
     * <p>必须跟踪引号状态：{@code am start -d "http://a?x=1&y=2"} 里的 {@code &}
     * 在双引号内，不是控制符；而按「含 & 就拒绝」判会让一条完全正常的命令
     * 退回到 shell 执行 —— 那正是我们想避免的。
     */
    private static boolean containsShellControl(String command) {
        boolean single = false;
        boolean dbl = false;
        boolean escape = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (!dbl && c == '\'') {
                single = !single;
                continue;
            }
            if (!single && c == '"') {
                dbl = !dbl;
                continue;
            }
            if (!single && !dbl
                && (c == ';' || c == '|' || c == '&' || c == '`' || c == '\n' || c == '\r')) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按 shell 规则切词。
     *
     * <p>引号与转义都要处理：{@code am start -a android.intent.action.VIEW -d "https://x y"}
     * 里的空格在引号内，不该切。引号未闭合时**抛错**而不是尽力切成词 ——
     * 未闭合说明我们对这条命令的理解是错的，继续猜下去会执行一条用户没写的意图。
     */
    private static List<String> shellWords(String command) {
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean single = false;
        boolean dbl = false;
        boolean escape = false;
        boolean started = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (escape) {
                current.append(c);
                escape = false;
                started = true;
                continue;
            }
            if (c == '\\' && !single) {
                escape = true;
                started = true;
                continue;
            }
            if (c == '\'' && !dbl) {
                single = !single;
                started = true;
                continue;
            }
            if (c == '"' && !single) {
                dbl = !dbl;
                started = true;
                continue;
            }
            if (Character.isWhitespace(c) && !single && !dbl) {
                if (started) {
                    words.add(current.toString());
                    current.setLength(0);
                    started = false;
                }
                continue;
            }
            current.append(c);
            started = true;
        }
        if (escape || single || dbl) throw new IllegalArgumentException("引号或转义未闭合");
        if (started) words.add(current.toString());
        return words;
    }

    /** {@code am} 的 flags 接受十进制与 {@code 0x} 十六进制两种写法。 */
    private static int parseInt(String raw) {
        String value = raw.trim().toLowerCase(Locale.US);
        if (value.startsWith("0x")) return (int) Long.parseLong(value.substring(2), 16);
        return Integer.parseInt(value);
    }

    /** 把异常压成一行可读文本；没有 message 时只给类名，免得出现「错误：null」。 */
    private static String readable(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName()
            + (message == null || message.trim().isEmpty() ? "" : ": " + message);
    }
}
