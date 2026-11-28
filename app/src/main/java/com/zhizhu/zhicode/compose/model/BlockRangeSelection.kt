package com.zhizhu.zhicode.compose.model

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
