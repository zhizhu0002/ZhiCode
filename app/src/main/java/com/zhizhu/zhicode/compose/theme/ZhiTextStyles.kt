package com.zhizhu.zhicode.compose.theme

import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.defaultTextStyles

/**
 * 应用的 Miuix 文字主题。
 *
 * ## 现在直接用 Miuix 的原生字阶，不再压缩
 *
 * 这里曾经把 Miuix 的每个槽位都收窄一档（`title1` → 24sp、`headline1` → 14sp、
 * `body1` → 13sp、`body2` → 12sp、`footnote1` → 11sp …），理由是"手机信息密度"。
 *
 * 那个理由不算错，但它有一个代价在逐屏对照中暴露了出来：
 * **所有 `miuix-preference` 组件的行高都由这几个槽位撑起**。
 * 于是设置页、关于页、第三方许可页的行——比 Miuix 官方 example 整整小一号：
 * 标题不够醒目、卡片被内容压扁、行与行之间没有呼吸感。
 * 用户拿官方 App 与截图逐屏对比时，第一眼看到的就是「卡片和字号好小」。
 *
 * 所以现在**原样返回 `defaultTextStyles()`**：与 Miuix 官方对齐，
 * 组件行高与字号层级全部回到库的设计值。
 *
 * ## 为什么保留这个函数而不是在调用点直接写 `defaultTextStyles()`
 *
 * 它现在是恒等转发，看起来可以直接删掉。但它仍是**唯一**的字阶切换点：
 * 应用主题（`ZhiAppTheme`）与 `MiuixTheme` 的接线都指向它。将来若要重新调整
 * （只对大屏压一档、跟随系统字号…），改动仍然只有这一处。
 * 一个恒等的命名函数不是抽象，是把"以后可能要调"这件事留在原地。
 *
 * ## 不要再逐个槽位改
 *
 * 上一版把 15 个槽位各写一行 `sized(…)`，结果是**没有任何一处能回答"整体缩小了多少"**
 * —— 每次调都要重新数一遍。真要再压，应当乘一个统一系数：
 *
 * ```kotlin
 * val s = 0.92f
 * base.copy(title1 = base.title1.scaled(s), …)
 * ```
 *
 * 这样"缩了多少"是源码里一个数，而不是十五个数。
 */
fun zhiTextStyles(): TextStyles = defaultTextStyles()
