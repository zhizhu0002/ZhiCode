package com.zhizhu.zhicode.compose.ui.pages.miuix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.zhizhu.zhicode.compose.ui.ProviderBrandIconBox
import com.zhizhu.zhicode.compose.ui.ZhiIconDropdownMenu
import com.zhizhu.zhicode.compose.ui.ZhiMenuItem
import com.zhizhu.zhicode.compose.ui.ZhiSmallPill
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.ApiConfigState
import com.zhizhu.zhicode.compose.model.ApiProfile
import com.zhizhu.zhicode.compose.model.ApiProfileDraft
import com.zhizhu.zhicode.compose.model.ApiProtocol
import com.zhizhu.zhicode.compose.model.COMPACT_PERCENT_MAX
import com.zhizhu.zhicode.compose.model.COMPACT_PERCENT_MIN
import com.zhizhu.zhicode.compose.model.ModelOption
import com.zhizhu.zhicode.compose.ui.ZhiFieldError
import com.zhizhu.zhicode.compose.ui.ZhiIconButton
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.ZhiMotion
import com.zhizhu.zhicode.compose.ui.ZhiSectionLabel
import com.zhizhu.zhicode.compose.ui.ZhiTextField
import com.zhizhu.zhicode.compose.ui.dialogs.SecondaryButton
import com.zhizhu.zhicode.compose.ui.settings.SettingsEntry
import com.zhizhu.zhicode.compose.ui.settings.SettingsGroup
import com.zhizhu.zhicode.compose.ui.settings.SettingsIconPlate
import com.zhizhu.zhicode.compose.ui.settings.SettingsLoadingHint
import com.zhizhu.zhicode.compose.ui.settings.SettingsPageKey
import com.zhizhu.zhicode.compose.ui.settings.SettingsPageStack
import com.zhizhu.zhicode.compose.ui.settings.SettingsPlateColors
import com.zhizhu.zhicode.compose.ui.settings.SettingsSubPage
import com.zhizhu.zhicode.compose.ui.settings.SettingsTextField
import com.zhizhu.zhicode.compose.ui.settings.SettingsToggle
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import com.zhizhu.zhicode.pi.PiCatalogAndPresets
import com.zhizhu.zhicode.pi.PiModelConfig
import com.zhizhu.zhicode.pi.PiProviderPreset
import com.zhizhu.zhicode.pi.PiThinkingLevel
import com.zhizhu.zhicode.pi.resolveSupportedThinkingLevels
import kotlinx.coroutines.launch
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val ZCODE_GATEWAY = "https://zcode.z.ai/api/v1/zcode-plan"
private const val ZCODE_HEADERS_JSON = """{"X-ZCode-App-Version":"3.14.3"}"""

