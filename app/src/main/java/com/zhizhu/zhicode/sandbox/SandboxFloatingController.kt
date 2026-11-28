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

    /** 底板的透明度。 */
    private const val BarAlpha = 0.86f

    /** 日志面板的透明度。比把手稍透，因为它的面积大得多。 */
    private const val PanelAlpha = 0.92f

    /** 现在挂上了吗。截图前要把它藏起来，否则会被拍进去。 */
    @JvmStatic
    fun isAttached(): Boolean = host != null

    @JvmStatic
    fun attachedView(): View? = host

    /** 挂上悬浮条。 */
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

        /* **不**用 `Settings.canDrawOverlays()` 预判，而是直接试。 */
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

    /** 把窗口**相对**挪一段并夹进屏幕。 */
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

    @Composable
    private fun FloatingBar(pkg: String) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val scheme = MiuixTheme.colorScheme
        var expanded by remember { mutableStateOf(false) }
        var logText by remember { mutableStateOf<String?>(null) }
        var size by remember { mutableStateOf(IntSize.Zero) }
        var placed by remember { mutableStateOf(false) }

            /* 首帧还原上次落点。 */
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
                /* 展开时窗口会变宽。 */
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
                    /* 圆点里的图标跟着展开状态转 90°（横 → 竖）。 */
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
                /* 有界快照 + **不带 logcat**。 */
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
