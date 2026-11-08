package com.zhizhu.zhicode.compose.theme

import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.defaultTextStyles

/**
 * 应用的 Miuix 文字主题：**直接用库的原生字阶**。
 *
 * ## 为什么不再自己压一档
 *
 * 这个文件在历史上改过三次，方向来回摆过：
 *
 * | 提交 | 做法 |
 * | --- | --- |
 * | `9c7f8fa` | 紧凑中间值（title1 24 / main 14） |
 * | `bb7924f` | 恢复原生字阶（title1 32 / main 17） |
 * | 本轮之前 | 又压回紧凑档（title1 24 / main 14） |
 *
 * 摆动的根源是把"看起来太大"当成了字号问题。实际上那批观感问题出在**别处**：
 * 卡片内边距、固定行高、以及若干页面里硬写的 `fontSize = 34.sp` 这类**绕过字阶**的值。
 * 逐槽位压字号是在用一个全局修改去补局部排版，代价是：
 *
 * 1. **偏离 Miuix 的设计规范**。`defaultTextStyles()` 就是规范本身
 *    （`miuix-ui/theme/TextStyles.kt`），它只有一套值、没有机型变体。
 *    这个工程整套 UI 都建立在 Miuix 之上，字体却不是它规定的尺寸，
 *    于是"官方的组件 + 自己的字"在视觉上永远差一点，且没有对照物可调。
 * 2. **层级被压缩**。`title1` 32→24、`title4` 18→15，四档标题挤进 9sp 的区间里，
 *    层级感明显变弱；`body1` 16→13 与 `body2` 14→12 只差 1sp，几乎分不出来。
 * 3. **下游组件按规范布局**。Miuix 的行高、图标底板、`SmallTopAppBar` 高度等
 *    都是按原生字号算的；字号一改，那些地方就要靠页面各自打补丁。
 *
 * 所以这里改回规范值。**要调观感请去调排版**（内边距、行高、卡片间距），
 * 而不是动这张表 —— 那才是问题真正所在。
 *
 * ## 原生字阶（`defaultTextStyles()`）
 *
 * | 槽位 | 字号 |
 * | --- | --- |
 * | `title1` / `title2` / `title3` / `title4` | 32 / 24 / 20 / 18 |
 * | `main` / `paragraph` / `button` / `headline1` | 17 |
 * | `body1` / `headline2` | 16 |
 * | `body2` / `subtitle` | 14 |
 * | `footnote1` / `footnote2` | 13 / 11 |
 *
 * 这套值由 `ZhiTextStylesTest` 逐项守卫（对照上面这张表）。
 *
 * ## 为什么还留着这个函数
 *
 * 调用方（`ZhiAppTheme`）通过它取字阶，所以"字阶从哪来"只有这一个入口。
 * 将来真要统一缩放，在这里乘一个系数即可 —— 那时它是**一处可读的数**：
 *
 * ```kotlin
 * val s = 0.92f
 * val base = defaultTextStyles()
 * fun TextStyle.scaled(k: Float) = copy(fontSize = (fontSize.value * k).sp)
 * base.copy(title1 = base.title1.scaled(s), …)
 * ```
 *
 * ⚠️ 但**不要**再逐个槽位写死数字。逐个写的话，没有任何一处能回答
 * "整体偏离规范多少"，每次调都要重新数一遍 —— 上面那张历史表就是这么来的。
 */
fun zhiTextStyles(): TextStyles = defaultTextStyles()
