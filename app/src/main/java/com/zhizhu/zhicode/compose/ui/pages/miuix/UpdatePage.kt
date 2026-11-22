package com.zhizhu.zhicode.compose.ui.pages.miuix

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.BuildConfig
import com.zhizhu.zhicode.compose.data.ReleaseNotes
import com.zhizhu.zhicode.compose.model.RELEASES_PAGE_URL
import com.zhizhu.zhicode.compose.model.UpdatePhase
import com.zhizhu.zhicode.compose.model.UpdateUiState
import com.zhizhu.zhicode.compose.model.changeLogFor
import com.zhizhu.zhicode.compose.model.toMarkdown
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.theme.ZhiSpacing
import com.zhizhu.zhicode.compose.ui.FloatingBottomShell
import com.zhizhu.zhicode.compose.ui.ZhiHorizontalDivider
import com.zhizhu.zhicode.compose.ui.ZhiMarkdown
import com.zhizhu.zhicode.compose.ui.pages.updateNotesMarkdown
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.effects.PageBlurBar
import com.zhizhu.zhicode.compose.ui.effects.ZhiSquircle
import com.zhizhu.zhicode.compose.ui.effects.rememberPageBlurBackdrop
import com.zhizhu.zhicode.compose.ui.rememberActionBusy
import com.zhizhu.zhicode.compose.ui.rememberGlass
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「检查更新」页。
 *
 * ## 版式（照参考图的「系统更新」）
 *
 * ```
 * 顶栏：「检查更新」+ 左返回（无右侧菜单）
 *   ┌ 居中超大「ZhiCode」
 *   │ 居中版本号
 *   ├────────── 一条 Divider ──────────
 *   │ 更新内容（小节标题 + 正文）
 *   ▼
 * 底部：只在真有新版本时出现「下载并安装」
 * ```
 *
 * ## 横线下面是"更新内容"，来源随状态切换
 *
 * - **有新版本** → 那个版本的更新内容（来自该 Release 的 `body`，经
 *   [ReleaseNotes] 分流成小节）；
 * - **没有新版本 / 上游还没发 Release / 检查失败** → **当前版本**的更新内容
 *   （本地自带的 `CHANGE_LOG`）。
 *
 * 两条路径都产出 Markdown 原文，所以用同一段渲染代码画（`ZhiMarkdown`），
 * 用户看不出这一段是远端来的还是本地写死的。
 *
 * ## 「已经是最新版本」用系统吐司，不做页面内的悬浮胶囊
 *
 * 参考图那个胶囊是系统更新应用的 Toast。这里用真 `Toast`（工程里
 * `TerminalChrome` / `EditorPage` / `UiDebugPage` 都是这个用法），
 * 于是它和其它系统提示的观感一致，也不会在页面上再占一块位置。
 * 吐司由**页面**在 [UpdatePhase] 迁移时弹，不在 ViewModel 里 ——
 * ViewModel 不该碰 `Context`。
 */
