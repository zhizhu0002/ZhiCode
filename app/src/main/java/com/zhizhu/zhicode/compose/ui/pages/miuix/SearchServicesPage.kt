package com.zhizhu.zhicode.compose.ui.pages.miuix

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.SearchField
import com.zhizhu.zhicode.compose.model.SearchFieldName
import com.zhizhu.zhicode.compose.model.SearchService
import com.zhizhu.zhicode.compose.model.SearchServiceDraft
import com.zhizhu.zhicode.compose.model.SearchServiceType
import com.zhizhu.zhicode.compose.model.SearchServicesState
import com.zhizhu.zhicode.compose.ui.ZhiFieldError
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsLoadingHint
import com.zhizhu.zhicode.compose.ui.settings.SettingsPageKey
import com.zhizhu.zhicode.compose.ui.settings.SettingsPageStack
import com.zhizhu.zhicode.compose.ui.settings.SettingsSubPage
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SearchServicesPage(
    state: SearchServicesState?,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onEdit: (SearchService) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDraftChange: ((SearchServiceDraft) -> SearchServiceDraft) -> Unit,
    onSave: () -> Unit,
    onCancelForm: () -> Unit,
) {
    if (state == null) return
    val form = state.form
    // 退出动画期间离场页仍在绘制，那时 form 已经是 null —— 见 rememberLastNonNull。
    val shownForm = rememberLastNonNull(form)
    SettingsPageStack(
        path = listOf(
            SettingsPageKey("search.list", 0),
            if (form != null) SettingsPageKey("search.form", 1) else null,
        ).filterNotNull(),
        onBack = if (form == null) onDismiss else onCancelForm,
    ) { key ->
        // ⚠️ 分支看**正在渲染的那一页**（key），不能看当前状态，否则转场会被硬切。
        val draft = if (key.id == "search.form") shownForm else null
        if (draft == null) {
            SettingsSubPage(
                title = "搜索服务",
                onBack = onDismiss,
                action = "新增" to onNew,
            ) {
                SearchServiceList(
                    state = state,
                    onEdit = onEdit,
                    onSelect = onSelect,
                    onDelete = onDelete,
                )
            }
        } else {
            SettingsSubPage(
                title = if (draft.isEditing) "编辑搜索服务" else "新增搜索服务",
                onBack = onCancelForm,
                action = "保存" to (if (draft.saveable) onSave else null),
            ) {
                SearchServiceForm(
                    draft = draft,
                    onChange = onDraftChange,
                    // 删除要在**编辑页**里也能做：列表上每行都有删除键，
                    // 但用户"点进来想删"是最自然的路径，不该逼他退回去找。
                    onDelete = { draft.id?.let(onDelete) },
                )
            }
        }
    }
}

