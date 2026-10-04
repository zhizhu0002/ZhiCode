package com.zhizhu.zhicode.compose.model

fun fileRangeSelection(paths: List<String>, anchor: Int, end: Int, base: Set<String>, selecting: Boolean): Set<String> {
    if (anchor !in paths.indices || end !in paths.indices) return base
    val range = paths.subList(minOf(anchor, end), maxOf(anchor, end) + 1).toSet()
    return if (selecting) base + range else base - range
}
