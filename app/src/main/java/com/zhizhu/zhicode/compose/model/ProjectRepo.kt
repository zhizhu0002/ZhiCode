package com.zhizhu.zhicode.compose.model

/**
 * 本工程自己的仓库坐标 —— **唯一真实来源**。
 *
 * ## 为什么单独一个文件
 *
 * 这三条字符串有两个使用者，它们的失败方式完全不同：
 *
 * 1. **界面**（关于页的「GitHub 开源页面」、关于页的「许可证全文」、许可页的
 *    NOTICE 链接）—— 写错只会在用户点开时看到一个 404，编译期毫无反应；
 * 2. **更新检查**（`data/UpdateChecker.kt`）—— 写错会让「检查更新」永远报错，
 *    或者更糟：指向另一个仓库，于是拿别人的 tag 来比自己的版本号。
 *
 * 之前它们分散在 `Licenses.kt`（`PROJECT_URL`）里，而更新检查又需要
 * `owner/repo` 与 API 地址。三份字符串三个地方，迟早出现
 * 「界面指向 A 仓库、更新检查指向 B 仓库」这种自相矛盾。
 *
 * ## ⚠️ 与 `Licenses.kt` 里 IQ Code 那一项的区别
 *
 * [PROJECT_URL] 是**本工程**的仓库。`THIRD_PARTY_LICENSES` 里第一条
 * 「IQ Code」（`iqisge-gif/IQ-Code-Android`）是**上游参考项目**的地址，
 * 那是对上游的署名，**不是**本工程的发布源。两者不能互相替换：
 * 上游的 tag（如 `0.30.4`）是 IQ Code 的版本号，拿来和本工程的
 * `BuildConfig.VERSION_NAME` 比大小毫无意义。
 */

/** 仓库的 `owner/repo`。GitHub API 的路径与 Releases 页都用它拼。 */
const val REPO_SLUG = "zhizhu0002/ZhiCode"

/** 本工程仓库主页。关于页与许可页的所有外链都从这里派生。 */
const val PROJECT_URL = "https://github.com/$REPO_SLUG"

/** Releases API。更新检查请求的就是它（只读公开信息）。 */
const val RELEASES_API_URL = "https://api.github.com/repos/$REPO_SLUG/releases"

/** 人类可读的 Releases 页（「在浏览器打开」用）。 */
const val RELEASES_PAGE_URL = "$PROJECT_URL/releases"
