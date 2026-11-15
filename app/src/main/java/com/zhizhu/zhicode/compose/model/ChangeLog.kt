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
 * 将来版本再升时**往上加**一条即可，不要动已有条目的措辞。
 */
val CHANGE_LOG: List<ChangeLogEntry> = listOf(
    ChangeLogEntry(
        version = "1.0.0",
        dateLabel = "2025-11",
        sections = listOf(
            ChangeLogSection(
                title = "首个正式版",
                items = listOf(
                    ChangeLogItem(
                        title = "版本号从 0.2 升到 1.0.0",
                        detail = "此前一直用 0.2，而功能面已经远超一个预发布版能代表的" +
                            "范围。1.0.0 表示「界面与设置的结构已经定下来」，" +
                            "不表示此后不再改 —— 下面这些条目本身就是这一轮刚改的。",
                    ),
                    ChangeLogItem(
                        title = "系统更新页",
                        detail = "「关于」页中间那个大 ZhiCode 字可以点，进去是检查更新页：" +
                            "上半是品牌与版本号，一条发丝线下面直接是更新内容。" +
                            "有新版本时底部出现下载按钮，下载完交给系统安装器。",
                    ),
                    ChangeLogItem(
                        title = "更新内容随状态切换来源",
                        detail = "有新版本时显示那个版本的发行说明（来自仓库的 Release），" +
                            "没有时显示**当前版本**的说明。两边用同一套结构渲染，" +
                            "所以看不出哪一段是远端来的。",
                    ),
                ),
            ),
            ChangeLogSection(
                title = "界面修复",
                items = listOf(
                    ChangeLogItem(
                        title = "字号回到 Miuix 规范",
                        detail = "此前把整套字阶整体压了一档（标题 32→24、正文 17→14），" +
                            "结果是「官方的组件 + 自己的字」，四档标题挤进 9sp 区间里、层级感变弱。" +
                            "现在按库的规范值来；要调观感请调排版，不要再压字阶。",
                    ),
                    ChangeLogItem(
                        title = "深浅色模式下图标不再发灰",
                        detail = "深色的弱化文字/图标色此前一直没被覆盖，沿用了库自带的蓝灰" +
                            "（在面板上对比度只有 3.86:1）。现在换成中性灰，实测 5.56:1；" +
                            "摘要文字从 50% 白改到不透明实色，避免同一段文字在页面与卡片上" +
                            "灰得不一样。",
                    ),
                    ChangeLogItem(
                        title = "侧栏不再顶进状态栏",
                        detail = "侧栏面板原先画到状态栏底下，汉堡按钮那一行被系统图标压住；" +
                            "另外点面板空白处会穿透到遮罩上、把侧栏关掉。两个都修了。",
                    ),
                    ChangeLogItem(
                        title = "工具组与单条工具同一个壳",
                        detail = "折叠起来的工具组此前没有卡片底，而单条工具有一层白卡，" +
                            "同一批里两种长相。现在组也套同一层壳。",
                    ),
                    ChangeLogItem(
                        title = "附件按类型分列",
                        detail = "文件与图片不再共用一条横向窄条 —— 文件卡片会被右边缘裁掉一半，" +
                            "而且两者长得几乎一样。现在文件是整宽卡片（名字 + 体积），" +
                            "图片仍是缩略图行。",
                    ),
                ),
            ),
            ChangeLogSection(
                title = "行为修复",
                items = listOf(
                    ChangeLogItem(
                        title = "上滑加载更早的消息不再置顶",
                        detail = "往前补一页时，第一个助手回合的标题键会变（首成员被更早的" +
                            "消息取代），列表把它当成全新一条、滚动锚点作废。" +
                            "现在把那一块的键钉在旧值上，前插后视口停在原处。",
                    ),
                    ChangeLogItem(
                        title = "深浅色选择不再被改回去",
                        detail = "主题另有一份 draft 缓存，而改主题时没有同步它；" +
                            "之后任何一次设置变更都会把旧值写回去 —— 表现为" +
                            "「选好深浅、退出、过一会儿又变回去，且设置页显示旧值」。" +
                            "现在三处一起更新。",
                    ),
                    ChangeLogItem(
                        title = "主题改动一定落盘",
                        detail = "此前从主题页侧滑返回时改动只活在内存里，进程被划掉就丢。" +
                            "现在登记待落盘标记，关设置页与切后台都会兑现。",
                    ),
                ),
            ),
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

/**
 * 把这一份版本说明转成 **Markdown 原文**。
 *
 * ## 为什么要转
 *
 * 更新页现在统一用工程既有的 `ZhiMarkdown` 渲染（它支持粗体/行内代码/代码块/
 * 列表/表格/链接）。远端 Release 的 `body` 本来就是 Markdown，而本地这份是
 * 结构化数据 —— 转成同一种写法，页面就只需要**一条渲染路径**。
 *
 * 上一版两端各走各的：远端走"行首标记分流"，本地走结构渲染，而那个分流
 * 完全没处理行内标记，于是 `**粗体**` 与反引号原样显示在界面上。
 *
 * ## 格式
 *
 * - 小节标题 → `## 标题`
 * - 条目 → `- **标题**：正文`（没有标题时就是 `- 正文`）
 *
 * 用的是**标准 Markdown**，不掺任何自定义标记，所以同一份文本贴到
 * Release 正文里也能正常显示。
 */
fun ChangeLogEntry.toMarkdown(): String = buildString {
    for (section in sections) {
        append("## ").append(section.title).append('\n').append('\n')
        for (item in section.items) {
            append("- ")
            if (item.title.isNotEmpty()) {
                append("**").append(item.title).append("**")
                if (item.detail.isNotEmpty()) append("：").append(item.detail)
            } else {
                append(item.detail)
            }
            append('\n')
        }
        append('\n')
    }
}.trim()
