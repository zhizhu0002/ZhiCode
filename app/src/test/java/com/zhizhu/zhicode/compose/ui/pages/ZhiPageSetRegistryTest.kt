package com.zhizhu.zhicode.compose.ui.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 界面主题组注册表 —— **纯逻辑**守卫。
 *
 * ## 为什么这些性质值得钉住
 *
 * `pageSetId` 是**落盘字符串**。下面每一条出错都不会编译失败，表现只是
 * 「用户切了主题，下次进来变回默认」或者「点开设置直接白屏」：
 *
 * - id 重复 → `byId` 只认第一个，后加的那组永远选不中；
 * - 首项不是默认组 → 没有 `pageSetId` 的老用户进到一个不确定的组；
 * - id 为空 → 落盘写成空串，读取时又被当成"没有值"，切换静默失效。
 *
 * 所以按 `ThemeSpecTest` 的方式钉在秒级单测里。
 *
 * ⚠️ 这里不测 `LocalZhiPageSet` —— 它是 Compose 的 CompositionLocal，
 * 而本工程这条快路径不带 compose 依赖（见 `test-jvm-fast.sh` 的说明）。
 */
class ZhiPageSetRegistryTest {

    @Test
    fun idsAreStableAndUnique() {
        val ids = ZhiPageSets.all.map { it.id }
        assertEquals("主题组 id 有重复", ids.size, ids.toSet().size)
        assertTrue("主题组列表不能为空", ids.isNotEmpty())
        for (id in ids) {
            assertTrue("主题组 id 不能是空白（它会作为落盘键）", id.isNotBlank())
        }
    }

    @Test
    fun firstEntryIsTheDefault() {
        // `byId` 的兜底和 `default` 都取首项。首项若被换成一个非默认组，
        // 没有 `pageSetId` 的老用户就会进到一个不确定的界面。
        assertSame(ZhiPageSets.all.first(), ZhiPageSets.default)
        assertEquals("默认组应当是 miuix", "miuix", ZhiPageSets.default.id)
    }

    @Test
    fun unknownIdFallsBackToDefault() {
        // 老版本写下的 id 在新版本里可能已经不存在（改名、删除）。
        // 那时必须回落，不能崩、不能空白 —— 与 presetId 同一套兜底。
        assertSame(ZhiPageSets.default, ZhiPageSets.byId("nope"))
        assertSame(ZhiPageSets.default, ZhiPageSets.byId(""))
        assertSame(ZhiPageSets.default, ZhiPageSets.byId(null))
        // 己有的 id 必须能查到自身，不能因为兜底逻辑写错而永远返回默认。
        assertSame(ZhiPageSets.default, ZhiPageSets.byId("miuix"))
        assertEquals("m3_expressive", ZhiPageSets.byId("m3_expressive").id)
    }

    @Test
    fun everySetHasDisplayName() {
        for (set in ZhiPageSets.all) {
            assertNotNull("主题组 ${set.id} 没有展示名", set.name)
            assertTrue("主题组 ${set.id} 的展示名不能为空", set.name.isNotBlank())
        }
    }
}
