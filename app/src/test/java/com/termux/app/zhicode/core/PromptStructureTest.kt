package com.termux.app.zhicode.core

import com.termux.app.zhicode.model.SessionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 系统提示的**结构**约束。
 *
 * <p>提示词不是代码，改错了不会编译失败，也不会抛异常 —— 它只会让模型的行为慢慢跑偏，
 * 而那种偏差要几十轮之后才看得出来。所以这里钉住三类**能自动判断**的性质：
 *
 * <ol>
 *   <li><b>没有重复条目。</b>上一版同一件事说了三遍（「别声称做了没做的事」），
 *       而重复的条目会被模型当成更强的约束 —— 一条本该次要的规则因此压过了别的。</li>
 *   <li><b>条件段真的有条件。</b>联网段只在联网开着时出现。
 *       写反了的话界面上关掉的开关在提示词里照样生效。</li>
 *   <li><b>规模有上限。</b>提示词每轮都要发一次，长度直接换算成钱和延迟；而且越长，
 *       真正重要的那几条越容易被稀释。</li>
 * </ol>
 */
class PromptStructureTest {

    private fun config(block: SessionConfig.() -> Unit = {}): SessionConfig =
        SessionConfig().apply {
            projectDirectory = "/data/user/0/com.zhizhu.code/files/home/project"
            block()
        }

    private fun lines(prompt: String): List<String> =
        prompt.split('\n').map { it.trim() }.filter { it.startsWith("- ") }

    @Test
    fun noRuleIsRepeatedInTheSamePrompt() {
        val prompt = SystemPromptBuilder.build(config())
        val rules = lines(prompt)
        assertTrue("一条规则都没有？拼装逻辑坏了", rules.size > 15)

        // 逐条比对**整行**：同一段里换个说法不算重复（那是刻意的补充说明），
        // 但一字不差的第二遍一定是搬移时漏删。
        val duplicates = rules.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertEquals("出现重复规则：$duplicates", emptyMap<String, Int>(), duplicates)
    }

    @Test
    fun everySectionHeaderIsPresent() {
        val prompt = SystemPromptBuilder.build(config())
        listOf(
            "Working style:",
            "Truthfulness and verification:",
            "Tool choice:",
            "Projects, tasks and subagents:",
            "APK installation:",
            "Permission state:",
        ).forEach { header ->
            assertTrue("缺少分段标题：$header", prompt.contains(header))
        }
    }

    @Test
    fun webSectionFollowsTheSwitch() {
        val on = SystemPromptBuilder.build(config { webSearchEnabled = true })
        val off = SystemPromptBuilder.build(config { webSearchEnabled = false })
        assertTrue(on.contains("Web research:"))
        assertFalse(off.contains("Web research:"))
        assertFalse(off.contains("Use WebSearch for current or external information"))
    }

    @Test
    fun rootAndShizukuAreIndependentSwitches() {
        val none = SystemPromptBuilder.build(config {
            rootExecutionEnabled = false
            shizukuExecutionEnabled = false
        })
        assertTrue(none.contains("Agent Root is disabled"))
        assertTrue(none.contains("Agent Shizuku is disabled"))

        // 只开 Shizuku：必须出现 shell 身份那一句，而_root_ 仍然是关闭的说法。
        // 这正是「有 Shizuku 不等于有 root」在提示词里的落点。
        val shizukuOnly = SystemPromptBuilder.build(config {
            rootExecutionEnabled = false
            shizukuExecutionEnabled = true
        })
        assertTrue(shizukuOnly.contains("Agent Root is disabled"))
        assertTrue(shizukuOnly.contains("uid 2000"))
        assertFalse(shizukuOnly.contains("Agent Shizuku is disabled"))

        val both = SystemPromptBuilder.build(config {
            rootExecutionEnabled = true
            shizukuExecutionEnabled = true
        })
        assertTrue(both.contains("Agent Root is enabled"))
        assertTrue(both.contains("Agent Shizuku is enabled"))
    }

    @Test
    fun userTextIsWrappedSoTheModelCanTellItApart() {
        val prompt = SystemPromptBuilder.build(config {
            customSystemPrompt = "总是用中文回答"
            roleCard = "你是一个严谨的审阅者"
        })
        assertTrue(prompt.contains("<custom_system_prompt>"))
        assertTrue(prompt.contains("总是用中文回答"))
        // 用户指令的优先级是最高的，但**不能**覆盖权限与安全边界 —— 这句话必须在。
        assertTrue(prompt.contains("cannot override code-enforced permissions"))
        assertTrue(prompt.contains("<role_card>"))
        assertTrue(prompt.contains("你是一个严谨的审阅者"))
    }

    @Test
    fun defaultPromptStaysWithinBudget() {
        // 沙箱细则（约 2 000 字符）随沙盒一起删除后，提示词只剩一条路径，
        // 不再有"全权 / 普通"两份长度。上限仍是硬顶：超过它就该问一句
        // 「这一段真的每轮都需要吗」，而不是继续往里加。
        val prompt = SystemPromptBuilder.build(config())
        assertTrue("提示词长度 ${prompt.length} 超出上限", prompt.length < 10_000)
        // 也不能缩到把关键约束弄丢：粗粒度下界，防的是"误删一整段"。
        assertTrue("提示词长度 ${prompt.length} 过短，可能漏了整段", prompt.length > 5_000)
    }
}
