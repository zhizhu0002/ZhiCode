package com.zhizhu.zhicode.compose.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图片降采样倍数的边界。
 *
 * <p>这是整条图片链路里唯一真正会算错的地方，而它算错的两种后果都只能靠肉眼发现：
 * 倍数太小 → 一张 4000×3000 的截图按原尺寸解码是 48 MB，低端机直接 OOM 崩掉；
 * 倍数太大 → 缩略图糊成马赛克。所以把它抽成纯 Int 函数钉在这里。
 */
class ZhiImageDecodeTest {

    @Test
    fun `原图小于目标时不降采样`() {
        assertEquals(1, sampleSizeFor(800, 600, 2000, 2000))
    }

    @Test
    fun `原图恰好等于目标时不降采样`() {
        // 边界：相等不该被降，否则 200×200 的图会被吐成 100×100 的糊图。
        assertEquals(1, sampleSizeFor(400, 400, 400, 400))
    }

    @Test
    fun `四倍大的图降两档`() {
        // 1600/2 = 800 仍 >= 400；800/2 = 400 >= 400 也成立 → 会到 4。
        // 这条用例的意义是把"向下取整到 2 的幂"这个方向钉死：
        // 若改成四舍五入，1600/400 = 4 → 仍是 4，所以再补一条非整倍数的。
        assertEquals(4, sampleSizeFor(1600, 1600, 400, 400))
    }

    @Test
    fun `非整倍数向下取整而不是向上`() {
        // 1000/2 = 500 >= 400 ✓；500/2 = 250 >= 400 ✗ → sample = 2。
        // 向上取整会得到 4，结果是解码出 250px 的图放进 400px 的框 —— 图会糊。
        assertEquals(2, sampleSizeFor(1000, 1000, 400, 400))
    }

    @Test
    fun `宽扁图按两边都满足才继续降`() {
        // 宽 4000、高 100。高度这边 100/2 = 50 < 400 立刻不满足，
        // 所以只降到 1 档就不动了 —— 这正是我们要的：一张全景图不该因为
        // "宽度还很大"被一路降到底，那会把高度压成几个像素。
        val sample = sampleSizeFor(4000, 100, 400, 400)
        assertTrue("宽扁图不应被压到小于原高：sample=$sample", 100 / sample >= 40)
    }

    @Test
    fun `尺寸未知时返回 1 而不是崩溃或 0`() {
        // BitmapFactory 对非图片数据会把 outWidth/outHeight 留成 -1。
        assertEquals(1, sampleSizeFor(-1, -1, 400, 400))
        assertEquals(1, sampleSizeFor(0, 0, 400, 400))
    }

    @Test
    fun `目标为 0 时返回 1（绝不返回 0）`() {
        // 返回 0 会让 BitmapFactory 抛 IllegalArgumentException。
        assertEquals(1, sampleSizeFor(4000, 3000, 0, 0))
    }

    @Test
    fun `真实手机截图降到缩略图尺度`() {
        // 4000×3000 的截图放进 400×280 的框（200dp/140dp @2x）：
        // 4000/8 = 500 >= 400 ✓，3000/8 = 375 >= 280 ✓ → 8。
        // 8 倍之后是 500×375，约 0.75 MB（RGB_565 两字节/像素），
        // 比原尺寸的 24 MB 低两个数量级。
        assertEquals(8, sampleSizeFor(4000, 3000, 400, 280))
    }

    // ---------------------------------------------------------------- 宽度夹取
    //
    // 同一行缩略图必须**同高**，宽度按原图长宽比算出来再夹进一个范围。
    // 夹取算错只有两种表现：全景图占满整屏（后面的图全被推到屏幕外），
    // 或长截图细成一条线（看不出内容）。两种都只能靠肉眼发现，所以钉在这里。

    @Test
    fun `普通照片按比例算宽`() {
        // 4:3 横图，高 140 → 140 * 4/3 = 186.67
        assertEquals(186.67f, thumbWidthFor(140f, 4f / 3f, 72f, 260f), 0.1f)
        // 3:4 竖图，高 140 → 105
        assertEquals(105f, thumbWidthFor(140f, 3f / 4f, 72f, 260f), 0.1f)
    }

    @Test
    fun `全景图被宽度上限夹住`() {
        // 3:1 的全景，高 140 → 按比例是 420dp，占满整屏。
        // 夹到 260 之后一行至少还能看到一张半，横向滑动才有意义。
        assertEquals(260f, thumbWidthFor(140f, 3f, 72f, 260f), 0.01f)
    }

    @Test
    fun `长截图被宽度下限夹住`() {
        // 1:4 的长截图，高 140 → 按比例只有 35dp，细成一条线。
        assertEquals(72f, thumbWidthFor(140f, 1f / 4f, 72f, 260f), 0.01f)
    }

    @Test
    fun `长宽比未知时退回正方形占位`() {
        // 尺寸还没量到时（头还没解析）必须给一个稳定的占位宽度，
        // 否则整行会在两三帧内抖一下。
        assertEquals(140f, thumbWidthFor(140f, null, 72f, 260f), 0.01f)
        assertEquals(140f, thumbWidthFor(140f, 0f, 72f, 260f), 0.01f)
        assertEquals(140f, thumbWidthFor(140f, -2f, 72f, 260f), 0.01f)
        // NaN / Infinity：坏数据不该让它算出 NaN 宽度（NaN.dp 会直接崩）
        assertEquals(140f, thumbWidthFor(140f, Float.NaN, 72f, 260f), 0.01f)
        assertEquals(140f, thumbWidthFor(140f, Float.POSITIVE_INFINITY, 72f, 260f), 0.01f)
    }

    @Test
    fun `上下限传反了也不会抛异常`() {
        // coerceIn 在 min > max 时抛 IllegalArgumentException —— 那是"打开对话直接崩"。
        // 这个函数必须自己把顺序摆正。
        assertEquals(260f, thumbWidthFor(140f, 3f, 260f, 72f), 0.01f)
        assertEquals(72f, thumbWidthFor(140f, 0.1f, 260f, 72f), 0.01f)
    }

    @Test
    fun `待发方框上下限相等时恒为正方形`() {
        // 待发区把上下限都钉在同一个值上：删除键压在右上角，宽度一变 X 就会飘。
        assertEquals(56f, thumbWidthFor(56f, 3f, 56f, 56f), 0.01f)
        assertEquals(56f, thumbWidthFor(56f, 0.25f, 56f, 56f), 0.01f)
    }
}
