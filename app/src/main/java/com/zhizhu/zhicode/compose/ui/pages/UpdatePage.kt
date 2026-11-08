package com.zhizhu.zhicode.compose.ui.pages

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import com.zhizhu.zhicode.compose.model.ChangeLogItem
import com.zhizhu.zhicode.compose.model.ChangeLogSection
import com.zhizhu.zhicode.compose.model.RELEASES_PAGE_URL
import com.zhizhu.zhicode.compose.model.UpdatePhase
import com.zhizhu.zhicode.compose.model.UpdateUiState
import com.zhizhu.zhicode.compose.model.changeLogFor
import com.zhizhu.zhicode.compose.theme.ZhiSpacing
import com.zhizhu.zhicode.compose.ui.ZhiHorizontalDivider
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.effects.PageBlurBar
import com.zhizhu.zhicode.compose.ui.effects.rememberPageBlurBackdrop
import com.zhizhu.zhicode.compose.ui.rememberActionBusy
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
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
 * 两条路径产出**同一个** [ChangeLogSection] 结构，所以用同一段渲染代码画，
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

                item(key = "status") {
                    UpdateStatusBlock(
                        state = state,
                        checking = checking,
                        onCheck = onCheck,
                        onCancelDownload = onCancelDownload,
                    )
                }

                // 更新内容。来源见类头注释：有新版看新版，否则看当前版本。
                val sections = updateSections(state)
                if (sections.isEmpty()) {
                    item(key = "notes-empty") {
                        SectionNote("这个版本没有留下更新说明。")
                    }
                } else {
                    notesSections(sections)
                }

                item(key = "bottom") {
                    UpdateBottomActions(
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
                    )
                }
            }
        }
    }
}

/**
 * 顶部品牌区：居中超大「ZhiCode」+ 居中版本号。
 *
 * 不套磨砂 —— 关于页那个 Hero 之所以需要 `textureBlur` + `DstIn`，是因为它压在一层
 * 动态渐变上；这一页是纯底色，套上去只会让字变糊。
 */
