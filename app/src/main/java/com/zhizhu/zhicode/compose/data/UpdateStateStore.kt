package com.zhizhu.zhicode.compose.data

import android.content.Context
import com.zhizhu.zhicode.compose.model.UpdatePhase
import com.zhizhu.zhicode.compose.model.UpdateReleaseInfo
import com.zhizhu.zhicode.compose.model.UpdateUiState
import org.json.JSONObject

/**
 * 「检查更新」结果的**磁盘缓存**。
 *
 * ## 为什么需要它
 *
 * `UpdateUiState` 原来是纯内存的（`WorkspaceUiState` 的字段，默认 `Idle`）。
 * 于是只要**进程被回收**，再进来就回到「还没检查过」，得重新联网问一次 GitHub
 * —— 用户的原话是「有时候退出后台再次进入，这个更新页面会重置」。
 *
 * 后台被回收不是异常，是这个应用的常态：它 `largeHeap` + 常驻 Termux 用户空间，
 * 内存压力大，系统很容易在切后台之后把进程收掉。所以任何"用户已经看到过的结果"
 * 都不该只活在内存里。
 *
 * ## 只缓存**结论**，不缓存过程
 *
 * 存的是「上次查到的版本号 / 页面 / 体积」与阶段（`UpToDate` / `NoReleases` /
 * `Available`），**不存** `Checking` / `Downloading` —— 那两个阶段跨进程没有意义，
 * 恢复出来的应当是"上一次稳定的结论"，而不是一个永远转圈的进度条。
 *
 * `Failed` 也不恢复：错误是**针对某一次请求**的，重新打开时不该继续显示
 * 一句可能是昨天的失败原因。恢复成 `Idle` 让它重新查。
 *
 * ## 为什么不复用 `UpdateChecker.Release`
 *
 * 那是 `data` 层的解析结果（带 `org.json` 与网络语义）。缓存要能跨版本读，
 * 只该依赖 `model` 层的 `UpdateReleaseInfo`。
 */
internal object UpdateStateStore {

    private const val PREFS = "zhicode_update"
    private const val KEY_PHASE = "phase"
    private const val KEY_TAG = "tag"
    private const val KEY_NAME = "name"
    private const val KEY_PUBLISHED = "published"
    private const val KEY_BODY = "body"
    private const val KEY_PRERELEASE = "prerelease"
    private const val KEY_PAGE = "page"
    private const val KEY_APK_URL = "apk_url"
    private const val KEY_APK_NAME = "apk_name"
    private const val KEY_APK_SIZE = "apk_size"

    /** 恢复上次的结论。读不到 / 数据是坏的 / 阶段不该恢复 → 返回 `null`（当作没查过）。 */
    fun read(context: Context): UpdateUiState? = runCatching {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val phaseName = prefs.getString(KEY_PHASE, null) ?: return null
        val phase = phaseFromName(phaseName) ?: return null
        // 只在**稳定的结论**上恢复。见类头说明。
        if (phase != UpdatePhase.UpToDate &&
            phase != UpdatePhase.NoReleases &&
            phase != UpdatePhase.Available
        ) {
            return null
        }
        val release = prefs.getString(KEY_TAG, null)?.takeIf { it.isNotBlank() }?.let { tag ->
            UpdateReleaseInfo(
                tag = tag,
                name = prefs.getString(KEY_NAME, "") ?: "",
                publishedAt = prefs.getString(KEY_PUBLISHED, "") ?: "",
                body = prefs.getString(KEY_BODY, "") ?: "",
                prerelease = prefs.getBoolean(KEY_PRERELEASE, false),
                pageUrl = prefs.getString(KEY_PAGE, "") ?: "",
                apkUrl = prefs.getString(KEY_APK_URL, null)?.takeIf { it.isNotBlank() },
                apkName = prefs.getString(KEY_APK_NAME, null)?.takeIf { it.isNotBlank() },
                apkSize = prefs.getLong(KEY_APK_SIZE, 0L),
            )
        }
        // `Available` 却没有 release = 坏数据，不恢复。
        if (phase == UpdatePhase.Available && release == null) return null
        UpdateUiState(
            phase = phase,
            release = release,
            // 恢复出来的状态**一定**是"查过了"，否则 `openAbout` 会再查一次，
            // 白白多一次请求 —— 而这个缓存存在的意义正是不重复请求。
            hasChecked = true,
        )
    }.getOrNull()

    /** 落一次结论。`Checking` / `Downloading` / `Failed` 传进来会被忽略（见类头说明）。 */
    fun write(context: Context, state: UpdateUiState) {
        if (state.phase != UpdatePhase.UpToDate &&
            state.phase != UpdatePhase.NoReleases &&
            state.phase != UpdatePhase.Available
        ) {
            return
        }
        runCatching {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val editor = prefs.edit().putString(KEY_PHASE, phaseName(state.phase))
            val release = state.release
            if (release == null) {
                editor.remove(KEY_TAG).remove(KEY_NAME).remove(KEY_PUBLISHED)
                    .remove(KEY_BODY).remove(KEY_PRERELEASE).remove(KEY_PAGE)
                    .remove(KEY_APK_URL).remove(KEY_APK_NAME).remove(KEY_APK_SIZE)
            } else {
                editor.putString(KEY_TAG, release.tag)
                    .putString(KEY_NAME, release.name)
                    .putString(KEY_PUBLISHED, release.publishedAt)
                    .putString(KEY_BODY, release.body)
                    .putBoolean(KEY_PRERELEASE, release.prerelease)
                    .putString(KEY_PAGE, release.pageUrl)
                    .putString(KEY_APK_URL, release.apkUrl)
                    .putString(KEY_APK_NAME, release.apkName)
                    .putLong(KEY_APK_SIZE, release.apkSize)
            }
            // 用 `commit()` 与工程其它设置存储一致：主题/API 配置都这么写。
            // 这是"点一下就要记住"的那类数据，几毫秒不值得省。
            editor.commit()
        }
    }

    /**
     * 阶段 ↔ 名字。`UpdatePhase` 是 **sealed interface 而不是 enum**（它没有
     * `entries`，因为每个阶段还带着自己的语义），所以这里手写一层映射。
     *
     * 只列**会落盘**的四档：`Checking` / `Downloading` / `Failed` / `Idle`
     * 跨进程没有意义（见类头说明），写进去也不会被读回来。
     */
    private fun phaseName(phase: UpdatePhase): String = when (phase) {
        UpdatePhase.UpToDate -> "UpToDate"
        UpdatePhase.NoReleases -> "NoReleases"
        UpdatePhase.Available -> "Available"
        UpdatePhase.Idle -> "Idle"
        UpdatePhase.Checking -> "Checking"
        UpdatePhase.Downloading -> "Downloading"
        UpdatePhase.Failed -> "Failed"
    }

    private fun phaseFromName(name: String): UpdatePhase? = when (name) {
        "UpToDate" -> UpdatePhase.UpToDate
        "NoReleases" -> UpdatePhase.NoReleases
        "Available" -> UpdatePhase.Available
        else -> null
    }

    /** 供调试与 JSON 之外的地方读一眼原始内容（目前没有调用方，留作可核对）。 */
    fun debugDump(context: Context): String = runCatching {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        JSONObject(prefs.all).toString(2)
    }.getOrDefault("")
}
