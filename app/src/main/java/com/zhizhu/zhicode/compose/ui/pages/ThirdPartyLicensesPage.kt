package com.zhizhu.zhicode.compose.ui.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.LayoutDirection
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
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroupHeader
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 第三方开源项目。
 *
 * ## 版式（照参考实现的分组卡片）
 *
 * 顶部一段说明 → 若干 `组标题 + 一张圆角卡`。卡里每行是
 * **组件名 + `作者 · 许可`**，行尾一个 `›`。组标题浮在底色上、不进卡，
 * 这样"分了哪几类"一眼可见，而每一类内部是连续的一摞行。
 *
 * 分组来自数据里的 [LicenseEntry.group]，按**首次出现顺序**渲染
 * （见 `Licenses.kt` 的字段说明）。
 *
 * ## 点击行为
 *
 * - 有 [LicenseEntry.homepage] → 打开上游主页，去看它自己的许可原文；
 * - 没有（目前只有 IQ Code）→ 弹出 `detail`，不画箭头。
 *   宁可少一个箭头，也不编一个打不开的链接。
 * - 任何一行**长按**都弹 `detail`：那是本应用对这一项做了什么改动的说明，
 *   只给名字与许可的话，用户不如直接去看 NOTICE。
 */
@OptIn(ExperimentalFoundationApi::class)
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

    // 分组：`groupBy` 返回 LinkedHashMap，保留每个组**首次出现**的位置。
    val groups = remember { THIRD_PARTY_LICENSES.groupBy { it.group } }

    Scaffold(
        topBar = {
            PageBlurBar(backdrop = backdrop, blurActive = backdrop != null) {
                SmallTopAppBar(
                    title = "第三方开源项目",
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) Color.Transparent else scheme.surface,
                    defaultWindowInsetsPadding = false,
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
                    top = insets.calculateTopPadding() + 8.dp,
                    bottom = insets.calculateBottomPadding() + 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "intro") {
                    Text(
                        text = "ZhiCode 用到了下面这些开源项目。点击任意一项可以打开它的主页，" +
                            "去看它自己的许可证原文；长按可以查看本应用对它的改动说明。",
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ZhiSpacing.pageMargin, vertical = 10.dp),
                    )
                }

                groups.forEach { (groupName, entries) ->
                    item(key = "group-$groupName") {
                        // 分组标签是**灰色小字**，不是 `SettingsGroupHeader`（那是 SmallTitle：
                        // 强调色 + 更重的字重）。参考图里分组只是分隔，不该抢行标题的注意力。
                        Text(
                            text = groupName,
                            color = scheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            modifier = Modifier.padding(
                                start = ZhiSpacing.pageMargin + 2.dp,
                                end = ZhiSpacing.pageMargin,
                                top = 14.dp,
                                bottom = 6.dp,
                            ),
                        )
                    }
                    item(key = "card-$groupName") {
                        Card(
                            modifier = Modifier.padding(horizontal = ZhiSpacing.pageMargin),
                            cornerRadius = CardDefaults.CornerRadius,
                            insideMargin = PaddingValues(0.dp),
                            colors = CardDefaults.defaultColors(
                                color = scheme.surfaceContainerHigh,
                                contentColor = scheme.onSurface,
                            ),
                        ) {
                            Column {
                                entries.forEach { entry ->
                                    LicenseRow(
                                        entry = entry,
                                        // 长按与点击都落到同一个"选中项"上，
                                        // 区别只在于点击同时会去开浏览器。
                                        onLongClick = {
                                            selectedIndex = THIRD_PARTY_LICENSES.indexOf(entry)
                                        },
                                        onClick = {
                                            val home = entry.homepage
                                            if (home != null) {
                                                openUri(home)
                                            } else {
                                                selectedIndex = THIRD_PARTY_LICENSES.indexOf(entry)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "notice") {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.padding(horizontal = ZhiSpacing.pageMargin),
                        cornerRadius = CardDefaults.CornerRadius,
                        insideMargin = PaddingValues(0.dp),
                        colors = CardDefaults.defaultColors(
                            color = scheme.surfaceContainerHigh,
                            contentColor = scheme.onSurface,
                        ),
                    ) {
                        LicenseRow(
                            entry = null,
                            title = "完整第三方声明（NOTICE）",
                            summary = "归属、修改说明与许可文本",
                            hasArrow = true,
                            onClick = { openUri("$PROJECT_URL/blob/main/NOTICE") },
                            onLongClick = {},
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
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
                text = "${item.author} · ${item.license}" +
                    if (item.version != "—") " · ${item.version}" else "",
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

/**
 * 列表行：**项目名称在上，`作者 · 开源协议` 在下一行、小字**。
 *
 * 不用 Miuix `BasicComponent` / `ArrowPreference`：那两个的 `summary` 是
 * `body2`（与正文同档），两行排出来一样重，看不出「名称是主、作者许可是辅」。
 * 这里直接写两个 `Text`，把字号差与颜色差显式固定下来。
 *
 * 行尾箭头只在 [hasArrow] 为真时画 —— `homepage` 为 null 的条目点了是弹详情，
 * 给它一个跳转箭头会误导。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LicenseRow(
    entry: LicenseEntry?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    title: String = entry?.name.orEmpty(),
    summary: String = entry?.let { "${it.author} · ${it.license}" }.orEmpty(),
    hasArrow: Boolean = entry?.homepage != null,
) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "查看改动说明",
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = scheme.onSurface,
                fontSize = MiuixTheme.textStyles.body1.fontSize,
            )
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    color = scheme.onSurfaceVariantSummary,
                    // 小字：比正文再低一档，与参考图的层次一致。
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (hasArrow) {
            val layoutDirection = LocalLayoutDirection.current
            Image(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                colorFilter = ColorFilter.tint(scheme.onSurfaceVariantActions),
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(width = 10.dp, height = 16.dp)
                    .graphicsLayer {
                        // RTL 下箭头要指左，与 Miuix `ArrowPreference` 一致。
                        scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                    },
            )
        }
    }
}