@Composable
fun UpdatePage(
    state: UpdateUiState,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberPageBlurBackdrop()

    val checking = rememberActionBusy("update.check")
    val downloading = state.phase == UpdatePhase.Downloading

    /*
     * 吐司：只在**阶段真的变了**那一刻弹一次。
     *
     * 不能写成"看到 phase == NoReleases 就弹"—— 那样每次重组（滚动、点按）
     * 都会再弹一次。`LaunchedEffect(phase)` 的 key 是阶段本身，所以同阶段内的
     * 重组不会触发它，这正是我们要的语义。
     *
     * 失败不弹吐司：错误原因已经在页面上有一整块文字，再弹一次是重复。
     */
    LaunchedEffect(state.phase) {
        val text = when (state.phase) {
            UpdatePhase.UpToDate -> "已经是最新版本"
            UpdatePhase.NoReleases -> "上游还没有发布可下载的版本"
            UpdatePhase.Available -> state.release?.let { "发现新版本 ${it.tag}" }
            else -> null
        }
        if (text != null) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            PageBlurBar(backdrop = backdrop, blurActive = backdrop != null) {
                SmallTopAppBar(
                    title = "检查更新",
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
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = insets.calculateTopPadding(),
                    bottom = insets.calculateBottomPadding() + 24.dp,
                ),
            ) {
                item(key = "brand") { UpdateBrandHeader() }

                /*
                 * 「有新版本」胶囊 —— 放在 hero 区里、品牌字下方居中。
                 *
                 * 参考图的「有新版本」就是这样一颗**居中的胶囊按钮**（不是卡片里的一行）：
                 * 它在视觉上属于"版本信息"那一块，而不是设置列表里的一项。
                 * 之前我把它做成关于页卡片里的一行，位置与分量都不对 ——
                 * 那是这个页面**最主要的动作**，应该一眼看到。
                 *
                 * 只在真有新版本时出现；无更新/上游无 Release 时不占位。
                 */
                if (state.phase == UpdatePhase.Available && state.release != null) {
                    item(key = "new-version-pill") {
                        NewVersionPill(
                            version = state.release.tag,
                            onClick = onCheck,
                        )
                    }
                }

                // 横线：用 Miuix 的 Divider（`Hairline` 得到恒定一个物理像素，
                // 正是参考图那条发丝线），不手搓 `Box + background`。
                item(key = "rule") {
                    ZhiHorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ZhiSpacing.pageMargin),
                        thickness = Dp.Hairline,
                    )
                }

                /*
                 * ⚠️ 这里**不放状态文案**。
                 *
                 * 解包了参考实现（`com.android.updater` 9.7.2）之后确认：
                 * 它把"有没有新版本"拆成**两个独立 fragment**（`no_update_fragment` /
                 * `new_update_fragment`），而两个 fragment 里**都没有**状态句 ——
                 * 「已经是最新版本」「发现新版本」是 **Toast**
                 * （`miui_summary_no_new_version` / `miui_summary_has_new_version`），
                 * 不是页面内容。
                 *
                 * 所以横线下面**永远只有更新日志**。之前我在这里塞了一整块
                 * 状态文案（还带一句"发布之后这里会显示新版本"的解释），
                 * 那正是"和参考图完全不统一"的直接原因 —— 参考图里那块位置
                 * 从头到尾都是正文。状态改走吐司，见上面那个 LaunchedEffect。
                 */

                /*
                 * 更新内容：直接交给工程既有的 `ZhiMarkdown`。
                 *
                 * ## 为什么不再自己分流
                 *
                 * 上一版这里用 `ReleaseNotes.parse` 把正文拆成"小节 + 条目"，
                 * 只处理了行首的 `#` / `-`，**完全没处理行内标记**。结果用户看到的是
                 * 一屏 `**粗体**`、`` `代码` ``、`| 表格 | 列 |` 原样显示 ——
                 * 标记语言泄漏到界面上。
                 *
                 * `ZhiMarkdown` 是这个工程自己的完整渲染器（粗体/斜体/行内代码/
                 * 代码块/列表/引用/表格/可点链接，全部复用 Miuix 配色）。
                 * 引它比自己再补一套行内解析正确得多，而且**更新说明本来就是
                 * Markdown** —— Release 的 `body` 直接就是 Markdown 原文。
                 *
                 * `ReleaseNotes` 保留：它仍然负责"这条 Release 有没有说明"的判断
                 * 与长度上限（那两道限制不该丢），只是不再负责渲染。
                 */
                val notes = updateNotesMarkdown(state)
                if (notes.isBlank()) {
                    item(key = "notes-empty") {
                        SectionNote("这个版本没有留下更新说明。")
                    }
                } else {
                    item(key = "notes") {
                        ZhiMarkdown(
                            source = notes,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = ZhiSpacing.pageMargin,
                                    end = ZhiSpacing.pageMargin,
                                    top = 4.dp,
                                    bottom = 8.dp,
                                ),
                            bodyFontSize = MiuixTheme.textStyles.body2.fontSize,
                        )
                    }
                }

                // 失败原因必须留在页面上（吐司一闪就没了，用户没法照着排查），
                // 但它是**附加信息**，不替代日志，所以放在日志之后、很轻的一行。
                if (state.phase == UpdatePhase.Failed && state.error != null) {
                    item(key = "error") { SectionNote("检查失败：${state.error}") }
                }

                if (state.phase == UpdatePhase.Downloading) {
                    item(key = "progress") {
                        Box(Modifier.padding(horizontal = ZhiSpacing.pageMargin, vertical = 12.dp)) {
                            DownloadProgress(state, onCancel = onCancelDownload)
                        }
                    }
                }

                item(key = "bottom-spacer") {
                    // 给悬浮条让出位置。高度按参考实现的两个值之和：
                    // `check_btn_margin_top` 9.09dp + 按钮 48dp + `check_btn_margin_bottom`
                    // 28dp ≈ 85dp，再留一点余量防止内容被压。
                    Spacer(Modifier.height(96.dp))
                }
            }

            // ---- 底部悬浮条 ----
            //
            // 参考实现把动作做成**贴底的悬浮按钮**（`no_update_fragment` 里的
            // `Button` 用 `check_btn_margin_top` 9.09dp / `check_btn_margin_bottom`
            // 28dp 做上下留白），而不是页面流里的一个实心蓝按钮。
            //
            // 这里复用工程自己的 `FloatingBottomShell`（任务卡 / 反馈条 / 输入器
            // 都从它出）而不是另写一个：圆角、阴影、模糊半径、横向内缩都在那一个
            // 地方定死，多处想不一致都不行。
            UpdateFloatingBar(
                state = state,
                checking = checking,
                downloading = downloading,
                onCheck = onCheck,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onInstall = onInstall,
                onOpenPage = {
                    val url = state.release?.pageUrl?.takeIf { it.isNotBlank() }
                        ?: RELEASES_PAGE_URL
                    runCatching { uriHandler.openUri(url) }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = insets.calculateBottomPadding()),
            )
        }
    }
}

