@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.zhizhu.zhicode.compose.ui.pages.m3e

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhizhu.zhicode.compose.model.ApiConfigState
import com.zhizhu.zhicode.compose.model.ApiProfile
import com.zhizhu.zhicode.compose.model.ApiProfileDraft
import com.zhizhu.zhicode.compose.model.ApiProtocol
import com.zhizhu.zhicode.compose.model.COMPACT_PERCENT_MAX
import com.zhizhu.zhicode.compose.model.COMPACT_PERCENT_MIN
import com.zhizhu.zhicode.compose.model.ModelOption
import com.zhizhu.zhicode.compose.ui.ProviderBrandIconBox
import com.zhizhu.zhicode.compose.ui.ZhiIcons
import com.zhizhu.zhicode.compose.ui.settings.rememberLastNonNull
import com.zhizhu.zhicode.pi.PiCatalogAndPresets
import com.zhizhu.zhicode.pi.PiModelConfig
import com.zhizhu.zhicode.pi.PiProviderPreset
import com.zhizhu.zhicode.pi.PiThinkingLevel
import com.zhizhu.zhicode.pi.resolveSupportedThinkingLevels
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val M3eZcodeGateway = "https://zcode.z.ai/api/v1/zcode-plan"
private const val M3eZcodeHeadersJson = """{"X-ZCode-App-Version":"3.14.3"}"""

