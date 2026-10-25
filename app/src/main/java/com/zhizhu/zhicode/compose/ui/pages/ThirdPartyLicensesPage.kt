package com.zhizhu.zhicode.compose.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.LicenseEntry
import com.zhizhu.zhicode.compose.model.PROJECT_URL
import com.zhizhu.zhicode.compose.model.THIRD_PARTY_LICENSES
import com.zhizhu.zhicode.compose.theme.ZhiRadius
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
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 第三方组件许可 —— 形态**逐字照 Miuix 官方 example 的 `ThirdPartyLicensesPage`**。
 *
 * ## 与上一版的结构差别（这一版是照着官方 App 的真实截图重做的）
 *
 * | | 上一版 | 这一版（= example） |
 * |---|---|---|
 * | 分组 | 所有条目塞进**一张**卡 | **每条一张卡**，卡之间有间距 |
 * | 行尾 | `＋/－` 就地展开 | **`>` 箭头**，点进详情 |
 * | 列表壳 | `SettingsSubPage`（`SmallTitle` 分组） | 自有 `Scaffold` + 大标题 |
 *
 * 「每条一张卡」看着与设置页「一张卡装多行」相反，但两者语义不同：
 * 设置页那一卡是**一个分组的若干开关**（同类、常一起看）；这里的每一条是
 * **一个独立条目**（彼此无关、各自有详情），所以一卡一条才是对的。
 *
 * ## 箭头 vs 就地展开
 *
 * 上一版用 `＋/－` 是因为「清单里好几项没有主页，给它们一个点不开的行只是噪音」。
 * 这一版改成箭头是因为**它现在真的点得开**：点进去是一个详情页（`OverlayDialog`），
 * 显示这一条的 [LicenseEntry.detail]。箭头与"点进去"的语义因此是诚实的。
 *
 * 详情用 `OverlayDialog` 而不是再压一层 `AppScaffold` 页面栈：
 * 许可条目有十几条，为每一条各注册一个 `NavKey` 只为显示一段静态文本，
 * 换来的是导航栈里多出十几种可能的层深。弹层足够表达"看一眼就关"。
 */
@Composable
fun ThirdPartyLicensesPage(
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }

    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val barBlurBackdrop = rememberPageBlurBackdrop()

    Scaffold(
        topBar = {
            PageBlurBar(backdrop = barBlurBackdrop, blurActive = false) {
                TopAppBar(
                    title = "第三方组件许可",
                    scrollBehavior = topAppBarScrollBehavior,
                    color = colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = ZhiIcons.back,
                                contentDescription = "返回",
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "intro", contentType = "intro") {
                    Text(
                        text = "下列组件随本应用一起分发。点一条查看它的目的、改动与已知缺口；" +
                            "完整声明（含修改说明与尚未闭合的事项）见仓库根目录的 NOTICE。",
                        color = colorScheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }

                // 一条一张卡：每张卡就是**一个** ArrowPreference，行尾自动带箭头。
                items(
                    items = THIRD_PARTY_LICENSES,
                    key = { it.name },
                    contentType = { "license-card" },
                ) { entry ->
                    LicenseCard(
                        entry = entry,
                        onClick = {
                            selected = THIRD_PARTY_LICENSES.indexOf(entry)
                        },
                    )
                }

                item(key = "notice", contentType = "notice") {
                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        cornerRadius = ZhiRadius.card,
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        ArrowPreference(
                            title = "查看完整声明",
                            summary = "含每一项的修改说明、核对方式与已知缺口",
                            onClick = {
                                runCatching { uriHandler.openUri("$PROJECT_URL/blob/main/NOTICE") }
                            },
                        )
                    }
                }
            }

            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                // ⚠️ 必须是 `fillMaxHeight()`，不能是 `fillMaxSize()`。
                // 后者把宽度也撑满 → `align(CenterEnd)` 失去参照，滚动条会画到**屏幕中间**。
                // `SettingsSubPage.kt` 里同一处用的就是 `fillMaxHeight()`。
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }

    // 详情弹层。`selected` 为 null 时不显示；按 name 取条目而不是存整个对象，
    // 这样进程重启后（rememberSaveable 只存下标/基本类型）不会拿到过期副本。
    val entry: LicenseEntry? = selected?.let { idx ->
        THIRD_PARTY_LICENSES.getOrNull(idx)
    }
    LicenseDetailDialog(entry = entry, onDismiss = { selected = null })
}

/** 一张卡 = 一条许可。行尾的值是 `版本, 许可`（与 example 的副标题同形）。 */
@Composable
private fun LicenseCard(
    entry: LicenseEntry,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.padding(horizontal = 12.dp),
        cornerRadius = ZhiRadius.card,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(colorScheme.surfaceContainer),
    ) {
        ArrowPreference(
            title = entry.name,
            // summary 是标题**下方**那行 —— example 的第二行正是 `1.13.0, Apache-2.0`。
            summary = "${entry.version}, ${entry.license}",
            onClick = onClick,
        )
    }
}

/**
 * 详情弹层。
 *
 * 正文用 `verticalScroll` + `heightIn(max)` 而不是 `LazyColumn`：一条许可是**一段连续的文本**，
 * 不分组、不需要复用，`LazyColumn` 在这里只会带来一个无用的列表状态。
 */
@Composable
private fun LicenseDetailDialog(
    entry: LicenseEntry?,
    onDismiss: () -> Unit,
) {
    val shown = rememberLastNonNull(entry)
    top.yukonga.miuix.kmp.overlay.OverlayDialog(
        show = entry != null,
        onDismissRequest = onDismiss,
        maxWidth = ZhiDialogWidth.Wide,
    ) {
        val item = shown ?: return@OverlayDialog
        DialogShell(
            title = item.name,
            groupBody = true,
            actions = {
                PrimaryButton(text = "关闭", onClick = onDismiss)
            },
        ) {
            Text(
                text = "${item.version}, ${item.license}",
                color = colorScheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 10.dp),
            ) {
                Text(
                    text = item.detail,
                    color = colorScheme.onSurface,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
