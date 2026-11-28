package com.zhizhu.zhicode.compose.model

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

/** 一条说明。 */
data class ChangeLogItem(
    val title: String,
    val detail: String,
)

/** 全部版本说明，**按版本倒序**（新的在前）。 */
val CHANGE_LOG: List<ChangeLogEntry> = listOf(
    ChangeLogEntry(
        version = "1.1.0",
        dateLabel = "2025-11",
        sections = listOf(
            ChangeLogSection(
                title = "界面风格可切换",
                items = listOf(
                    ChangeLogItem(
                        title = "三套完整界面",
                        detail = "「主题设置 → 界面风格」现在可以在 Miuix（默认）、" +
                            "Material 3 Expressive 与麻薯 Mochi 之间切换。" +
                            "三套是**独立的页面组**，不是换肤：卡片、列表、设置行、" +
                            "浮层的布局与材质各自成立。",
                    ),
                    ChangeLogItem(
                        title = "页面组契约",
                        detail = "新增 `ZhiPageSet` 接口，每个页面都有默认实现回落到 Miuix。" +
                            "这意味着新增一套风格**不必一次写完所有页面** —— " +
                            "只覆写想改的那几页就能装上验证，其余自动沿用默认。" +
                            "「关于 / 更新 / 隐私」三页的麻薯版就是这样只写了三页。" +
                            "Material 3 Expressive 覆盖全部十七页。",
                    ),
                    ChangeLogItem(
                        title = "切换立即生效",
                        detail = "选中即应用并落盘，不需要重启。选择以页面组 id 保存，" +
                            "将来某个组被移除时自动回落到默认组，不会白屏。",
                    ),
                ),
            ),
            ChangeLogSection(
                title = "流畅度",
                items = listOf(
                    ChangeLogItem(
                        title = "上滑加载历史不再跳到顶部",
                        detail = "往前补一页历史时，第一个助手回合的标题键会变，" +
                            "对列表而言那是一条全新条目，之前的滚动锚点全部作废。" +
                            "现在给每个回合一个按位置对齐的稳定键，只在前插且未切会话时启用。",
                    ),
                    ChangeLogItem(
                        title = "打开设置页不再把对话流顶到底",
                        detail = "跟随意图曾被跨页面保留，回到工作区时会把正在读的历史顶走。" +
                            "现在恢复后的第一轮不当作「用户要继续跟随」，真正的新内容仍然跟随。",
                    ),
                    ChangeLogItem(
                        title = "重组开销",
                        detail = "开启 Compose 强跳过：状态未变时整个页面跳过重组，" +
                            "单条消息变化时只有那一项重绘。此前工作区那个含两百多个字段的" +
                            "状态类被判为不稳定，任何一次流式输出都会让整棵对话流重组。",
                    ),
                    ChangeLogItem(
                        title = "测量可在正式包开启",
                        detail = "帧耗时与重组计数探针此前只在调试包编译，" +
                            "而真机问题几乎只在正式包出现 —— 等于一直拿不到数据。" +
                            "现在用构建参数 `-PframeTrace=true` 打开，平时不传则完全编译掉。",
                    ),
                ),
            ),
            ChangeLogSection(
                title = "修复",
                items = listOf(
                    ChangeLogItem(
                        title = "顶栏与底栏的模糊",
                        detail = "上一轮为省一次整屏录制，在页面切换期间暂停了背景捕获。" +
                            "实际效果是模糊块在最关键的 420 毫秒里取不到背景而显示成半透明，" +
                            "然后才变回模糊；若那次暂停没有正常结束，模糊会一直不出现。" +
                            "该机制已整体移除，恢复为始终捕获。",
                    ),
                    ChangeLogItem(
                        title = "移除麻薯导航栈",
                        detail = "它会在转场期间同时组合上下两页，而工作区那一屏极重。" +
                            "同时压栈的下层页始终保有组合，导致对话流的跟随逻辑在设置页打开时仍在跑。" +
                            "已回到 Miuix 自己的页面栈。",
                    ),
                ),
            ),
            ChangeLogSection(
                title = "其它",
                items = listOf(
                    ChangeLogItem(
                        title = "侧栏工作区聚合",
                        detail = "侧栏按工作区归并会话，切换项目时不再需要在一长串会话里找。",
                    ),
                    ChangeLogItem(
                        title = "运行环境",
                        detail = "云端镜像切到 Ubuntu 24.04，内置 Node 与 Pi；" +
                            "修复首次启动引导流程与终端 groups 告警。",
                    ),
                    ChangeLogItem(
                        title = "模型配置",
                        detail = "多供应商预设图标、模型名滚动显示、本地双级弹窗；" +
                            "修复重启后丢上下文、无 API 配置时保存越界、上下文窗口支持小数显示。",
                    ),
                ),
            ),
        ),
    ),
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

/** 按版本号取一份更新内容；**取不到返回 `null`**。 */
fun changeLogFor(version: String?): ChangeLogEntry? {
    val target = version?.trim().orEmpty()
    if (target.isEmpty()) return null
    return CHANGE_LOG.firstOrNull { it.version == target }
}

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