@Composable
internal fun M3eApiConfigPage(
    config: ApiConfigState?,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onEdit: (ApiProfile) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDraftChange: ((ApiProfileDraft) -> ApiProfileDraft) -> Unit,
    onSave: () -> Unit,
    onCancelForm: () -> Unit,
    onApplyPreset: (PiProviderPreset) -> Unit,
    onFetchDraftModels: suspend (ApiProfileDraft) -> Result<List<ModelOption>>,
    onUpdateContextAndPrompt: (Boolean, Int, String) -> Unit,
    onOpenPrompts: () -> Unit,
) {
    if (config == null) return
    val form = config.form
    val shownForm = rememberLastNonNull(form)
    val standalone = config.standalone

    val back: () -> Unit = when {
        form == null -> onDismiss
        !form.isEditing && !form.pickingPreset -> {
            { onDraftChange { it.copy(pickingPreset = true) } }
        }
        standalone -> onDismiss
        else -> onCancelForm
    }

    when {
        form == null -> M3eSubPageScaffold(
            title = "供应商与模型管理",
            onBack = onDismiss,
            actions = { TextButton(onClick = onNew) { Text("新增") } },
        ) { padding, _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                M3eApiProfileList(
                    config = config,
                    onEdit = onEdit,
                    onSelect = onSelect,
                    onDelete = onDelete,
                    onUpdateContextAndPrompt = onUpdateContextAndPrompt,
                    onOpenPrompts = onOpenPrompts,
                )
            }
        }

        !form.isEditing && form.pickingPreset -> {
            val presetDraft = shownForm ?: ApiProfileDraft.blank()
            M3eSubPageScaffold(
                title = "选择供应商模板",
                onBack = if (standalone) onDismiss else onCancelForm,
            ) { padding, _ ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                ) {
                    M3ePresetPickerStep(
                        selectedPresetId = presetDraft.presetId,
                        onPick = onApplyPreset,
                    )
                }
            }
        }

        else -> {
            val draft = shownForm ?: return
            M3eSubPageScaffold(
                title = if (draft.isEditing) "编辑供应商" else "新增供应商",
                onBack = back,
                actions = {
                    TextButton(onClick = onSave, enabled = draft.saveable) { Text("保存") }
                },
            ) { padding, _ ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                ) {
                    M3eApiProfileForm(
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
private fun M3eApiProfileList(
    config: ApiConfigState,
    onEdit: (ApiProfile) -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onUpdateContextAndPrompt: (Boolean, Int, String) -> Unit,
    onOpenPrompts: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        M3eSectionHeader("已配置供应商")
        when {
            config.loading -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoadingIndicator(modifier = Modifier.size(M3eDimens.IconSmall))
                Text(
                    text = "正在加载…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }

            config.profiles.isEmpty() -> Text(
                text = "还没有供应商配置。点右上角「新增」选择自定义供应商或主流预设，" +
                    "API Key 会存进系统加密存储。",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = 12.dp),
            )

            else -> M3eSegmentedColumn {
                config.profiles.forEachIndexed { index, profile ->
                    val active = profile.id == config.activeId
                    M3eSegmentedItem(index = index, count = config.profiles.size, onClick = {
                        if (!active) onSelect(profile.id)
                    }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = M3eDimens.RowPaddingH,
                                    end = 8.dp,
                                    top = M3eDimens.RowSpaceV,
                                    bottom = M3eDimens.RowSpaceV,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProviderBrandIconBox(
                                icon = PiCatalogAndPresets.resolveIconKey(
                                    profile.providerKey,
                                    profile.name,
                                ),
                                name = profile.name,
                                size = 30.dp,
                            )
                            if (active) {
                                Icon(
                                    painter = ZhiIcons.check,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(start = 4.dp),
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp),
                            ) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (active) scheme.primary else scheme.onSurface,
                                )
                                Text(
                                    text = "${profile.summary} · 已配 " +
                                        "${profile.effectiveModels().size} 个模型",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = { onEdit(profile) }) { Text("编辑") }
                            TextButton(onClick = { onDelete(profile.id) }) {
                                Text("删除", color = scheme.error)
                            }
                        }
                    }
                }
            }
        }

        M3eSectionHeader("上下文与提示词")
        var compactSliderVisible by remember { mutableStateOf(config.autoCompact) }
        compactSliderVisible = config.autoCompact
        M3eSegmentedColumn {
            M3eSwitchRow(
                index = 0,
                count = 3,
                title = "上下文压缩",
                checked = config.autoCompact,
                onCheckedChange = { enabled ->
                    onUpdateContextAndPrompt(
                        enabled,
                        config.autoCompactPercent,
                        config.customSystemPrompt,
                    )
                },
            )
            AnimatedVisibility(
                visible = config.autoCompact,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
            ) {
                M3eSliderRow(
                    index = 1,
                    count = 3,
                    title = "自动压缩上限（50-100%）",
                    value = config.autoCompactPercent.toFloat(),
                    valueText = "${config.autoCompactPercent}%",
                    valueRange = COMPACT_PERCENT_MIN.toFloat()..COMPACT_PERCENT_MAX.toFloat(),
                    steps = COMPACT_PERCENT_MAX - COMPACT_PERCENT_MIN - 1,
                    summary = "达到当前模型上下文窗口的 ${config.autoCompactPercent}% 时触发压缩",
                    onValueChange = { value ->
                        onUpdateContextAndPrompt(
                            config.autoCompact,
                            value.toInt(),
                            config.customSystemPrompt,
                        )
                    },
                )
            }
            M3eNavigationRow(
                index = 2,
                count = 3,
                title = "提示词模板",
                summary = "自定义 /init、压缩摘要与开场段；留空即用默认",
                icon = ZhiIcons.edit,
                onClick = onOpenPrompts,
            )
        }

        M3eSectionHeader("自定义头部提示词")
        OutlinedTextField(
            value = config.customSystemPrompt,
            onValueChange = { text ->
                onUpdateContextAndPrompt(
                    config.autoCompact,
                    config.autoCompactPercent,
                    text,
                )
            },
            label = { Text("随请求发送，下一任务生效；不能覆盖安全规则，勿填密钥。") },
            minLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
        )
    }
}

private enum class M3ePresetTab(val label: String) {
    ALL("全部"),
    OFFICIAL("官方"),
    CLOUD("云厂商"),
    LOCAL("本地"),
    AGGREGATOR("聚合 / 三方"),
}

