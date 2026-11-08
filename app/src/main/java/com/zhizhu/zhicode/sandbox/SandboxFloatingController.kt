package com.zhizhu.zhicode.sandbox

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.data.ThemeSettings
import com.zhizhu.zhicode.compose.theme.ZhiAppTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 沙箱悬浮控制条。
 *
 * ## 它为什么必须存在
 *
 * guest 全屏之后返回键归它自己所有 —— 没有这条浮层，用户没有任何办法退出或
 * 停止一个卡死的虚拟应用。所以它不是"调试附件"，而是**逃生通道**：
 * 它不能被任何窗口盖住、不能拖出屏幕、位置不能每次都跳。
 *
 * ## 四个设计决定
 *
 * ### 1. 系统级窗口（`TYPE_APPLICATION_OVERLAY`）
 *
 * 早先它加在 guest Activity 的 DecoView 上。那条路有**结构性**上限：
 * 只要 guest 弹出任何一个独立 Window（`WindowDialog`、系统权限框、输入法候选），
 * 它就在这个 Activity 窗口之上，浮层被整块盖住 —— 用户弹出确认框之后找不到"返回"。
 * 而 `WindowDialog` 恰恰是本应用最常用的弹窗形态。所以必须提升到窗口层级。
 *
 * ### 2. 用 Compose 画，而不是手工拼 View
 *
 * 第一版是 `LinearLayout` + `TextView` + 手写的 `RippleDrawable` /
 * `GradientDrawable`。它的问题是**每一处颜色、圆角、时长都得手抄一遍主题值**，
 * 而抄错不会报错，只会看起来"不太对"：
 *  · 深浅色读的是 `iqge_ui_preferences/ui_theme` —— 那两个键在本工程里
 *    根本不存在，判断恒假，于是浅色主题下它永远是一块深色；
 *  · 强调色写死成两串绿，与用户选的种子色无关。
 *
 * 现在它是普通的 `@Composable`，直接读 `MiuixTheme` —— 与主界面同一套色板、
 * 同一套字阶、同一套圆角。主题换一下（预设色 / Monet / 深浅）它自动跟上。
 *
 * ### 3. 位置只有**一个**真实来源
 *
 * 这是上一版拖动"有问题"的根因：拖动时改 `translationX/Y`（View 相对位移），
 * 而保存/恢复时读的是 `lp.x/y`（窗口的绝对位置）。两个坐标系，改一个读另一个 ——
 * 松手后落点会跳、拖动时手感发飘。
 *
 * 现在统一用 `lp.x / lp.y`：拖动只改它，读也只读它。
 * `translation` 全程保持 0，避免两套位移叠在一起。
 *
 * ### 4. 随 guest 进出自动收放
 *
 * 系统级窗口**不归任何 Activity 管**：不主动摘的话，用户切到别的应用时会看到
 * 一条"日志/返回/停止"压在上面。所以 [detach] 必须挂在 guest Activity 离开前台
 * 的那一刻（见 `ZhiSandboxEngine` 的 `onActivityPaused`）。
 */
object SandboxFloatingController {

    /** 宿主与各 guest 进程各持一份。 */
    private var host: ComposeView? = null
    private var owner: SandboxOverlayOwner? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    /** 位置存成屏幕比例（0..1）：换分辨率或旋转后仍落在同一个角落。 */
    private const val PREFS = "sandbox_floating"
    private const val KEY_X = "x_ratio"
    private const val KEY_Y = "y_ratio"

    /** 边缘留白。贴到 0 会让圆角压在屏幕弧边上，看着像被裁掉了。 */
    private const val EDGE_DP = 8

    /** 展开 / 收起动画的时长与曲线（与 Miuix 弹层同一档：220ms 进入、140ms 退出）。 */
    private val MiuixEasing = androidx.compose.animation.core.FastOutSlowInEasing

    /** 展开时长 / 收起时长。收起快一点：用户点了"收起"是想马上让开视线。 */
    private const val ENTER_MS = 220
    private const val EXIT_MS = 140

    /** 收起态只有一个圆点；展开态要放得下「日志 / 返回 / 停止」。 */
    private val COLLAPSED_WIDTH = 44.dp
    private val EXPANDED_WIDTH = 210.dp
    private val BarHeight = 38.dp
    private val BarShape = RoundedCornerShape(19.dp)

