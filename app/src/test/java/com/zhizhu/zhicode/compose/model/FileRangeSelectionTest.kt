package com.zhizhu.zhicode.compose.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FileRangeSelectionTest {
    private val paths = listOf("a", "b", "c", "d", "e")

    @Test fun expandsAndShrinksFromSnapshot() {
        val base = setOf("e")
        assertEquals(setOf("b", "c", "d", "e"), fileRangeSelection(paths, 1, 3, base, true))
        assertEquals(setOf("b", "e"), fileRangeSelection(paths, 1, 1, base, true))
    }

    @Test fun supportsReverseDirection() {
        assertEquals(setOf("b", "c", "d"), fileRangeSelection(paths, 3, 1, emptySet(), true))
    }

    @Test fun deselectionRestoresItemsOutsideShrunkRange() {
        val base = paths.toSet()
        assertEquals(setOf("a", "e"), fileRangeSelection(paths, 1, 3, base, false))
        assertEquals(setOf("a", "c", "d", "e"), fileRangeSelection(paths, 1, 1, base, false))
    }

    @Test fun invalidIndicesLeaveSelectionAlone() {
        assertEquals(setOf("a"), fileRangeSelection(paths, -1, 3, setOf("a"), true))
        assertEquals(setOf("a"), fileRangeSelection(paths, 0, 5, setOf("a"), false))
    }
}
