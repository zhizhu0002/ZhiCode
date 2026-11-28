package com.zhizhu.zhicode.compose.ui.debug

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.zhizhu.zhicode.compose.BuildConfig

internal object ZhiFrameTrace {

    private const val TAG = "ZhiFrame"

    /** 一次测量覆盖的帧数（60Hz 下约 670ms）。 */
    private const val TRACE_FRAMES = 40

    /** 单帧超过 [JANK_FACTOR] × 16.7ms 就算一次卡顿。 */
    private const val JANK_FACTOR = 1.5f
    private const val FRAME_16_7_NS = 16_700_000L

    /** 空闲探针的统计窗口（帧数）与最多报几次，避免刷屏。 */
    private const val IDLE_PROBE_FRAMES = 300
    private const val IDLE_PROBE_WINDOWS = 3

    val enabled: Boolean get() = BuildConfig.FRAME_TRACE

    var sink: ((String) -> Unit)? = null

    private var logFile: java.io.File? = null

    private var sharedLogFile: java.io.File? = null

    private val logWriter = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "zhi-frame-log").apply { isDaemon = true }
    }

    /** 绑定落盘文件并清空旧内容。 */
    fun bindLogFile(file: java.io.File, shared: java.io.File? = null) {
        logFile = file
        sharedLogFile = shared
        logWriter.execute {
            runCatching { file.writeText("") }
            shared?.let { runCatching { it.writeText("") } }
        }
    }

    /** 打一个带绝对时间的标记，用于**跨函数比较**各段耗时。 */
    fun stamp(tag: String) {
        if (!enabled) return
        note("stamp $tag t=${System.nanoTime() / 1_000_000}")
    }

    fun note(message: String) {
        if (!enabled) return
        Log.i(TAG, message)
        writeToFile(message)
    }

    private val recomposeCounts = java.util.HashMap<String, Int>()

    /** 给 [key] 记一次重组。从组合体里调，必须便宜且无副作用。 */
    fun countRecompose(key: String) {
        if (!enabled) return
        synchronized(recomposeCounts) {
            recomposeCounts[key] = (recomposeCounts[key] ?: 0) + 1
        }
    }

    /** 把这一秒的重组计数写成一行日志并清零。 */
    fun flushRecomposeCounts() {
        if (!enabled) return
        val snapshot: Map<String, Int> = synchronized(recomposeCounts) {
            if (recomposeCounts.isEmpty()) return
            val copy = java.util.HashMap(recomposeCounts)
            recomposeCounts.clear()
            copy
        }
        val builder = StringBuilder("recompose/s")
        for (entry in snapshot.entries.sortedByDescending { it.value }) {
            builder.append(' ').append(entry.key).append('=').append(entry.value)
        }
        note(builder.toString())
    }

    private fun writeToFile(line: String) {
        val file = logFile
        val shared = sharedLogFile
        if (file == null && shared == null) return
        logWriter.execute {
            file?.let { runCatching { it.appendText(line + "\n") } }
            shared?.let { runCatching { it.appendText(line + "\n") } }
        }
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

    /** 记录手指**落下**的时刻。 */
    fun markTouchDown() {
        if (!enabled) return
        touchDownNs = System.nanoTime()
    }

    fun markTouchUp() {
        if (!enabled) return
        touchUpNs = System.nanoTime()
    }

    /** 记录手指**抬起**的时刻，并记一笔「抬起 → 页面切完」的耗时。 */
    fun markPageSettled() {
        if (!enabled) return
        if (touchUpNs == 0L) return
        val ms = (System.nanoTime() - touchUpNs) / 1_000_000.0
        note("settle upToSettled=${format(ms)}ms")
        touchUpNs = 0L
    }

    private var choreographer: android.view.Choreographer? = null

    private val frameCallback = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            onFrame(frameTimeNanos)
            if (label != null) choreographer?.postFrameCallback(this)
        }
    }

    /** 启动一次帧测量（由 [begin] 调用）。 */
    private fun startFramePump() {
        if (!enabled) return
        val c = choreographer ?: android.view.Choreographer.getInstance().also { choreographer = it }
        c.removeFrameCallback(frameCallback)
        c.postFrameCallback(frameCallback)
    }

    /** 每帧回调（由 [ZhiFrameTraceHost] 驱动）。 */
    fun onFrame(frameNs: Long) {
        val current = label ?: run {
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
        /* TEMP-PROBE：逐帧尖峰定位（验完即删）。 */
        if (delta > 25_000_000L) {
            note("jankFrame #$frameCount delta=${format(delta / 1_000_000.0)}ms label=$current")
        }

        if (frameCount >= TRACE_FRAMES) {
            val avgMs = sumNs / frameCount.toDouble() / 1_000_000.0
            // 三个数分开报，因为它们的病根完全不同
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
