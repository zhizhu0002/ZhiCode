package com.zhizhu.zhicode.compose.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.defaultTextStyles

/**
 * 应用自有的**手机紧凑字阶**。
 *
 * ## 为什么必须有这样一处（而不是继续逐处传 `fontSize`）
 *
 * Miuix 的默认字阶是给平板/桌面尺度的（实测本地 0.9.4 产物）：
 * `title1..4 = 32/24/20/18sp`、`main`/`paragraph`/`button`/`headline1` = 17sp、
 * `body1`/`headline2` = 16sp、`body2`/`subtitle` = 14sp、`footnote1` = 13sp、
 * `footnote2` = 11sp。手机上一屏塞不下，所以本工程实际一直在用更小的一档
 * （9.3 ~ 21sp）。
 *
 * 问题是那一档**只存在于各处硬编码的 `fontSize =` 里**：全工程约 120 处、
 * 15 种不同字号（9.3/9.5/10/10.5/11/11.5/12/12.5/13/13.5/14/15/17/18/21），
 * 而 `MiuixTheme.textStyles` 只被 import 过一次。后果有两个：
 *
 * 1. **数字会自己长出新档位**。同一层次的东西改两次就成了 12.5 与 13 并存，
 *    看起来就是"没设计过" —— 与当初 `RoundedCornerShape(7/8/9/11/13/16dp)`
 *    满地跑是同一类问题，那次靠 [ZhiRadius] 收敛掉了，字阶没有。
 * 2. **改不动 Miuix 自己的组件**。`TextField` 读 `main`、`Button` 读 `button`、
 *    `BasicComponent` 标题读 `headline1`/说明读 `body2`、`SmallTitle` 读 `subtitle`
 *    （以上为对本地 0.9.4 产物的实测结果，不是推测）。这些组件的字号
 *    **逐个传 `fontSize` 是够不到的**，只能覆盖主题样式 —— 所以必须在根级做一次。
 *
 * ## 档位是从哪来的（不是拍出来的）
 *
 * [ZhiTextScale] 的九个值取自本工程**已经在用的**手写字号里出现频率最高、
 * 且能构成单调阶梯的那些。第一步先按「每个 Miuix 档位都有人用」的直觉选了
 * 10.5/11.5/12.5 做中低档，实测发现那正好是**少数派**：工程里 10/11/12 分别
 * 出现 34/19/18 次，而 10.5/11.5/12.5 只有 15/4/2 次。照前者定档，
 * 六成（71 处）的调用点都要平移 0.5sp；照后者定档只需动 25 处，且阶梯是整数、
 * 节奏更干净。所以最终取整数档 —— 这不是拿 Miuix 默认值乘系数硬算
 * （那样顶端会偏出去：32×0.75 = 24，而工程最大只用 21），而是一个**手机尺度**
 * 的实用阶梯，缩放比例在各档之间并不统一（0.66 ~ 0.79），刻意如此：
 * 顶部压得比底部狠，因为大标题最占地方。
 *
 * 单调性由 [verifyOrder] 在类初始化时守住：Miuix 自己的层级关系
 * （标题 > 正文 > 脚注）不能因为这次覆盖而被拉平或倒过来。
 */

/** 紧凑字阶的九个档位。迁移调用点时用它，不要写新的字面量。 */
object ZhiTextScale {

    /** 空状态大标题。替代散落的 `21.sp`（`MessageCards` 空状态）。 */
    val Title = 21.sp

    /** 品牌名、页面级标题。替代 `17.sp`。 */
    val TitleSmall = 17.sp

    /** 区块标题、面板标题行。替代 `15.sp`（顶栏对话标题、终端标题）。 */
    val Heading = 15.sp

    /** 卡片标题、输入框正文。替代 `14.sp`。 */
    val Subheading = 14.sp

    /** 常规正文 —— 出现最多的一档。替代 `13.sp`。 */
    val Body = 13.sp

    /** 次要正文（设置行说明、列表副标题）。 */
    val BodySmall = 12.sp

    /** 说明文字、标签。 */
    val Caption = 11.sp

    /** 脚注（时间戳、计数、单位）。 */
    val Footnote = 10.sp

    /** 最小字（徽标、极小注记）。替代 `9.5.sp`、`9.3.sp`。 */
    val Micro = 9.5.sp

    /**
     * 脚注档比最小档大、正文档比脚注档大……
     *
     * 这个断言存在的理由：这些值将来一定还会被调，而**把两档调成相等或颠倒**
     * 不会让编译失败，只会让界面层级糊掉（标题和正文一样大、脚注比正文还大），
     * 那是最难被看一眼就发现的一类回归。
     */
    private val ascending = listOf(
        "Micro" to Micro,
        "Footnote" to Footnote,
        "Caption" to Caption,
        "BodySmall" to BodySmall,
        "Body" to Body,
        "Subheading" to Subheading,
        "Heading" to Heading,
        "TitleSmall" to TitleSmall,
        "Title" to Title,
    )

    init {
        for (i in 1 until ascending.size) {
            val (prevName, prev) = ascending[i - 1]
            val (name, value) = ascending[i]
            require(value.value > prev.value) {
                "字阶必须单调递增：$prevName(${prev.value}) 不小于 $name(${value.value})"
            }
        }
    }
}

/**
 * 本工程的紧凑 [TextStyles]：以 Miuix 的默认样式为底，只换字号。
 *
 * 只换字号、不动其它字段是刻意的：
 * - `subtitle` 的 **Bold** 权重是 Miuix 的设计意图（小标题要比正文显眼），保留；
 * - `paragraph` 的 `lineHeight = 1.2em` 是**相对单位**，会跟着字号自动缩放，
 *   所以不需要（也不应该）像弹窗那样置为 Unspecified。
 *
 * 不用 `MiuixTheme.textStyles` 作底：那个值一旦是本函数提供的，就形成了
 * 自己读自己的循环。这里用 Miuix 的静态默认值作为唯一的底。
 */
fun zhiTextStyles(): TextStyles {
    val base = defaultTextStyles()
    fun TextStyle.sized(size: TextUnit) = copy(fontSize = size)
    return base.copy(
        title1 = base.title1.sized(ZhiTextScale.Title),
        title2 = base.title2.sized(ZhiTextScale.TitleSmall),
        title3 = base.title3.sized(ZhiTextScale.Heading),
        title4 = base.title4.sized(ZhiTextScale.Subheading),
        // 四档正文共用一档：Miuix 里它们本来就是同一个 17sp，
        // 拆开只会诱使调用点各自挑一个不同的值。
        main = base.main.sized(ZhiTextScale.Body),
        paragraph = base.paragraph.sized(ZhiTextScale.Body),
        button = base.button.sized(ZhiTextScale.Body),
        headline1 = base.headline1.sized(ZhiTextScale.Body),
        // body1 与 headline2 在 Miuix 里同为 16sp
        body1 = base.body1.sized(ZhiTextScale.BodySmall),
        headline2 = base.headline2.sized(ZhiTextScale.BodySmall),
        // body2 与 subtitle 在 Miuix 里同为 14sp
        body2 = base.body2.sized(ZhiTextScale.Caption),
        subtitle = base.subtitle.sized(ZhiTextScale.Caption),
        footnote1 = base.footnote1.sized(ZhiTextScale.Footnote),
        footnote2 = base.footnote2.sized(ZhiTextScale.Micro),
    )
}
