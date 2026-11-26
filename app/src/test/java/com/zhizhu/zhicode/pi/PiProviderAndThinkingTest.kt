package com.zhizhu.zhicode.pi

import com.termux.app.zhicode.model.ApiProfile as JavaApiProfile
import com.zhizhu.zhicode.compose.engine.engineModeToUi
import com.zhizhu.zhicode.compose.model.ApiProfileDraft
import com.zhizhu.zhicode.compose.model.EffortLevel
import com.zhizhu.zhicode.compose.model.ModelThinkingPickerState
import com.zhizhu.zhicode.compose.model.PermissionMode
import com.zhizhu.zhicode.compose.ui.ProviderBrandIcons
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiProviderAndThinkingTest {

    @Test
    fun resolveSupportedThinkingLevels_respectsReasoningAndMap() {
        val plain = PiModelConfig(id = "gpt-4o", reasoning = false)
        assertEquals(listOf(PiThinkingLevel.OFF), resolveSupportedThinkingLevels(plain))

        val standard = PiModelConfig(id = "deepseek-r1", reasoning = true)
        assertEquals(
            listOf(
                PiThinkingLevel.OFF,
                PiThinkingLevel.MINIMAL,
                PiThinkingLevel.LOW,
                PiThinkingLevel.MEDIUM,
                PiThinkingLevel.HIGH,
            ),
            resolveSupportedThinkingLevels(standard),
        )

        val gpt5 = PiCatalogAndPresets.inferModelConfig(
            id = "gpt-5.4",
            api = PiApiFormat.OPENAI_RESPONSES,
        )
        assertEquals(
            listOf(
                PiThinkingLevel.OFF,
                PiThinkingLevel.LOW,
                PiThinkingLevel.MEDIUM,
                PiThinkingLevel.HIGH,
                PiThinkingLevel.XHIGH,
                PiThinkingLevel.MAX,
            ),
            resolveSupportedThinkingLevels(gpt5),
        )

        val claudeOpus = PiCatalogAndPresets.inferModelConfig(
            id = "claude-opus-4-6",
            api = PiApiFormat.ANTHROPIC_MESSAGES,
        )
        assertFalse(PiThinkingLevel.OFF in resolveSupportedThinkingLevels(claudeOpus))
        assertTrue(PiThinkingLevel.XHIGH in resolveSupportedThinkingLevels(claudeOpus))
        assertTrue(PiThinkingLevel.MAX in resolveSupportedThinkingLevels(claudeOpus))
    }

    @Test
    fun clampThinkingLevel_clampsUnsupportedToNearest() {
        val nonReasoning = PiModelConfig(id = "gpt-4.1", reasoning = false)
        assertEquals(PiThinkingLevel.OFF, clampThinkingLevel(nonReasoning, PiThinkingLevel.HIGH))

        val kimiK3 = PiCatalogAndPresets.inferModelConfig(
            id = "kimi-k3",
            api = PiApiFormat.OPENAI_COMPLETIONS,
        )
        assertEquals(PiThinkingLevel.LOW, clampThinkingLevel(kimiK3, PiThinkingLevel.OFF))
        assertEquals(PiThinkingLevel.HIGH, clampThinkingLevel(kimiK3, PiThinkingLevel.MEDIUM))
        assertEquals(PiThinkingLevel.MAX, clampThinkingLevel(kimiK3, PiThinkingLevel.XHIGH))
    }

    @Test
    fun buildMultiProviderModelsJson_outputsMultipleProvidersAndPreservesV4Url() {
        val dsSnapshot = PiConfigSync.PiProviderSnapshot(
            profileId = "p1",
            key = "deepseek",
            name = "DeepSeek",
            api = PiApiFormat.OPENAI_COMPLETIONS,
            baseUrl = "https://api.deepseek.com",
            apiKey = "sk-ds",
            extraHeaders = "",
            compatJson = """{"thinkingFormat":"deepseek"}""",
            models = listOf(PiCatalogAndPresets.inferModelConfig("deepseek-chat", api = PiApiFormat.OPENAI_COMPLETIONS)),
            envVarName = "ZHICODE_KEY_0",
        )
        val zhipuSnapshot = PiConfigSync.PiProviderSnapshot(
            profileId = "p2",
            key = "zhipu",
            name = "智谱 GLM",
            api = PiApiFormat.OPENAI_COMPLETIONS,
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            apiKey = "sk-zhipu",
            extraHeaders = "",
            compatJson = "",
            models = listOf(PiCatalogAndPresets.inferModelConfig("glm-5", api = PiApiFormat.OPENAI_COMPLETIONS)),
            envVarName = "ZHICODE_KEY_1",
        )

        val json = PiConfigSync.buildMultiProviderModelsJson(listOf(dsSnapshot, zhipuSnapshot))
        val providers = json.getJSONObject("providers")
        assertTrue(providers.has("deepseek"))
        assertTrue(providers.has("zhipu"))

        val ds = providers.getJSONObject("deepseek")
        assertEquals("\$ZHICODE_KEY_0", ds.getString("apiKey"))
        assertEquals("https://api.deepseek.com/v1", ds.getString("baseUrl"))
        assertEquals("deepseek", ds.getJSONObject("compat").getString("thinkingFormat"))

        val zhipu = providers.getJSONObject("zhipu")
        assertEquals("\$ZHICODE_KEY_1", zhipu.getString("apiKey"))
        assertEquals("https://open.bigmodel.cn/api/paas/v4", zhipu.getString("baseUrl"))
    }

    @Test
    fun apiProfileJson_roundTripsNewFieldsAndHandlesLegacyJson() {
        val original = JavaApiProfile().apply {
            id = "id-1"
            name = "My OpenAI"
            protocol = "openai-responses"
            baseUrl = "https://api.openai.com/v1"
            defaultModel = "gpt-5.4"
            providerKey = "openai"
            compatJson = """{"supportsStore":true}"""
            modelsJson = """[{"id":"gpt-5.4","reasoning":true,"contextWindow":1000000,"maxTokens":128000}]"""
        }
        val parsed = JavaApiProfile.fromJson(original.toJson())
        assertEquals("openai", parsed.providerKey)
        assertEquals("""{"supportsStore":true}""", parsed.compatJson)
        val models = PiModelConfig.decodeList(parsed.modelsJson)
        assertEquals(1, models.size)
        assertEquals(1_000_000, models[0].contextWindow)

        val legacyJson = JSONObject()
            .put("id", "legacy")
            .put("name", "Legacy")
            .put("protocol", "openai-chat")
            .put("base_url", "https://example.com")
            .put("default_model", "gpt-4o")
        val legacy = JavaApiProfile.fromJson(legacyJson)
        assertEquals("", legacy.providerKey)
        assertEquals("", legacy.modelsJson)
        assertEquals("gpt-4o", legacy.defaultModel)
    }

    @Test
    fun engineModeToUi_mapsLegacyModesSafelyToThreeMinimalTiers() {
        assertEquals(PermissionMode.ASK, engineModeToUi("default"))
        assertEquals(PermissionMode.ASK, engineModeToUi("acceptEdits"))
        assertEquals(PermissionMode.ASK, engineModeToUi("auto"))
        assertEquals(PermissionMode.ASK, engineModeToUi("dontAsk"))
        assertEquals(PermissionMode.PLAN, engineModeToUi("plan"))
        assertEquals(PermissionMode.BYPASS, engineModeToUi("bypassPermissions"))
    }

    @Test
    fun draftPresetStepAndTwoLevelThinkingState_behaveCorrectly() {
        val blank = ApiProfileDraft.blank()
        assertTrue(blank.pickingPreset)
        assertFalse(blank.saveable)

        val preset = PiCatalogAndPresets.presets.first { it.id == "deepseek" }
        val filled = ApiProfileDraft.fromPreset(preset)
        assertFalse(filled.pickingPreset)
        assertEquals("deepseek", filled.providerKey)
        assertTrue(filled.models.isNotEmpty())
        assertTrue(filled.saveable)

        val thinkingPicker = ModelThinkingPickerState(
            providerId = "p1",
            providerName = "DeepSeek",
            models = filled.models,
            currentModelId = filled.model,
            currentEffort = EffortLevel.HIGH,
        )
        assertEquals(null, thinkingPicker.expandedModel)
        val firstModel = thinkingPicker.models.first()
        val level2 = thinkingPicker.copy(expandedModel = firstModel)
        assertEquals(firstModel.id, level2.expandedModel?.id)
    }

    @Test
    fun allCcsPresetsAndBrandIcons_loadWithoutDuplicatesOrMissingDrawables() {
        val presets = PiCatalogAndPresets.presets
        assertTrue("Expected >= 123 presets, got ${presets.size}", presets.size >= 123)
        assertEquals(presets.size, presets.map { it.id }.toSet().size)
        val missingIcons = presets.filter { !it.isCustom && (it.icon.isBlank() || ProviderBrandIcons.resolve(it.icon) == null) }
        assertEquals(emptyList<String>(), missingIcons.map { "${it.id}:${it.icon}" })
        assertEquals("deepseek", PiCatalogAndPresets.resolveIconKey("deepseek", "DeepSeek"))
    }
}
