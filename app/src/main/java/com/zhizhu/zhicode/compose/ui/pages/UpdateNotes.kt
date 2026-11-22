package com.zhizhu.zhicode.compose.ui.pages

import com.zhizhu.zhicode.compose.BuildConfig
import com.zhizhu.zhicode.compose.data.ReleaseNotes
import com.zhizhu.zhicode.compose.model.changeLogFor
import com.zhizhu.zhicode.compose.model.toMarkdown
import com.zhizhu.zhicode.compose.model.UpdatePhase
import com.zhizhu.zhicode.compose.model.UpdateUiState

/**
 * 更新页「横线下面那块内容」该渲染哪段 Markdown。
 *
 * ## 为什么它是一个共享函数，而不是各页面组各写一份
 *
 * 这条取舍与**观感无关**，是内容决策：
 *
 *  · 有新版 → 渲染那一版的 release 说明；
 *  · 有新版但远端没写说明 → 退回当前版本的更新日志；
 *  · 没有新版（已是最新 / 上游还没发版 / 还没查过）→ 也渲染当前版本的更新日志。
 *
 * 结果就是「横线下面永远有内容」，不会出现一块空白。如果两组各写一份，
 * 迟早会有一边漏掉"远端没写说明"这个分支，用户在那组里看到空白。
 *
 * 与渲染器无关（不依赖 Miuix 或 Mochi 的任何类型），所以能放在中立位置。
 */
internal fun updateNotesMarkdown(state: UpdateUiState): String {
    val release = state.release
    if (state.phase == UpdatePhase.Available && release != null) {
        // `ReleaseNotes.parse` 仍用来做两件事：判断"有没有说明"与截断超长正文
        // （那两道限制不该因为换了渲染器就丢掉）。它返回空 = 这条 Release 没写说明。
        val parsed = ReleaseNotes.parse(release.body)
        if (parsed.isNotEmpty()) return release.body
        // 远端没写说明：退回当前版本的说明，而不是给一片空白 ——
        // 用户至少能看到"我装的这个版本有什么"。
    }
    return changeLogFor(BuildConfig.VERSION_NAME)?.toMarkdown().orEmpty()
}
