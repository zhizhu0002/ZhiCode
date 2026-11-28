package com.zhizhu.zhicode.compose.ui.pages.m3e
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.BuildConfig
import com.zhizhu.zhicode.compose.data.ChatFontFamily
import com.zhizhu.zhicode.compose.data.ChatFontLoader
import com.zhizhu.zhicode.compose.data.ThemeSettings
import com.zhizhu.zhicode.compose.data.ThemeStore
import com.zhizhu.zhicode.compose.model.DONATE_URL
import com.zhizhu.zhicode.compose.model.LicenseEntry
import com.zhizhu.zhicode.compose.model.OWN_LICENSE
import com.zhizhu.zhicode.compose.model.PROJECT_URL
import com.zhizhu.zhicode.compose.model.RELEASES_PAGE_URL
import com.zhizhu.zhicode.compose.model.THIRD_PARTY_LICENSES
import com.zhizhu.zhicode.compose.model.UpdatePhase
import com.zhizhu.zhicode.compose.model.UpdateUiState
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMarkdown
import com.zhizhu.zhicode.compose.ui.pages.updateNotesMarkdown
import com.zhizhu.zhicode.compose.ui.rememberActionBusy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun M3eAboutPage(
    update: UpdateUiState,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val openUri: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }
    val scheme = MaterialTheme.colorScheme

    M3eSubPageScaffold(title = "关于", onBack = onBack) { innerPadding, _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "hero") {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                    ),
                    onClick = onOpenUpdate,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ZhiCode",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "版本 ${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        FilledTonalButton(onClick = onOpenUpdate) {
                            Text("检查更新")
                        }
                    }
                }
            }

            item(key = "info") {
                M3eSectionHeader("应用信息")
                val newer = update.release
                val hasNew = update.phase == UpdatePhase.Available && newer != null
                val count = if (hasNew) 4 else 3
                var idx = 0
                M3eSegmentedColumn {
                    M3eNavigationRow(
                        index = idx++,
                        count = count,
                        title = "检查更新",
                        valueText = BuildConfig.VERSION_NAME,
                        icon = ZhiIcons.info,
                        onClick = onOpenUpdate,
                    )
                    if (hasNew && newer != null) {
                        M3eNavigationRow(
                            index = idx++,
                            count = count,
                            title = "有新版本 ${newer.tag}",
                            summary = "点此查看更新内容并下载",
                            icon = ZhiIcons.refresh,
                            iconContainer = scheme.primaryContainer,
                            iconTint = scheme.onPrimaryContainer,
                            onClick = onOpenUpdate,
                        )
                    }
                    M3eNavigationRow(
                        index = idx++,
                        count = count,
                        title = "GitHub 开源页面",
                        summary = "发行、问题与源码",
                        icon = ZhiIcons.link,
                        onClick = { openUri(PROJECT_URL) },
                    )
                    M3eNavigationRow(
                        index = idx,
                        count = count,
                        title = "赞助",
                        summary = "爱发电支持开发",
                        icon = ZhiIcons.done,
                        onClick = { openUri(DONATE_URL) },
                    )
                }
            }

            item(key = "legal") {
                M3eSectionHeader("开源与许可")
                M3eSegmentedColumn {
                    M3eNavigationRow(
                        index = 0,
                        count = 4,
                        title = "开发者",
                        valueText = "zhizhu0002",
                        showChevron = false,
                        onClick = { openUri(PROJECT_URL) },
                    )
                    M3eNavigationRow(
                        index = 1,
                        count = 4,
                        title = "开源许可证",
                        valueText = "$OWN_LICENSE License",
                        showChevron = false,
                        onClick = { openUri("$PROJECT_URL/blob/main/LICENSE") },
                    )
                    M3eNavigationRow(
                        index = 2,
                        count = 4,
                        title = "第三方开源项目",
                        summary = "站在这些项目的肩膀上",
                        icon = ZhiIcons.layers,
                        onClick = onOpenLicenses,
                    )
                    M3eNavigationRow(
                        index = 3,
                        count = 4,
                        title = "隐私政策",
                        summary = "本应用会碰什么、不碰什么",
                        icon = ZhiIcons.lock,
                        onClick = onOpenPrivacy,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun M3eUpdatePage(
    state: UpdateUiState,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scheme = MaterialTheme.colorScheme
    val checking = rememberActionBusy("update.check")

    LaunchedEffect(state.phase) {
        when (state.phase) {
            UpdatePhase.UpToDate -> Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
            UpdatePhase.NoReleases -> Toast.makeText(context, "上游还没有发布可下载的版本", Toast.LENGTH_SHORT).show()
            else -> Unit
        }
    }

    val hasUpdate = state.phase == UpdatePhase.Available && state.release != null
    val hasApk = !state.release?.apkUrl.isNullOrBlank()
    val downloaded = hasUpdate && state.downloadPercent >= 100

    val statusText = when (state.phase) {
        UpdatePhase.UpToDate -> "已是最新版本"
        UpdatePhase.NoReleases -> "上游还没有发布可下载的版本"
        UpdatePhase.Checking -> "正在检查更新…"
        UpdatePhase.Available -> if (downloaded) "安装包已就绪，点击安装" else state.release?.let { "发现新版本 ${it.tag}" } ?: "有可用更新"
        UpdatePhase.Downloading -> "正在下载 ${state.downloadPercent}%"
        UpdatePhase.Failed -> state.error ?: "检查失败"
        UpdatePhase.Idle -> "点击下方按钮检查 GitHub Release"
    }

    M3eSubPageScaffold(
        title = "检查更新",
        subtitle = BuildConfig.VERSION_NAME,
        onBack = onBack,
        bottomBar = {
            Surface(
                color = scheme.surfaceContainerLow,
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SplitButtonLayout(
                        leadingButton = {
                            when {
                                state.phase == UpdatePhase.Downloading -> OutlinedButton(
                                    onClick = onCancelDownload,
                                    modifier = Modifier.height(M3eDimens.ControlHeight),
                                ) { Text("取消下载") }

                                hasUpdate && hasApk && downloaded -> Button(
                                    onClick = onInstall,
                                    modifier = Modifier.height(M3eDimens.ControlHeight),
                                ) { Text("立即安装") }

                                hasUpdate && hasApk -> Button(
                                    onClick = onDownload,
                                    modifier = Modifier.height(M3eDimens.ControlHeight),
                                ) { Text("下载并安装") }

                                else -> Button(
                                    onClick = onCheck,
                                    enabled = !checking,
                                    modifier = Modifier.height(M3eDimens.ControlHeight),
                                ) { Text(if (checking) "正在检查…" else "检查更新") }
                            }
                        },
                        trailingButton = {
                            FilledTonalButton(
                                onClick = { runCatching { uriHandler.openUri(RELEASES_PAGE_URL) } },
                                modifier = Modifier.height(M3eDimens.ControlHeight),
                            ) { Text("Release") }
                        },
                    )
                }
            }
        },
    ) { innerPadding, _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "status") {
                M3eSegmentedColumn {
                    M3eSegmentedItem(index = 0, count = 1, containerColor = scheme.surfaceContainerHigh) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("ZhiCode", style = MaterialTheme.typography.headlineMedium)
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (state.phase == UpdatePhase.Failed) scheme.error else scheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                if (checking) {
                                    LoadingIndicator(modifier = Modifier.size(44.dp))
                                }
                            }
                            if (state.phase == UpdatePhase.Downloading) {
                                LinearWavyProgressIndicator(
                                    progress = { state.downloadPercent.coerceIn(0, 100) / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            val notes = updateNotesMarkdown(state)
            if (notes.isNotBlank()) {
                item(key = "notes") {
                    M3eSectionHeader("更新内容")
                    M3eSegmentedColumn {
                        M3eSegmentedItem(index = 0, count = 1) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                ZhiMarkdown(source = notes, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M3ePrivacyPage(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val sections = remember {
        listOf(
            "关于数据处理" to listOf(
                "你的对话去了哪里" to "你输入的内容会发送到你自己配置的模型服务商——DeepSeek、OpenAI、Anthropic，或者你自建的服务。本应用没有自己的服务器，不中转、不另存你的对话。",
                "读写哪些文件" to "应用内自带一套 Termux 用户空间，HOME 在应用私有目录下。文件面板只在你操作时读写；只有在授予「所有文件访问」后才能浏览 /sdcard。",
                "本地存了什么" to "只有偏好设置、会话数据库与本地配置文件；API 密钥使用 Android Keystore（AES/GCM）加密存储。",
            ),
            "权限与网络" to listOf(
                "权限用来做什么" to "INTERNET 用于请求你配置的模型与搜索服务；存储用于读写项目文件与导出；前台服务用于长任务后台保活；安装权限用于更新。",
                "没有申请的权限" to "未在清单声明相机、麦克风、通讯录、日历、定位、短信、通话记录、读取已安装应用列表。",
                "检查更新会连到哪里" to "打开关于页或检查更新时请求 GitHub Releases 公开接口（api.github.com），只读版本列表，不携带任何用户/设备标识。",
            ),
            "数据控制" to listOf(
                "没有行为采集" to "不包含统计、埋点或崩溃上报 SDK，源码已在 GitHub 公开。",
                "如何删除数据" to "可在对话长按删除单条、侧栏长按删除会话，或通过系统设置清除应用数据完整移除。",
            ),
        )
    }

    M3eSubPageScaffold(title = "隐私政策", onBack = onBack) { innerPadding, _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "intro") {
                Text(
                    text = "ZhiCode 是跑在 Android 上的 AI 编程工作区，需要联网才能工作。下面逐条列出它会碰什么、不碰什么。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            sections.forEach { (header, rows) ->
                item(key = header) {
                    M3eSectionHeader(header)
                    M3eSegmentedColumn {
                        rows.forEachIndexed { idx, (title, body) ->
                            M3eSegmentedItem(index = idx, count = rows.size) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = M3eDimens.RowPaddingH, vertical = M3eDimens.RowSpaceV),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(title, style = MaterialTheme.typography.titleMedium)
                                    Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M3eLicensesPage(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val groups = remember { THIRD_PARTY_LICENSES.groupBy { it.group } }

    M3eSubPageScaffold(title = "第三方开源项目", onBack = onBack) { innerPadding, _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            groups.forEach { (groupName, entries) ->
                item(key = groupName) {
                    M3eSectionHeader(groupName)
                    M3eSegmentedColumn {
                        entries.forEachIndexed { idx, entry ->
                            val globalIdx = THIRD_PARTY_LICENSES.indexOf(entry)
                            M3eNavigationRow(
                                index = idx,
                                count = entries.size,
                                title = entry.name,
                                summary = "${entry.author} · ${entry.license}",
                                showChevron = entry.homepage != null,
                                onClick = {
                                    val url = entry.homepage
                                    if (url != null) {
                                        runCatching { uriHandler.openUri(url) }
                                    } else {
                                        selected = globalIdx
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    val shown: LicenseEntry? = selected?.let(THIRD_PARTY_LICENSES::getOrNull)
    if (shown != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(shown.name) },
            text = { Text("${shown.author} · ${shown.license}\n\n${shown.detail}") },
            confirmButton = {
                TextButton(onClick = { selected = null }) { Text("关闭") }
            },
        )
    }
}

@Composable
fun M3eChatDisplayPage(
    theme: ThemeSettings,
    onBack: () -> Unit,
    onChange: ((ThemeSettings) -> ThemeSettings) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    var errorText by remember { mutableStateOf<String?>(null) }

    val fontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
            }.getOrNull().orEmpty()
            val result = withContext(Dispatchers.IO) { ChatFontLoader.import(context, uri, name) }
            result.onSuccess { imported ->
                errorText = null
                onChange {
                    it.copy(
                        chatFont = ChatFontFamily.CUSTOM.name,
                        customFontPath = imported.relativePath,
                        customFontName = imported.displayName,
                    )
                }
            }.onFailure { err ->
                errorText = "导入失败：${err.message ?: err.javaClass.simpleName}"
            }
        }
    }

    val currentFamily = ChatFontFamily.entries.firstOrNull { it.name == theme.chatFont } ?: ChatFontFamily.DEFAULT
    val resolvedFamily = ChatFontLoader.resolve(context, theme)
    val fontScale = ThemeStore.clampChatFontScale(theme.chatFontScale)

    M3eSubPageScaffold(title = "聊天显示", onBack = onBack) { innerPadding, _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "font") {
                M3eSectionHeader("字体与字号")
                M3eSegmentedColumn {
                    M3eSegmentedItem(index = 0, count = 2) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("正文字体", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ChatFontFamily.entries.forEach { family ->
                                    val selected = family == currentFamily
                                    val sample = when (family) {
                                        ChatFontFamily.DEFAULT -> FontFamily.Default
                                        ChatFontFamily.SERIF -> FontFamily.Serif
                                        ChatFontFamily.MONOSPACE -> FontFamily.Monospace
                                        ChatFontFamily.CUSTOM -> resolvedFamily
                                    }
                                    val interaction = remember { MutableInteractionSource() }
                                    val pressed by interaction.collectIsPressedAsState()
                                    val cellCorner by animateDpAsState(
                                        targetValue = if (pressed) 20.dp else 16.dp,
                                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                                        label = "fontCellCorner",
                                    )
                                    val cellColor by animateColorAsState(
                                        targetValue = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh,
                                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                        label = "fontCellColor",
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(cellCorner))
                                            .clickable(
                                                interactionSource = interaction,
                                                indication = ripple(),
                                            ) {
                                                if (family == ChatFontFamily.CUSTOM && theme.customFontPath.isBlank()) {
                                                    fontPicker.launch(ChatFontLoader.MIME_TYPES)
                                                } else {
                                                    onChange { it.copy(chatFont = family.name) }
                                                }
                                            },
                                        shape = RoundedCornerShape(cellCorner),
                                        color = cellColor,
                                        border = if (selected) BorderStroke(1.5.dp, scheme.primary) else null,
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text("Aa", fontFamily = sample, style = MaterialTheme.typography.titleLarge)
                                            Text(family.label, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            if (errorText != null) {
                                Text(
                                    text = errorText.orEmpty(),
                                    color = scheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = scheme.surfaceContainerHighest,
                            ) {
                                Text(
                                    text = "ZhiCode 会在预览框中展示当前字体与字号（${(fontScale * 100).toInt()}%）。\nThe quick brown fox jumps over the lazy dog.",
                                    fontFamily = resolvedFamily,
                                    fontSize = (15f * fontScale).sp,
                                    lineHeight = (22f * fontScale).sp,
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                )
                            }
                        }
                    }
                    M3eSliderRow(
                        index = 1,
                        count = 2,
                        title = "正文字号比例",
                        value = fontScale,
                        valueText = "${(fontScale * 100).toInt()}%",
                        valueRange = ThemeStore.CHAT_FONT_SCALE_MIN..ThemeStore.CHAT_FONT_SCALE_MAX,
                        onValueChange = { s -> onChange { it.copy(chatFontScale = ThemeStore.clampChatFontScale(s)) } },
                        icon = ZhiIcons.tune,
                    )
                }
            }

            item(key = "markdown") {
                M3eSectionHeader("Markdown 与代码块")
                M3eSegmentedColumn {
                    M3eSwitchRow(
                        index = 0,
                        count = 4,
                        title = "渲染 LaTeX 数学公式",
                        checked = theme.latexEnabled,
                        onCheckedChange = { v -> onChange { it.copy(latexEnabled = v) } },
                    )
                    M3eSwitchRow(
                        index = 1,
                        count = 4,
                        title = "代码块自动换行",
                        checked = theme.codeWrap,
                        onCheckedChange = { v -> onChange { it.copy(codeWrap = v) } },
                    )
                    M3eSwitchRow(
                        index = 2,
                        count = 4,
                        title = "代码块默认折叠",
                        checked = theme.codeCollapse,
                        onCheckedChange = { v -> onChange { it.copy(codeCollapse = v) } },
                    )
                    M3eSwitchRow(
                        index = 3,
                        count = 4,
                        title = "代码块显示行号",
                        checked = theme.codeLineNumbers,
                        onCheckedChange = { v -> onChange { it.copy(codeLineNumbers = v) } },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
