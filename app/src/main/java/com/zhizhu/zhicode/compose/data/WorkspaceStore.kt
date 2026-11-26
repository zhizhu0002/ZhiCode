package com.zhizhu.zhicode.compose.data

import android.content.Context
import com.zhizhu.zhicode.compose.model.SessionSummary
import com.zhizhu.zhicode.compose.model.SidebarOrderBy
import com.zhizhu.zhicode.compose.model.WorkspaceEntry
import com.zhizhu.zhicode.compose.model.WorkspaceGroup
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal const val COLLAPSED_SESSION_LIMIT = 5
private const val AUTO_WATERMARK_MAX = 50

internal fun canonicalWorkspacePath(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    return trimmed.ifEmpty { "/" }
}

/** 根据已注册工作区与全库会话构建 DeepSeek Harness 风格的工作区会话分组树。 */
internal fun buildWorkspaceGroups(
    registeredWorkspaces: List<WorkspaceEntry>,
    sessions: List<SessionSummary>,
    defaultWorkspacePath: String,
    activeSessionId: String,
    activeProjectPath: String,
    query: String = "",
    orderBy: SidebarOrderBy = SidebarOrderBy.UPDATED,
    expandedMap: Map<String, Boolean> = emptyMap(),
    watermarks: Map<String, Int> = emptyMap(),
    collapsedLimit: Int = COLLAPSED_SESSION_LIMIT,
): List<WorkspaceGroup> {
    val canonicalDefault = canonicalWorkspacePath(defaultWorkspacePath)
    val canonicalActiveProject = canonicalWorkspacePath(activeProjectPath)
    val needle = query.trim().lowercase()

    // 先收集已注册工作区，保证默认工作区始终存在。
    val entryMap = LinkedHashMap<String, WorkspaceEntry>()
    val defaultEntry = registeredWorkspaces.firstOrNull {
        canonicalWorkspacePath(it.path) == canonicalDefault || it.isDefault
    }?.copy(path = canonicalDefault, isDefault = true)
        ?: WorkspaceEntry(
            id = "default",
            path = canonicalDefault,
            label = "默认工作区",
            isDefault = true,
            expanded = true,
        )

    for (entry in registeredWorkspaces) {
        val cPath = canonicalWorkspacePath(entry.path)
        if (cPath == canonicalDefault || entry.isDefault) continue
        if (cPath !in entryMap) {
            entryMap[cPath] = entry.copy(
                id = entry.id.ifBlank { cPath },
                path = cPath,
                label = entry.label.ifBlank { WorkspacePaths.nameOf(cPath) },
                isDefault = false,
            )
        }
    }

    // 把全库会话按规范 projectPath 归组，未注册目录自动纳管以免历史失踪。
    val sessionsByPath = LinkedHashMap<String, MutableList<SessionSummary>>()
    for (session in sessions) {
        val rawPath = session.projectPath.ifBlank { canonicalDefault }
        val cPath = canonicalWorkspacePath(rawPath)
        val bucketKey = if (cPath == canonicalDefault) canonicalDefault else cPath
        if (bucketKey != canonicalDefault && bucketKey !in entryMap) {
            entryMap[bucketKey] = WorkspaceEntry(
                id = bucketKey,
                path = bucketKey,
                label = WorkspacePaths.nameOf(bucketKey),
                isDefault = false,
                expanded = true,
                createdAt = session.updatedAtMs,
            )
        }
        sessionsByPath.getOrPut(bucketKey) { mutableListOf() }.add(session)
    }

    val allEntries = entryMap.values.toList() + defaultEntry
    val groups = allEntries.mapNotNull { entry ->
        val cPath = canonicalWorkspacePath(entry.path)
        val rawList = sessionsByPath[cPath].orEmpty().sortedByDescending { it.updatedAtMs }
        val filtered = if (needle.isEmpty()) {
            rawList
        } else {
            rawList.filter { s ->
                s.title.lowercase().contains(needle) ||
                    s.note.lowercase().contains(needle) ||
                    entry.label.lowercase().contains(needle)
            }
        }
        if (needle.isNotEmpty() && filtered.isEmpty()) return@mapNotNull null

        val containsActive = (activeSessionId.isNotEmpty() && rawList.any { it.id == activeSessionId }) ||
            (activeSessionId.isEmpty() && cPath == canonicalActiveProject)
        val expanded = if (needle.isNotEmpty()) {
            true
        } else {
            expandedMap[cPath] ?: entry.expanded
        }
        val activeOrBusyIdx = filtered.indexOfFirst { it.id == activeSessionId || it.busy }
        val requiredByAttention = if (activeOrBusyIdx in 0 until AUTO_WATERMARK_MAX) {
            activeOrBusyIdx + 1
        } else {
            collapsedLimit
        }
        val limit = maxOf(collapsedLimit, watermarks[cPath] ?: collapsedLimit, requiredByAttention)
        val visible = if (needle.isNotEmpty()) filtered else filtered.take(limit)
        val hiddenCount = if (needle.isNotEmpty()) 0 else (filtered.size - visible.size).coerceAtLeast(0)
        WorkspaceGroup(
            key = cPath,
            title = entry.label,
            path = cPath,
            isDefault = entry.isDefault,
            expanded = expanded,
            containsActive = containsActive,
            totalCount = filtered.size,
            hiddenCount = hiddenCount,
            sessions = visible,
        )
    }

    return when (orderBy) {
        SidebarOrderBy.MANUAL -> groups
        SidebarOrderBy.UPDATED -> groups.sortedWith(
            compareByDescending<WorkspaceGroup> { it.sessions.firstOrNull()?.updatedAtMs ?: 0L }
                .thenBy { if (it.isDefault) 1 else 0 },
        )
    }
}

