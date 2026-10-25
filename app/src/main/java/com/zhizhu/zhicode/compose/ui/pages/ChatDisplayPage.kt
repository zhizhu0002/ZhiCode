package com.zhizhu.zhicode.compose.ui.pages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.data.ChatFontFamily
import com.zhizhu.zhicode.compose.data.ChatFontLoader
import com.zhizhu.zhicode.compose.data.ThemeSettings
import com.zhizhu.zhicode.compose.data.ThemeStore
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.settings.SettingsEntry
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsPlateColors
import com.zhizhu.zhicode.compose.ui.settings.SettingsSliderRow
import com.zhizhu.zhicode.compose.ui.settings.SettingsSubPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 聊天显示设置页（自定义字体 + 正文字号）。
 *
 * ## 来源
 *
 * 照 rikkahub 的 `SettingPreferencesUIPage` 搬，其中字体导入那一整套
 * （`Import` 的原子替换、失败回滚、越界检查）在 [ChatFontLoader] 里，
 * 这一页只负责"选、预览、删"。
 *
 * ## 与 `uiScale` 的分工
 *
 * 主题页的「界面缩放」改的是 `LocalDensity`——按钮、间距、图标一起变。
 * 这一页的「正文字号」只改**阅读正文**的字号：用户说"字大一点但按钮别变大"时用它。
 * 两者独立存盘、互不影响，这一点写在 [ThemeSettings.chatFontScale] 的注释里。
 *
 * ## 为什么选字体要真的渲染预览
 *
 * 字体列表里的名字（"衬线""等宽"）说明不了字形长什么样。rikkahub 的做法是
 * 每个按钮用**该字体本身**写一个 `Aa`，下面再写标签 —— 一眼就能分辨。
 * 这一页照做，另外多给一块正文预览，因为它改了字号比例之后要能立刻看到效果。
 */
