package com.zhizhu.zhicode.compose.ui.debug

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.zhizhu.zhicode.compose.BuildConfig

/**
 * 切换页面/页签时的帧耗时测量（**仅 debug 构建**）。
 *
 * ## 为什么要有它
 *
 * 用户反复报「切换页面的滞后感还是有」。此前两次改动都是"推理 → 改 → 宣布修好"，
 * 结果一次帮倒忙（把全量落盘挂到逐字符回调上，卡顿反而变多）。所以这一次的规矩是
 * **先拿到数，再动动画**。
 *
 * 本对象只做一件事：把"用户点了之后这 40 帧到底花了多少时间"记下来，落到 logcat
 * （tag `ZhiFrame`），这样能在沙箱/真机上直接读，不需要为了看数去改界面。
 *
 * ## 三项指标各自能区分什么
 *
 * | 指标 | 大意味着什么 |
 * |---|---|
 * | `tapToFirstFrame` | 点击到**第一帧**画面的间隔。偏大（> 1 帧）说明转场开始得晚 —— 例如导航栈要等一次协程派发才更新（`LaunchedEffect` reconcile） |
 * | `janks` / `max` | 转场过程中**单帧超时**的次数与最长帧。偏大说明那一帧在组合/测量/绘制上花了太多时间（重页面首帧、全屏裁剪+调暗） |
 * | `span` | 从点击到第 [TRACE_FRAMES] 帧的总时长。远超 `帧数 × 16.7ms` 说明动画本身太长（弹簧收敛慢、淡入 300ms 之类） |
 *
 * ## 为什么用 logcat 而不是显示在界面上
 *
 * 界面上再画一个数字，本身也要占帧 —— 测的东西会被测量者影响。logcat 与被测过程无关，
 * 而且沙箱里可以直接读。
 *
 * ## 为什么 40 帧
 *
 * 60Hz 下约 670ms，足够覆盖一次转场（Miuix 的页面转场约 300~500ms）又不会把
 * 测量窗口拉长到混入用户的下一个操作。
 */
internal object ZhiFrameTrace {

    private const val TAG = "ZhiFrame"

    /** 一次测量覆盖的帧数（60Hz 下约 670ms）。 */
    private const val TRACE_FRAMES = 40

    /**
     * 单帧超过 [JANK_FACTOR] × 16.7ms 就算一次卡顿。
     *
     * 用 1.5 倍而不是 2 倍：2 倍（33ms）只抓得到"掉两帧"，而用户能感觉到的
     * 顿挫往往从"掉一帧半"就开始了。
     */
    private const val JANK_FACTOR = 1.5f
    private const val FRAME_16_7_NS = 16_700_000L

    /** 空闲探针的统计窗口（帧数）与最多报几次，避免刷屏。 */
    private const val IDLE_PROBE_FRAMES = 300
    private const val IDLE_PROBE_WINDOWS = 3

    /** 仅 debug 构建启用：release 里这个方法体不会被调用，也不会有日志开销。 */
    val enabled: Boolean get() = BuildConfig.DEBUG

    /**
     * 测量结果的出口。
     *
     * ## 为什么要有它，而不是只写 logcat
     *
     * 第一次实现只 `Log.i`，结果在 IQ 沙箱里**读不到**：guest 进程的日志不进宿主 logcat
     * （实测 `logcat -d` 只能看到宿主的，看不到当前 guest 这一次运行的）。而沙箱截图是
     * 唯一可靠的观察通道 —— 所以结果要能被"看见"。
     *
     * 由 `AppScaffold` 挂到 `WorkspaceViewModel.reportExternalEvent`，于是每次测量结果
     * 以一条 INFO 消息出现在对话流里，人眼和截图都能读到。
     *
     * ⚠️ 写入发生在 [TRACE_FRAMES] 帧**之后**，那时测量已经结束并归档，
     * 所以"写消息 → 触发重组"不会污染刚测完的那一段。
     */
    var sink: ((String) -> Unit)? = null

