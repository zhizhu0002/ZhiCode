package com.zhizhu.zhicode.compose.model


/** 仓库的 `owner/repo`。GitHub API 的路径与 Releases 页都用它拼。 */
const val REPO_SLUG = "zhizhu0002/ZhiCode"

/** 本工程仓库主页。关于页与许可页的所有外链都从这里派生。 */
const val PROJECT_URL = "https://github.com/$REPO_SLUG"

/** Releases API。更新检查请求的就是它（只读公开信息）。 */
const val RELEASES_API_URL = "https://api.github.com/repos/$REPO_SLUG/releases"

/** 人类可读的 Releases 页（「在浏览器打开」用）。 */
const val RELEASES_PAGE_URL = "$PROJECT_URL/releases"
