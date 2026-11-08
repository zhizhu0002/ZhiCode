package com.zhizhu.zhicode.compose.model

/**
 * 拖动对选：把「从 anchor 块滑到 end 块」之间所有块的成员一次性选/取消。
 *
 * ## 为什么按「块」而不是按「消息」
 *
 * 对话流的一个 `LazyColumn` item 是**一个回合块**（见 `TurnLayout`）：
 * 用户消息独自成块，而一轮助手回复（正文 + 思考 + 若干工具组）合并在同一块里。
 *
 * 拖动时能拿到的可靠位置信息只有「手指落在第几个 item」—— 块内各成员的高度
 * 需要额外测量才知道，而拖动是逐帧的，不可能每帧去测。
 *
 * 所以按块区间处理：**滑过哪个回合，就选中那个回合的全部成员**。
 * 这在对话场景下语义正确（用户是想"把这一段对话导出去"，而不是"只要这条正文"），
 * 而且不会出现"块内一半选中、一半没选"的诡异状态。
 *
 * ## 与文件管理器那份的关系
 *
 * 判据与 [fileRangeSelection] 相同（区间内加/减 base），区别只在粒度：
 * 那份是"路径列表 + 下标"，这份是"每个块对应一组 id"。没有合并成一个函数，
 * 因为两者的数据结构不同，强行抽象会让两边都变难读。
 *
 * @param blockIds   每个块包含的消息 id（按列表顺序）
 * @param anchor     按下时的块下标
 * @param end        手指当前所在的块下标
 * @param base       这次拖动**开始前**的选中集合
 * @param selecting  true = 这次拖动是"选中"（起点未选中），false = "取消选中"
 */
fun blockRangeSelection(
    blockIds: List<List<String>>,
    anchor: Int,
    end: Int,
    base: Set<String>,
    selecting: Boolean,
): Set<String> {
    if (blockIds.isEmpty()) return base
    if (anchor !in blockIds.indices || end !in blockIds.indices) return base
    val from = minOf(anchor, end)
    val to = maxOf(anchor, end)
    val range = HashSet<String>()
    for (i in from..to) range.addAll(blockIds[i])
    return if (selecting) base + range else base - range
}