    /**
     * 底板的透明度。
     *
     * 它压在**别人的应用**上面，完全不透明就是一块挡内容的实心板子。
     * 0.86 是权衡：再低会让上面的字在亮色内容上读不清。
     */
    private const val BarAlpha = 0.86f

    /** 日志面板的透明度。比把手稍透，因为它的面积大得多。 */
    private const val PanelAlpha = 0.92f

    /** 现在挂上了吗。截图前要把它藏起来，否则会被拍进去。 */
    @JvmStatic
    fun isAttached(): Boolean = host != null

    @JvmStatic
    fun attachedView(): View? = host

    /**
     * 挂上悬浮条。
     *
     * <p>已挂着就直接返回：`attach` 会被**每个** `onActivityResumed` 调用，
     * 每次重建的话用户每换一个 guest 页面都会重播一遍入场动画。
     */
    @JvmStatic
    fun attach(activity: Activity?, pkg: String) {
        if (activity == null || activity.isFinishing()) return
        val app = activity.applicationContext
        activity.runOnUiThread {
            if (host != null && windowManager != null) return@runOnUiThread
            runCatching { show(app, pkg) }
                .onFailure { SandboxDebugLog.event("悬浮条挂载失败: $it") }
        }
    }

    private fun show(app: Context, pkg: String) {
        /*
         * ⚠️ 不能在这里 `ThemeStore.read(app)`。
         *
         * 这段代码跑在 **guest 进程**里，而 guest 的 `getSharedPreferences` 被
         * BlackBox 虚拟化 —— 读宿主写的 `zhi_theme` 只会拿到一套默认值，
         * 于是"用户选浅色、悬浮条仍是深色"这个老问题原样回来。
         *
         * 所以深浅由**宿主**在 `onResume` 时写进沙箱设置（同 UID 的真实文件），
         * 这里读那一份。见 SandboxSettingsStore.publishThemeDark。
         */
        val dark = SandboxSettingsStore.themeDark(
            app,
            (app.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES,
        )
        val theme = ThemeSettings(
            mode = if (dark) com.zhizhu.zhicode.compose.model.ThemeMode.DARK
            else com.zhizhu.zhicode.compose.model.ThemeMode.LIGHT,
        )
        val view = ComposeView(app)
        val lifecycleOwner = SandboxOverlayOwner(app)
        lifecycleOwner.attachTo(view)
        view.setContent {
            ZhiAppTheme(theme = theme) {
                FloatingBar(pkg = pkg)
            }
        }

        /*
         * **不**用 `Settings.canDrawOverlays()` 预判，而是直接试。
         *
         * 这段代码跑在 **guest 进程**里，而 guest 的包名被 BlackBox 虚拟化过 ——
         * `canDrawOverlays` 内部拿 `getOpPackageName()` 去问 AppOpsManager，
         * 在虚拟包名下可能返回 false，即使宿主（`com.zhizhu.code`，同 UID）
         * 确实已授权。而"真的去 addView"不会被吓住：有权限就成功，没有就抛异常。
         */
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val density = app.resources.displayMetrics.density
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_FOCUSABLE：不抢输入焦点，guest 的输入框仍能正常弹键盘。
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (EDGE_DP * density).toInt()
            y = (36 * density).toInt()
        }
        wm.addView(view, lp)

        host = view
        owner = lifecycleOwner
        windowManager = wm
        layoutParams = lp
        SandboxDebugLog.event("悬浮条已挂上（Compose / 系统级）: $pkg")
    }

    /**
     * 摘掉悬浮条。
     *
     * <p>⚠️ 系统级窗口不归任何 Activity 管。这一步不做的话，
     * guest 退到后台之后浮层**仍然盖在别的应用上** —— 用户切到微信会看到
     * 一条"日志/返回/停止"压在最上面。必须在 guest 离开前台时摘。
     */
    @JvmStatic
    fun detach(activity: Activity?) {
        val wm = windowManager
        val view = host
        val lifecycleOwner = owner
        host = null
        owner = null
        windowManager = null
        layoutParams = null
        if (view == null || wm == null) return
        val remove = Runnable {
            runCatching { wm.removeViewImmediate(view) }
                .onFailure { SandboxDebugLog.event("悬浮条摘除失败: $it") }
            // DESTROYED 才会让 Compose 跑 onDispose，把订阅与协程收干净。
            lifecycleOwner?.destroy()
        }
        if (activity != null) activity.runOnUiThread(remove)
        else Handler(Looper.getMainLooper()).post(remove)
    }

