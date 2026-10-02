package com.zhizhu.zhicode;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.JNI;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.terminal.TextStyle;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;
import com.zhizhu.zhicode.sandbox.SandboxShell;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 终端面板的 Android 侧宿主：持有真实 PTY 会话，并把可观察状态交给 Compose 渲染。
 *
 * <h3>为什么它只剩这些</h3>
 * 这个类以前连工具栏、会话抽屉、两行扩展键、两个对话框、主题刷色一起做了 ——
 * 六百多行纯 Java 拼 View 的界面代码，与界面上方那一行 Compose 标题栏割裂，
 * 而且和原版逐行相同（重合度一度是 95.4%，全工程最大的一块）。
 *
 * <p>现在界面全在 `compose/ui/panes/TerminalChrome.kt` 与 `TerminalDialogs.kt`，
 * 本类只做三件 Compose 做不了的事：
 * <ol>
 *   <li><b>PTY 本身</b>：拿 {@link JNI} 加载 {@code libtermux.so}、按环境变量数组起
 *       {@link TerminalSession}，并持有会话列表。</li>
 *   <li><b>承载上游 {@link TerminalView}</b>：它是 Terminalux 的 Canvas 逐字符渲染器
 *       （CSI 解析、文本选择、缩放手势），属 Termux 上游，不重写。
 *       本类作为它的父容器被 Compose 的 `AndroidView` 挂载。</li>
 *   <li><b>两个 Client 接口</b>：{@link TerminalViewClient} 与 {@link TerminalSessionClient}
 *       由 TerminalView 反过来调用，只能在 View 进程一侧实现。</li>
 * </ol>
 *
 * <h3>状态怎么交给 Compose</h3>
 * {@link #state()} 返回一份不可变快照，{@link #addObserver} 登记变更回调。
 * 用的是「快照 + 回调」而不是 Compose 的 `StateFlow`：本类是 Java，
 * 而 `StateFlow` 是 Kotlin 类型，从 Java 构造它要绕到 `StateFlowKt` 去 ——
 * 那等于让这个最底层的宿主反过来依赖界面框架。
 *
 * <p>**只有低频变化才会回调**：{@link #onTextChanged} 是每次按键回声/每条命令输出都会触发的
 * 高频回调，它只负责让 TerminalView 重画，**不**通知观察者 —— 否则每敲一个字符都要重建
 * 一次快照并驱动一整轮 Compose 重组。标题、会话增删、锁定键、通知文案这些低频变化才通知。
 *
 * <h3>不动的三处</h3>
 * <ol>
 *   <li>{@link #environmentArray()} 逐字保留，尤其 {@code LD_LIBRARY_PATH}：
 *       内置 Termux 的 ELF 内嵌 {@code DT_RUNPATH} 指向并不存在的
 *       {@code /data/data/com.termux/files/usr/lib}（见 {@link RuntimeInstaller}），
 *       少了这个变量终端里所有二进制都会 `CANNOT LINK EXECUTABLE`。</li>
 *   <li>{@link #selectSession} 必须等 TerminalView 完成首次 layout 才 attach。
 *       早 attach 会在 `onSizeChanged` 里立刻算行列并加载 {@code libtermux.so}，
 *       那时本视图还没被插进父容器，任何 JNI 错误都会从 `onSizeChanged` 逃出去
 *       **杀掉整个 Activity**（这个崩溃真实发生过）。</li>
 *   <li>{@link #applyTerminalPalette} 的深色档色值表逐字节不变。浅色档那张表
 *       是历史上写下的但走不到（原先唯一的入口 {@code applyTheme} 没有任何调用方），
 *       本类**不**把它接上：终端跟随主题要单独定，现在接上等于在重写里夹带外观变更。</li>
 * </ol>
 */
public final class TermuxTerminalPane extends FrameLayout
        implements TerminalViewClient, TerminalSessionClient {

    private static final String LOG_TAG = "ZhiTerminal";

    /** 未配置 `extra-keys` 时的扩展键矩阵（Termux 默认布局）。 */
    private static final String DEFAULT_EXTRA_KEYS =
        "[['ESC','/',{key: '-', popup: '|'},'HOME','UP','END','PGUP'], ['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]";

    /** `extra-keys` 解析失败时的兜底行。与上面那个串保持同一份布局。 */
    private static final String[] DEFAULT_ROW_TOP = {"ESC", "/", "-", "HOME", "UP", "END", "PGUP"};
    private static final String[] DEFAULT_ROW_BOTTOM = {"TAB", "CTRL", "ALT", "LEFT", "DOWN", "RIGHT", "PGDN"};

    /** 终端字号范围（与 `changeFont` 的上下界一致，单位 sp）。 */
    private static final float MIN_TEXT_SP = 8f;
    private static final float MAX_TEXT_SP = 32f;

    /** 重挂之后补量一次尺寸的延迟。见 {@link #refreshTerminal()} 的说明。 */
    private static final long REFRESH_RETRY_DELAY_MS = 300L;

    private static final String SHELL_NAME = "bash";
    private static final String SESSION_NAME_PREFIX = "bash ";
    private static final int SCROLLBACK_LINES = 5000;
    private static final String TERMINAL_TITLE_FALLBACK = "终端";
    private static final String PROPERTIES_FILE = "/.termux/termux.properties";
    private static final String WAKELOCK_SUFFIX = ":terminal";

    /** 环境未就绪时的说明。刻意指路而不是说"正在准备"，见原实现的说明。 */
    private static final String RUNTIME_NOTICE =
        "内置 Termux 环境尚未就绪。\n请先在侧栏「准备内置 Termux 环境」里初始化，完成后这里就是可输入的真实终端。";
    /** PTY 失败界面的固定文字。Compose 侧按 [State.failureDetail] 是否存在决定要不要显示。 */
    public static final String FAILURE_TITLE = "终端启动失败";
    public static final String FAILURE_FOOTNOTE =
        "\n\n应用没有退出，你可以先修好运行环境再回来，而不是直接崩掉。";
    private static final String FAILURE_UNKNOWN = "未知的 PTY 错误";

    // ------------------------------------------------------------------ 交给 Compose 的快照

    /** 一个扩展键：显示什么、按下时发什么。 */
    public static final class ExtraKey {
        public final String display;
        /** 交给 {@link #sendExtraKey} 的动作名；可能是宏（按空白切分后逐个发）。 */
        public final String action;

        ExtraKey(String display, String action) {
            this.display = display;
            this.action = action;
        }
    }

    /** 会话列表里的一行。 */
    public static final class SessionInfo {
        public final String name;
        public final boolean running;
        public final boolean selected;

        SessionInfo(String name, boolean running, boolean selected) {
            this.name = name;
            this.running = running;
            this.selected = selected;
        }
    }

    /**
     * 一次完整快照。字段全是 public final 且不含可变集合，Compose 侧可直接读。
     *
     * <p>{@link #extraKeys} 是「行 → 键」的两级列表，构建后不再修改。
     */
    public static final class State {
        public final List<SessionInfo> sessions;
        public final String title;
        /** 环境未就绪的说明；为空表示环境正常。 */
        public final String runtimeNotice;
        /** PTY 启动失败的详情（含 cause 链）；为空表示没失败。 */
        public final String failureDetail;
        public final boolean ctrl;
        public final boolean alt;
        public final boolean shift;
        public final boolean fn;
        public final boolean backMapsEscape;
        public final float textSp;
        public final List<List<ExtraKey>> extraKeys;
        public final boolean wakeLockHeld;
        /**
         * 宿主是否在视图树上。
         *
         * <p>为 {@code false} 表示承载它的 `AndroidView` 节点已经不带它了 ——
         * 那种情况下界面层必须重建那个节点（宿主自己接不回去）。
         * 这是「旋转后终端空白」这类问题的兜底判定依据。
         */
        public final boolean hostInTree;

        State(List<SessionInfo> sessions, String title, String runtimeNotice, String failureDetail,
              boolean ctrl, boolean alt, boolean shift, boolean fn, boolean backMapsEscape,
              float textSp, List<List<ExtraKey>> extraKeys, boolean wakeLockHeld,
              boolean hostInTree) {
            this.sessions = sessions;
            this.title = title;
            this.runtimeNotice = runtimeNotice;
            this.failureDetail = failureDetail;
            this.ctrl = ctrl;
            this.alt = alt;
            this.shift = shift;
            this.fn = fn;
            this.backMapsEscape = backMapsEscape;
            this.textSp = textSp;
            this.extraKeys = extraKeys;
            this.wakeLockHeld = wakeLockHeld;
            this.hostInTree = hostInTree;
        }
    }

    private final RuntimeInstaller runtime;
    private final List<TerminalSession> sessions = new ArrayList<>();
    private final List<Runnable> observers = new ArrayList<>();

    /** 构建后不再修改；`reloadProperties` 换的是整个引用。 */
    private volatile List<List<ExtraKey>> extraKeys = Collections.emptyList();

    private TerminalView terminalView;
    private int selected = -1;
    private float terminalTextSp = 14f;
    private boolean ctrl;
    private boolean alt;
    private boolean shift;
    private boolean fn;
    private boolean backMapsEscape;
    private PowerManager.WakeLock wakeLock;

    /** 上一次上报给界面的「宿主在不在视图树上」。只在变化时通知，避免无谓重组。 */
    private boolean hostInTree = true;

    /** 环境未就绪的说明；为空表示环境正常。 */
    private String runtimeNotice;
    /** PTY 失败详情；为空表示没失败。 */
    private String failureDetail;

    private String nextSessionWorkingDirectory = TermuxConstants.TERMUX_HOME_DIR_PATH;

    public TermuxTerminalPane(Context context, RuntimeInstaller runtime) {
        super(context);
        this.runtime = runtime;
        // 上游 TerminalView 自己会按模拟器的背景色绘制，这里的底色只在
        // 「还没有会话 / 会话已退出」的空档里露出来，取 ANSI 调色板的 0 号色（黑）。
        setBackgroundColor(Color.BLACK);

        if (runtime.isInstalled()) {
            newSession();
        } else {
            showRuntimeNotice();
        }
        reloadProperties();
    }

    // ------------------------------------------------------------------ 状态分发

    /** 当前快照。每次调用都新建，不做缓存 —— 它只在低频变化时被读一次。 */
    public State state() {
        List<SessionInfo> rows = new ArrayList<>(sessions.size());
        for (int i = 0; i < sessions.size(); i++) {
            TerminalSession session = sessions.get(i);
            String name = session.mSessionName == null
                ? "session " + (i + 1)
                : session.mSessionName;
            rows.add(new SessionInfo(name, session.isRunning(), i == selected));
        }
        return new State(rows, currentTitle(), runtimeNotice, failureDetail,
            ctrl, alt, shift, fn, backMapsEscape, terminalTextSp, extraKeys,
            wakeLock != null && wakeLock.isHeld(),
            isAttachedToWindow() && getParent() != null);
    }

    /** 登记变更回调。回调在主线程执行（本类的所有状态变化都发生在主线程）。 */
    public void addObserver(Runnable observer) {
        if (observer != null && !observers.contains(observer)) observers.add(observer);
    }

    public void removeObserver(Runnable observer) {
        observers.remove(observer);
    }

    /**
     * 状态变了。
     *
     * <p>遍历前先复制一份：观察者里很可能有 Compose 的 `DisposableEffect`，
     * 它会在回调中（间接地）注销自己，那会让正在遍历的列表被改。
     */
    private void notifyStateChanged() {
        if (observers.isEmpty()) return;
        for (Runnable observer : new ArrayList<>(observers)) {
            try {
                observer.run();
            } catch (Throwable error) {
                Log.e(LOG_TAG, "terminal state observer failed", error);
            }
        }
    }

    // ------------------------------------------------------------------ 对外动作（Compose 调用）

    /** 环境准备就绪时调用；没有会话就起一个。 */
    public void onRuntimeReady() {
        if (sessions.isEmpty()) newSession();
    }

    /** 项目目录变化：下一次新建会话用它当工作目录。目录不存在则忽略。 */
    public void setNextSessionWorkingDirectory(String path) {
        if (path != null && new File(path).isDirectory()) nextSessionWorkingDirectory = path;
    }

    /**
     * 让当前终端重新渲染一次。**配置变化后必须调**。
     *
     * <h3>为什么需要它（这是真机上报回来的一个 bug）</h3>
     * 上游 {@link TerminalView} 的渲染依赖它自己的 {@code mEmulator}，而那个字段：
     * <ul>
     *   <li>在 {@code attachSession()} 里被置为 {@code null}，紧接着调一次 {@code updateSize()}；</li>
     *   <li>只在 {@code updateSize()} 里被设回来，而 {@code updateSize()} 在
     *       「宽或高为 0」或「还没有会话」时**静默返回**；</li>
     *   <li>{@code onDraw()} 在 {@code mEmulator == null} 时**只画一块纯黑**。</li>
     * </ul>
     * 于是只要 {@code mEmulator} 是 null，屏幕就是纯黑 —— 真机上表现为「旋转后终端一片空白」。
     *
     * <h3>为什么是「摘下来再挂回去」而不是只调 updateSize</h3>
     * 用户实测：同样的情况下「切到别的标签再切回来」能恢复。那一步做的事就是让本视图
     * **离开视图树再回来**，重新走一遍完整的测量/布局。这里把那件事做在 View 这一层，
     * 于是不需要用户去切标签。
     *
     * <p>显式调 {@code updateSize()} 是必须的：同一个 View 摘下来再挂回**同样的尺寸**时
     * {@code onSizeChanged} 不会触发，而 {@code updateSize()} 是唯一会设 {@code mEmulator}
     * 的入口。{@code postDelayed} 那次重试用于覆盖「第一次调用时布局还没完成」——
     * {@code updateSize()} 在尺寸为 0 时是静默返回的，所以必须再试一次。
     *
     * <p>刻意**不碰会话与 PTY**：只让渲染重新对齐。
     */
    public void refreshTerminal() {
        TerminalView view = terminalView;
        TerminalSession session = current();
        if (view == null || session == null) {
            // 没有会话时把「宿主是否还在视图树上」记进状态就够了：
            // 界面据此决定要不要重建承载它的 AndroidView 节点。
            reportAttachState("no-session");
            return;
        }
        if (view.getCurrentSession() != session) {
            // 视图上的会话不是当前会话（例如新会话还没挂上去）→ 重走一次挂载。
            selectSession(selected);
            return;
        }

        // 摘下来再挂回去：复刻「切标签再切回」那条已验证可行的恢复路径。
        removeAllViews();
        addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        view.post(() -> {
            view.updateSize();
            view.onScreenUpdated();
            view.invalidate();
        });
        view.postDelayed(() -> {
            view.updateSize();
            view.invalidate();
        }, REFRESH_RETRY_DELAY_MS);
        reportAttachState("refreshed");
    }

    /**
     * 把「宿主是否还在视图树上」记进状态，并打一行现场日志。
     *
     * <p>这一行是这类问题的**唯一**取证手段：旋转在沙箱里无法复现（没有旋转 API），
     * 而这条路径只在配置变化时才会走到。真机上跑 `logcat -d | grep ZhiTerminal`
     * 就能看到「自动重挂」到底有没有发生、宿主当时在不在树上、尺寸是多少。
     */
    private void reportAttachState(String reason) {
        boolean inTree = isAttachedToWindow() && getParent() != null;
        Log.i(LOG_TAG, "refreshTerminal(" + reason + "): inTree=" + inTree
            + " size=" + getWidth() + "x" + getHeight()
            + " sessions=" + sessions.size()
            + " sessionAttached=" + (terminalView != null && terminalView.getCurrentSession() != null));
        if (inTree != hostInTree) {
            hostInTree = inTree;
            notifyStateChanged();
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        // 重新进入视图树：这正是「切标签回来」时发生的事，渲染机会也在这里。
        if (!hostInTree) {
            hostInTree = true;
            notifyStateChanged();
        }
        post(() -> {
            TerminalView view = terminalView;
            if (view != null) {
                view.updateSize();
                view.invalidate();
            }
        });
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        // 旋转/换主题：视图树会收到这个分发，让终端重新对齐一次尺寸。
        refreshTerminal();
    }

    public void newSession() {
        if (!runtime.isInstalled()) {
            showRuntimeNotice();
            return;
        }
        failureDetail = null;
        Throwable loadError = preloadNativePty();
        if (loadError != null) {
            showTerminalFailure(loadError);
            return;
        }
        String shell = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/" + SHELL_NAME;
        if (!new File(shell).isFile()) {
            showRuntimeNotice();
            return;
        }
        String cwd = new File(nextSessionWorkingDirectory).isDirectory()
            ? nextSessionWorkingDirectory
            : TermuxConstants.TERMUX_HOME_DIR_PATH;
        String[] args = new String[]{shell, "-l"};

        // 前面几道关都过了，说明环境和 PTY 都是好的：把之前的"未就绪"提示撤掉。
        runtimeNotice = null;

        TerminalSession session = new TerminalSession(shell, cwd, args, environmentArray(),
            SCROLLBACK_LINES, this);
        session.mSessionName = SESSION_NAME_PREFIX + (sessions.size() + 1);
        sessions.add(session);
        applyTerminalPalette(session);
        selectSession(sessions.size() - 1);
    }

    public void selectSession(int index) {
        if (index < 0 || index >= sessions.size()) return;
        selected = index;
        failureDetail = null;
        removeAllViews();

        // 关键：TerminalView 完成首次 layout 之前不能 attach PTY（见类注释）。
        final TerminalView view = new TerminalView(getContext(), null);
        view.setTerminalViewClient(this);
        view.setTextSize(spPx(terminalTextSp));
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        terminalView = view;
        addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        notifyStateChanged();

        view.post(() -> {
            // 三道校验：这三件事在 post 落地前都可能已经变了。
            if (terminalView != view || selected != index || index >= sessions.size()) return;
            try {
                TerminalSession attached = sessions.get(index);
                view.attachSession(attached);
                applyTerminalPalette(attached);
                view.onScreenUpdated();
                view.requestFocus();
                view.postDelayed(TermuxTerminalPane.this::showKeyboard, 180);
                // attach 时视图可能还没有尺寸（那时 TerminalView 的 mEmulator 会保持 null，
                // 屏幕画成纯黑）。再确认一次：尺寸已经定下来就立刻量一次。
                refreshTerminal();
            } catch (Throwable error) {
                Log.e(LOG_TAG, "Failed to attach native Termux PTY", error);
                if (terminalView == view) showTerminalFailure(error);
            }
        });
    }

    /**
     * 重试当前终端（"Terminal failed to start" 界面上那个按钮）。
     *
     * <p>没有会话时改为新建：原实现无论何种失败都调 `selectSession(max(0, selected))`，
     * 而 `libtermux.so` 加载失败时列表本来就是空的，那个调用会立刻被"下标越界"
     * 挡回去 —— 也就是说这种情况下**重试按钮什么都不会发生**。这里补上这一支。
     */
    public void retryTerminal() {
        if (sessions.isEmpty()) {
            newSession();
        } else {
            selectSession(Math.max(0, selected));
        }
    }

    /**
     * 关闭一个会话。
     *
     * <p>关掉最后一个时会自动新建一个：否则终端变成一块什么都没有的黑框，
     * 用户还得自己去找「新建会话」——而他在这一步的意图明显是"继续用终端"。
     */
    public void closeSession(int index) {
        if (index < 0 || index >= sessions.size()) return;
        try {
            sessions.get(index).finishIfRunning();
        } catch (Throwable ignored) {
            // 会话可能已经自己退出了，重复 finish 抛异常不代表这次操作失败。
        }
        sessions.remove(index);
        if (sessions.isEmpty()) {
            selected = -1;
            removeAllViews();
            terminalView = null;
            newSession();
        } else {
            selectSession(Math.min(index, sessions.size() - 1));
        }
        notifyStateChanged();
    }

    /**
     * 给会话改名。
     *
     * @return 是否真的改了（空名字会被拒绝，与原实现一致）
     */
    public boolean renameSession(int index, String name) {
        if (index < 0 || index >= sessions.size()) return false;
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return false;
        sessions.get(index).mSessionName = trimmed;
        notifyStateChanged();
        return true;
    }

    public void resetTerminal() {
        TerminalSession session = current();
        if (session != null) session.reset();
    }

    /** 杀掉当前 shell 进程（会话本身留在列表里，显示为已停止）。 */
    public void killShell() {
        TerminalSession session = current();
        if (session != null) session.finishIfRunning();
    }

    public void changeFont(int delta) {
        terminalTextSp = Math.max(MIN_TEXT_SP, Math.min(MAX_TEXT_SP, terminalTextSp + delta));
        if (terminalView != null) terminalView.setTextSize(spPx(terminalTextSp));
        notifyStateChanged();
    }

    /** 把一个锁定的修饰键置反。 */
    private void toggleLatch(String upper) {
        switch (upper) {
            case "CTRL": ctrl = !ctrl; break;
            case "ALT": alt = !alt; break;
            case "SHIFT": shift = !shift; break;
            case "FN": fn = !fn; break;
            default: return;
        }
        notifyStateChanged();
    }

    public void reloadProperties() {
        backMapsEscape = false;
        String back = readTermuxProperty("back-key");
        if (back != null) backMapsEscape = "escape".equalsIgnoreCase(back.trim());
        String matrix = readTermuxProperty("extra-keys");
        parseExtraKeys(matrix == null || matrix.trim().isEmpty() ? DEFAULT_EXTRA_KEYS : matrix);
        notifyStateChanged();
    }

    /**
     * 按下扩展键。
     *
     * @param raw 键的**动作名**（不是显示名）；含空白时按宏处理，逐个递归发出去
     */
    public void sendExtraKey(String raw) {
        if (raw == null) return;
        String key = raw.trim();
        String upper = key.toUpperCase();

        // 宏：`extra-keys` 里可以写 "CTRL c" 这种一串动作，按空白切开逐个执行。
        if (upper.contains(" ")) {
            for (String token : key.split("\\s+")) sendExtraKey(token);
            return;
        }
        switch (upper) {
            case "CTRL":
            case "ALT":
            case "SHIFT":
            case "FN":
                toggleLatch(upper);
                return;
            case "KEYBOARD":
                toggleKeyboard();
                return;
            case "DRAWER":
                // 抽屉现在由 Compose 画，动作也从 Compose 直接触发；
                // 属性文件里写了 DRAWER 就当没写，不能让它悄悄什么都不做。
                Log.w(LOG_TAG, "extra-keys 里的 DRAWER 已由 Compose 接管，这里不再处理");
                return;
            case "ESC": write("\u001b"); return;
            case "TAB": write("\t"); return;
            case "ENTER": write("\r"); return;
            case "BKSP":
            case "BACKSPACE": write("\u007f"); return;
            case "HOME": write("\u001b[H"); return;
            case "END": write("\u001b[F"); return;
            case "PGUP": write("\u001b[5~"); return;
            case "PGDN": write("\u001b[6~"); return;
            case "LEFT": write("\u001b[D"); return;
            case "RIGHT": write("\u001b[C"); return;
            case "UP": write("\u001b[A"); return;
            case "DOWN": write("\u001b[B"); return;
            default: write(key);
        }
    }

    public void showKeyboard() {
        if (terminalView == null) return;
        terminalView.requestFocus();
        InputMethodManager imm =
            (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT);
    }

    public void toggleKeyboard() {
        if (terminalView == null) return;
        terminalView.requestFocus();
        InputMethodManager imm =
            (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
    }

    /** 复制终端的当前选区。返回给用户看的提示，没选到东西时返回 null。 */
    public String copySelection() {
        if (terminalView == null) return null;
        String text = terminalView.getSelectedText();
        if (text == null || text.isEmpty()) return null;
        clipboard().setPrimaryClip(ClipData.newPlainText("terminal", text));
        return "Copied";
    }

    /** 把剪贴板内容写进当前会话。 */
    public void pasteFromClipboard() {
        ClipboardManager manager = clipboard();
        if (!manager.hasPrimaryClip() || manager.getPrimaryClip() == null) return;
        if (manager.getPrimaryClip().getItemCount() <= 0) return;
        CharSequence text = manager.getPrimaryClip().getItemAt(0).coerceToText(getContext());
        TerminalSession session = current();
        if (text == null || session == null) return;
        byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
        session.write(bytes, 0, bytes.length);
    }

    /**
     * 置反 WakeLock。
     *
     * @return 给用户看的提示（成功与失败都有）；拿不到 PowerManager 时返回 null
     */
    public String toggleWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                notifyStateChanged();
                return "Wake lock released";
            }
            PowerManager manager =
                (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
            if (manager == null) return null;
            wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                TermuxConstants.BRAND_SLUG + WAKELOCK_SUFFIX);
            wakeLock.acquire();
            notifyStateChanged();
            return "Wake lock acquired";
        } catch (Throwable error) {
            return "Wake lock: " + error.getMessage();
        }
    }

    /** 释放所有会话与 WakeLock。Activity 真正销毁时调用。 */
    public void closeAll() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Throwable ignored) {
            // WakeLock 可能已经被系统回收；这里不应该阻止会话清理。
        }
        for (TerminalSession session : new ArrayList<>(sessions)) {
            try {
                session.finishIfRunning();
            } catch (Throwable ignored) {
                // 同上：清理阶段不因为单个会话失败而中断。
            }
        }
        sessions.clear();
        selected = -1;
        removeAllViews();
        terminalView = null;
        notifyStateChanged();
    }

    // ------------------------------------------------------------------ 内部：通知与失败

    private void showRuntimeNotice() {
        runtimeNotice = RUNTIME_NOTICE;
        failureDetail = null;
        removeAllViews();
        terminalView = null;
        notifyStateChanged();
    }

    private void showTerminalFailure(Throwable error) {
        failureDetail = terminalErrorDetail(error);
        runtimeNotice = null;
        removeAllViews();
        terminalView = null;
        notifyStateChanged();
    }

    /**
     * 失败详情：cause 链（最多 5 层）+ 加载器自己的错误。
     *
     * <p>只返回详情：标题与脚注是 [FAILURE_TITLE] / [FAILURE_FOOTNOTE]，
     * 三者样式不同（标题粗体、详情等宽且可选中），拼成一个串会丢掉这个区别。
     */
    private String terminalErrorDetail(Throwable error) {
        if (error == null) return FAILURE_UNKNOWN;
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 5) {
            if (builder.length() > 0) builder.append("\ncaused by: ");
            builder.append(current.getClass().getSimpleName())
                .append(": ")
                .append(String.valueOf(current.getMessage()));
            current = current.getCause();
        }
        Throwable loadError = JNI.getLoadError();
        if (loadError != null && loadError != error) {
            builder.append("\nloader: ")
                .append(loadError.getClass().getSimpleName())
                .append(": ")
                .append(String.valueOf(loadError.getMessage()));
        }
        return builder.toString();
    }

    /** 标题栏要显示的文字：优先 shell 报的标题，退回会话名，再退回 "Terminal"。 */
    private String currentTitle() {
        TerminalSession session = current();
        if (session == null) return TERMINAL_TITLE_FALLBACK;
        String title = session.getTitle();
        if (title == null || title.trim().isEmpty()) title = session.mSessionName;
        if (title == null || title.trim().isEmpty()) return TERMINAL_TITLE_FALLBACK;
        return title;
    }

    // ------------------------------------------------------------------ 内部：PTY 与运行环境

    private TerminalSession current() {
        return selected >= 0 && selected < sessions.size() ? sessions.get(selected) : null;
    }

    private Throwable preloadNativePty() {
        try {
            if (JNI.isLoaded()) return null;
            String dir = getContext().getApplicationInfo().nativeLibraryDir;
            File library = new File(dir, "libtermux.so");
            if (!library.isFile()) {
                throw new UnsatisfiedLinkError(
                    "libtermux.so is missing from nativeLibraryDir: " + library);
            }
            JNI.load(library.getAbsolutePath());
            return null;
        } catch (Throwable error) {
            Log.e(LOG_TAG, "Could not load libtermux.so", error);
            return error;
        }
    }

    private String[] environmentArray() {
        SandboxShell.ensureCliInstalled(getContext());
        Map<String, String> env = new LinkedHashMap<>();
        String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
        env.put("HOME", TermuxConstants.TERMUX_HOME_DIR_PATH);
        env.put("PREFIX", prefix);
        env.put("TMPDIR", prefix + "/tmp");
        env.put("PATH", prefix + "/bin");
        // 终端 PTY 直接 exec <prefix>/bin/bash（经 libtermux.so），不走 TermuxShellExecutor，
        // 所以这条路径必须**单独**设置 LD_LIBRARY_PATH。
        // 内置 Termux 的 ELF 不会被改写前缀（见 RuntimeInstaller），它们内嵌的
        // DT_RUNPATH 指向不存在的 /data/data/com.termux/files/usr/lib，
        // 少了这个变量终端里所有二进制都会 CANNOT LINK EXECUTABLE。
        env.put("LD_LIBRARY_PATH", prefix + "/lib");
        env.put("SHELL", prefix + "/bin/bash");
        env.put("TERM", "xterm-256color");
        env.put("COLORTERM", "truecolor");
        env.put("LANG", "en_US.UTF-8");
        env.put("TERMUX_VERSION", "0.118.3");
        env.put("TERMUX_APP__PACKAGE_NAME", getContext().getPackageName());
        env.put("TERMUX_APP__PACKAGE_MANAGER", "apt");
        env.put("TERMUX_APP__PACKAGE_VARIANT", "apt-android-7");
        env.put("TERMUX_APP__FILES_DIR", TermuxConstants.TERMUX_FILES_DIR_PATH);
        env.put("TERMUX_APP__DATA_DIR", TermuxConstants.TERMUX_DATA_DIR_PATH);
        env.put("TERMUX_APP__LEGACY_DATA_DIR", TermuxConstants.TERMUX_DATA_DIR_PATH);
        env.put("TERMUX_APP__PID", Integer.toString(android.os.Process.myPid()));
        env.put("TERMUX_APP__UID", Integer.toString(android.os.Process.myUid()));
        env.put("TERMUX_APP__TARGET_SDK", "28");
        env.put("TERMUX_MAIN_PACKAGE_FORMAT", "debian");
        env.put("TERMUX_PKG_NO_MIRROR_SELECT", "1");
        env.put("TERMUX_APK_RELEASE", "ZHICODE");
        env.put("TERMUX__HOME", TermuxConstants.TERMUX_HOME_DIR_PATH);
        env.put("TERMUX__PREFIX", prefix);
        env.put("TERMUX__ROOTFS_DIR", TermuxConstants.TERMUX_FILES_DIR_PATH);
        env.put("TERMUX__ROOTFS", TermuxConstants.TERMUX_FILES_DIR_PATH);
        env.put("ZHICODE_APP", "1");
        env.put("ZHICODE_TERMINAL_SESSION", "1");
        env.put("ZHICODE_SANDBOX_BRIDGE_DIR", SandboxShell.bridgeDir(getContext()));
        env.put("ZHICODE_APK_PATH", getContext().getApplicationInfo().sourceDir);

        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            out.add(entry.getKey() + "=" + entry.getValue());
        }
        return out.toArray(new String[0]);
    }

    private void write(String text) {
        TerminalSession session = current();
        if (session == null) return;
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        session.write(bytes, 0, bytes.length);
        // 修饰键是"按一次管一个键"，发完就清 —— TerminalView 也是这么读的
        // （readControlKey 等方法的读后即清语义）。
        ctrl = alt = shift = fn = false;
        notifyStateChanged();
    }

    private ClipboardManager clipboard() {
        return (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
    }

    private int spPx(float sp) {
        return (int) (sp * getResources().getDisplayMetrics().scaledDensity + 0.5f);
    }

    // ------------------------------------------------------------------ 内部：属性与扩展键

    private void parseExtraKeys(String source) {
        try {
            // 属性文件里的矩阵是 JS 风格（键名不加引号），先补上引号再交给 JSON。
            String normalized = source.replace('\'', '"');
            Pattern pattern = Pattern.compile("([\\{,]\\s*)(key|popup|macro|display)\\s*:");
            Matcher matcher = pattern.matcher(normalized);
            normalized = matcher.replaceAll("$1\"$2\":");

            JSONArray matrix = new JSONArray(normalized);
            List<List<ExtraKey>> rows = new ArrayList<>();
            for (int i = 0; i < matrix.length(); i++) {
                JSONArray row = matrix.getJSONArray(i);
                List<ExtraKey> keys = new ArrayList<>();
                for (int j = 0; j < row.length(); j++) {
                    Object item = row.get(j);
                    String action;
                    String display;
                    if (item instanceof JSONObject) {
                        JSONObject object = (JSONObject) item;
                        action = object.optString("key", object.optString("macro", ""));
                        display = object.optString("display", displayName(action));
                    } else {
                        action = String.valueOf(item);
                        display = displayName(action);
                    }
                    keys.add(new ExtraKey(display, action));
                }
                rows.add(Collections.unmodifiableList(keys));
            }
            extraKeys = rows.isEmpty() ? defaultExtraKeys() : Collections.unmodifiableList(rows);
        } catch (Exception parseError) {
            // 属性文件写坏了就退回默认布局：终端必须始终可用，
            // 不能因为一个配置文件让扩展键整行消失。
            extraKeys = defaultExtraKeys();
        }
    }

    private List<List<ExtraKey>> defaultExtraKeys() {
        List<List<ExtraKey>> rows = new ArrayList<>();
        rows.add(expandDefaultRow(DEFAULT_ROW_TOP));
        rows.add(expandDefaultRow(DEFAULT_ROW_BOTTOM));
        return Collections.unmodifiableList(rows);
    }

    private List<ExtraKey> expandDefaultRow(String[] row) {
        List<ExtraKey> keys = new ArrayList<>(row.length);
        for (String key : row) keys.add(new ExtraKey(displayName(key), key));
        return Collections.unmodifiableList(keys);
    }

    /** 方向键在界面上显示成箭头，其余显示原名。 */
    private String displayName(String key) {
        if (key == null) return "";
        switch (key.toUpperCase()) {
            case "LEFT": return "←";
            case "RIGHT": return "→";
            case "UP": return "↑";
            case "DOWN": return "↓";
            default: return key;
        }
    }

    /**
     * 读一行 `~/.termux/termux.properties`。
     *
     * <p>要处理**续行**（行尾 `\`）：属性值里允许把长矩阵折成多行，
     * 只按行切会把 JSON 截断，表现为"扩展键恢复成了默认布局"。
     */
    private String readTermuxProperty(String wanted) {
        File file = new File(TermuxConstants.TERMUX_HOME_DIR_PATH + PROPERTIES_FILE);
        if (!file.isFile()) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            StringBuilder pending = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#") || trimmed.isEmpty()) continue;
                pending.append(pending.length() > 0 ? trimmed : line);
                if (trimmed.endsWith("\\")) {
                    pending.setLength(pending.length() - 1);
                    continue;
                }
                String full = pending.toString();
                pending.setLength(0);
                int equals = full.indexOf('=');
                if (equals < 0) continue;
                if (wanted.equals(full.substring(0, equals).trim())) {
                    return full.substring(equals + 1).trim();
                }
            }
        } catch (Exception ignored) {
            // 读不了就当作没配置：调用方会退回默认值。
        }
        return null;
    }

    // ------------------------------------------------------------------ 调色板

    /**
     * 把 ANSI 调色板写进模拟器。
     *
     * <p>只走深色档：浅色档那张表是历史上写下的，但唯一入口 `applyTheme` 没有任何调用方，
     * 也就是说**终端从来没有跟随过应用主题**。本类保持这个行为不变 ——
     * 让终端跟随主题是一次可见的外观变更，应该单独做，不该夹在结构重写里。
     */
    private void applyTerminalPalette(TerminalSession session) {
        if (session == null || session.getEmulator() == null) return;
        int[] colors = session.getEmulator().mColors.mCurrentColors;
        colors[0] = Color.rgb(0, 0, 0);
        colors[1] = Color.rgb(205, 0, 0);
        colors[2] = Color.rgb(0, 205, 0);
        colors[3] = Color.rgb(205, 205, 0);
        colors[4] = Color.rgb(100, 149, 237);
        colors[5] = Color.rgb(205, 0, 205);
        colors[6] = Color.rgb(0, 205, 205);
        colors[7] = Color.rgb(238, 238, 238);
        colors[8] = Color.rgb(158, 158, 158);
        colors[9] = Color.rgb(214, 120, 111);
        colors[10] = Color.rgb(0, 255, 0);
        colors[11] = Color.rgb(255, 255, 0);
        colors[12] = Color.rgb(92, 92, 255);
        colors[13] = Color.rgb(255, 0, 255);
        colors[14] = Color.rgb(0, 255, 255);
        colors[15] = Color.WHITE;
        colors[TextStyle.COLOR_INDEX_FOREGROUND] = Color.rgb(238, 238, 238);
        colors[TextStyle.COLOR_INDEX_BACKGROUND] = Color.rgb(0, 0, 0);
        colors[TextStyle.COLOR_INDEX_CURSOR] = Color.WHITE;
    }

    // ------------------------------------------------------------------ TerminalSessionClient

    @Override
    public void onTextChanged(@NonNull TerminalSession session) {
        // 高频回调：只让 TerminalView 重画，**不**通知观察者（见类注释）。
        if (session == current() && terminalView != null) terminalView.onScreenUpdated();
    }

    @Override
    public void onTitleChanged(@NonNull TerminalSession session) {
        if (session == current()) notifyStateChanged();
    }

    @Override
    public void onSessionFinished(@NonNull TerminalSession session) {
        notifyStateChanged();
    }

    @Override
    public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
        clipboard().setPrimaryClip(ClipData.newPlainText("terminal", text));
    }

    @Override
    public void onPasteTextFromClipboard(@Nullable TerminalSession session) {
        if (session == null) return;
        ClipboardManager manager = clipboard();
        if (!manager.hasPrimaryClip() || manager.getPrimaryClip() == null) return;
        if (manager.getPrimaryClip().getItemCount() <= 0) return;
        CharSequence text = manager.getPrimaryClip().getItemAt(0).coerceToText(getContext());
        if (text == null) return;
        byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
        session.write(bytes, 0, bytes.length);
    }

    @Override
    public void onBell(@NonNull TerminalSession session) {
        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
    }

    @Override
    public void onColorsChanged(@NonNull TerminalSession session) {
        if (terminalView != null) terminalView.invalidate();
    }

    @Override
    public void onTerminalCursorStateChange(boolean state) {
        if (terminalView != null) terminalView.invalidate();
    }

    @Override
    public void setTerminalShellPid(@NonNull TerminalSession session, int pid) {
        // 不需要：PTY 就活在本进程里。
    }

    @Override
    public Integer getTerminalCursorStyle() {
        return null;
    }

    // ------------------------------------------------------------------ TerminalViewClient

    @Override
    public float onScale(float scale) {
        if (scale > 1.04f) {
            changeFont(1);
        } else if (scale < 0.96f) {
            changeFont(-1);
        }
        return 1f;
    }

    @Override
    public void onSingleTapUp(MotionEvent event) {
        showKeyboard();
    }

    @Override
    public boolean shouldBackButtonBeMappedToEscape() {
        return backMapsEscape;
    }

    @Override
    public boolean shouldEnforceCharBasedInput() {
        return true;
    }

    @Override
    public boolean shouldUseCtrlSpaceWorkaround() {
        return false;
    }

    @Override
    public boolean isTerminalViewSelected() {
        return terminalView != null && terminalView.hasFocus();
    }

    @Override
    public void copyModeChanged(boolean copyMode) {
        // 选区状态由 TerminalView 自己管，界面不需要跟着变。
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event, TerminalSession session) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            ctrl = true;
            notifyStateChanged();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            fn = true;
            notifyStateChanged();
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            ctrl = false;
            notifyStateChanged();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            fn = false;
            notifyStateChanged();
            return true;
        }
        return false;
    }

    @Override
    public boolean onLongPress(MotionEvent event) {
        return false;
    }

    @Override
    public boolean readControlKey() {
        boolean value = ctrl;
        ctrl = false;
        // 被 TerminalView 读走就等于这个锁定键用掉了：界面上的高亮必须跟着落下，
        // 否则会停在上一次按下时的"已激活"颜色上。
        if (value) notifyStateChanged();
        return value;
    }

    @Override
    public boolean readAltKey() {
        boolean value = alt;
        alt = false;
        if (value) notifyStateChanged();
        return value;
    }

    @Override
    public boolean readShiftKey() {
        boolean value = shift;
        shift = false;
        if (value) notifyStateChanged();
        return value;
    }

    @Override
    public boolean readFnKey() {
        boolean value = fn;
        fn = false;
        if (value) notifyStateChanged();
        return value;
    }

    @Override
    public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) {
        return false;
    }

    @Override
    public void onEmulatorSet() {
        // 不需要：调色板在 attach 之后由 applyTerminalPalette 直接写入。
    }

    // ------------------------------------------------------------------ 日志

    @Override
    public void logError(String tag, String message) {
        Log.e(tag, message);
    }

    @Override
    public void logWarn(String tag, String message) {
        Log.w(tag, message);
    }

    @Override
    public void logInfo(String tag, String message) {
        Log.i(tag, message);
    }

    @Override
    public void logDebug(String tag, String message) {
        Log.d(tag, message);
    }

    @Override
    public void logVerbose(String tag, String message) {
        Log.v(tag, message);
    }

    @Override
    public void logStackTraceWithMessage(String tag, String message, Exception error) {
        Log.e(tag, message, error);
    }

    @Override
    public void logStackTrace(String tag, Exception error) {
        Log.e(tag, "terminal", error);
    }
}
