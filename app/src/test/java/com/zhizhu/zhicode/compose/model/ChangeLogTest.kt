package com.zhizhu.zhicode.compose.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地自带的版本说明。
 *
 * ## 为什么值得断言
 *
 * 更新页在"已经是最新版本"时显示的是**本地这一份**（仓库没有 CHANGELOG、
 * GitHub 上也还没有 Release，没有别的来源）。所以这份数据的正确性直接决定
 * 那一屏是"有内容"还是"空白"。
 *
 * 尤其要钉住的是 **version 必须与 `BuildConfig.VERSION_NAME` 一致** ——
 * 不对齐的话 `changeLogFor` 取不到，页面会显示"这个版本没有留下更新说明"，
 * 而原因只是一个打错的字。这里用常量而不是读 BuildConfig（单元测试里没有它），
 * 所以那条一致性由 `changeLogCoversTheCurrentVersionName` 里的注释与
 * 发布流程保证；本测试至少钉住"这个文件里有一条 0.2"。
 */
class ChangeLogTest {

    /** 与 `app/build.gradle` 的 `versionName` 手工对齐；改版本号时这里要一起改。 */
    private val currentVersionName = "0.2"

    @Test
    fun changeLogCoversTheCurrentVersionName() {
        // 取不到就意味着更新页会显示"这个版本没有留下更新说明"。
        val entry = changeLogFor(currentVersionName)
        assertNotNull("CHANGE_LOG 里必须有当前版本（$currentVersionName）的说明", entry)
        assertEquals(currentVersionName, entry!!.version)
    }

    @Test
    fun unknownVersionReturnsNullInsteadOfFabricatingOne() {
        // 不返回"最新一条"也不返回空小节：那会让用户以为当前版本就长这样。
        assertNull(changeLogFor("99.9"))
        assertNull(changeLogFor(""))
        assertNull(changeLogFor("   "))
        assertNull(changeLogFor(null))
    }

    @Test
    fun versionLookupToleratesSurroundingWhitespace() {
        assertNotNull(changeLogFor(" 0.2 "))
    }

    @Test
    fun entriesAreNewestFirst() {
        // 页面按列表顺序渲染，倒序是它的隐含前提。
        // 用一个宽松的语义化比较，而不是字符串比较（`"0.9" > "0.30"` 是错的）。
        val versions = CHANGE_LOG.map { it.version }
        assertTrue("至少要有一条", versions.isNotEmpty())
        for (i in 0 until versions.lastIndex) {
            val newer = versions[i]
            val older = versions[i + 1]
            assertTrue(
                "CHANGE_LOG 必须按版本倒序：$newer 应比 $older 新",
                com.zhizhu.zhicode.compose.data.VersionCompare.isNewer(newer, older),
            )
        }
    }

    @Test
    fun everyEntryIsSubstantive() {
        for (entry in CHANGE_LOG) {
            assertTrue("${entry.version} 缺少日期标签", entry.dateLabel.isNotBlank())
            assertTrue("${entry.version} 没有任何小节", entry.sections.isNotEmpty())
            for (section in entry.sections) {
                assertTrue("${entry.version} 有小节没有标题", section.title.isNotBlank())
                assertTrue("「${section.title}」没有任何条目", section.items.isNotEmpty())
                for (item in section.items) {
                    // 正文是这一页唯一的实质内容：空正文等于一行没有信息的粗体标题。
                    assertTrue(
                        "「${section.title}」里有条目缺少正文",
                        item.detail.isNotBlank(),
                    )
                }
            }
        }
    }

    @Test
    fun noDuplicateVersions() {
        val duplicated = CHANGE_LOG.groupBy { it.version }.filterValues { it.size > 1 }.keys
        assertTrue("版本号重复：$duplicated", duplicated.isEmpty())
    }
}
