package com.zhizhu.zhicode.compose.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
 * 而原因只是一个打错的字。
 *
 * ## 为什么从 `app/build.gradle` 里读版本号，而不是写死
 *
 * 上一版这里写死成 `"0.2"`，注释还写着"改版本号时这里要一起改"。
 * 那种约定必然失效：这次升到 `1.0.0` 就是差点漏掉 —— 漏掉的后果不是编译失败，
 * 而是**更新页永远显示"这个版本没有留下更新说明"**，且没人会想到去查一个测试。
 *
 * 现在直接解析 `app/build.gradle` 的 `versionName`。单测工作目录是工程根
 * （见 `app/build.gradle` 的 `testOptions`），所以路径是相对根写的。
 * 读不到就明确失败 —— 那说明目录约定变了，该修的是测试而不是放过它。
 */
class ChangeLogTest {

    /** 从构建脚本里取**当下**的 `versionName`，避免手工同步漏掉。 */
    private val currentVersionName: String by lazy {
        val script = File("app/build.gradle")
        assertTrue("找不到 app/build.gradle：${script.absolutePath}", script.isFile)
        val match = Regex("""versionName\s+'([^']+)'""").find(script.readText())
        assertNotNull("app/build.gradle 里没有 versionName", match)
        match!!.groupValues[1]
    }

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
        // 用当前版本号而不是字面量：这条测的是"前后空白会被 trim"，
        // 不该因为版本号变化而失效（上一版写死了 "0.2"，升级时就被它拦住了）。
        assertNotNull(changeLogFor(" $currentVersionName "))
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