    /**
     * 拖动增量，**按帧合并后**才提交给窗口管理器。
     *
     * <h3>为什么必须合并</h3>
     *
     * `WindowManager.updateViewLayout` 是一次跨进程窗口事务（要过
     * WindowManagerService，含一次 binder 往返与 WMS 侧的重新布局）。
     * 触摸事件是按**扫描率**来的 —— 好一点的屏幕 120~240Hz，远高于刷新率。
     * 每个事件都提交一次的话，主线程一秒要打上百次窗口事务，
     * 结果就是浮层永远落后手指几帧：用户的原话是"比手慢"。
     *
     * 所以这里把增量累加，用 `postOnAnimation` 合并到**每帧一次**。
     * 一帧一次的窗口事务与屏幕刷新同频，位移就不会再欠账。
     *
     * <p>注意累加必须在 `Float` 上做、到最后才取整：`lp.x` 是整数像素，
     * 单次 0.4px 的增量直接取整会变成 0，慢慢拖就完全不动。
     */
    private var pendingDx = 0f
    private var pendingDy = 0f
    private var frameScheduled = false

    private fun queueMove(app: Context, dx: Float, dy: Float) {
        pendingDx += dx
        pendingDy += dy
        if (frameScheduled) return
        val view = host ?: return
        frameScheduled = true
        view.postOnAnimation {
            frameScheduled = false
            val ddx = pendingDx
            val ddy = pendingDy
            pendingDx = 0f
            pendingDy = 0f
            if (ddx != 0f || ddy != 0f) nudge(app, ddx, ddy)
        }
    }

    /**
     * 把窗口**相对**挪一段并夹进屏幕。`lp.x/y` 是位置的唯一真值。
     *
     * <p>尺寸直接读 `host`，不从参数传：窗口是 `WRAP_CONTENT`，
     * 展开/收起日志面板时它自己会变，调用方缓存的那份立刻就过期了。
     */
    private fun nudge(app: Context, dx: Float, dy: Float) {
        val wm = windowManager ?: return
        val lp = layoutParams ?: return
        val view = host ?: return
        val metrics = app.resources.displayMetrics
        val edge = (EDGE_DP * metrics.density).toInt()
        val maxX = (metrics.widthPixels - view.width - edge).coerceAtLeast(edge)
        val maxY = (metrics.heightPixels - view.height - edge).coerceAtLeast(edge)
        lp.x = (lp.x + dx.toInt()).coerceIn(edge, maxX)
        lp.y = (lp.y + dy.toInt()).coerceIn(edge, maxY)
        runCatching { wm.updateViewLayout(view, lp) }
        rememberPosition(app, lp, metrics)
    }

    /** 按当前 `lp` 与当前窗口尺寸重新夹一次（展开变宽后防它顶出屏幕）。 */
    private fun clampCurrent(app: Context) {
        val wm = windowManager ?: return
        val lp = layoutParams ?: return
        val view = host ?: return
        val metrics = app.resources.displayMetrics
        val edge = (EDGE_DP * metrics.density).toInt()
        val maxX = (metrics.widthPixels - view.width - edge).coerceAtLeast(edge)
        val maxY = (metrics.heightPixels - view.height - edge).coerceAtLeast(edge)
        val nx = lp.x.coerceIn(edge, maxX)
        val ny = lp.y.coerceIn(edge, maxY)
        if (nx == lp.x && ny == lp.y) return
        lp.x = nx
        lp.y = ny
        runCatching { wm.updateViewLayout(view, lp) }
        rememberPosition(app, lp, metrics)
    }

    /** 绝对值定位（首帧还原上次落点用）。 */
    private fun placeWindow(app: Context, x: Float, y: Float) {
        val wm = windowManager ?: return
        val lp = layoutParams ?: return
        val view = host ?: return
        val metrics = app.resources.displayMetrics
        val edge = (EDGE_DP * metrics.density).toInt()
        val maxX = (metrics.widthPixels - view.width - edge).coerceAtLeast(edge)
        val maxY = (metrics.heightPixels - view.height - edge).coerceAtLeast(edge)
        lp.x = x.toInt().coerceIn(edge, maxX)
        lp.y = y.toInt().coerceIn(edge, maxY)
        runCatching { wm.updateViewLayout(view, lp) }
        rememberPosition(app, lp, metrics)
    }

