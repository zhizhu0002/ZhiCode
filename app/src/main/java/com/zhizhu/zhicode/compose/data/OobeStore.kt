package com.zhizhu.zhicode.compose.data

import android.content.Context
import android.content.SharedPreferences
import com.termux.shared.termux.TermuxConstants

/** 首次引导（OOBE）完成状态存储；通过版本号确保新装与老用户升级都能进入引导。 */
internal object OobeStore {

    const val CURRENT_VERSION = 1
    private const val KEY_COMPLETED_VERSION = "oobe_completed_version"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("${TermuxConstants.BRAND_SLUG}_oobe", Context.MODE_PRIVATE)

    fun isRequired(context: Context, version: Int = CURRENT_VERSION): Boolean =
        prefs(context).getInt(KEY_COMPLETED_VERSION, 0) < version

    fun markCompleted(context: Context, version: Int = CURRENT_VERSION) {
        prefs(context).edit().putInt(KEY_COMPLETED_VERSION, version).apply()
    }

    fun reset(context: Context) {
        prefs(context).edit().remove(KEY_COMPLETED_VERSION).apply()
    }
}
