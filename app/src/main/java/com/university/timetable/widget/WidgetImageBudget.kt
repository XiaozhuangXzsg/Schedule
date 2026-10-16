package com.university.timetable.widget

import android.content.Context
import kotlin.math.sqrt

/**
 * 桌面卡片里图片的内存预算。
 *
 * Android 17（API 37）起，系统对 `RemoteViews` 里 **Bitmap 与 Icon 的总内存**施加硬限制：
 * `1.5 × 屏宽 × 屏高 × 4` 字节。一旦超出，`AppWidgetManager.updateAppWidget` 会抛出致命的
 * `IllegalArgumentException` 并**直接杀掉应用进程**——不是忽略、不是降级。
 *
 * 本应用最多往卡片里放 3 张待办图片。若沿用应用内的 1440px 上限，
 * 单张 1440×1440×4 ≈ 8.3 MB，三张 ≈ 24.9 MB；而 1272×2772 的机器上限只有约 21.2 MB，
 * **已经超了**。所以卡片必须走一套更小的、按真实屏幕尺寸算出来的边长。
 */
internal object WidgetImageBudget {

    /** 卡片图片最多占用的份额；余量留给卡片里的图标与其他位图。 */
    internal const val SHARE = 0.25

    /** 卡片上图片显示得很小，超过这个边长肉眼已无差别；同时作为绝对上限兜底。 */
    internal const val HARD_CAP_PX = 512

    /** 下限，避免在极小屏上算出 0 导致图片完全不显示。 */
    internal const val MIN_PX = 128

    /** Android 17 对 RemoteViews 内 Bitmap/Icon 总内存的硬上限（字节）。 */
    internal fun remoteViewsLimitBytes(widthPx: Int, heightPx: Int): Long =
        (1.5 * widthPx * heightPx * 4).toLong()

    /**
     * 在给定内存上限下，平均分给 [imageCount] 张图后，单张图片允许的最大边长（像素）。
     *
     * 按 `边 × 边 × 4 字节`（ARGB_8888）反推，并夹在 [MIN_PX] 与 [HARD_CAP_PX] 之间。
     */
    internal fun maxDimension(limitBytes: Long, imageCount: Int): Int {
        if (imageCount <= 0) return MIN_PX
        val perImageBytes = limitBytes * SHARE / imageCount
        return sqrt(perImageBytes / 4.0).toInt().coerceIn(MIN_PX, HARD_CAP_PX)
    }

    /**
     * 结合设备真实屏幕尺寸，算出待办卡片图片应使用的最大边长。
     *
     * 用 `displayMetrics` 而不是 `currentWindowMetrics`：这里只取上限的 [SHARE] 份额，
     * 还额外被 [HARD_CAP_PX] 压住，尺寸精度不影响结论，但换来 minSdk 26 上的简单实现。
     */
    internal fun maxDimensionFor(context: Context, imageCount: Int): Int {
        val metrics = context.resources.displayMetrics
        return maxDimension(
            remoteViewsLimitBytes(metrics.widthPixels, metrics.heightPixels),
            imageCount
        )
    }
}
