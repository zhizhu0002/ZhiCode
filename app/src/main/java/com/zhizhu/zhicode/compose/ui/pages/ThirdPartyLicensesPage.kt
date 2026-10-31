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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.LicenseEntry
import com.zhizhu.zhicode.compose.model.PROJECT_URL
import com.zhizhu.zhicode.compose.model.THIRD_PARTY_LICENSES
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Readable catalogue of bundled and referenced open-source projects and their licenses. */
@Composable
fun ThirdPartyLicensesPage(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val openUri: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val shownIndex = rememberLastNonNull(selectedIndex)
    val selected = shownIndex?.let(THIRD_PARTY_LICENSES::getOrNull)
    val scheme = MiuixTheme.colorScheme
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberPageBlurBackdrop()

    Scaffold(
        topBar = {
            PageBlurBar(backdrop = backdrop, blurActive = backdrop != null) {
                TopAppBar(
                    title = "第三方开源项目",
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) Color.Transparent else scheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painter = ZhiIcons.back, contentDescription = "返回", tint = scheme.onBackground)
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
                    top = insets.calculateTopPadding() + 12.dp,
                    bottom = insets.calculateBottomPadding() + 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "intro") {
                    Text(
                        text = "列出本应用随附或参考的开源项目、版本与许可证。点选项目可查看用途及修改说明。",
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ZhiSpacing.pageMargin, vertical = 4.dp),
                    )
                }
                items(THIRD_PARTY_LICENSES.indices.toList(), key = { THIRD_PARTY_LICENSES[it].name }) { index ->
                    val item = THIRD_PARTY_LICENSES[index]
                    Card(
                        modifier = Modifier.padding(horizontal = ZhiSpacing.pageMargin),
                        cornerRadius = ZhiRadius.card,
                        insideMargin = PaddingValues(0.dp),
                        colors = CardDefaults.defaultColors(
                            color = scheme.surfaceContainerHigh,
                            contentColor = scheme.onSurface,
                        ),
                    ) {
                        ArrowPreference(
                            title = item.name,
                            summary = "${item.version} · ${item.license}",
                            onClick = { selectedIndex = index },
                        )
                    }
                }
                item(key = "notice") {
                    Card(
                        modifier = Modifier.padding(horizontal = ZhiSpacing.pageMargin),
                        cornerRadius = ZhiRadius.card,
                        insideMargin = PaddingValues(0.dp),
                        colors = CardDefaults.defaultColors(
                            color = scheme.surfaceContainerHigh,
                            contentColor = scheme.onSurface,
                        ),
                    ) {
                        ArrowPreference(
                            title = "完整第三方声明（NOTICE）",
                            summary = "归属、修改说明与许可文本",
                            onClick = { openUri("$PROJECT_URL/blob/main/NOTICE") },
                        )
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }
        }
    }

    OverlayDialog(
        show = selectedIndex != null,
        onDismissRequest = { selectedIndex = null },
        maxWidth = ZhiDialogWidth.Wide,
    ) {
        val item = selected ?: return@OverlayDialog
        DialogShell(
            title = item.name,
            groupBody = true,
            actions = { PrimaryButton(text = "关闭", onClick = { selectedIndex = null }) },
        ) {
            Text(
                text = "${item.version} · ${item.license}",
                color = scheme.primary,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
            )
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()).padding(top = 10.dp),
            ) {
                Text(
                    text = item.detail,
                    color = scheme.onSurface,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
