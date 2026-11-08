package com.zhizhu.zhicode.compose.model

/**
 * 更新内容（版本说明）。
 *
 * ## 为什么本地要自带一份
 *
 * 更新页在「已经是最新版本」时也要显示**当前版本的更新内容**（用户的要求：
 * 横线下面就是更新内容）。而这份内容有两个可能的来源：
 *
 * 1. **远端 Release 的 `body`** —— 只在真发了 Release 时才有；
 * 2. **本地自带** —— 本文件。
 *
 * 本仓库根目录**没有 CHANGELOG**，GitHub 上也**还没有发布过 Release**，
 * 所以"没有新版本时"没有任何远端来源可读。这不是一个可以靠兜底糊过去的情况：
 * 横线下面如果是空白，用户只会以为页面坏了。
 *
 * ## 与远端 Release 共用同一个结构
 *
 * [ChangeLogSection] / [ChangeLogItem] 这一套**同时**用于渲染远端的 Release
 * `body`（见 `data/ReleaseNotes.kt` 的 Markdown 极简分流）。两端走同一个渲染器，
 * 于是「Beta 版说明」「基础体验」这类小节在两种来源下长相一致 ——
 * 这是刻意的：用户不该能看出"这段是本地写死的"。 
 *
 * ## ⚠️ 只写真实发生过的事
 *
 * 每一条都来自本仓库 `git log` 里**已经落地**的提交，措辞对着提交内容写。
 * 不写计划中的功能、不写"优化了体验"这种无法核对的空话 ——
 * 版本说明是用户判断"要不要更新"的唯一依据，编一条就等于骗一次。
 */
data class ChangeLogEntry(
    /** 版本号，必须与 `BuildConfig.VERSION_NAME` 逐字一致才会被取到。 */
    val version: String,
    /** 日期标签（`2025-11` 这种粒度就够；写死具体日子意义不大且在手机上读不出差异）。 */
    val dateLabel: String,
    val sections: List<ChangeLogSection>,
)

/** 一个小节：粗体标题 + 若干条说明。 */
data class ChangeLogSection(
    val title: String,
    val items: List<ChangeLogItem>,
)

/**
 * 一条说明。
 *
 * @property title 短标题（可能为空 —— 远端 Release 的正文段落就没有子标题，
 *   那时整段是一句话，不硬造一个标题）
 * @property detail 正文
 */
data class ChangeLogItem(
    val title: String,
    val detail: String,
)

/**
 * 全部版本说明，**按版本倒序**（新的在前）。
 *
 * 只有当前版本一条 —— 历史版本在"已经是最新"的场景里用不上，
 * 而写一份我们并未逐条核对过的历史清单，正是上面警告的那种编造。
 * 将来版本升到 0.3 时**往上加**一条即可，不要动这一条的措辞。
 */
val CHANGE_LOG: List<ChangeLogEntry> = listOf(
    ChangeLogEntry(
        version = "0.2",
        dateLabel = "2025-11",
        sections = listOf(
            ChangeLogSection(
                title = "内置环境与沙箱",
                items = listOf(
                    ChangeLogItem(
                        title = "免安装运行与调试 APK",
                        detail = "内置的虚拟化运行时可以直接安装、启动、停止 guest 应用，" +
                            "不必装到系统里。沙箱管理页给的是真实状态：进程、模块基址、" +
                            "Frida 装载情况，都能对着看。",
                    ),
                    ChangeLogItem(
                        title = "Frida 动态调试",
                        detail = "按需下载官方 arm64 Gadget 并做 SHA-256 校验，" +
                            "支持在 guest 进程里读内存、扫特征、下 hook。",
                    ),
                    ChangeLogItem(
                        title = "内置 Termux 用户空间",
                        detail = "随应用分发一个完整的 arm64 用户空间，终端、包管理与" +
                            "Agent 的工具执行共用同一套环境，不需要用户另装 Termux。",
                    ),
                ),
            ),
            ChangeLogSection(
                title = "Agent 与工具",
                items = listOf(
                    ChangeLogItem(
                        title = "多协议模型接入",
                        detail = "OpenAI Chat Completions / Responses、Anthropic Messages " +
                            "与自有协议各自一条链路，模型目录可直接从服务端拉取。",
                    ),
                    ChangeLogItem(
                        title = "工具集",
                        detail = "读写文件、跑命令、搜索、抓网页、发意图给系统。" +
                            "危险动作会被单独标出来并在执行前征求确认。",
                    ),
                    ChangeLogItem(
                        title = "会话存储",
                        detail = "会话落在本地单个 SQLite 库里；长会话分页加载，" +
                            "历史不会被一次性读进内存。",
                    ),
                ),
            ),
            ChangeLogSection(
                title = "界面",
                items = listOf(
                    ChangeLogItem(
                        title = "主题与显示",
                        detail = "深浅模式、预设色、Monet 取色、标签栏位置与底栏形态、" +
                            "模糊与缩放开关，改一个就能立刻看到另一个的效果。",
                    ),
                    ChangeLogItem(
                        title = "对话流",
                        detail = "工具调用成组折叠、代码块可折叠带行号、LaTeX 公式渲染、" +
                            "消息可多选与导出。",
                    ),
                    ChangeLogItem(
                        title = "扩展配置",
                        detail = "API 配置、搜索服务、MCP 服务器、Skill 管理器、" +
                            "自定义角色卡与记忆文件，都在设置页里统一管理。",
                    ),
                ),
            ),
        ),
    ),
)

/**
 * 按版本号取一份更新内容；**取不到返回 `null`**。
 *
 * 不返回一个"空的小节列表"也不返回最新一条：前者会让页面显示空白，
 * 后者会让用户以为"当前版本的说明就是那条旧的"。两种情况都该由页面
 * 如实说一句"这个版本没有留下更新说明"。
 */
fun changeLogFor(version: String?): ChangeLogEntry? {
    val target = version?.trim().orEmpty()
    if (target.isEmpty()) return null
    return CHANGE_LOG.firstOrNull { it.version == target }
}