/** 扫描默认 `$HOME` 下一级非隐藏子目录作为快捷工作区候选。 */
internal fun discoverChildWorkspaceCandidates(rootPath: String, existingPaths: Set<String>): List<WorkspaceEntry> {
    val dir = File(rootPath)
    if (!dir.isDirectory) return emptyList()
    val canonicalExisting = existingPaths.mapTo(HashSet()) { canonicalWorkspacePath(it) }
    val children = dir.listFiles { file ->
        file.isDirectory && !file.name.startsWith(".") && file.name != "tmp"
    }?.sortedBy { it.name.lowercase() }.orEmpty()
    return children.mapNotNull { child ->
        val cPath = canonicalWorkspacePath(child.absolutePath)
        if (cPath in canonicalExisting) null
        else WorkspaceEntry(id = cPath, path = cPath, label = child.name, isDefault = false)
    }
}

/** 工作区列表、展开态与排序偏好的持久化仓库。 */
internal class WorkspaceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadWorkspaces(defaultPath: String): List<WorkspaceEntry> {
        val canonicalDefault = canonicalWorkspacePath(defaultPath)
        val raw = prefs.getString(KEY_WORKSPACES, null)?.trim().orEmpty()
        val parsed = if (raw.isEmpty()) {
            emptyList()
        } else {
            runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { idx ->
                    val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                    val path = canonicalWorkspacePath(obj.optString("path", ""))
                    if (path.isBlank()) return@mapNotNull null
                    WorkspaceEntry(
                        id = obj.optString("id", path).ifBlank { path },
                        path = path,
                        label = obj.optString("label", WorkspacePaths.nameOf(path)).ifBlank { WorkspacePaths.nameOf(path) },
                        isDefault = obj.optBoolean("isDefault", path == canonicalDefault),
                        expanded = obj.optBoolean("expanded", true),
                        createdAt = obj.optLong("createdAt", 0L),
                    )
                }
            }.getOrDefault(emptyList())
        }
        val hasDefault = parsed.any { it.path == canonicalDefault || it.isDefault }
        if (hasDefault) return parsed
        return parsed + WorkspaceEntry(
            id = "default",
            path = canonicalDefault,
            label = "默认工作区",
            isDefault = true,
            expanded = true,
        )
    }

    fun saveWorkspaces(entries: List<WorkspaceEntry>) {
        val arr = JSONArray()
        for (entry in entries) {
            arr.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("path", canonicalWorkspacePath(entry.path))
                    .put("label", entry.label)
                    .put("isDefault", entry.isDefault)
                    .put("expanded", entry.expanded)
                    .put("createdAt", entry.createdAt),
            )
        }
        prefs.edit().putString(KEY_WORKSPACES, arr.toString()).apply()
    }

    fun loadOrderBy(): SidebarOrderBy {
        val raw = prefs.getString(KEY_ORDER_BY, SidebarOrderBy.UPDATED.name)
        return SidebarOrderBy.entries.firstOrNull { it.name == raw } ?: SidebarOrderBy.UPDATED
    }

    fun saveOrderBy(orderBy: SidebarOrderBy) {
        prefs.edit().putString(KEY_ORDER_BY, orderBy.name).apply()
    }

    fun loadLastActivePath(defaultPath: String): String {
        val raw = prefs.getString(KEY_ACTIVE_PATH, null)?.trim().orEmpty()
        return if (raw.isNotEmpty() && File(raw).isDirectory) canonicalWorkspacePath(raw)
        else canonicalWorkspacePath(defaultPath)
    }

    fun saveLastActivePath(path: String) {
        if (path.isBlank()) return
        prefs.edit().putString(KEY_ACTIVE_PATH, canonicalWorkspacePath(path)).apply()
    }

    companion object {
        private const val PREFS_NAME = "zhicode_workspaces"
        private const val KEY_WORKSPACES = "workspaces_json"
        private const val KEY_ORDER_BY = "sidebar_order_by"
        private const val KEY_ACTIVE_PATH = "last_active_path"
    }
}
