package com.zhizhu.zhicode.compose.ui.chat

import com.zhizhu.zhicode.compose.model.AgentTask
import com.zhizhu.zhicode.compose.model.TaskState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 悬浮任务卡「露哪几条」的断言。
 *
 * <h3>为什么这条必须有真单测</h3>
 *
 * 这里出过一次事故，而且**编译、源码守卫都发现不了**：悬浮形态传 `max = 0`，
 * 而 [currentWindow] 当时把 `max <= 0` 当成"全部"，调用点又把"不画任务行"
 * 与 compact 绑在一起 —— 两边理解相反，结果悬浮卡只剩「任务进度 3 / 7」和一根进度条。
 * 用户看到的就是"我设的几个任务怎么没显现出来"。
 *
 * <p>这种"两个地方对同一个参数理解相反"的错误，只有**把行为跑一遍**才抓得住，
 * 所以这里断言的是返回的那几条，而不是"代码里写了什么"。
 */
class AgentProgressCardTest {

    /** 任务模型没有独立 id，标题即身份（与界面上的判断一致）。 */
    private fun task(name: String, state: TaskState) = AgentTask(title = name, detail = "", state = state)

    @Test
    fun `零或负数条数返回空列表而不是全部`() {
        // 这一条就是那次事故的形状：曾经 (max <= 0) 返回 this（全部），
        // 于是"以为它会给全部"的调用点配合"按空渲染"的界面，把任务全吞了。
        val tasks = listOf(
            task("1", TaskState.DONE),
            task("2", TaskState.RUNNING),
            task("3", TaskState.PENDING),
        )
        assertEquals(emptyList<AgentTask>(), tasks.currentWindow(0))
        assertEquals(emptyList<AgentTask>(), tasks.currentWindow(-1))
    }

    @Test
    fun `不超过上限时原样返回`() {
        val tasks = listOf(task("1", TaskState.PENDING), task("2", TaskState.PENDING))
        assertEquals(tasks, tasks.currentWindow(2))
        assertEquals(tasks, tasks.currentWindow(5))
    }

    @Test
    fun `窗口从第一条未完成开始`() {
        // 悬浮卡最多两条，用户要看的是"现在做到哪一条"，
        // 所以已完成的不该占掉窗口（否则进度条在走、任务行却停在开头）。
        val tasks = listOf(
            task("1", TaskState.DONE),
            task("2", TaskState.DONE),
            task("3", TaskState.RUNNING),
            task("4", TaskState.PENDING),
            task("5", TaskState.PENDING),
        )
        val window = tasks.currentWindow(2)
        assertEquals(listOf("3", "4"), window.map { it.title })
    }

    @Test
    fun `全部完成时回退到最后几条`() {
        val tasks = listOf(
            task("1", TaskState.DONE),
            task("2", TaskState.DONE),
            task("3", TaskState.DONE),
        )
        assertEquals(listOf("2", "3"), tasks.currentWindow(2).map { it.title })
    }

    @Test
    fun `窗口里的条数不会超过上限`() {
        val tasks = (1..7).map { task("$it", if (it <= 3) TaskState.DONE else TaskState.PENDING) }
        for (max in 1..7) {
            val window = tasks.currentWindow(max)
            assertTrue("max=$max 时返回了 ${window.size} 条，超过上限会让悬浮卡把对话遮住",
                window.size <= max)
        }
    }
}
