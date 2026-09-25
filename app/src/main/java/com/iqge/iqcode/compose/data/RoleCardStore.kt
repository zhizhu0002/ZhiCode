package com.iqge.iqcode.compose.data

import android.content.Context
import com.iqge.iqcode.compose.model.RoleCard
import com.termux.app.iqcode.storage.ApiSettingsStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 角色卡的读写。
 *
 * ## 存储在哪（以及为什么不自己存）
 *
 * 角色卡列表与"当前启用项"由引擎侧 [ApiSettingsStore] 持久化
 * （`getRoleCards()` / `saveRoleCards(cards, activeId)` / `getActiveRoleCardId()`），
 * 而**内容**是通过 `SessionConfig.roleCard` 生效的：引擎的 `SystemPromptBuilder`
 * 把它包成 `<role_card>` 块拼进系统提示词。
 *
 * 所以这里不新建存储，只做两件事：把 JSON 翻成界面模型、把选中项的内容
 * 交给调用方去灌进会话配置。
 *
 * ## 为什么内容会重复存一份
 *
 * `saveRoleCards` 存的是"卡片清单"，`SessionConfig.roleCard` 存的是"当前生效内容"。
 * 这两份必须同步——原版在选中/停用时两个都写。不同步的现象是：
 * 列表里明明勾着某张卡，但实际请求里没有它（或反过来，停用了却还在生效）。
 */
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

    /**
     * 保存卡片清单与启用项。
     *
     * 空 id 表示"停用"。选择与停用都走这一个入口，避免出现两条写法不一致的路径。
     */
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