    /** 位置存成屏幕比例：换分辨率或旋转后仍落在同一个角落。 */
    private fun rememberPosition(
        app: Context,
        lp: WindowManager.LayoutParams,
        metrics: android.util.DisplayMetrics,
    ) {
        prefs(app).edit()
            .putFloat(KEY_X, lp.x.toFloat() / metrics.widthPixels)
            .putFloat(KEY_Y, lp.y.toFloat() / metrics.heightPixels)
            .apply()
    }

    fun readXRatio(app: Context): Float = prefs(app).getFloat(KEY_X, Float.NaN)
    fun readYRatio(app: Context): Float = prefs(app).getFloat(KEY_Y, Float.NaN)

    private fun openIQ(app: Context) {
        val intent = Intent(app, com.zhizhu.zhicode.compose.MainActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    or Intent.FLAG_ACTIVITY_NEW_TASK
            )
            .putExtra("iq_sandbox_return", true)
        runCatching { app.startActivity(intent) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ─────────────────────────── 界面 ───────────────────────────

    /**
     * 悬浮条本体。
     *
     * 形态：一个可拖动的胶囊。收起态只有一个「IQ」圆点；点一下**动画**展开成
     * 「日志 / 返回 / 停止」，再点回到圆点。日志面板在胶囊下方就地展开。
     *
     * ## 为什么半透明
     *
     * 这块浮层压在**别的应用**上面。完全不透明时它就是一块挡内容的实心板子
     * （用户截图里那条横在黑底应用上的长条）。用 `surfaceContainerHigh` 的
     * alpha 版本：仍能看清上面的字，底下的内容也漏得出来。
     *
     * ## 为什么拖动要自己合并增量
     *
     * `detectDragGestures` 的回调频率跟着**触摸扫描率**（120~240Hz），
     * 而每次落位都是一次跨进程窗口事务。逐事件提交的话浮层永远落后手指几帧 ——
     * 用户的原话是"比手慢"。所以交给 [queueMove] 合并到每帧一次。
     */
    @Composable
    private fun FloatingBar(pkg: String) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val scheme = MiuixTheme.colorScheme
        var expanded by remember { mutableStateOf(false) }
        var logText by remember { mutableStateOf<String?>(null) }
        var size by remember { mutableStateOf(IntSize.Zero) }
        var placed by remember { mutableStateOf(false) }

            /*
         * 首帧还原上次落点。要等尺寸量出来才能夹取。
         */
        LaunchedEffect(size) {
            if (size.width == 0 || size.height == 0) return@LaunchedEffect
            if (!placed) {
                placed = true
                val xr = readXRatio(context)
                val yr = readYRatio(context)
                if (!xr.isNaN() && !yr.isNaN()) {
                    val metrics = context.resources.displayMetrics
                    placeWindow(context, xr * metrics.widthPixels, yr * metrics.heightPixels)
                }
            } else {
                /*
                 * 展开时窗口会变宽。若它此刻贴着右边缘，变宽就会顶出屏幕 ——
                 * 而浮层可以贴着右边缘停（用户就是往那儿拖的）。
                 * 所以每次尺寸变化都按**当前位置**再夹一次。
                 */
                clampCurrent(context)
            }
        }

        val targetWidth = if (expanded) EXPANDED_WIDTH else COLLAPSED_WIDTH
        val animatedWidth by animateDpAsState(
            targetValue = targetWidth,
            animationSpec = tween(durationMillis = if (expanded) ENTER_MS else EXIT_MS, easing = MiuixEasing),
            label = "sandbox-bar-width",
        )

        Column(
            modifier = Modifier
                .onSizeChanged { size = it }
                .width(animatedWidth)
                .shadow(elevation = 6.dp, shape = BarShape)
                .clip(BarShape)
                .background(scheme.surfaceContainerHigh.copy(alpha = BarAlpha)),
        ) {
            // ── 把手行 ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BarHeight)
                    // 整行可拖：只让那个小圆圈可拖的话，手指稍微偏一点就点到了按钮。
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            queueMove(context, drag.x, drag.y)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(BarHeight)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            expanded = !expanded
                            if (!expanded) logText = null
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    /*
                     * 圆点里的图标跟着展开状态转 90°（横 → 竖）。
                     *
                     * 用旋转而不是换图标：换图标在展开动画进行到一半时太跳，
                     * 而旋转本身就把"点一下会变"这件事说清楚了。
                     */
                    val glyphRotation by animateFloatAsState(
                        targetValue = if (expanded) 90f else 0f,
                        animationSpec = tween(
                            durationMillis = if (expanded) ENTER_MS else EXIT_MS,
                            easing = MiuixEasing,
                        ),
                        label = "sandbox-bar-glyph",
                    )
                    Text(
                        text = if (expanded) "×" else "IQ",
                        color = scheme.onSurfaceContainerHigh,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.graphicsLayer { rotationZ = glyphRotation },
                    )
                }

                // 展开/收起都走动画：宽度由上面的 animateDpAsState 负责，
                // 这里管按钮组的淡入淡出与横向推挤，两者时长必须一致，
                // 否则会出现"框已经宽了但按钮还没出现"的空档。
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(ENTER_MS, easing = MiuixEasing)) +
                        expandHorizontally(
                            tween(ENTER_MS, easing = MiuixEasing),
                            expandFrom = Alignment.Start,
                        ),
                    exit = fadeOut(tween(EXIT_MS, easing = MiuixEasing)) +
                        shrinkHorizontally(
                            tween(EXIT_MS, easing = MiuixEasing),
                            shrinkTowards = Alignment.Start,
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(end = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BarAction("日志", primary = logText == null) {
                            logText = if (logText == null) "正在读取日志…" else null
                            if (logText != null) loadLog(context) { logText = it }
                        }
                        BarAction("返回") { openIQ(context) }
                        BarAction("停止", danger = true) {
                            Thread({
                                runCatching {
                                    ZhiSandboxEngine.stop(pkg)
                                    SandboxGuardService.stop(context)
                                }
                                Handler(Looper.getMainLooper()).post { openIQ(context) }
                            }, "iq-sandbox-stop").start()
                        }
                    }
                }
            }