/**
 * 「有新版本」胶囊。
 *
 * 照参考图的形态：**居中的圆角胶囊**，字号与正文同级但更轻，底色是比页面亮一档的
 * 中性填充（参考图里那颗在深紫渐变上是很淡的一层）。
 *
 * ## 为什么不是 `PrimaryButton`
 *
 * `PrimaryButton` 是**实心强调色**，那是"提交/确认"的重量。这颗胶囊在参考图里
 * 只是一层很淡的填充 + 常规文字 —— 它是**入口**（点了滚到更新说明），
 * 不是危险或不可逆的动作。真正的下载按钮在底部悬浮条上，那一个才是实心的。
 *
 * 用 `surfaceContainerHigh` 而不是写死颜色：深浅色各自成立，跟随预设色。
 */
@Composable
private fun NewVersionPill(version: String, onClick: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(ZhiSquircle(ZhiRadius.button))
                .background(scheme.surfaceContainerHigh)
                .clickableNoRipple(onClick)
                .padding(horizontal = 36.dp, vertical = 12.dp),
        ) {
            Text(
                // 参考图只写「有新版本」；这里带上版本号，因为它就在品牌字下面，
                // 用户一眼能看到"新的是哪个版本"，不必再往下找。
                text = "有新版本 $version",
                color = scheme.onSurface,
                fontSize = MiuixTheme.textStyles.body1.fontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 顶部品牌区：居中超大「ZhiCode」+ 居中版本号。
 *
 * 不套磨砂 —— 关于页那个 Hero 之所以需要 `textureBlur` + `DstIn`，是因为它压在一层
 * 动态渐变上；这一页是纯底色，套上去只会让字变糊。
 *
 * 间距取自参考实现（`com.android.updater` 9.7.2 的 `no_update_fragment`）：
 * 品牌字距顶 `miui_white_text_logo_margin_top` = 121dp（这里取 40dp，因为我们的
 * 顶栏比它的高）、版本号距品牌字 `miui_version_summary_margin_top` = 10dp。
 */
@Composable
private fun UpdateBrandHeader() {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "ZhiCode",
            modifier = Modifier.fillMaxWidth(),
            color = scheme.onBackground,
            // 字号走字阶里最大的一档（`title1`），不硬写 sp。
            // 硬写会让这一个字成为整套字阶之外的例外：将来字阶统一调整时漏改它，
            // 而"官方组件 + 自己的字"正是这个工程之前偏离 Miuix 规范的原因。
            fontSize = MiuixTheme.textStyles.title1.fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        // 参考实现是 10dp（`miui_version_summary_margin_top`）。
        Spacer(Modifier.height(10.dp))
        Text(
            text = BuildConfig.VERSION_NAME,
            modifier = Modifier.fillMaxWidth(),
            color = scheme.onSurfaceVariantSummary,
            // 参考实现用 `text_size_13p3`（14.55sp）；这里取 `body2`（14sp）——
            // 与规范值差 0.55sp 肉眼不可辨，而挂到字阶上才能随主题一起缩放。
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            textAlign = TextAlign.Center,
        )
    }
}

/** 下载进度：进度条 + 百分比 + 取消。 */
@Composable
private fun DownloadProgress(state: UpdateUiState, onCancel: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = (state.downloadPercent.coerceIn(0, 100)) / 100f,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildString {
                    append("已下载 ").append(formatSize(state.downloadedBytes))
                    if (state.totalBytes > 0) {
                        append(" / ").append(formatSize(state.totalBytes))
                    }
                    append("（").append(state.downloadPercent.coerceIn(0, 100)).append("%）")
                },
                modifier = Modifier.weight(1f),
                color = scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            )
            Text(
                text = "取消",
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clickableNoRipple(onCancel),
                color = scheme.primary,
                fontSize = MiuixTheme.textStyles.body1.fontSize,
            )
        }
    }
}

/**
 * 底部悬浮条。
 *
 * ## 为什么是悬浮条而不是页面里的按钮
 *
 * 参考实现的「检查更新 / 立即下载」是**贴底的悬浮按钮**（`no_update_fragment`
 * 里那个 `Button` 用 `check_btn_margin_top` 9.09dp、`check_btn_margin_bottom`
 * 28dp 做上下留白，横向上与内容同宽），而不是插在正文流里的一个实心块。
 *
 * 那不只是外观：悬浮条**不随内容滚走**，而"检查更新"必须随时可按 ——
 * 日志一长（真发过几个版本之后必然如此），流里的按钮会被推到几屏之外，
 * 用户得先滚到底才能点。
 *
 * 复用工程自己的 [FloatingBottomShell]（任务卡 / 反馈条 / 输入器都从它出）：
 * 圆角、阴影、模糊半径、横向内缩在那一处定死，三块想不一致都不行。
 */