    /**
     * 落盘出口。
     *
     * ## 为什么还要一个文件
     *
     * [sink] 那条路（写进对话流）只在**对话面板可见**时才能被读到 —— 而排查
     * 「页签高亮和面板内容对不上」这类问题时，对话面板恰恰是看不见的那个。
     * 而 logcat 在 IQ 沙箱里读不到（guest 日志不进宿主 logcat）。
     *
     * 所以再开一条不依赖界面的通道：写进应用自己的 `filesDir`。宿主机上
     * 对应 `blackbox/data/user/0/<包名>/files/`，可以直接读。
     *
     * ⚠️ 写入**必须**在别的线程：本文件测的就是主线程的帧耗时，在主线程上做
     * 文件 IO 会把被测对象本身弄脏（一次 append+flush 就是几毫秒，与要找的
     * 单帧尖峰同量级）。所以走一个单线程执行器，顺序仍然保持。
     */
    private var logFile: java.io.File? = null

    private val logWriter = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "zhi-frame-log").apply { isDaemon = true }
    }

    /** 绑定落盘文件并清空旧内容。由 `AppScaffold` 在启动时调用一次。 */
    fun bindLogFile(file: java.io.File) {
        logFile = file
        logWriter.execute { runCatching { file.writeText("") } }
    }

    /**
     * 打一个带绝对时间的标记，用于**跨函数比较**各段耗时。
     *
     * 只落 logcat/文件（见 [note] 的说明：不能走 sink，否则会形成重组回路）。
     * 时间用 `System.nanoTime()/1e6`，同一台设备内相减即为毫秒差。
     */
    fun stamp(tag: String) {
        if (!enabled) return
        note("stamp $tag t=${System.nanoTime() / 1_000_000}")
    }

    /**
     * 立刻记一条**开发诊断**，不参与帧统计。
     *
     * ⚠️ 刻意**不走 [sink]**，只进 logcat 与日志文件。原因是 `sink` 会往对话流写消息、
     * 进而改状态 —— 一旦有人从组合体里调 `note`（很容易发生，比如想看"这一页什么时候
     * 被组合"），就会形成 写消息 → 改状态 → 重组 → 再写 的无限重组循环。实测踩过一次：
     * 日志里 `pager/compose` 刷屏，等于自己造了个永久卡顿源。
     *
     * 所以两条通道的职责分开：
     *  · [sink]（用户可见）：只放**测量结果**，且只从帧回调里发；
     *  · 本函数（开发可见）：诊断信息，只落 logcat/文件。
     */
    fun note(message: String) {
        if (!enabled) return
        Log.i(TAG, message)
        writeToFile(message)
    }

    private fun writeToFile(line: String) {
        val file = logFile ?: return
        logWriter.execute { runCatching { file.appendText(line + "\n") } }
    }

    private fun emit(line: String) {
        Log.i(TAG, line)
        sink?.invoke(line)
        writeToFile(line)
    }

    // ---- 活动测量（只在主线程读写）----
    private var label: String? = null
    private var frameCount = 0
    private var sumNs = 0L
    private var maxNs = 0L
    private var janks = 0
    private var lastFrameNs = 0L
    /** 第一帧的绝对时刻，用来分别算「手指落下→首帧」与「onClick→首帧」。 */
    private var firstFrameNs = 0L
    /** `begin()` 被调用的时刻（即 onClick 内部）。 */
    private var clickNs = 0L

    /** 最近一次 `ACTION_DOWN` 的时刻。 */
    private var touchDownNs = 0L

    /** 最近一次 `ACTION_UP` 的时刻；页面落定后据此算出"点完等了多久"。 */
    private var touchUpNs = 0L

    // ---- 空闲探针（只在没有活动测量时累计）----
    private var idleFrames = 0
    private var idleSumNs = 0L
    private var idleLastNs = 0L
    private var idleWindows = 0

    /**
     * 在**点击回调里**调用，标记一次测量开始。
     *
     * ⚠️ 必须在真正触发状态变化的那一句**之前**调：我们要的是"点击 → 第一帧"，
     * 放在后面就变成"状态已改 → 第一帧"，那个数永远是 0，测不出滞后。
     */
    fun begin(traceLabel: String) {
        if (!enabled) return
        label = traceLabel
        clickNs = System.nanoTime()

        frameCount = 0
        sumNs = 0L
        maxNs = 0L
        janks = 0
        lastFrameNs = 0L
        firstFrameNs = 0L
        // 按需启动帧泵（见下面 startFramePump 的说明）：测量开始才申请帧。
        startFramePump()
    }

    /**
     * 记录手指**落下**的时刻。由 `MainActivity.dispatchTouchEvent` 在 `ACTION_DOWN` 时调用。
     *
     * 热路径（每一次触摸都会走），所以只做一次赋值。
     */
    fun markTouchDown() {
        if (!enabled) return
        touchDownNs = System.nanoTime()
    }

    /**
     * 记录手指**抬起**的时刻。由 `MainActivity.dispatchTouchEvent` 在 `ACTION_UP` 时调用。
     *
     * ## 为什么这个时刻才是真正的起点
     *
     * `onClick` 是**抬起时**才触发的，所以 `ACTION_DOWN → onClick` 那段约等于
     * "按住时长"（一次正常点击按住 50~100ms）。它**不是延迟** —— 拿它当延迟看会得出
     * 完全错误的结论（实测踩过：以为有 100ms 延迟，其实那是按住时长）。
     *
     * 用户感知的"点完要等一会儿才切换"，量的是 **抬起 → 页面真正切完**，
     * 由 [markPageSettled] 在 Pager 落定时结算。
     */
    fun markTouchUp() {
        if (!enabled) return
        touchUpNs = System.nanoTime()
    }

    /**
     * 记录手指**抬起**的时刻，并记一笔「抬起 → 页面切完」的耗时。
     *
     * 由 `WorkspaceLayouts` 在 Pager 落定（`settledPage` 稳定）时调用。
     * 这个数才是用户说的"点完要等一会儿才切换"。
     */
    fun markPageSettled() {
        if (!enabled) return
        if (touchUpNs == 0L) return
        val ms = (System.nanoTime() - touchUpNs) / 1_000_000.0
        note("settle upToSettled=${format(ms)}ms")
        touchUpNs = 0L
    }

    /**
     * 帧泵：**只在测量期间**向系统申请帧。
     *
     * ## 为什么不能像原来那样常开
     *
     * 第一版是挂在根部的 `while (true) { withFrameNanos { ... } }` —— 那等于
     * **每一帧都主动申请一帧，应用永不休眠**。实测（空闲 14 秒、完全不碰屏幕）：
     *
     * ```
     * idle frames=300 avg=11.4ms (=87.4fps)
     * idle frames=300 avg=8.3ms  (=120.0fps)   ← 持续满帧
     * idle frames=300 avg=8.3ms  (=120.0fps)
     * ```
     *
     * 后果不是"多画几帧"，而是**主线程永远被占着**：任何一次触摸到达时都得等
     * 当前帧画完，于是"点设置、点模型、点 TAB 全都滞后"，且与点哪里无关。
     * 这正是用户反馈的那一类现象，而且它是**测量代码自己造出来的**。
     *
     * 现在改成按需：`begin()` 申请一帧，回调里若测量还在就再申请一帧，
     * 测量结束就自然停下。空闲时一帧都不申请，应用可以真正进入静止。
     */
    private var choreographer: android.view.Choreographer? = null

    private val frameCallback = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            onFrame(frameTimeNanos)
            if (label != null) choreographer?.postFrameCallback(this)
        }
    }

    /**
     * 启动一次帧测量（由 [begin] 调用）。必须在主线程。
     */
    private fun startFramePump() {
        if (!enabled) return
        val c = choreographer ?: android.view.Choreographer.getInstance().also { choreographer = it }
        c.removeFrameCallback(frameCallback)
        c.postFrameCallback(frameCallback)
    }

    /**
     * 每帧回调（由 [ZhiFrameTraceHost] 驱动）。
     *
     * 没有活动测量时只更新帧时间戳，不做任何分配与统计 —— 这个函数每帧都会跑，
     * 待在热路径上的东西必须便宜。
     */
    fun onFrame(frameNs: Long) {
        val current = label ?: run {
            // 空闲探针：**没有测量时**应用还在不在持续出帧？
            //
            // 这一项用来回答"为什么点什么都慢"：如果用户什么都没碰、界面完全静止，
            // 应用却仍在以满帧渲染，说明有东西在**永久申请帧**（无限动画、
            // `while(true) withFrameNanos` 这类泵）。那种情况下主线程永远被占着，
            // 每一次触摸都要排队等当前帧画完 —— 表现就是"点什么都滞后"，
            // 而且和点的是哪一页无关。
            if (idleLastNs != 0L) {
                idleSumNs += (frameNs - idleLastNs).coerceAtLeast(0L)
                idleFrames++
            }
            idleLastNs = frameNs
            if (idleFrames >= IDLE_PROBE_FRAMES && idleWindows < IDLE_PROBE_WINDOWS) {
                val avgMs = idleSumNs / idleFrames.toDouble() / 1_000_000.0
                note("idle frames=$idleFrames avg=${format(avgMs)}ms (=${format(1000.0 / avgMs)}fps)")
                idleFrames = 0
                idleSumNs = 0L
                idleWindows++
            }
            lastFrameNs = frameNs
            return
        }
        // 与上一帧的间隔（第一次回调时没有上一帧，用 16.7ms 兜底，避免把
        // "点击时刻到第一帧"算成一次巨大的卡顿）
        val delta = if (lastFrameNs == 0L) FRAME_16_7_NS else (frameNs - lastFrameNs).coerceAtLeast(0L)
        lastFrameNs = frameNs

        if (firstFrameNs == 0L) {
            firstFrameNs = System.nanoTime()
            // 顺带把「触摸落下 → onClick」这段单独记一笔：它若明显大于 0，
            // 就说明点击在手势识别/事件派发上等了 —— 与"onClick 之后的重活"是两回事。
            if (touchDownNs > 0 && clickNs > touchDownNs) {
                ZhiFrameTrace.note(
                    "tap/pair downToClick=${format((clickNs - touchDownNs) / 1_000_000.0)}ms",
                )
            }
        }

        frameCount++
        sumNs += delta
        if (delta > maxNs) maxNs = delta
        if (delta > (FRAME_16_7_NS * JANK_FACTOR).toLong()) janks++

        if (frameCount >= TRACE_FRAMES) {
            val avgMs = sumNs / frameCount.toDouble() / 1_000_000.0
            // 三个数分开报，因为它们的病根完全不同：
            //  · fromTouch：手指落下 → 第一帧。**这段时间大 = 触摸在排队**（主线程忙）。
            //  · fromClick：onClick → 第一帧。大 = 状态翻转后的首次组合太重。
            //  · span/avg/max/janks：转场本身的帧质量。max 大 = 单帧尖峰（重建/重活）。
            val fromClickMs = (firstFrameNs - clickNs).coerceAtLeast(0L) / 1_000_000.0
            val fromTouchMs = if (touchDownNs > 0) {
                (firstFrameNs - touchDownNs).coerceAtLeast(0L) / 1_000_000.0
            } else {
                -1.0
            }
            val line = "$current fromTouch=${format(fromTouchMs)}ms" +
                " fromClick=${format(fromClickMs)}ms frames=$frameCount" +
                " span=${format(sumNs / 1_000_000.0)}ms avg=${format(avgMs)}ms" +
                " max=${format(maxNs / 1_000_000.0)}ms janks=$janks"
            emit(line)
            label = null
        }
    }

    private fun format(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return rounded.toString()
    }
}
