package com.zhizhu.zhicode.compose.ui.pages.mochi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import ui.feice.mochi.basic.Text
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.list.mochiListDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.BuildConfig
import com.zhizhu.zhicode.compose.model.DONATE_URL
import com.zhizhu.zhicode.compose.model.OWN_LICENSE
import com.zhizhu.zhicode.compose.model.PROJECT_URL
import com.zhizhu.zhicode.compose.model.UpdatePhase
import com.zhizhu.zhicode.compose.model.UpdateUiState
import com.zhizhu.zhicode.compose.state.WorkspaceViewModel
import com.zhizhu.zhicode.compose.ui.ZhiMarkdown
import com.zhizhu.zhicode.compose.ui.pages.updateNotesMarkdown
import com.zhizhu.zhicode.compose.ui.pages.MiuixPageSet
import com.zhizhu.zhicode.compose.ui.pages.ZhiPageSet
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.icons.MochiIconStyle
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListButton
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiCompactTitleScaffold

object MochiPageSet : ZhiPageSet {

    override val id: String = "mochi"

    override val name: String = "麻薯（Mochi）"

    // ------------------------------------------------------------------ 关于

    @Composable
    override fun About(update: UpdateUiState, viewModel: WorkspaceViewModel) {
        val uri = LocalUriHandler.current
        MochiCompactTitleScaffold(
            title = "关于",
            navigationIcon = { MochiBackButton(onClick = viewModel::closeAbout) },
        ) {
            MochiListSection(header = "应用信息") {
                MochiListRow(
                    title = "版本",
                    value = BuildConfig.VERSION_NAME,
                    leading = { MochiIonicon("information-circle", style = MochiIconStyle.Filled) },
                )
                /* 「有新版本」只在**真检测到**时出现，与 Miuix 组同一条判据 */
                val newer = update.release
                if (update.phase == UpdatePhase.Available && newer != null) {
                    MochiListRow(
                        title = "有新版本 ${newer.tag}",
                        subtitle = "点此查看更新内容并下载",
                        onClick = viewModel::openUpdate,
                        chevron = true,
                        leading = { MochiIonicon("cloud-download", style = MochiIconStyle.Filled) },
                    )
                }
                MochiListRow(
                    title = "GitHub 开源页面",
                    subtitle = "发行、问题与源码",
                    onClick = { uri.openUri(PROJECT_URL) },
                    chevron = true,
                    leading = { MochiIonicon("code-slash", style = MochiIconStyle.Filled) },
                )
                MochiListRow(
                    title = "赞助",
                    subtitle = "爱发电",
                    onClick = { uri.openUri(DONATE_URL) },
                    chevron = true,
                    leading = { MochiIonicon("heart", style = MochiIconStyle.Filled) },
                )
            }

            MochiListSection(header = "开源与许可") {
                MochiListRow(title = "开发者", value = "zhizhu0002")
                MochiListRow(title = "开源许可证", value = "$OWN_LICENSE License")
                MochiListRow(
                    title = "第三方开源项目",
                    subtitle = "站在这些项目的肩膀上",
                    onClick = { viewModel.openLicenses() },
                    chevron = true,
                    leading = { MochiIonicon("layers", style = MochiIconStyle.Filled) },
                )
                MochiListRow(
                    title = "隐私政策",
                    subtitle = "本应用会碰什么、不碰什么",
                    onClick = { viewModel.openPrivacy() },
                    chevron = true,
                    leading = { MochiIonicon("shield-checkmark", style = MochiIconStyle.Filled) },
                )
            }

            MochiListSection(footer = "ZhiCode 基于开源软件构建。各依赖与随应用分发组件继续适用各自的许可证；完整归属、修改说明与许可文本请查看第三方声明。") {
                MochiListButton(
                    text = "查看许可证全文",
                    onClick = { uri.openUri("$PROJECT_URL/blob/main/LICENSE") },
                )
            }
        }
    }

    // ------------------------------------------------------------------ 更新

    @Composable
    override fun Update(state: UpdateUiState, viewModel: WorkspaceViewModel) {
        val versionText = when (state.phase) {
            UpdatePhase.UpToDate -> "已是最新版本"
            UpdatePhase.NoReleases -> "上游还没有发布可下载的版本"
            UpdatePhase.Checking -> "正在检查…"
            UpdatePhase.Available -> state.release?.let { "发现新版本 ${it.tag}" } ?: "有可用更新"
            UpdatePhase.Downloading -> "正在下载 ${state.downloadPercent}%"
            UpdatePhase.Failed -> state.error ?: "检查失败"
            UpdatePhase.Idle -> "还没有检查过"
        }

        MochiCompactTitleScaffold(
            title = "更新",
            navigationIcon = { MochiBackButton(onClick = viewModel::closeUpdate) },
        ) {
            MochiListSection(header = "当前版本") {
                MochiListRow(
                    title = BuildConfig.VERSION_NAME,
                    subtitle = versionText,
                    leading = { MochiIonicon("information-circle", style = MochiIconStyle.Filled) },
                )
                if (state.phase == UpdatePhase.Available) {
                    MochiListRow(
                        title = "下载并安装",
                        subtitle = state.release?.tag?.let { "将下载 $it 的安装包" },
                        onClick = viewModel::downloadUpdate,
                        chevron = true,
                        leading = { MochiIonicon("download", style = MochiIconStyle.Filled) },
                    )
                }
                if (state.phase == UpdatePhase.Downloading) {
                    MochiListRow(
                        title = "取消下载",
                        subtitle = "${state.downloadPercent}% · ${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}",
                        onClick = viewModel::cancelUpdateDownload,
                        destructive = true,
                        leading = { MochiIonicon("close-circle", style = MochiIconStyle.Filled) },
                    )
                }
            }

            /* 更新说明。 */
            val notes = updateNotesMarkdown(state)
            if (notes.isNotBlank()) {
                MochiListSection(header = "更新内容") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        ZhiMarkdown(
                            source = notes,
                            modifier = Modifier.fillMaxWidth(),
                            bodyFontSize = ui.feice.mochi.theme.MochiTheme.textStyles.bodySmall.fontSize,
                        )
                    }
                }
            }