@Composable
private fun UpdateFloatingBar(
    state: UpdateUiState,
    checking: Boolean,
    downloading: Boolean,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    val glass = rememberGlass()
    val hasUpdate = state.phase == UpdatePhase.Available && state.release != null
    val downloaded = hasUpdate && state.downloadPercent >= 100
    // 源码-only 的 Release 确实存在：没有 apkUrl 时**不给**「下载」按钮，
    // 只留「在浏览器打开」，而不是给一个点了没反应的按钮。
    val hasApk = !state.release?.apkUrl.isNullOrBlank()

    /*
     * 主按钮的文案与动作。**永远有一个主按钮**："检查更新"是这个页面的主要动作，
     * 没有新版本时它就是唯一的按钮（参考实现同理 —— 无更新态那个 fragment 里
     * 也有一个可点的按钮）。
     */
    val primaryText: String
    val primaryAction: () -> Unit
    val primaryEnabled: Boolean
    when {
        downloading -> {
            primaryText = "取消下载"
            primaryAction = onCancelDownload
            primaryEnabled = true
        }
        hasUpdate && hasApk && downloaded -> {
            primaryText = "安装"
            primaryAction = onInstall
            primaryEnabled = true
        }
        hasUpdate && hasApk -> {
            primaryText = "下载并安装"
            primaryAction = onDownload
            primaryEnabled = true
        }
        else -> {
            // 没有可下载的包（无更新 / 上游还没发 / 失败 / 只有源码）→ 就是"检查更新"。
            // 标签先算好再传：内联的 `text = if (…) "…" : "…"` 在 Kotlin 里会与
            // "没有 else 的 if 语句"产生歧义，解析器会在字符串那一列报错。
            primaryText = if (checking) "检查中…" else "检查更新"
            primaryAction = onCheck
            primaryEnabled = !checking
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        FloatingBottomShell(wide = false, glass = glass, verticalPadding = 8.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZhiSpacing.pageMargin, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 这次发布没带安装包（只发了源码）时如实说一句，不假装能下载。
                if (hasUpdate && !hasApk) {
                    Text(
                        text = "这次发布没有附带安装包，可在仓库查看源码与标签。",
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    )
                }
                PrimaryButton(
                    text = primaryText,
                    onClick = primaryAction,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = primaryEnabled,
                )
                // 次要入口：有新版时去 Release 页，无 Release 时去仓库。
                // 只在真有目标时出现 —— 无更新态不需要多一行链接。
                if (hasUpdate || state.phase == UpdatePhase.NoReleases) {
                    Text(
                        text = if (hasUpdate) "在浏览器打开 Release 页" else "在浏览器打开仓库",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickableNoRipple(onOpenPage)
                            .padding(vertical = 4.dp),
                        color = scheme.primary,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 一段说明文字（用于「没有更新说明」这类情况）。 */
@Composable
private fun SectionNote(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZhiSpacing.pageMargin, vertical = 8.dp),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
    )
}

/**
 * 横线下面该显示哪一份更新内容 —— 返回 **Markdown 原文**。
 *
 * 有新版就看新版的（远端 Release 的 `body`，本来就是 Markdown），
 * 否则看当前版本的（本地 `CHANGE_LOG`，由 [com.zhizhu.zhicode.compose.model.toMarkdown]
 * 转成同一套写法）。两端都是 Markdown，所以渲染只有一套 —— `ZhiMarkdown`。
 *
 * ⚠️ 上一版这里返回的是"小节 + 条目"结构，配合自己写的分流渲染，
 * 只处理了行首标记、没处理行内标记，于是 `**粗体**`、反引号、表格全部原样泄漏到界面。
 * 换成 Markdown 原文 + 既有渲染器之后，这类问题从根上不会再有。
 */

/* 注：原先这里有个 `formatPublished`（把 ISO 时间截成日期），已经随
 * "更新页不再单独显示发布日期"一起去掉 —— 参考图里也没有这一行。 */

/** 字节数 → 人类可读。只在两处用，不引公共工具。 */
private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024} KB"
    bytes < 1024L * 1024L * 1024L -> "${bytes / (1024 * 1024)} MB"
    else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

/**
 * 无涟漪点击。
 *
 * 大字号文字（品牌字、整行文字动作）不是标准点按目标：加上涟漪会让人以为
 * 那是一个按钮，而这几个位置的语义就是"文字本身可以点"。
 */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
