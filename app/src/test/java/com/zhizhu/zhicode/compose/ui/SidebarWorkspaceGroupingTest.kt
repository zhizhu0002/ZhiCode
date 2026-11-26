package com.zhizhu.zhicode.compose.ui

import com.zhizhu.zhicode.compose.data.SessionReader
import com.zhizhu.zhicode.compose.data.buildWorkspaceGroups
import com.zhizhu.zhicode.compose.model.SessionSummary
import com.zhizhu.zhicode.compose.model.SidebarOrderBy
import com.zhizhu.zhicode.compose.model.WorkspaceEntry
import com.zhizhu.zhicode.compose.model.WorkspaceUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SidebarWorkspaceGroupingTest {

    private fun session(
        id: String,
        title: String,
        projectPath: String,
        updatedAtMs: Long,
        busy: Boolean = false,
        note: String = "",
    ) = SessionSummary(
        id = id,
        title = title,
        project = projectPath.substringAfterLast('/'),
        projectPath = projectPath,
        messageCount = 4,
        updatedAtLabel = "1小时",
        updatedAtMs = updatedAtMs,
        busy = busy,
        note = note,
    )

    @Test
    fun buildWorkspaceGroups_autoDiscoversSessionsAndKeepsDefaultWorkspace() {
        val defaultPath = "/data/root"
        val sessions = listOf(
            session("s1", "逆向协助", "/data/root/opencodepojia", 3000L),
            session("s2", "问候", "/data/root/lianbox", 2000L),
            session("s3", "默认任务", defaultPath, 4000L),
        )
        val groups = buildWorkspaceGroups(
            registeredWorkspaces = emptyList(),
            sessions = sessions,
            defaultWorkspacePath = defaultPath,
            activeSessionId = "s3",
            activeProjectPath = defaultPath,
        )

        assertEquals(listOf(defaultPath, "/data/root/opencodepojia", "/data/root/lianbox"), groups.map { it.path })
        assertTrue(groups.first().isDefault)
        assertEquals("默认工作区", groups.first().title)
        assertTrue(groups.first().containsActive)
        assertFalse(groups[1].isDefault)
        assertEquals("opencodepojia", groups[1].title)
    }

    @Test
    fun buildWorkspaceGroups_appliesCollapsedLimit5AndRevealsActiveBeyondLimit() {
        val defaultPath = "/data/root"
        val many = (1..8).map { i ->
            session("s$i", "会话 $i", "/proj/a", (1000 - i).toLong())
        }
        val baseGroups = buildWorkspaceGroups(
            registeredWorkspaces = listOf(WorkspaceEntry("a", "/proj/a", "ProjA")),
            sessions = many,
            defaultWorkspacePath = defaultPath,
            activeSessionId = "s1",
            activeProjectPath = "/proj/a",
        )
        val projA = baseGroups.first { it.path == "/proj/a" }
        assertEquals(5, projA.sessions.size)
        assertEquals(3, projA.hiddenCount)

        val expandedGroups = buildWorkspaceGroups(
            registeredWorkspaces = listOf(WorkspaceEntry("a", "/proj/a", "ProjA")),
            sessions = many,
            defaultWorkspacePath = defaultPath,
            activeSessionId = "s7",
            activeProjectPath = "/proj/a",
        )
        val revealedProjA = expandedGroups.first { it.path == "/proj/a" }
        assertEquals(7, revealedProjA.sessions.size)
        assertEquals(1, revealedProjA.hiddenCount)
    }

    @Test
    fun buildWorkspaceGroups_filtersAcrossWorkspacesWithoutHidingOverflow() {
        val defaultPath = "/data/root"
        val sessions = listOf(
            session("s1", "普通任务", "/proj/a", 5000L),
            session("s2", "深层调试", "/proj/b", 4000L, note = "修复动画"),
        )
        val filtered = buildWorkspaceGroups(
            registeredWorkspaces = emptyList(),
            sessions = sessions,
            defaultWorkspacePath = defaultPath,
            activeSessionId = "s1",
            activeProjectPath = "/proj/a",
            query = "动画",
            orderBy = SidebarOrderBy.UPDATED,
        )
        assertEquals(1, filtered.size)
        assertEquals("/proj/b", filtered.first().path)
        assertEquals("s2", filtered.first().sessions.first().id)
        assertEquals(0, filtered.first().hiddenCount)
    }

    @Test
    fun compactRelativeTime_andSidebarOrderByDefaults_matchExpectedContract() {
        val now = 1_700_000_000_000L
        assertEquals("刚刚", SessionReader.relativeTime(now - 20_000L, now))
        assertEquals("5分钟", SessionReader.relativeTime(now - 5 * 60_000L, now))
        assertEquals("11小时", SessionReader.relativeTime(now - 11 * 3_600_000L, now))
        assertEquals("3天", SessionReader.relativeTime(now - 3 * 86_400_000L, now))

        val uiState = WorkspaceUiState()
        assertEquals(SidebarOrderBy.UPDATED, uiState.sidebarOrderBy)
    }
}