/** CC Switch 风格供应商管理（两步预设 + 模型折叠编辑器 + 高级参数 + 上下文与提示词整合）。 */
@Composable
fun ApiConfigPage(
    config: ApiConfigState?,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onEdit: (ApiProfile) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDraftChange: ((ApiProfileDraft) -> ApiProfileDraft) -> Unit,
    onSave: () -> Unit,
    onCancelForm: () -> Unit,
    onApplyPreset: (PiProviderPreset) -> Unit = {},
    onFetchDraftModels: suspend (ApiProfileDraft) -> Result<List<ModelOption>> = {
        Result.failure(IllegalStateException("未接入模型拉取"))
    },
    onUpdateContextAndPrompt: (Boolean, Int, String) -> Unit = { _, _, _ -> },
    onOpenPrompts: () -> Unit = {},
) {
    if (config == null) return
    val form = config.form
    val shownForm = rememberLastNonNull(form)
    val standalone = config.standalone
    val path = buildList {
        if (!standalone) add(SettingsPageKey("api.list", 0))
        if (form != null) {
            if (!form.isEditing) {
                add(SettingsPageKey("api.preset", if (standalone) 0 else 1))
            }
            if (form.isEditing || !form.pickingPreset) {
                val formDepth = when {
                    standalone && form.isEditing -> 0
                    standalone -> 1
                    form.isEditing -> 1
                    else -> 2
                }
                add(SettingsPageKey("api.form", formDepth))
            }
        }
    }
    val handleBack: () -> Unit = when {
        form == null -> onDismiss
        !form.isEditing && !form.pickingPreset -> {
            { onDraftChange { it.copy(pickingPreset = true) } }
        }
        standalone -> onDismiss
        else -> onCancelForm
    }
    SettingsPageStack(
        path = path,
        onBack = handleBack,
    ) { key ->
        when (key.id) {
            "api.list" -> SettingsSubPage(
                title = "供应商与模型管理",
                onBack = onDismiss,
                action = "新增" to onNew,
            ) {
                ApiProfileList(
                    config = config,
                    onEdit = onEdit,
                    onSelect = onSelect,
                    onDelete = onDelete,
                    onUpdateContextAndPrompt = onUpdateContextAndPrompt,
                    onOpenPrompts = onOpenPrompts,
                )
            }
            "api.preset" -> {
                val presetDraft = shownForm ?: ApiProfileDraft.blank()
                SettingsSubPage(
                    title = "选择供应商模板",
                    onBack = if (standalone) onDismiss else onCancelForm,
                ) {
                    PresetPickerStep(
                        selectedPresetId = presetDraft.presetId,
                        onPick = { preset -> onApplyPreset(preset) },
                    )
                }
            }
            else -> {
                val draft = shownForm ?: return@SettingsPageStack
                SettingsSubPage(
                    title = if (draft.isEditing) "编辑供应商" else "新增供应商",
                    onBack = {
                        if (!draft.isEditing) {
                            onDraftChange { it.copy(pickingPreset = true) }
                        } else if (standalone) {
                            onDismiss()
                        } else {
                            onCancelForm()
                        }
                    },
                    action = "保存" to (if (draft.saveable) onSave else null),
                ) {
                    ApiProfileForm(
                        draft = draft,
                        onChange = onDraftChange,
                        onSwitchPresetStep = { onDraftChange { it.copy(pickingPreset = true) } },
                        onFetchDraftModels = onFetchDraftModels,
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiProfileList(
    config: ApiConfigState,
    onEdit: (ApiProfile) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onUpdateContextAndPrompt: (Boolean, Int, String) -> Unit,
    onOpenPrompts: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column {
        SettingsGroup("已配置供应商") {
            if (config.loading) {
                SettingsLoadingHint()
            } else if (config.profiles.isEmpty()) {
                Text(
                    text = "还没有供应商配置。点右上角「新增」选择自定义供应商或主流预设，API Key 会存进系统加密存储。",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            config.profiles.forEach { profile ->
                val active = profile.id == config.activeId
                val titleColor by animateColorAsState(
                    targetValue = if (active) scheme.primary else scheme.onBackground,
                    animationSpec = ZhiMotion.colorSpec,
                    label = "apiProfileTitle",
                )
                BasicComponent(
                    title = profile.name,
                    titleColor = BasicComponentDefaults.titleColor(color = titleColor),
                    summary = "${profile.summary} · 已配 ${profile.effectiveModels().size} 个模型",
                    summaryColor = BasicComponentDefaults.summaryColor(color = scheme.onSurfaceVariantSummary),
                    startAction = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ProviderBrandIconBox(
                                icon = PiCatalogAndPresets.resolveIconKey(profile.providerKey, profile.name),
                                name = profile.name,
                                size = 30.dp,
                            )
                            if (active) {
                                Icon(
                                    painter = ZhiIcons.check,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(DropdownDefaults.CheckIconSize),
                                )
                            }
                        }
                    },
                    endActions = {
                        ZhiIconButton(
                            icon = ZhiIcons.edit,
                            description = "编辑",
                            onClick = { onEdit(profile) },
                            iconSize = 15.dp,
                        )
                        ZhiIconButton(
                            icon = ZhiIcons.close,
                            description = "删除",
                            onClick = { onDelete(profile.id) },
                            iconSize = 15.dp,
                        )
                    },
                    onClick = { if (!active) onSelect(profile.id) },
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        SettingsGroup("上下文与提示词") {
            SettingsToggle(
                title = "上下文压缩",
                checked = config.autoCompact,
                onCheckedChange = { enabled ->
                    onUpdateContextAndPrompt(enabled, config.autoCompactPercent, config.customSystemPrompt)
                },
                icon = ZhiIcons.compress,
                plate = SettingsPlateColors.green,
            )
            AnimatedVisibility(
                visible = config.autoCompact,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                SliderPreference(
                    title = "自动压缩上限（50-100%）",
                    value = config.autoCompactPercent.toFloat(),
                    onValueChange = { value ->
                        onUpdateContextAndPrompt(config.autoCompact, value.toInt(), config.customSystemPrompt)
                    },
                    valueRange = COMPACT_PERCENT_MIN.toFloat()..COMPACT_PERCENT_MAX.toFloat(),
                    steps = COMPACT_PERCENT_MAX - COMPACT_PERCENT_MIN - 1,
                    valueText = "${config.autoCompactPercent}%",
                    summary = "达到当前模型上下文窗口的 ${config.autoCompactPercent}% 时触发压缩",
                    startAction = { SettingsIconPlate(icon = ZhiIcons.tune, color = SettingsPlateColors.green) },
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            SettingsTextField(
                title = "自定义头部提示词",
                value = config.customSystemPrompt,
                onValueChange = { text ->
                    onUpdateContextAndPrompt(config.autoCompact, config.autoCompactPercent, text)
                },
                summary = "随请求发送，下一任务生效；不能覆盖安全规则，勿填密钥。",
                singleLine = false,
                minLines = 4,
                icon = ZhiIcons.edit,
                plate = SettingsPlateColors.orange,
            )
            SettingsEntry(
                title = "提示词模板",
                valueText = "自定义 /init、压缩摘要与开场段；留空即用默认",
                onClick = onOpenPrompts,
                icon = ZhiIcons.edit,
                plate = SettingsPlateColors.purple,
            )
        }
    }
}

private enum class PresetCategoryTab(val id: String, val label: String) {
    ALL("all", "全部"),
    OFFICIAL("official", "官方"),
    CLOUD("cloud", "云厂商"),
    LOCAL("local", "本地"),
    AGGREGATOR("aggregator", "聚合 / 三方"),
}

/** CC Switch 风格预设选择步：最顶部自定义供应商按钮 -> 分隔线 -> 搜索与分类 + 双列预设网格。 */
@Composable
private fun PresetPickerStep(
    selectedPresetId: String,
    onPick: (PiProviderPreset) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val customPreset = remember {
        PiCatalogAndPresets.presets.firstOrNull { it.isCustom } ?: PiCatalogAndPresets.presets.first()
    }
    val builtInPresets = remember {
        PiCatalogAndPresets.presets.filter { !it.isCustom }
    }
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(PresetCategoryTab.ALL) }

    val filteredPresets = remember(builtInPresets, query, selectedTab) {
        val needle = query.trim().lowercase()
        builtInPresets.filter { preset ->
            val catOk = when (selectedTab) {
                PresetCategoryTab.ALL -> true
                PresetCategoryTab.OFFICIAL -> preset.category == "official" || preset.category == "cn_official"
                PresetCategoryTab.CLOUD -> preset.category == "cloud_provider"
                PresetCategoryTab.LOCAL -> preset.category == "local"
                PresetCategoryTab.AGGREGATOR -> preset.category == "aggregator" || preset.category == "third_party"
            }
            if (!catOk) return@filter false
            if (needle.isEmpty()) return@filter true
            preset.name.lowercase().contains(needle) ||
                preset.providerKey.lowercase().contains(needle) ||
                preset.baseUrl.lowercase().contains(needle) ||
                preset.api.label.lowercase().contains(needle)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = if (selectedPresetId == customPreset.id) scheme.primaryContainer else scheme.surfaceContainer,
            ),
        ) {
            BasicComponent(
                title = "自定义供应商",
                summary = "手动配置接口协议、Base URL、API Key 与模型及思考档位",
                startAction = {
                    SettingsIconPlate(icon = ZhiIcons.tune, color = SettingsPlateColors.blue)
                },
                endActions = {
                    Icon(
                        painter = ZhiIcons.expand,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = { onPick(customPreset) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

        ZhiSectionLabel(
            text = "内置供应商预设（共 ${builtInPresets.size} 个）",
            insideMargin = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ZhiTextField(
                value = query,
                onValueChange = { query = it },
                label = "搜索名称、域名或协议…",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(visible = query.isNotEmpty()) {
                ZhiIconButton(
                    icon = ZhiIcons.close,
                    description = "清空搜索",
                    onClick = { query = "" },
                    iconSize = 15.dp,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PresetCategoryTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val chipBg by animateColorAsState(
                    targetValue = if (isSelected) scheme.primary else scheme.surfaceContainer,
                    animationSpec = ZhiMotion.colorSpec,
                    label = "presetCatBg",
                )
                val chipFg by animateColorAsState(
                    targetValue = if (isSelected) scheme.onPrimary else scheme.onBackground,
                    animationSpec = ZhiMotion.colorSpec,
                    label = "presetCatFg",
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(chipBg)
                        .border(
                            width = 0.75.dp,
                            color = if (isSelected) Color.Transparent else scheme.outline.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(16.dp),
                        )
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = tab.label,
                        color = chipFg,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        if (filteredPresets.isEmpty()) {
            Text(
                text = "未找到匹配的供应商预设，可点顶部「自定义供应商」手动创建。",
                color = scheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            )
        } else {
            filteredPresets.chunked(2).forEach { rowPresets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowPresets.forEach { preset ->
                        PresetGridCard(
                            preset = preset,
                            selected = preset.id == selectedPresetId,
                            onClick = { onPick(preset) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowPresets.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetGridCard(
    preset: PiProviderPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    val domain = remember(preset.baseUrl) {
        preset.baseUrl.removePrefix("https://").removePrefix("http://").substringBefore('/').ifBlank { preset.api.label }
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(
            color = if (selected) scheme.primaryContainer else scheme.surfaceContainer,
        ),
    ) {
        BasicComponent(
            title = preset.name,
            summary = domain,
            startAction = {
                ProviderBrandIconBox(
                    icon = preset.icon,
                    name = preset.name,
                    size = 32.dp,
                )
            },
            onClick = onClick,
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ApiProfileForm(
    draft: ApiProfileDraft,
    onChange: ((ApiProfileDraft) -> ApiProfileDraft) -> Unit,
    onSwitchPresetStep: () -> Unit,
    onFetchDraftModels: suspend (ApiProfileDraft) -> Result<List<ModelOption>>,
) {
    val scheme = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    var allowCleartext by remember(draft.id) {
        mutableStateOf(draft.baseUrl.startsWith("http://", ignoreCase = true))
    }
    var nameTouched by remember(draft.id) { mutableStateOf(false) }
    var baseUrlTouched by remember(draft.id) { mutableStateOf(false) }
    var expandedModelKeys by remember(draft.id) {
        mutableStateOf(draft.models.firstOrNull()?.let { setOf(it.id) } ?: emptySet())
    }
    var expandedThinkingKeys by remember(draft.id) { mutableStateOf<Set<String>>(emptySet()) }
    var advancedExpanded by remember(draft.id) { mutableStateOf(false) }
    var fetchingCatalog by remember(draft.id) { mutableStateOf(false) }
    var fetchStatus by remember(draft.id) { mutableStateOf("") }
    var fetchedModels by remember(draft.id) { mutableStateOf<List<ModelOption>>(emptyList()) }
    var showAllFetched by remember(draft.id) { mutableStateOf(false) }

    LaunchedEffect(draft.baseUrl) {
        val url = draft.baseUrl
        if (url.startsWith("http://", ignoreCase = true)) {
            allowCleartext = true
        } else if (url.startsWith("https://", ignoreCase = true)) {
            allowCleartext = false
        }
    }

    Column {
        if (!draft.isEditing) {
            val activePreset = PiCatalogAndPresets.presets.firstOrNull { it.id == draft.presetId }
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                colors = CardDefaults.defaultColors(color = scheme.surfaceContainer),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ProviderBrandIconBox(
                        icon = activePreset?.icon.orEmpty(),
                        name = activePreset?.name ?: "自定义",
                        size = 32.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "当前模板：${activePreset?.name ?: "自定义供应商"}",
                            color = scheme.onBackground,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            fontWeight = FontWeight.Medium,
                        )
                        if (activePreset != null && !activePreset.isCustom) {
                            Text(
                                text = activePreset.api.label,
                                color = scheme.onSurfaceVariantSummary,
                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            )
                        }
                    }
                    SecondaryButton(text = "更换模板", onClick = onSwitchPresetStep)
                }
            }
        }

        SettingsGroup("基本连接") {
            ZhiTextField(
                value = draft.providerKey,
                onValueChange = { value ->
                    val cleaned = value.lowercase().replace(Regex("[^a-z0-9-]"), "")
                    onChange { it.copy(providerKey = cleaned) }
                },
                label = "Pi Provider Key（小写字母/数字/连字符，留空自动派生）",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
            ZhiTextField(
                value = draft.name,
                onValueChange = { value ->
                    nameTouched = true
                    onChange { it.copy(name = value) }
                },
                label = "供应商显示名称",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )

            val protocols = remember {
                ApiProtocol.entries.filter {
                    !it.debugOnly || com.zhizhu.zhicode.compose.BuildConfig.DEBUG
                }
            }
            OverlayDropdownPreference(
                items = protocols.map { it.label },
                selectedIndex = protocols.indexOf(draft.protocol).coerceAtLeast(0),
                title = "接口格式",
                summary = null,
                onSelectedIndexChange = { index ->
                    protocols.getOrNull(index)?.let { protocol ->
                        onChange { draftNow ->
                            val autoFill = protocol == ApiProtocol.ZCODE && draftNow.baseUrl.isBlank()
                            if (autoFill) {
                                baseUrlTouched = true
                                draftNow.copy(
                                    protocol = protocol,
                                    baseUrl = ZCODE_GATEWAY,
                                    extraHeaders = ZCODE_HEADERS_JSON,
                                )
                            } else {
                                draftNow.copy(protocol = protocol)
                            }
                        }
                    }
                },
            )

            ZhiTextField(
                value = draft.apiKey,
                onValueChange = { value -> onChange { it.copy(apiKey = value) } },
                label = when {
                    draft.isEditing -> "API Key（留空 = 沿用原密钥）"
                    draft.protocol == ApiProtocol.ZCODE -> "ZCode 授权码"
                    else -> "API Key"
                },
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )

            ZhiTextField(
                value = draft.baseUrl,
                onValueChange = { value ->
                    baseUrlTouched = true
                    onChange { it.copy(baseUrl = value) }
                },
                label = if (draft.protocol == ApiProtocol.BEDROCK_CONVERSE_STREAM) "Base URL（Bedrock 可留空）" else "Base URL",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )

            if (draft.protocol == ApiProtocol.ZCODE) {
                BasicComponent(
                    title = "重置为 ZCode 默认值",
                    summary = "恢复默认网关地址与版本请求头",
                    onClick = {
                        baseUrlTouched = true
                        onChange { it.copy(baseUrl = ZCODE_GATEWAY, extraHeaders = ZCODE_HEADERS_JSON) }
                    },
                )
            }

            SwitchPreference(
                title = "允许明文 HTTP",
                summary = if (allowCleartext) "当前地址走 http（不加密）" else "仅使用 https",
                checked = allowCleartext,
                onCheckedChange = { checked ->
                    allowCleartext = checked
                    onChange { current ->
                        val url = current.baseUrl
                        when {
                            checked && url.startsWith("https://", ignoreCase = true) ->
                                current.copy(baseUrl = "http://" + url.drop(8))
                            !checked && url.startsWith("http://", ignoreCase = true) ->
                                current.copy(baseUrl = "https://" + url.drop(7))
                            else -> current
                        }
                    }
                },
            )
        }

        SettingsGroup("模型配置") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (draft.models.isEmpty()) "尚未添加模型" else "已添加模型（${draft.models.size}）",
                    color = scheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = if (fetchingCatalog) "拉取中…" else "获取模型",
                    onClick = {
                        if (!fetchingCatalog) {
                            fetchingCatalog = true
                            fetchStatus = "正在从端点请求模型列表…"
                            scope.launch {
                                val result = onFetchDraftModels(draft)
                                val models = result.getOrNull()
                                fetchingCatalog = false
                                if (models == null) {
                                    fetchStatus = result.exceptionOrNull()?.message ?: "拉取失败"
                                } else {
                                    fetchedModels = models
                                    showAllFetched = false
                                    fetchStatus = if (models.isEmpty()) {
                                        "端点未返回可用模型，请手动添加"
                                    } else {
                                        "从端点获取到 ${models.size} 个模型（点击添加到上方已添加列表）"
                                    }
                                }
                            }
                        }
                    },
                )
                SecondaryButton(
                    text = "+ 添加模型",
                    onClick = {
                        val suffix = draft.models.size + 1
                        val freshId = if (draft.models.none { it.id == "new-model-$suffix" }) {
                            "new-model-$suffix"
                        } else {
                            "model-${System.currentTimeMillis() % 10000}"
                        }
                        val fresh = PiModelConfig(
                            id = freshId,
                            name = "",
                            reasoning = true,
                            supportsImage = true,
                        )
                        onChange { current ->
                            current.copy(
                                models = current.models + fresh,
                                model = current.model.ifBlank { fresh.id },
                            )
                        }
                        expandedModelKeys = expandedModelKeys + fresh.id
                    },
                )
            }

            draft.models.forEachIndexed { index, modelCfg ->
                val itemKey = "${index}:${modelCfg.id}"
                val isExpanded = (modelCfg.id in expandedModelKeys) || (itemKey in expandedModelKeys)
                val thinkingExpanded = (modelCfg.id in expandedThinkingKeys) || (itemKey in expandedThinkingKeys)
                val isDefault = modelCfg.id == draft.model.trim()
                val levels = resolveSupportedThinkingLevels(modelCfg)
                val capSummary = buildString {
                    append(modelCfg.id)
                    append(" · ")
                    append(if (modelCfg.reasoning) "推理(${levels.joinToString("/") { it.id }})" else "非推理")
                    append(" · ")
                    append(if (modelCfg.supportsImage) "视觉" else "纯文本")
                    append(" · ")
                    append("${modelCfg.contextWindow / 1000}K/${modelCfg.maxTokens / 1000}K")
                }

                BasicComponent(
                    title = modelCfg.name.ifBlank { modelCfg.id } + if (isDefault) "（默认）" else "",
                    summary = capSummary,
                    startAction = {
                        Icon(
                            painter = if (isExpanded) ZhiIcons.collapse else ZhiIcons.expand,
                            contentDescription = "展开或收起",
                            tint = scheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    endActions = {
                        if (!isDefault) {
                            ZhiIconButton(
                                icon = ZhiIcons.check,
                                description = "设为默认模型",
                                onClick = { onChange { it.copy(model = modelCfg.id) } },
                                iconSize = 15.dp,
                            )
                        }
                        ZhiIconButton(
                            icon = ZhiIcons.delete,
                            description = "删除模型",
                            onClick = {
                                onChange { current ->
                                    val next = current.models.filterIndexed { i, _ -> i != index }
                                    val nextDefault = if (current.model.trim() == modelCfg.id) {
                                        next.firstOrNull()?.id.orEmpty()
                                    } else {
                                        current.model
                                    }
                                    current.copy(models = next, model = nextDefault)
                                }
                            },
                            iconSize = 15.dp,
                        )
                    },
                    onClick = {
                        expandedModelKeys = if (isExpanded) {
                            expandedModelKeys - modelCfg.id - itemKey
                        } else {
                            expandedModelKeys + modelCfg.id + itemKey
                        }
                    },
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                )

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    PiModelCardEditor(
                        model = modelCfg,
                        isDefault = isDefault,
                        thinkingMapExpanded = thinkingExpanded,
                        onToggleThinkingMap = {
                            expandedThinkingKeys = if (thinkingExpanded) {
                                expandedThinkingKeys - modelCfg.id - itemKey
                            } else {
                                expandedThinkingKeys + modelCfg.id + itemKey
                            }
                        },
                        onSetDefault = { onChange { it.copy(model = modelCfg.id) } },
                        onUpdate = { updated ->
                            val wasDefault = draft.model.trim() == modelCfg.id
                            onChange { current ->
                                current.copy(
                                    models = current.models.mapIndexed { i, existing -> if (i == index) updated else existing },
                                    model = if (wasDefault) updated.id else current.model,
                                )
                            }
                        },
                    )
                }
            }

            if (fetchedModels.isNotEmpty() || fetchStatus.isNotBlank()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                if (fetchStatus.isNotBlank()) {
                    Text(
                        text = fetchStatus,
                        color = scheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (fetchedModels.isNotEmpty()) {
                    val configuredIds = draft.models.mapTo(HashSet()) { it.id }
                    val visibleFetched = if (showAllFetched) fetchedModels else fetchedModels.take(15)
                    visibleFetched.forEach { opt ->
                        val added = opt.id in configuredIds
                        BasicComponent(
                            title = opt.displayName,
                            summary = if (added) "${opt.id} · 已在上方列表" else "${opt.id} · 点击添加并自动匹配参数",
                            endActions = {
                                Icon(
                                    painter = if (added) ZhiIcons.check else ZhiIcons.add,
                                    contentDescription = if (added) "已添加" else "添加到列表",
                                    tint = if (added) scheme.primary else scheme.onSurfaceVariantSummary,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            onClick = {
                                if (!added) {
                                    val inferred = PiCatalogAndPresets.inferModelConfig(
                                        id = opt.id,
                                        displayName = opt.displayName,
                                        api = draft.protocol.toPiFormat(),
                                    )
                                    onChange { current ->
                                        if (current.models.any { it.id == opt.id }) current
                                        else current.copy(
                                            models = current.models + inferred,
                                            model = current.model.ifBlank { opt.id },
                                        )
                                    }
                                    expandedModelKeys = expandedModelKeys + opt.id
                                }
                            },
                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (!showAllFetched && fetchedModels.size > 15) {
                        BasicComponent(
                            title = "展开剩余 ${fetchedModels.size - 15} 个获取到的模型",
                            onClick = { showAllFetched = true },
                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        SettingsGroup("高级选项") {
            BasicComponent(
                title = "自定义请求头与兼容性参数",
                summary = if (advancedExpanded) "点击收起 headers 与 compat" else "点击展开 headers 键值对与 compat JSON",
                startAction = {
                    Icon(
                        painter = if (advancedExpanded) ZhiIcons.collapse else ZhiIcons.expand,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = { advancedExpanded = !advancedExpanded },
            )
            AnimatedVisibility(visible = advancedExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    RequestHeadersKvEditor(
                        rawJson = draft.extraHeaders,
                        onUpdateJson = { json -> onChange { it.copy(extraHeaders = json) } },
                    )
                    ZhiTextField(
                        value = draft.compatJson,
                        onValueChange = { value -> onChange { it.copy(compatJson = value) } },
                        label = "兼容性 compat (JSON，如 {\"thinkingFormat\":\"deepseek\"})",
                        useLabelAsPlaceholder = true,
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        ZhiFieldError(
            message = draft.nameError ?: draft.baseUrlError,
            touched = nameTouched || baseUrlTouched,
        )
    }
}

/** CC Switch PiProviderForm 同款单模型展开编辑区（ID/名称/推理/视觉/窗口/thinkingLevelMap）。 */
@Composable
private fun PiModelCardEditor(
    model: PiModelConfig,
    isDefault: Boolean,
    thinkingMapExpanded: Boolean,
    onToggleThinkingMap: () -> Unit,
    onSetDefault: () -> Unit,
    onUpdate: (PiModelConfig) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    var ctxInput by remember(model.id, model.contextWindow) {
        mutableStateOf(model.contextWindow.toString())
    }
    var maxOutInput by remember(model.id, model.maxTokens) {
        mutableStateOf(model.maxTokens.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ZhiTextField(
            value = model.id,
            onValueChange = { raw ->
                val cleaned = raw.trim()
                if (cleaned.isNotEmpty()) {
                    onUpdate(model.copy(id = cleaned))
                } else {
                    onUpdate(model.copy(id = ""))
                }
            },
            label = "模型 ID（如 deepseek-chat / claude-sonnet-4-6）",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ZhiTextField(
                value = model.name,
                onValueChange = { onUpdate(model.copy(name = it)) },
                label = "显示名称（可留空同 ID）",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            if (!isDefault) {
                SecondaryButton(text = "设为默认", onClick = onSetDefault)
            }
        }

        SwitchPreference(
            title = "支持推理 (Reasoning)",
            checked = model.reasoning,
            onCheckedChange = { checked -> onUpdate(model.copy(reasoning = checked)) },
        )
        SwitchPreference(
            title = "支持图片输入 (Vision)",
            checked = model.supportsImage,
            onCheckedChange = { checked -> onUpdate(model.copy(supportsImage = checked)) },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ZhiTextField(
                value = ctxInput,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }
                    ctxInput = digits
                    digits.toIntOrNull()?.takeIf { it >= PiModelConfig.MIN_CONTEXT_WINDOW }?.let {
                        onUpdate(model.copy(contextWindow = it))
                    }
                },
                label = "上下文窗口",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            ZhiTextField(
                value = maxOutInput,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }
                    maxOutInput = digits
                    digits.toIntOrNull()?.takeIf { it >= PiModelConfig.MIN_MAX_TOKENS }?.let {
                        onUpdate(model.copy(maxTokens = it))
                    }
                },
                label = "最大输出",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        if (model.reasoning) {
            BasicComponent(
                title = "推理档位映射 (thinkingLevelMap)",
                summary = "当前可用：${resolveSupportedThinkingLevels(model).joinToString(" / ") { it.id }} · 已自定义 ${model.thinkingLevelMap.size} 项",
                startAction = {
                    Icon(
                        painter = if (thinkingMapExpanded) ZhiIcons.collapse else ZhiIcons.expand,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(16.dp),
                    )
                },
                onClick = onToggleThinkingMap,
            )
            AnimatedVisibility(
                visible = thinkingMapExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                val configuredLevels = PiThinkingLevel.entries.filter { it in model.thinkingLevelMap }
                val remainingLevels = PiThinkingLevel.entries.filter { it !in model.thinkingLevelMap }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (configuredLevels.isEmpty()) {
                        Text(
                            text = "尚未单独覆盖档位，默认支持 off / minimal / low / medium / high。",
                            color = scheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    } else {
                        configuredLevels.forEach { level ->
                            ThinkingLevelMapRow(
                                level = level,
                                map = model.thinkingLevelMap,
                                onChangeMap = { nextMap -> onUpdate(model.copy(thinkingLevelMap = nextMap)) },
                            )
                        }
                    }
                    if (remainingLevels.isNotEmpty()) {
                        ZhiIconDropdownMenu(
                            items = remainingLevels.map { level ->
                                ZhiMenuItem(
                                    text = level.id,
                                    summary = if (level == PiThinkingLevel.OFF) "默认添加为已禁用 (null) 或发送值" else "添加自定义或禁用规则",
                                    onClick = {
                                        val next = model.thinkingLevelMap.toMutableMap()
                                        next[level] = if (level == PiThinkingLevel.OFF) null else level.id
                                        onUpdate(model.copy(thinkingLevelMap = next))
                                    },
                                )
                            },
                            backgroundColor = scheme.secondaryContainer,
                            cornerRadius = 10.dp,
                            minHeight = 34.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    painter = ZhiIcons.add,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = "添加推理档位",
                                    color = scheme.primary,
                                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingLevelMapRow(
    level: PiThinkingLevel,
    map: Map<PiThinkingLevel, String?>,
    onChangeMap: (Map<PiThinkingLevel, String?>) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val mapped = map[level]
    val unsupported = mapped == null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = level.id,
                color = scheme.onBackground,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = if (unsupported) "已禁用 (null)" else "自定义值",
                onClick = {
                    val next = map.toMutableMap()
                    next[level] = if (unsupported) level.id else null
                    onChangeMap(next)
                },
            )
            ZhiIconButton(
                icon = ZhiIcons.close,
                description = "移除档位",
                onClick = {
                    val next = map.toMutableMap()
                    next.remove(level)
                    onChangeMap(next)
                },
                iconSize = 14.dp,
            )
        }
        if (!unsupported) {
            ZhiTextField(
                value = mapped.orEmpty(),
                onValueChange = { value ->
                    val next = map.toMutableMap()
                    next[level] = value
                    onChangeMap(next)
                },
                label = "映射发送值（如 none / high / HIGH）",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RequestHeadersKvEditor(
    rawJson: String,
    onUpdateJson: (String) -> Unit,
) {
    val entries = remember(rawJson) { parseHeadersList(rawJson) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "请求头 (headers)",
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = "+ 添加请求头",
                onClick = {
                    val next = entries + ("X-Custom-Header" to "")
                    onUpdateJson(serializeHeadersList(next))
                },
            )
        }
        entries.forEachIndexed { idx, (key, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ZhiTextField(
                    value = key,
                    onValueChange = { nextKey ->
                        val next = entries.toMutableList()
                        next[idx] = nextKey to value
                        onUpdateJson(serializeHeadersList(next))
                    },
                    label = "Header 名",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                ZhiTextField(
                    value = value,
                    onValueChange = { nextVal ->
                        val next = entries.toMutableList()
                        next[idx] = key to nextVal
                        onUpdateJson(serializeHeadersList(next))
                    },
                    label = "值",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                ZhiIconButton(
                    icon = ZhiIcons.close,
                    description = "删除请求头",
                    onClick = {
                        val next = entries.filterIndexed { i, _ -> i != idx }
                        onUpdateJson(serializeHeadersList(next))
                    },
                    iconSize = 14.dp,
                )
            }
        }
    }
}

private fun parseHeadersList(rawJson: String): List<Pair<String, String>> {
    val trimmed = rawJson.trim()
    if (trimmed.isEmpty()) return emptyList()
    val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return emptyList()
    return buildList {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            add(k to obj.optString(k, ""))
        }
    }
}

private fun serializeHeadersList(items: List<Pair<String, String>>): String {
    val nonEmpty = items.filter { it.first.isNotBlank() }
    if (nonEmpty.isEmpty()) return ""
    val obj = JSONObject()
    for ((k, v) in nonEmpty) {
        obj.put(k.trim(), v)
    }
    return obj.toString()
}