@Composable
fun ChatDisplayPage(
    theme: ThemeSettings,
    onBack: () -> Unit,
    onChange: ((ThemeSettings) -> ThemeSettings) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

            val result = withContext(Dispatchers.IO) {
                ChatFontLoader.import(context, uri, name)
            }
            result.onSuccess { imported ->
                errorText = null
                onChange {
                    it.copy(
                        chatFont = ChatFontFamily.CUSTOM.name,
                        customFontPath = imported.relativePath,
                        customFontName = imported.displayName,
                    )
                }
            }.onFailure { error ->
                // 如实上报：'不是字体' 与 '读不到文件' 是两件事，
                // 用户据此决定换一个还是重选。
                errorText = "导入失败：${error.message ?: error.javaClass.simpleName}"
            }
        }
    }

    val currentFamily = ChatFontFamily.entries
        .firstOrNull { it.name == theme.chatFont } ?: ChatFontFamily.DEFAULT
    val resolvedFamily = ChatFontLoader.resolve(context, theme)

    SettingsSubPage(title = "聊天显示", onBack = onBack) {
        // ---- 字体 ----
        SettingsGroup("字体") {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "正文字体",
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChatFontFamily.entries.forEach { family ->
                        FontChoice(
                            family = family,
                            selected = family == currentFamily,
                            sampleFamily = when (family) {
                                ChatFontFamily.DEFAULT -> FontFamily.Default
                                ChatFontFamily.SERIF -> FontFamily.Serif
                                ChatFontFamily.MONOSPACE -> FontFamily.Monospace
                                ChatFontFamily.CUSTOM -> resolvedFamily
                            },
                            onClick = {
                                if (family == ChatFontFamily.CUSTOM && theme.customFontPath.isBlank()) {
                                    // 还没导入过就点了「自定义」——直接开选择器，
                                    // 而不是先切到一个没有文件的空状态。
                                    fontPicker.launch(ChatFontLoader.MIME_TYPES)
                                } else {
                                    onChange { it.copy(chatFont = family.name) }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        SettingsGroup("自定义字体文件") {
            SettingsEntry(
                title = "导入字体文件",
                valueText = theme.customFontName.ifBlank { "未导入" },
                onClick = { fontPicker.launch(ChatFontLoader.MIME_TYPES) },
                summary = "支持 .ttf / .otf / .ttc；导入后会立刻切换到该字体",
                icon = ZhiIcons.link,
                plate = SettingsPlateColors.blue,
            )
            if (theme.customFontPath.isNotBlank()) {
                SettingsEntry(
                    title = "删除自定义字体",
                    valueText = "回落默认",
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                ChatFontLoader.delete(context, theme.customFontPath)
                            }
                            onChange {
                                it.copy(
                                    chatFont = ChatFontFamily.DEFAULT.name,
                                    customFontPath = "",
                                    customFontName = "",
                                )
                            }
                        }
                    },
                    summary = "删掉已导入的字体文件，并切回默认字体",
                    icon = ZhiIcons.collapse,
                    plate = SettingsPlateColors.red,
                )
            }
        }

        errorText?.let { message ->
            Text(
                text = message,
                color = MiuixTheme.colorScheme.error,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        // ---- 字号 ----
        SettingsGroup("字号") {
            SettingsSliderRow(
                title = "正文字号",
                summary = "只改聊天正文；按钮与图标的大小由「界面缩放」控制，两者互不影响",
                value = theme.chatFontScale,
                valueText = "${(theme.chatFontScale * 100).toInt()}%",
                onValueChange = { scale ->
                    onChange { it.copy(chatFontScale = ThemeStore.clampChatFontScale(scale)) }
                },
                valueRange = ThemeStore.CHAT_FONT_SCALE_MIN..ThemeStore.CHAT_FONT_SCALE_MAX,
                showKeyPoints = true,
                keyPoints = listOf(
                    ThemeStore.CHAT_FONT_SCALE_MIN,
                    1.0f,
                    ThemeStore.CHAT_FONT_SCALE_MAX,
                ),
                magnetThreshold = 0.01f,
                icon = ZhiIcons.tune,
                plate = SettingsPlateColors.orange,
            )
        }

        // ---- 预览 ----
        Text(
            text = "预览",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 6.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            cornerRadius = ZhiRadius.card,
            colors = CardDefaults.defaultColors(MiuixTheme.colorScheme.surfaceContainer),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                // 两段：一段中文、一段中英混排 + 数字。字体选择最容易暴露问题的
                // 就是"英文好看但中文回落"与"数字不等宽"，两段一起看能同时覆盖。
                Text(
                    text = "这是聊天正文的预览。Adjust the size until it reads comfortably.",
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = MiuixTheme.textStyles.body1.fontSize * theme.chatFontScale,
                    fontFamily = resolvedFamily,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "0123456789 · 行高、字距与字形都取自当前设置",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.body2.fontSize * theme.chatFontScale,
                    fontFamily = resolvedFamily,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * 一个字体选项：上面用该字体写一个 `Aa`，下面写标签。
 *
 * 为什么不是 `RadioButton` / `Chip`：那样只能用同一套字体画所有选项，
 * 而这一项要表达的恰恰是"字形不一样"。用字体本身渲染样字是唯一说得清的写法
 * （rikkahub 的 `ConnectedChoiceButtons` 也是这么做的）。
 */
@Composable
private fun FontChoice(
    family: ChatFontFamily,
    selected: Boolean,
    sampleFamily: FontFamily,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(ZhiRadius.inner))
            .background(if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh)
            .padding(vertical = 8.dp)
            .then(
                Modifier.clickableWithoutRipple(onClick),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Aa",
            color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
            fontSize = MiuixTheme.textStyles.title2.fontSize,
            fontFamily = sampleFamily,
        )
        Text(
            text = family.label,
            color = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 无涟漪点击：色块底色本身已经表达了选中态，涟漪叠上去只会变脏。 */
@Composable
private fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = source,
        indication = null,
        onClick = onClick,
    )
}
