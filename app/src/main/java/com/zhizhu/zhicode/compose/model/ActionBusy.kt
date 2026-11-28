package com.zhizhu.zhicode.compose.model

class ActionBusy {

    private val keys = linkedSetOf<String>()

    /** 尝试占用 [key]。 */
    fun acquire(key: String): Boolean = keys.add(key)

    /** 释放 [key]。重复释放是无害的。 */
    fun release(key: String) {
        keys.remove(key)
    }

    /** [key] 是否在跑。 */
    fun isBusy(key: String): Boolean = key in keys

    /** 任意动作在跑（用于"不要同时开两个破坏性操作"这类全局判断）。 */
    fun anyBusy(): Boolean = keys.isNotEmpty()

    /** 当前快照，交给界面做禁用与进度提示。 */
    fun snapshot(): Set<String> = keys.toSet()

    /** 进程内唯一的登记表。 */
    companion object {
        /** 保存类动作的前缀：`skills.save`、`memory.save`、`api.save` … */
        const val SAVE = "save"

        /** 删除类动作的前缀。 */
        const val DELETE = "delete"
    }
}