            // ── 日志面板（就地展开，不另起窗口）────────────────────
            // 同样带展开/收起动画。纵向用它自己的 expandVertically ——
            // 面板是"从无到有"的一整块，和按钮组那种横向推挤不是一回事。
            AnimatedVisibility(
                visible = logText != null,
                enter = expandVertically(tween(ENTER_MS, easing = MiuixEasing), expandFrom = Alignment.Top) +
                    fadeIn(tween(ENTER_MS, easing = MiuixEasing)),
                exit = shrinkVertically(tween(EXIT_MS, easing = MiuixEasing), shrinkTowards = Alignment.Top) +
                    fadeOut(tween(EXIT_MS, easing = MiuixEasing)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.surfaceContainer.copy(alpha = PanelAlpha))
                        .verticalScroll(rememberScrollState()),
                ) {
                    SelectionContainer {
                        Text(
                            text = logText.orEmpty(),
                            color = scheme.onSurfaceContainer,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
    }

    private fun loadLog(context: Context, onResult: (String) -> Unit) {
        Thread({
            val value = runCatching {
                /*
                 * 有界快照 + **不带 logcat**。
                 *
                 * 全量 42 万字符塞进一个 300dp 宽的浮层里没有意义，而且会让
                 * Compose 每帧测量几十万个字形；而读 logcat 要起子进程等它
                 * dump 完 —— 那一项比读事件流慢一个数量级，是这个浮层卡的主因。
                 * 悬浮条要看的是"刚才那几步走到哪了"，事件流里就有。
                 */
                SandboxDebugLog.snapshot(context, 20_000, false)
            }.getOrElse { "读取日志失败: $it" }
            Handler(Looper.getMainLooper()).post { onResult(value) }
        }, "iq-sandbox-log").start()
    }

    @Composable
    private fun BarAction(
        label: String,
        primary: Boolean = false,
        danger: Boolean = false,
        onClick: () -> Unit,
    ) {
        val scheme = MiuixTheme.colorScheme
        val color = when {
            danger -> scheme.error
            primary -> scheme.primary
            else -> scheme.onSurfaceContainerHigh
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 9.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, color = color, fontSize = 11.sp)
        }
    }
}
