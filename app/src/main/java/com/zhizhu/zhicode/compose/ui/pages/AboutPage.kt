package com.zhizhu.zhicode.compose.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhizhu.zhicode.compose.BuildConfig
import com.zhizhu.zhicode.compose.model.OWN_LICENSE
import com.zhizhu.zhicode.compose.model.PROJECT_URL
import com.zhizhu.zhicode.compose.theme.ZhiRadius
import com.zhizhu.zhicode.compose.theme.ZhiSpacing
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.dialogs.DialogShell
import com.zhizhu.zhicode.compose.ui.dialogs.PrimaryButton
import com.zhizhu.zhicode.compose.ui.dialogs.ZhiDialogWidth
import com.zhizhu.zhicode.compose.ui.effects.PageBlurBar
import com.zhizhu.zhicode.compose.ui.effects.rememberPageBlurBackdrop
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/**
 * About page following the KSU-style Miuix structure: large identity hero first,
 * then one standard Card of links. Existing project/license actions are preserved.
 */
@Composable
fun AboutPage(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val openUri: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }
    val scheme = MiuixTheme.colorScheme
    val backdrop = rememberPageBlurBackdrop()
    val scrollBehavior = MiuixScrollBehavior()
    var showLicense by remember { mutableStateOf(false) }
    val licenseVisible = rememberLastNonNull(if (showLicense) OWN_LICENSE else null)

    Scaffold(
        topBar = {
            PageBlurBar(backdrop = backdrop, blurActive = backdrop != null) {
                SmallTopAppBar(
                    title = "关于",
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) Color.Transparent else scheme.surface,
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(ZhiIcons.back, contentDescription = "返回", tint = scheme.onBackground)
                        }
                    },
                )
            }
        },
    ) { insets ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = insets.calculateTopPadding() + 28.dp,
                    bottom = insets.calculateBottomPadding() + 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "hero") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Card(
                            modifier = Modifier.size(104.dp).graphicsLayer { clip = true },
                            cornerRadius = ZhiRadius.card,
                            colors = CardDefaults.defaultColors(
                                color = scheme.primary,
                                contentColor = scheme.onPrimary,
                            ),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = ZhiIcons.terminal,
                                    contentDescription = null,
                                    tint = scheme.onPrimary,
                                    modifier = Modifier.size(52.dp),
                                )
                            }
                        }
                        Text(
                            text = "ZhiCode",
                            color = scheme.onBackground,
                            // 原来是写死的 35sp。规范要求字阶只从 Miuix 取，
                            // 而 `title1` 正是"页面主标题"这一档（32sp）——
                            // 差 3sp 的观感远小于"多出第二个字号来源"的代价。
                            style = MiuixTheme.textStyles.title1,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Text(
                            text = "Android 上的 AI 编程工作区",
                            color = scheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            text = "版本 ${BuildConfig.VERSION_NAME} · 构建 ${BuildConfig.VERSION_CODE}",
                            color = scheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
                item(key = "links") {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ZhiSpacing.pageMargin),
                        cornerRadius = ZhiRadius.card,
                        insideMargin = PaddingValues(0.dp),
                        colors = CardDefaults.defaultColors(
                            color = scheme.surfaceContainer,
                            contentColor = scheme.onSurface,
                        ),
                    ) {
                        ArrowPreference(
                            title = "项目源码",
                            summary = "查看仓库、问题与发布记录",
                            onClick = { openUri(PROJECT_URL) },
                        )
                        ArrowPreference(
                            title = "本项目许可证",
                            summary = "${OWN_LICENSE} License",
                            onClick = { showLicense = true },
                        )
                        ArrowPreference(
                            title = "第三方开源项目",
                            summary = "组件清单、用途与许可证详情",
                            onClick = onOpenLicenses,
                        )
                    }
                }
                item(key = "credits") {
                    Text(
                        text = "ZhiCode 基于开源软件构建。各依赖与随应用分发组件继续适用各自的许可证；完整归属、修改说明与许可文本请查看第三方声明。",
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }

    OverlayDialog(
        show = showLicense,
        onDismissRequest = { showLicense = false },
        maxWidth = ZhiDialogWidth.Regular,
    ) {
        val license = licenseVisible ?: return@OverlayDialog
        DialogShell(
            title = "本项目许可证",
            actions = { PrimaryButton(text = "关闭", onClick = { showLicense = false }) },
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(text = "$license License", color = scheme.primary, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "本项目按 MIT 许可证发布。完整许可证正文请访问项目仓库中的 LICENSE 文件。",
                    color = scheme.onSurface,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    modifier = Modifier.padding(top = 10.dp),
                )
                ArrowPreference(
                    title = "在线查看 LICENSE",
                    onClick = { openUri("$PROJECT_URL/blob/main/LICENSE") },
                )
            }
        }
    }
}