@Composable
private fun SearchServiceList(
    state: SearchServicesState,
    onEdit: (SearchService) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column {
        SettingsGroup("已添加的服务") {
            // 载荷在 IO 上读（见 openSearchServices）：先于空态判断，
            // 否则读取期间会先闪一下「还没有添加搜索服务」。
            if (state.loading) {
                SettingsLoadingHint()
            } else if (state.services.isEmpty()) {
                // 真正走到这里只有一种情况：用户把种下的两条预设也删光了
                // （首次进入时会自动种 DuckDuckGo + Bing RSS，见 SearchServiceStore）。
                // 所以文案说的是"删光了"而不是"还没配过"。
                Text(
                    text = "服务列表是空的，搜索会回落到内置的免费后端。" +
                        "点右上角「新增」可以接回 DuckDuckGo / Bing，或接入 Tavily、Exa、Brave、" +
                        "Perplexity、SearXNG 等 —— 密钥存进系统加密存储，与其他 API 密钥同一套保护。",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            state.services.forEach { service ->
                val active = service.id == state.activeId
                // 副标题在选中时会整段换成「使用中 · …」并变主色 —— 这一句受 `active` 控制，
                // 硬切会像“文字闪了一下”，所以颜色也走淡变。
                val summaryColor by animateColorAsState(
                    targetValue = if (active) scheme.primary else scheme.onSurfaceVariantSummary,
                    animationSpec = ZhiMotion.colorSpec,
                    label = "searchServiceSummary",
                )
                BasicComponent(
                    title = service.name,
                    titleColor = BasicComponentDefaults.titleColor(color = scheme.onBackground),
                    summary = if (active) "使用中 · ${service.subtitle}" else service.subtitle,
                    summaryColor = BasicComponentDefaults.summaryColor(color = summaryColor),
                    // 点整行 = 设为当前使用（RikkaHub 就是这个交互）。
                    onClick = { onSelect(service.id) },
                    endActions = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (active) {
                                Icon(
                                    painter = ZhiIcons.check,
                                    contentDescription = "使用中",
                                    tint = scheme.primary,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            }
                            ZhiIconButton(
                                icon = ZhiIcons.edit,
                                description = "编辑",
                                onClick = { onEdit(service) },
                                compact = 34.dp,
                            )
                            ZhiIconButton(
                                icon = ZhiIcons.delete,
                                description = "删除",
                                tint = scheme.error,
                                onClick = { onDelete(service.id) },
                                compact = 34.dp,
                            )
                        }
                    },
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchServiceForm(
    draft: SearchServiceDraft,
    onChange: ((SearchServiceDraft) -> SearchServiceDraft) -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    // 见 ZhiFieldError：没碰过的字段不飘红。切换记录或服务类型时重新归零。
    var nameTouched by remember(draft.id) { mutableStateOf(false) }
    var fieldTouched by remember(draft.id, draft.type) { mutableStateOf(false) }
    var keyTouched by remember(draft.id, draft.type) { mutableStateOf(false) }
    Column {
        SettingsGroup("基本信息") {
            ZhiTextField(
                value = draft.name,
                onValueChange = { value ->
                    nameTouched = true
                    onChange { it.copy(name = value) }
                },
                label = "名称",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            ZhiFieldError(draft.nameError, nameTouched)

            OverlayDropdownPreference(
                items = SearchServiceType.entries.map { it.label },
                selectedIndex = SearchServiceType.entries.indexOf(draft.type).coerceAtLeast(0),
                title = "服务类型",
                summary = draft.type.detail,
                onSelectedIndexChange = { index ->
                    SearchServiceType.entries.getOrNull(index)?.let { type ->
                        // 换类型时把无关字段丢掉，并清空密钥输入：那家的 Key 对这家无效。
                        onChange {
                            it.copy(
                                type = type,
                                config = it.config.filterKeys { key -> type.fields.any { f -> f.name == key } },
                                apiKey = "",
                            )
                        }
                    }
                },
            )

            SwitchPreference(
                title = "启用",
                summary = "停用后不会被选为当前服务；配置仍然保留。",
                checked = draft.enabled,
                onCheckedChange = { value -> onChange { it.copy(enabled = value) } },
            )
        }

        SettingsGroup("连接与选项") {
            // 取 Key 的指引：RikkaHub 每个服务都带这个，值得照做 ——
            // 没有它，用户看到空 Key 框只能自己去找文档。
            draft.type.keyUrl?.let { url ->
                Text(
                    text = "申请地址：$url",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            // 字段按服务类型声明渲染：加一家服务只需要在枚举里加 fields，不碰这里。
            draft.type.fields.forEach { field ->
                SearchServiceField(
                    field = field,
                    draft = draft,
                    onChange = onChange,
                    onTouch = {
                        if (field.name == SearchFieldName.API_KEY) keyTouched = true
                        else fieldTouched = true
                    },
                )
            }
            ZhiFieldError(draft.fieldError, fieldTouched)
            ZhiFieldError(draft.keyError, keyTouched)
        }

        if (draft.isEditing) {
            SettingsGroup("危险操作") {
                BasicComponent(
                    title = "删除这条服务",
                    titleColor = BasicComponentDefaults.titleColor(color = scheme.error),
                    summary = "配置与已保存的密钥一并删除。",
                    summaryColor = BasicComponentDefaults.summaryColor(
                        color = scheme.onSurfaceVariantSummary,
                    ),
                    onClick = onDelete,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** 渲染一个 [SearchField]：有 [SearchField.options] 时用下拉，否则用文本框。 */
@Composable
private fun SearchServiceField(
    field: SearchField,
    draft: SearchServiceDraft,
    onChange: ((SearchServiceDraft) -> SearchServiceDraft) -> Unit,
    onTouch: () -> Unit,
) {
    val value = if (field.name == SearchFieldName.API_KEY) draft.apiKey else draft.config[field.name].orEmpty()

    if (field.options.isNotEmpty()) {
        val index = field.options.indexOf(value).let { if (it < 0) 0 else it }
        OverlayDropdownPreference(
            items = field.options,
            selectedIndex = index,
            title = field.label,
            summary = field.hint.ifBlank { null },
            onSelectedIndexChange = { picked ->
                onTouch()
                field.options.getOrNull(picked)?.let { option ->
                    onChange { it.copy(config = it.config + (field.name to option)) }
                }
            },
        )
        return
    }

    ZhiTextField(
        value = value,
        onValueChange = { text ->
            onTouch()
            if (field.name == SearchFieldName.API_KEY) {
                onChange { it.copy(apiKey = text) }
            } else {
                onChange { it.copy(config = it.config + (field.name to text)) }
            }
        },
        label = field.label,
        useLabelAsPlaceholder = true,
        singleLine = field.name != SearchFieldName.HEADERS,
        minLines = if (field.name == SearchFieldName.HEADERS) 3 else 1,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    )
    if (field.hint.isNotBlank()) {
        Text(
            text = field.hint,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        )
    }
}
