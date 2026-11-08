package com.zhizhu.zhicode.compose.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.theme.ZhiSpacing
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.effects.PageBlurBar
import com.zhizhu.zhicode.compose.ui.effects.rememberPageBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 隐私政策。
 *
 * ## 版式
 *
 * 顶部一段不带底色的总述，然后若干「粗体小节标题 + 圆角卡片正文」。
 * 标题浮在底色上而不是包进卡片：卡片一多时，标题若也被包住，整页会变成
 * 一摞同等重量的方块，扫读时找不到分界。
 *
 * ## ⚠️ 内容必须是真的
 *
 * 这类文案最容易写成「本应用完全离线、不联网」——对 ZhiCode 那是**假的**：
 * `AndroidManifest.xml` 声明了 `INTERNET`，它就是 AI 客户端，必须联网。
 * 下面每一条都对应代码里能查到的实现：
 * - 出网地址全是第三方（用户自己配置的模型服务商、搜索服务商、Termux 官方源），
 *   没有自有后端；
 * - 没有统计 / 埋点 / 崩溃上报 SDK；
 * - API 密钥走 Android Keystore（AES/GCM）加密，见 `AndroidSecretStore`。
 *
 * 改文案时请继续保持「能核对」这个标准，不要写成宣传语。
 */
@Composable
fun PrivacyPolicyPage(onBack: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberPageBlurBackdrop()

    Scaffold(
        topBar = {
            PageBlurBar(backdrop = backdrop, blurActive = backdrop != null) {
                SmallTopAppBar(
                    title = "隐私政策",
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) Color.Transparent else scheme.surface,
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = ZhiIcons.back,
                                contentDescription = "返回",
                                tint = scheme.onBackground,
                            )
                        }
                    },
                )
            }
        },
    ) { insets ->
        Box(
            modifier = Modifier.fillMaxSize().then(
                if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
            ),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = insets.calculateTopPadding() + 8.dp,
                    bottom = insets.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "intro") {
                    Text(
                        text = "ZhiCode 是跑在 Android 上的 AI 编程工作区，需要联网才能工作。" +
                            "下面逐条列出它会碰什么、不碰什么。",
                        color = scheme.onSurface,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ZhiSpacing.pageMargin, vertical = 6.dp),
                    )
                }

                section(
                    key = "dialogue",
                    title = "你的对话去了哪里",
                    body = "你输入的内容会发送到你自己配置的模型服务商——DeepSeek、OpenAI、" +
                        "Anthropic，或者你自建的服务。本应用没有自己的服务器，不中转、不另存你的对话。" +
                        "对方如何处理这些内容，适用它自己的隐私政策。",
                )

                section(
                    key = "files",
                    title = "读写哪些文件",
                    body = "应用内自带一套 Termux 用户空间，它的 HOME 在应用私有目录下。" +
                        "你的项目文件、会话历史（SQLite）、导出的 .md 与 .png 都在这里。" +
                        "文件面板只在你操作时读写，不会遍历其它目录。" +
                        "只有在授予「所有文件访问」之后才能浏览 /sdcard；不授予也能正常使用私有目录。",
                )

                section(
                    key = "local",
                    title = "本地存了什么",
                    body = "只有少量偏好：主题与显示设置、字体、权限模式、推理强度、API 服务商配置，" +
                        "存在应用私有的 SharedPreferences 里。" +
                        "API 密钥用 Android Keystore（AES/GCM）加密，明文不落盘，界面上也读不回来。" +
                        "会话内容存在应用私有数据库中，卸载即全部删除。",
                )

                section(
                    key = "permissions",
                    title = "权限用来做什么",
                    body = "INTERNET：请求你配置的模型服务。\n" +
                        "存储：读写项目文件与导出产物。\n" +
                        "通知 / 前台服务：让长任务在后台不被系统杀掉。\n" +
                        "安装应用：更新自身、或安装调试用的安装包。\n" +
                        "忽略电池优化：同上，需你在系统设置里手动授予。",
                )

                section(
                    key = "search",
                    title = "联网搜索会发出去什么",
                    body = "打开「联网搜索」并在对话里触发搜索时，会发送：你的查询词、" +
                        "以及所选服务商要求的鉴权凭据。搜索服务商由你在设置里选定" +
                        "（Tavily / Exa / Brave / Perplexity / SearXNG / Firecrawl / 博查 / 秘塔 / 智谱 等），" +
                        "请求直接发给它，不经过本项目作者。" +
                        "关闭「联网搜索」后不会有任何搜索请求。",
                )

                section(
                    key = "third-party",
                    title = "第三方链接与搜索服务",
                    body = "「GitHub 开源页面」「赞助」「第三方开源项目」会在你自己的浏览器中打开网页。" +
                        "离开本应用之后的行为由对应网站负责。" +
                        "模型服务商与搜索服务商如何处理收到的数据，适用它们各自的隐私政策。",
                )

                section(
                    key = "no-tracking",
                    title = "没有行为采集",
                    body = "本应用不包含统计、埋点或崩溃上报 SDK，不会把使用数据发给本项目作者。" +
                        "源码在 GitHub 上公开，可以自行核对。",
                )

                section(
                    key = "not-requested",
                    title = "没有申请的权限",
                    body = "以下权限**未**在清单中声明，因此应用在系统层面就无法使用它们：" +
                        "相机、麦克风、通讯录、日历、定位、短信、通话记录、读取已安装应用列表。",
                )

                section(
                    key = "delete",
                    title = "数据怎么删掉",
                    body = "单条对话：在对话流里长按那条消息 → 删除。\n" +
                        "整条会话：侧栏里长按会话 → 删除。\n" +
                        "全部数据：在系统设置里清除本应用数据、或直接卸载 —— " +
                        "私有目录（会话库、设置、Keystore 里的密钥）随之删除。\n" +
                        "外部存储：撤销「所有文件访问」后本应用就不再能读写 /sdcard；" +
                        "已经导出到公共目录的文件需要你自己删。",
                )

                section(
                    key = "changes",
                    title = "政策变更",
                    body = "这份说明随版本更新。任何改动都会出现在对应版本的发行说明里，" +
                        "同时这一页的文案也会更新 —— 两者不一致时以源码仓库中的这一页为准。",
                )
            }
        }
    }
}

/** 一个小节：粗体标题（无底色）+ 圆角卡片正文。 */
private fun LazyListScope.section(
    key: String,
    title: String,
    body: String,
) {
    item(key = key) {
        val scheme = MiuixTheme.colorScheme
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = ZhiSpacing.pageMargin)) {
            Text(
                text = title,
                color = scheme.onBackground,
                fontSize = MiuixTheme.textStyles.body1.fontSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 8.dp),
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = CardDefaults.CornerRadius,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                colors = CardDefaults.defaultColors(
                    color = scheme.surfaceContainer,
                    contentColor = scheme.onSurface,
                ),
            ) {
                Text(
                    text = body,
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
