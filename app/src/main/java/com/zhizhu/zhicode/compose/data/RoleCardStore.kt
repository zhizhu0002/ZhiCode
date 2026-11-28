package com.zhizhu.zhicode.compose.data

import android.content.Context
import com.zhizhu.zhicode.compose.model.RoleCard
import com.termux.app.zhicode.storage.ApiSettingsStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal object RoleCardStore {

    fun list(context: Context): List<RoleCard> = runCatching {
        val array = ApiSettingsStore(context).getRoleCards()
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { card ->
                RoleCard(
                    id = card.optString("id"),
                    name = card.optString("name").ifBlank { "未命名角色" },
                    content = card.optString("content"),
                )
            }
        }
    }.getOrDefault(emptyList())

    fun activeId(context: Context): String =
        runCatching { ApiSettingsStore(context).getActiveRoleCardId() }.getOrDefault("")

    /** 当前启用卡的内容。没有启用项时返回空串（引擎会因此不注入任何角色块）。 */
    fun activeContent(context: Context): String {
        val id = activeId(context)
        if (id.isEmpty()) return ""
        return list(context).firstOrNull { it.id == id }?.content.orEmpty()
    }

    /** 保存卡片清单与启用项。 */
    fun save(context: Context, cards: List<RoleCard>, activeId: String): Result<Unit> = runCatching {
        val array = JSONArray()
        cards.forEach { card ->
            array.put(
                JSONObject()
                    .put("id", card.id)
                    .put("name", card.name)
                    .put("content", card.content),
            )
        }
        ApiSettingsStore(context).saveRoleCards(array, activeId)
        Unit
    }

    /** 新增（id 为空时生成）。返回保存后的卡片。 */
    fun upsert(context: Context, draft: RoleCard): Result<RoleCard> = runCatching {
        val cards = list(context).toMutableList()
        val card = if (draft.id.isBlank()) draft.copy(id = UUID.randomUUID().toString()) else draft
        val index = cards.indexOfFirst { it.id == card.id }
        if (index >= 0) cards[index] = card else cards.add(card)
        save(context, cards, card.id).getOrThrow()
        card
    }

    /** 删除。删掉的正好是启用项时顺带停用，否则会留下一个指向不存在卡片的 activeId。 */
    fun delete(context: Context, id: String): Result<Unit> = runCatching {
        val cards = list(context).filterNot { it.id == id }
        val active = if (activeId(context) == id) "" else activeId(context)
        save(context, cards, active).getOrThrow()
    }
}