@Composable
private fun M3ePresetPickerStep(
    selectedPresetId: String,
    onPick: (PiProviderPreset) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val customPreset = remember {
        PiCatalogAndPresets.presets.firstOrNull { it.isCustom }
            ?: PiCatalogAndPresets.presets.first()
    }
    val builtInPresets = remember { PiCatalogAndPresets.presets.filter { !it.isCustom } }
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(M3ePresetTab.ALL) }

    val filteredPresets = remember(builtInPresets, query, selectedTab) {
        val needle = query.trim().lowercase()
        builtInPresets.filter { preset ->
            val catOk = when (selectedTab) {
                M3ePresetTab.ALL -> true
                M3ePresetTab.OFFICIAL -> preset.category == "official" || preset.category == "cn_official"
                M3ePresetTab.CLOUD -> preset.category == "cloud_provider"
                M3ePresetTab.LOCAL -> preset.category == "local"
                M3ePresetTab.AGGREGATOR ->
                    preset.category == "aggregator" || preset.category == "third_party"
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
            .padding(horizontal = M3eDimens.RowPaddingH, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = m3eSegmentShape(0, 1),
            color = if (selectedPresetId == customPreset.id) {
                scheme.primaryContainer
            } else {
                scheme.surfaceContainer
            },
            onClick = { onPick(customPreset) },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = ZhiIcons.tune,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(M3eDimens.IconSmall),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = "自定义供应商",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = "手动配置接口协议、Base URL、API Key 与模型及思考档位",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        HorizontalDivider()

        Text(
            text = "内置供应商预设（共 ${builtInPresets.size} 个）",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索名称、域名或协议…") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            if (query.isNotEmpty()) {
                TextButton(onClick = { query = "" }) {
                    Icon(
                        painter = ZhiIcons.close,
                        contentDescription = "清空搜索",
                        modifier = Modifier.size(M3eDimens.IconSmall),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            M3ePresetTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) scheme.primary else scheme.surfaceContainer,
                    contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
                    onClick = { selectedTab = tab },
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        if (filteredPresets.isEmpty()) {
            Text(
                text = "未找到匹配的供应商预设，可点顶部「自定义供应商」手动创建。",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            filteredPresets.chunked(2).forEach { rowPresets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowPresets.forEach { preset ->
                        M3ePresetGridCard(
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
private fun M3ePresetGridCard(
    preset: PiProviderPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val domain = remember(preset.baseUrl) {
        preset.baseUrl.removePrefix("https://").removePrefix("http://")
            .substringBefore('/').ifBlank { preset.api.label }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainer,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderBrandIconBox(icon = preset.icon, name = preset.name, size = 32.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            ) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun M3eApiProfileForm(
    draft: ApiProfileDraft,
    onChange: ((ApiProfileDraft) -> ApiProfileDraft) -> Unit,
    onSwitchPresetStep: () -> Unit,
    onFetchDraftModels: suspend (ApiProfileDraft) -> Result<List<ModelOption>>,
) {
    val scheme = MaterialTheme.colorScheme
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
                shape = m3eSegmentShape(0, 1),
                color = scheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
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
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurface,
                        )
                        if (activePreset != null && !activePreset.isCustom) {
                            Text(
                                text = activePreset.api.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedButton(onClick = onSwitchPresetStep) { Text("更换模板") }
                }
            }
        }

        M3eSectionHeader("基本连接")
        OutlinedTextField(
            value = draft.providerKey,
            onValueChange = { value ->
                val cleaned = value.lowercase().replace(Regex("[^a-z0-9-]"), "")
                onChange { it.copy(providerKey = cleaned) }
            },
            label = { Text("Pi Provider Key（小写字母/数字/连字符，留空自动派生）") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
        )
        OutlinedTextField(
            value = draft.name,
            onValueChange = { value ->
                nameTouched = true
                onChange { it.copy(name = value) }
            },
            label = { Text("供应商显示名称") },
            singleLine = true,
            isError = nameTouched && draft.nameError != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
        )

        val protocols = remember {
            ApiProtocol.entries.filter {
                !it.debugOnly || com.zhizhu.zhicode.compose.BuildConfig.DEBUG
            }
        }
        M3eSegmentedColumn {
            M3eChoiceRow(
                index = 0,
                count = 2,
                title = "接口格式",
                options = protocols.map { it.label },
                selectedIndex = protocols.indexOf(draft.protocol).coerceAtLeast(0),
                onSelect = { index ->
                    protocols.getOrNull(index)?.let { protocol ->
                        onChange { draftNow ->
                            val autoFill = protocol == ApiProtocol.ZCODE && draftNow.baseUrl.isBlank()
                            if (autoFill) {
                                baseUrlTouched = true
                                draftNow.copy(
                                    protocol = protocol,
                                    baseUrl = M3eZcodeGateway,
                                    extraHeaders = M3eZcodeHeadersJson,
                                )
                            } else {
                                draftNow.copy(protocol = protocol)
                            }
                        }
                    }
                },
            )
            M3eSwitchRow(
                index = 1,
                count = 2,
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

        OutlinedTextField(
            value = draft.apiKey,
            onValueChange = { value -> onChange { it.copy(apiKey = value) } },
            label = {
                Text(
                    when {
                        draft.isEditing -> "API Key（留空 = 沿用原密钥）"
                        draft.protocol == ApiProtocol.ZCODE -> "ZCode 授权码"
                        else -> "API Key"
                    },
                )
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
        )
        OutlinedTextField(
            value = draft.baseUrl,
            onValueChange = { value ->
                baseUrlTouched = true
                onChange { it.copy(baseUrl = value) }
            },
            label = {
                Text(
                    if (draft.protocol == ApiProtocol.BEDROCK_CONVERSE_STREAM) {
                        "Base URL（Bedrock 可留空）"
                    } else {
                        "Base URL"
                    },
                )
            },
            singleLine = true,
            isError = baseUrlTouched && draft.baseUrlError != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
        )

        if (draft.protocol == ApiProtocol.ZCODE) {
            TextButton(
                onClick = {
                    baseUrlTouched = true
                    onChange {
                        it.copy(
                            baseUrl = M3eZcodeGateway,
                            extraHeaders = M3eZcodeHeadersJson,
                        )
                    }
                },
                modifier = Modifier.padding(horizontal = M3eDimens.RowPaddingH),
            ) { Text("重置为 ZCode 默认值（网关与版本请求头）") }
        }

        M3eSectionHeader("模型配置")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = M3eDimens.RowPaddingH, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (draft.models.isEmpty()) "尚未添加模型" else "已添加模型（${draft.models.size}）",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
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
            ) { Text(if (fetchingCatalog) "拉取中…" else "获取模型") }
            OutlinedButton(
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
            ) { Text("+ 添加模型") }
        }

        draft.models.forEachIndexed { index, modelCfg ->
            val itemKey = "${index}:${modelCfg.id}"
            val isExpanded = (modelCfg.id in expandedModelKeys) || (itemKey in expandedModelKeys)
            val thinkingExpanded =
                (modelCfg.id in expandedThinkingKeys) || (itemKey in expandedThinkingKeys)
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

            M3eSegmentedColumn {
                M3eSegmentedItem(
                    index = index.coerceAtMost(1),
                    count = 2,
                    onClick = {
                        expandedModelKeys = if (isExpanded) {
                            expandedModelKeys - modelCfg.id - itemKey
                        } else {
                            expandedModelKeys + modelCfg.id + itemKey
                        }
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = M3eDimens.RowPaddingH,
                                end = 8.dp,
                                top = M3eDimens.RowSpaceV,
                                bottom = M3eDimens.RowSpaceV,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = if (isExpanded) ZhiIcons.collapse else ZhiIcons.expand,
                            contentDescription = "展开或收起",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(M3eDimens.IconSmall),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp),
                        ) {
                            Text(
                                text = modelCfg.name.ifBlank { modelCfg.id } +
                                    if (isDefault) "（默认）" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurface,
                            )
                            Text(
                                text = capSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!isDefault) {
                            TextButton(onClick = { onChange { it.copy(model = modelCfg.id) } }) {
                                Icon(
                                    painter = ZhiIcons.check,
                                    contentDescription = "设为默认模型",
                                    modifier = Modifier.size(M3eDimens.IconSmall),
                                )
                            }
                        }
                        TextButton(
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
                        ) {
                            Icon(
                                painter = ZhiIcons.delete,
                                contentDescription = "删除模型",
                                tint = scheme.error,
                                modifier = Modifier.size(M3eDimens.IconSmall),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
            ) {
                M3ePiModelCardEditor(
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
                                models = current.models.mapIndexed { i, existing ->
                                    if (i == index) updated else existing
                                },
                                model = if (wasDefault) updated.id else current.model,
                            )
                        }
                    },
                )
            }
        }

        if (fetchedModels.isNotEmpty() || fetchStatus.isNotBlank()) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = M3eDimens.RowPaddingH, vertical = 8.dp))
            if (fetchStatus.isNotBlank()) {
                Text(
                    text = fetchStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = M3eDimens.RowPaddingH, vertical = 4.dp),
                )
            }
            if (fetchedModels.isNotEmpty()) {
                val configuredIds = draft.models.mapTo(HashSet()) { it.id }
                val visibleFetched = if (showAllFetched) fetchedModels else fetchedModels.take(15)
                M3eSegmentedColumn {
                    visibleFetched.forEachIndexed { idx, opt ->
                        val added = opt.id in configuredIds
                        M3eSegmentedItem(index = idx, count = visibleFetched.size, onClick = {
                            if (!added) {
                                val inferred = PiCatalogAndPresets.inferModelConfig(
                                    id = opt.id,
                                    displayName = opt.displayName,
                                    api = draft.protocol.toPiFormat(),
                                )
                                onChange { current ->
                                    if (current.models.any { it.id == opt.id }) {
                                        current
                                    } else {
                                        current.copy(
                                            models = current.models + inferred,
                                            model = current.model.ifBlank { opt.id },
                                        )
                                    }
                                }
                                expandedModelKeys = expandedModelKeys + opt.id
                            }
                        }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = M3eDimens.RowPaddingH,
                                        vertical = 8.dp,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = opt.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = scheme.onSurface,
                                    )
                                    Text(
                                        text = if (added) {
                                            "${opt.id} · 已在上方列表"
                                        } else {
                                            "${opt.id} · 点击添加并自动匹配参数"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    painter = if (added) ZhiIcons.check else ZhiIcons.add,
                                    contentDescription = if (added) "已添加" else "添加到列表",
                                    tint = if (added) scheme.primary else scheme.onSurfaceVariant,
                                    modifier = Modifier.size(M3eDimens.IconSmall),
                                )
                            }
                        }
                    }
                }
                if (!showAllFetched && fetchedModels.size > 15) {
                    TextButton(
                        onClick = { showAllFetched = true },
                        modifier = Modifier.padding(horizontal = M3eDimens.RowPaddingH),
                    ) { Text("展开剩余 ${fetchedModels.size - 15} 个获取到的模型") }
                }
            }
        }

        M3eSectionHeader("高级选项")
        M3eSegmentedColumn {
            M3eNavigationRow(
                index = 0,
                count = 1,
                title = "自定义请求头与兼容性参数",
                summary = if (advancedExpanded) {
                    "点击收起 headers 与 compat"
                } else {
                    "点击展开 headers 键值对与 compat JSON"
                },
                onClick = { advancedExpanded = !advancedExpanded },
            )
        }
        AnimatedVisibility(
            visible = advancedExpanded,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                M3eRequestHeadersEditor(
                    rawJson = draft.extraHeaders,
                    onUpdateJson = { json -> onChange { it.copy(extraHeaders = json) } },
                )
                OutlinedTextField(
                    value = draft.compatJson,
                    onValueChange = { value -> onChange { it.copy(compatJson = value) } },
                    label = { Text("兼容性 compat (JSON，如 {\"thinkingFormat\":\"deepseek\"})") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = M3eDimens.RowPaddingH, vertical = 8.dp),
                )
            }
        }

        if (nameTouched || baseUrlTouched) {
            val message = draft.nameError ?: draft.baseUrlError
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error,
                    modifier = Modifier.padding(
                        start = M3eDimens.RowPaddingH,
                        end = M3eDimens.RowPaddingH,
                        bottom = 8.dp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun M3ePiModelCardEditor(
    model: PiModelConfig,
    isDefault: Boolean,
    thinkingMapExpanded: Boolean,
    onToggleThinkingMap: () -> Unit,
    onSetDefault: () -> Unit,
    onUpdate: (PiModelConfig) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var ctxInput by remember(model.id, model.contextWindow) {
        mutableStateOf(model.contextWindow.toString())
    }
    var maxOutInput by remember(model.id, model.maxTokens) {
        mutableStateOf(model.maxTokens.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = M3eDimens.RowPaddingH, end = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = model.id,
            onValueChange = { raw -> onUpdate(model.copy(id = raw.trim())) },
            label = { Text("模型 ID（如 deepseek-chat / claude-sonnet-4-6）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = model.name,
                onValueChange = { onUpdate(model.copy(name = it)) },
                label = { Text("显示名称（可留空同 ID）") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            if (!isDefault) {
                OutlinedButton(onClick = onSetDefault) { Text("设为默认") }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = ctxInput,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }
                    ctxInput = digits
                    digits.toIntOrNull()?.takeIf { it >= PiModelConfig.MIN_CONTEXT_WINDOW }?.let {
                        onUpdate(model.copy(contextWindow = it))
                    }
                },
                label = { Text("上下文窗口") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = maxOutInput,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }
                    maxOutInput = digits
                    digits.toIntOrNull()?.takeIf { it >= PiModelConfig.MIN_MAX_TOKENS }?.let {
                        onUpdate(model.copy(maxTokens = it))
                    }
                },
                label = { Text("最大输出") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = if (model.reasoning) scheme.secondaryContainer else scheme.surfaceContainer,
                onClick = { onUpdate(model.copy(reasoning = !model.reasoning)) },
            ) {
                Text(
                    text = "支持推理",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = if (model.supportsImage) scheme.secondaryContainer else scheme.surfaceContainer,
                onClick = { onUpdate(model.copy(supportsImage = !model.supportsImage)) },
            ) {
                Text(
                    text = "支持图片输入",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }

        if (model.reasoning) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = scheme.surfaceContainer,
                onClick = onToggleThinkingMap,
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = "推理档位映射 (thinkingLevelMap)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = "当前可用：" +
                            resolveSupportedThinkingLevels(model).joinToString(" / ") { it.id } +
                            " · 已自定义 ${model.thinkingLevelMap.size} 项",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(
                visible = thinkingMapExpanded,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
            ) {
                val configuredLevels = PiThinkingLevel.entries.filter { it in model.thinkingLevelMap }
                val remainingLevels = PiThinkingLevel.entries.filter { it !in model.thinkingLevelMap }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (configuredLevels.isEmpty()) {
                        Text(
                            text = "尚未单独覆盖档位，默认支持 off / minimal / low / medium / high。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    } else {
                        configuredLevels.forEach { level ->
                            M3eThinkingLevelRow(
                                level = level,
                                map = model.thinkingLevelMap,
                                onChangeMap = { nextMap ->
                                    onUpdate(model.copy(thinkingLevelMap = nextMap))
                                },
                            )
                        }
                    }
                    if (remainingLevels.isNotEmpty()) {
                        var addMenu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { addMenu = true }) {
                                Icon(
                                    painter = ZhiIcons.add,
                                    contentDescription = null,
                                    modifier = Modifier.size(M3eDimens.IconSmall),
                                )
                                Text("添加推理档位", modifier = Modifier.padding(start = 6.dp))
                            }
                            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                                remainingLevels.forEach { level ->
                                    DropdownMenuItem(
                                        text = { Text(level.id) },
                                        onClick = {
                                            addMenu = false
                                            val next = model.thinkingLevelMap.toMutableMap()
                                            next[level] =
                                                if (level == PiThinkingLevel.OFF) null else level.id
                                            onUpdate(model.copy(thinkingLevelMap = next))
                                        },
                                    )
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
private fun M3eThinkingLevelRow(
    level: PiThinkingLevel,
    map: Map<PiThinkingLevel, String?>,
    onChangeMap: (Map<PiThinkingLevel, String?>) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val mapped = map[level]
    val unsupported = mapped == null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer, RoundedCornerShape(12.dp))
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
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    val next = map.toMutableMap()
                    next[level] = if (unsupported) level.id else null
                    onChangeMap(next)
                },
            ) { Text(if (unsupported) "已禁用" else "自定义值") }
            TextButton(
                onClick = {
                    val next = map.toMutableMap()
                    next.remove(level)
                    onChangeMap(next)
                },
            ) {
                Icon(
                    painter = ZhiIcons.close,
                    contentDescription = "移除档位",
                    modifier = Modifier.size(M3eDimens.IconSmall),
                )
            }
        }
        if (!unsupported) {
            OutlinedTextField(
                value = mapped.orEmpty(),
                onValueChange = { value ->
                    val next = map.toMutableMap()
                    next[level] = value
                    onChangeMap(next)
                },
                label = { Text("映射发送值（如 none / high / HIGH）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun M3eRequestHeadersEditor(
    rawJson: String,
    onUpdateJson: (String) -> Unit,
) {
    val entries = remember(rawJson) { m3eParseHeaders(rawJson) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = M3eDimens.RowPaddingH, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "请求头 (headers)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onUpdateJson(m3eSerializeHeaders(entries + ("X-Custom-Header" to ""))) },
            ) { Text("+ 添加请求头") }
        }
        entries.forEachIndexed { idx, (key, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { nextKey ->
                        val next = entries.toMutableList()
                        next[idx] = nextKey to value
                        onUpdateJson(m3eSerializeHeaders(next))
                    },
                    label = { Text("Header 名") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { nextVal ->
                        val next = entries.toMutableList()
                        next[idx] = key to nextVal
                        onUpdateJson(m3eSerializeHeaders(next))
                    },
                    label = { Text("值") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        val next = entries.filterIndexed { i, _ -> i != idx }
                        onUpdateJson(m3eSerializeHeaders(next))
                    },
                ) {
                    Icon(
                        painter = ZhiIcons.close,
                        contentDescription = "删除请求头",
                        modifier = Modifier.size(M3eDimens.IconSmall),
                    )
                }
            }
        }
    }
}

private fun m3eParseHeaders(rawJson: String): List<Pair<String, String>> {
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

private fun m3eSerializeHeaders(items: List<Pair<String, String>>): String {
    val nonEmpty = items.filter { it.first.isNotBlank() }
    if (nonEmpty.isEmpty()) return ""
    val obj = JSONObject()
    for ((k, v) in nonEmpty) {
        obj.put(k.trim(), v)
    }
    return obj.toString()
}