            MochiListSection {
                MochiListButton(
                    text = if (state.phase == UpdatePhase.Checking) "正在检查…" else "检查更新",
                    onClick = { viewModel.checkForUpdates() },
                )
            }
        }
    }

    // ------------------------------------------------------------------ 隐私

    @Composable
    override fun Privacy(viewModel: WorkspaceViewModel) {
        MochiCompactTitleScaffold(
            title = "隐私政策",
            navigationIcon = { MochiBackButton(onClick = viewModel::closePrivacy) },
        ) {
            MochiListSection(
                footer = "ZhiCode 是跑在 Android 上的 AI 编程工作区，需要联网才能工作。" +
                    "下面逐条列出它会碰什么、不碰什么。",
            ) {
                PrivacyEntry("你的对话去了哪里", "你输入的内容会发送到你自己配置的模型服务商——DeepSeek、OpenAI、" +
                    "Anthropic，或者你自建的服务。本应用没有自己的服务器，不中转、不另存你的对话。" +
                    "对方如何处理这些内容，适用它自己的隐私政策。")
                PrivacyEntry("读写哪些文件", "应用内自带一套 Termux 用户空间，它的 HOME 在应用私有目录下。" +
                    "你的项目文件、会话历史（SQLite）、导出的 .md 与 .png 都在这里。" +
                    "文件面板只在你操作时读写，不会遍历其它目录。" +
                    "只有在授予「所有文件访问」之后才能浏览 /sdcard；不授予也能正常使用私有目录。")
                PrivacyEntry("本地存了什么", "只有少量偏好：主题与显示设置、字体、权限模式、推理强度、API 服务商配置，" +
                    "存在应用私有的 SharedPreferences 里。" +
                    "API 密钥用 Android Keystore（AES/GCM）加密，明文不落盘，界面上也读不回来。" +
                    "会话内容存在应用私有数据库中，卸载即全部删除。")
            }

            MochiListSection(header = "权限") {
                PrivacyEntry("权限用来做什么", "INTERNET：请求你配置的模型服务。\n" +
                    "存储：读写项目文件与导出产物。\n" +
                    "通知 / 前台服务：让长任务在后台不被系统杀掉。\n" +
                    "安装应用：更新自身、或安装调试用的安装包。\n" +
                    "忽略电池优化：同上，需你在系统设置里手动授予。")
                PrivacyEntry("没有申请的权限", "以下权限**未**在清单中声明，因此应用在系统层面就无法使用它们：" +
                    "相机、麦克风、通讯录、日历、定位、短信、通话记录、读取已安装应用列表。")
            }

            MochiListSection(header = "网络") {
                PrivacyEntry("联网搜索会发出去什么", "打开「联网搜索」并在对话里触发搜索时，会发送：你的查询词、" +
                    "以及所选服务商要求的鉴权凭据。搜索服务商由你在设置里选定" +
                    "（Tavily / Exa / Brave / Perplexity / SearXNG / Firecrawl / 博查 / 秘塔 / 智谱 等），" +
                    "请求直接发给它，不经过本项目作者。" +
                    "关闭「联网搜索」后不会有任何搜索请求。")
                PrivacyEntry("检查更新会连到哪里", "打开「关于」页、或点开「检查更新」页时，会向 GitHub 的公开接口" +
                    "（api.github.com）查询本项目的版本列表，用来判断有没有新版本。" +
                    "这个请求只读公开的版本信息，不携带任何用户数据、不携带应用标识或设备标识，" +
                    "也不需要登录。除此之外，不会因为「检查更新」而连接任何其它服务器；" +
                    "下载安装包时才由你按下按钮，从 GitHub 的发行附件地址取文件。")
                PrivacyEntry("第三方链接与搜索服务", "「GitHub 开源页面」「赞助」「第三方开源项目」会在你自己的浏览器中打开网页。" +
                    "离开本应用之后的行为由对应网站负责。" +
                    "模型服务商与搜索服务商如何处理收到的数据，适用它们各自的隐私政策。")
            }

            MochiListSection(header = "数据") {
                PrivacyEntry("没有行为采集", "本应用不包含统计、埋点或崩溃上报 SDK，不会把使用数据发给本项目作者。" +
                    "源码在 GitHub 上公开，可以自行核对。")
                PrivacyEntry("数据怎么删掉", "单条对话：在对话流里长按那条消息 → 删除。\n" +
                    "整条会话：侧栏里长按会话 → 删除。\n" +
                    "全部数据：在系统设置里清除本应用数据、或直接卸载 —— " +
                    "私有目录（会话库、设置、Keystore 里的密钥）随之删除。\n" +
                    "外部存储：撤销「所有文件访问」后本应用就不再能读写 /sdcard；" +
                    "已经导出到公共目录的文件需要你自己删。")
            }
        }
    }
}

@Composable
private fun PrivacyEntry(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .mochiListDivider(16.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MochiTheme.textStyles.bodyLarge,
            color = MochiTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MochiTheme.textStyles.bodySmall,
            color = MochiTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 字节数的可读写法（与 Miuix 组更新页同一套换算）。 */
private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format("%.1f KB", bytes / 1024.0)
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
