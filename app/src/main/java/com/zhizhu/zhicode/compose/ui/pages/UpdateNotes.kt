package com.zhizhu.zhicode.compose.ui.pages

import com.zhizhu.zhicode.compose.BuildConfig
import com.zhizhu.zhicode.compose.data.ReleaseNotes
import com.zhizhu.zhicode.compose.model.changeLogFor
import com.zhizhu.zhicode.compose.model.toMarkdown
import com.zhizhu.zhicode.compose.model.UpdatePhase
import com.zhizhu.zhicode.compose.model.UpdateUiState

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
