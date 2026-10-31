package com.zhizhu.zhicode.compose.theme

import androidx.compose.ui.unit.dp

/**
 * 视觉基准的**唯一真源**：圆角九档、间距节奏、行高与控件高度。
 *
 * ## 为什么要有这个文件
 *
 * 改版前工程里并存**三套**几何 token：
 *
 * | 来源 | 内容 | 典型值 |
 * | --- | --- | --- |
 * | `ZhiRadius` | 4 / 8 / 10 / 12 / 14 / 20 | 卡片 **14dp** |
 * | `ZhiSpacing` / `ZhiRow` | 页边距 16、行高 44 | — |
 * | `MochiMetrics`（`theme/MochiSkin.kt`） | Mochi 侧的行高与热区 | 44 / 48 |
 *
 * 三套都在用，于是同一个"卡片圆角"在不同页面写的是不同数字；而调用点还另有
 * `ZhiSquircle(28.dp)` / `ZhiSquircle(18.dp)` / `ZhiSquircle(14.dp)` 这种**直接写死**
 * 的用法（12 个文件）。这不是"设计有多档"，是**没有任何一处能回答这个值该是多少**。
 *
 * 现在全部收敛到这里，其余对象只做**转发**，调用点读到的仍是原来的名字。
 *
 * ## 数值来源：Mochi 的 `MochiShapes` 权威表
 *
 * 取自 `mochi-compose` 官方文档 `docs/zh_CN/guide/theme.md` 的 MochiShapes 一节
 * （**不是**从字节码反推 —— 我最初那版反推出来的 8/12/16/16/20/28/32/64 是错的）：
 *
 * | 名称 | 圆角 |
 * | --- | --- |
 * | extraSmall | 4dp |
 * | small | 8dp |
 * | medium | 12dp |
 * | large | 16dp |
 * | largeIncreased | 20dp |
 * | extraLarge | **28dp** |
 * | extraLargeIncreased | 32dp |
 * | extraExtraLarge | 48dp |
 * | full | 胶囊 |
 *
 * ## 同心公式（这是"视觉对齐"真正的判据）
 *
 * 规范要求：**容器的内层圆角 = 外层圆角 − 内边距**（`MochiConcentric`）。
 * 本工程页边距/内容内边距统一 16dp，于是：
 *
 * ```
 * 卡片 28 − 内边距 16 = 12 = medium
 * ```
 *
 * 这个 12 恰好等于 Mochi 的 `medium` 档 —— 两套数字能对上，说明 `card = 28`、
 * `inner = 12` 是**自洽的一组**，不是随手挑的。
 *
 * ## 关于「九档」与「本工程只用 4 个名字」
 *
 * 这里保留完整九档，是因为 Mochi 组件（`MochiListSection`、`MochiSheet` 等）
 * 从 `MochiTheme.shapes` 取的是档位名而不是数字。少给一档，将来接 Mochi 组件时
 * 就会缺值。工程自己的语义名（[ZhiRadius.card] 等）**指向**这九档里的某几档。
 */
object MochiBaseline {

    // ---- 圆角九档（1:1 对齐 MochiShapes）----

    val cornerExtraSmall = 4.dp
    val cornerSmall = 8.dp
    val cornerMedium = 12.dp
    val cornerLarge = 16.dp
    val cornerLargeIncreased = 20.dp
    val cornerExtraLarge = 28.dp
    val cornerExtraLargeIncreased = 32.dp
    val cornerExtraExtraLarge = 48.dp

    // ---- 间距节奏 ----

    /** 页面级水平边距（手机单手防误触基准）。 */
    val pageMargin = 16.dp

    /** 页面级水平边距（折叠屏 / 平板）。 */
    val pageMarginWide = 24.dp

    /**
     * 列表行文字的**内容起点**（不含卡片内边距）。
     *
     * Mochi 规定纯文字行的文字从 16dp 开始，与页边距同值 —— 这是"文字左缘对齐"
     * 的来源：分组小标题、说明文字、行内容都落在同一条竖线上。
     */
    val rowTextInset = 16.dp

    /** 页面内一级区块之间的距离。 */
    val sectionGap = 12.dp

    /** 行与行之间、行内元素之间。 */
    val rowGap = 8.dp

    /** 相邻可点元素之间的最小间距（防误触）。 */
    val touchGap = 8.dp

    // ---- 行高与控件高度 ----

    /**
     * 列表行 / 设置行的最小高度。
     *
     * Mochi 的 iOS 26 基准是 **52dp**（正文行高 + 上下各 15dp）。
     *
     * ⚠️ **这个值只作用于本工程自己画的行使之。**
     * Miuix 的 `BasicComponent` / `Preference` 内部行高由 `TextStyles` 撑起，
     * `docs/ui-miuix.md` 明确要求**不要覆盖库的内部 padding** —— 强行塞固定高度
     * 会让大字号下文字被裁。所以设置页那类行**改不到**，这里也不假装改到。
     */
    val rowHeight = 52.dp

    /** 紧凑行（终端扩展键、次级信息）。 */
    val compactRowHeight = 44.dp

    /**
     * 按钮 / 输入框的标准高度。
     *
     * Mochi：`MochiTextFieldDefaults.Height` 直接取 `MochiButtonDefaults.Height` = 48dp，
     * 所以并排的输入框与按钮**同高、同一条中线**。本工程自定义按钮按这个值走。
     */
    val controlHeight = 48.dp

    /** 最小触控热区（Android 无障碍规范）。 */
    val minTouchTarget = 48.dp

    /**
     * 密集工具行里的触控热区下限。
     *
     * 顶栏总高 48dp，里面再塞 48dp 的按钮盒会把图标挤到贴边；这一档**只允许**
     * 出现在高度受限的栏内，且必须与相邻可点元素保持 ≥ [touchGap] 间距。
     */
    val compactTouchTarget = 40.dp

    // ---- 同心 ----

    /**
     * 由外层圆角与内边距推出内层圆角，落实 `inner = outer − padding`。
     *
     * 结果下限钳到 0：内边距比圆角还大时（例如 4dp 圆角配 16dp 内边距），
     * 直接相减会得到负数，`RoundedCornerShape` 会抛异常。
     */
    fun concentric(outerRadius: androidx.compose.ui.unit.Dp, padding: androidx.compose.ui.unit.Dp) =
        (outerRadius - padding).coerceAtLeast(0.dp)
}