@Composable
private fun UpdateBrandHeader() {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "ZhiCode",
            modifier = Modifier.fillMaxWidth(),
            color = scheme.onBackground,
            // 字号走**字阶里最大的一档**（`title1` = 32sp），不硬写 sp。
            // 硬写会让这一个字成为整套字阶之外的例外：将来字阶统一调整时漏改它，
            // 而"官方组件 + 自己的字"正是这个工程之前偏离 Miuix 规范的原因。
            fontSize = MiuixTheme.textStyles.title1.fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = BuildConfig.VERSION_NAME,
            modifier = Modifier.fillMaxWidth(),
            color = scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.body1.fontSize,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 状态区：一句话说清现在是什么情况，外加「检查更新」入口。
 *
 * 六种阶段各有明确文案。**不合并**"已是最新"与"上游还没有发布"——
 * 前者是"比过了没有更新的"，后者是"没有可比的东西"，
 * 混成一句话会在上游发布之后仍然显示"已是最新"，用户会以为检查坏了。
 */
@Composable
private fun UpdateStatusBlock(
    state: UpdateUiState,
    checking: Boolean,
    onCheck: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val message = when {
        checking || state.phase == UpdatePhase.Checking -> "正在检查更新…"
        state.phase == UpdatePhase.Failed -> state.error ?: "检查更新失败"
        state.phase == UpdatePhase.NoReleases -> "上游还没有发布可下载的版本。发布之后这里会显示新版本。"
        state.phase == UpdatePhase.UpToDate -> "已经是最新版本。"
        state.phase == UpdatePhase.Available && state.release != null -> {
            buildString {
                append("发现新版本 ").append(state.release.tag)
                if (state.release.prerelease) append("（预发布）")
                val date = formatPublished(state.release.publishedAt)
                if (date.isNotEmpty()) append(" · ").append(date)
            }
        }
        state.phase == UpdatePhase.Downloading -> "正在下载安装包…"
        else -> "手动检查一次，看有没有新版本。"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZhiSpacing.pageMargin, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = message,
            color = if (state.phase == UpdatePhase.Failed) scheme.error else scheme.onSurface,
            fontSize = MiuixTheme.textStyles.body1.fontSize,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 标签先算好再传：内联的 `text = if (…) "…" : "…"` 在 Kotlin 里
            // 会与"没有 else 的 if 语句"产生歧义，解析器会在字符串那一列报错。
            val checkLabel = if (checking) "检查中…" else "检查更新"
            PrimaryButton(
                text = checkLabel,
                onClick = onCheck,
                enabled = !checking && state.phase != UpdatePhase.Downloading,
            )
            if (state.phase == UpdatePhase.Available && !state.release?.apkUrl.isNullOrBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = state.release?.apkSize?.takeIf { it > 0 }?.let { formatSize(it) } ?: "",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                )
            }
        }
        if (state.phase == UpdatePhase.Downloading) {
            DownloadProgress(state, onCancel = onCancelDownload)
        }
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

/** 底部动作：只在真有新版本（或正在下载）时出现。 */
@Composable
private fun UpdateBottomActions(
    state: UpdateUiState,
    checking: Boolean,
    downloading: Boolean,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenPage: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val hasUpdate = state.phase == UpdatePhase.Available && state.release != null
    val downloaded = hasUpdate && state.downloadPercent >= 100
    // 源码-only 的 Release 确实存在：没有 apkUrl 时**不给**「下载」按钮，
    // 只留「在浏览器打开」，而不是给一个点了没反应的按钮。
    val hasApk = !state.release?.apkUrl.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZhiSpacing.pageMargin, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (hasUpdate && hasApk) {
            PrimaryButton(
                text = when {
                    downloading -> "取消下载"
                    downloaded -> "安装"
                    else -> "下载并安装"
                },
                onClick = when {
                    downloading -> onCancelDownload
                    downloaded -> onInstall
                    else -> onDownload
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (hasUpdate && !hasApk) {
            // 这次发布没有附带安装包（只发了源码）。如实说，不假装能下载。
            Text(
                text = "这次发布没有附带安装包，可以到仓库查看源码与标签。",
                color = scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            )
        }

        if (hasUpdate || state.phase == UpdatePhase.NoReleases) {
            SecondaryTextAction(
                text = "在浏览器打开" + if (hasUpdate) " Release 页" else "仓库",
                onClick = onOpenPage,
            )
        }

        if (state.phase == UpdatePhase.Failed) {
            SecondaryTextAction(text = "重试", onClick = onCheck)
        }
    }
}

/** 一行可点的次要动作（左对齐的蓝色文字，不带按钮底色）。 */
@Composable
private fun SecondaryTextAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(vertical = 6.dp),
        color = MiuixTheme.colorScheme.primary,
        fontSize = MiuixTheme.textStyles.body1.fontSize,
        textAlign = TextAlign.Center,
    )
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
 * 把小节列表铺进 LazyColumn。
 *
 * 结构与隐私政策页一致：**小节标题浮在底色上、正文进一张卡**。
 * 标题不进卡片是刻意的 —— 卡片一多时，标题若也被包住，整页会变成
 * 一摞同等重量的方块，扫读时找不到分界。
 */
private fun LazyListScope.notesSections(sections: List<ChangeLogSection>) {
    sections.forEachIndexed { index, section ->
        item(key = "section-$index") { NotesSection(section) }
    }
}

@Composable
private fun NotesSection(section: ChangeLogSection) {
    val scheme = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = section.title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = ZhiSpacing.pageMargin,
                    end = ZhiSpacing.pageMargin,
                    top = 16.dp,
                    bottom = 8.dp,
                ),
            color = scheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            fontSize = MiuixTheme.textStyles.title4.fontSize,
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZhiSpacing.pageMargin),
            colors = CardDefaults.defaultColors(),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                section.items.forEachIndexed { index, item ->
                    if (index > 0) {
                        ZhiHorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = scheme.dividerLine.copy(alpha = 0.5f),
                        )
                    }
                    NotesItem(item)
                }
            }
        }
    }
}

@Composable
private fun NotesItem(item: ChangeLogItem) {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (item.title.isNotEmpty()) {
            Text(
                text = item.title,
                color = scheme.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = MiuixTheme.textStyles.body1.fontSize,
            )
        }
        if (item.detail.isNotEmpty()) {
            Text(
                text = item.detail,
                color = if (item.title.isEmpty()) scheme.onSurface else scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
            )
        }
    }
}

/**
 * 横线下面该显示哪一份更新内容。
 *
 * 有新版就看新版的（远端 `body`），否则看当前版本的（本地 `CHANGE_LOG`）。
 * **两端都返回 [ChangeLogSection]**，所以渲染只有一套。
 */
private fun updateSections(state: UpdateUiState): List<ChangeLogSection> {
    val release = state.release
    if (state.phase == UpdatePhase.Available && release != null) {
        val fromRelease = ReleaseNotes.parse(release.body)
        if (fromRelease.isNotEmpty()) return fromRelease
        // 远端这条 Release 没写说明：退回当前版本的说明，而不是给一片空白。
        // 用户至少能看到"我装的这个版本有什么"。
    }
    return changeLogFor(BuildConfig.VERSION_NAME)?.sections.orEmpty()
}

/** `2025-11-08T10:20:30Z` → `2025-11-08`。解析不了就返回空串（不显示一个假日期）。 */
private fun formatPublished(raw: String): String {
    val date = raw.substringBefore('T').trim()
    return if (date.length == 10) date else ""
}

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
