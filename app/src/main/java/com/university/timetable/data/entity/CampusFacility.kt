package com.university.timetable.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 校园生活设施：洗衣机 / 烘干机 / 换桶机 / 浴室 / 公寓门禁 / 供电 等。
 *
 * 开放时间按「星期」与「节假日」区分，因此同一天可能有多段区间
 * （例如浴室 11:00-14:00 & 16:30-23:00）。
 */
@Entity(tableName = "campus_facilities")
data class CampusFacility(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 名称，例如「滚筒洗衣机」「浴室」「公寓大门」 */
    val name: String,
    /**
     * 类型：
     * DOOR 公寓开关门 / POWER 宿舍供电 / BATH 浴室 / LAUNDRY 洗衣机 /
     * DRYER 烘干机 / CHANGE 换桶机 / SHOE 洗鞋机 / HAIRDRYER 吹风机 /
     * WATER 饮水机 / STUDY 自习室 / CUSTOM 其他
     */
    val type: String = "CUSTOM",
    /** 位置说明，例如「1-6 层」「1 层」「2 层、5 层」 */
    val location: String? = null,
    /** 默认（每天通用）开放区间，编码串，例如 "11:00-14:00;16:30-23:00" */
    val windows: String,
    /**
     * 按星期覆盖，编码串：[TimeWindowCodec.encodeDays] 生成，
     * 以 `#` 分隔 7 段（周一..周日），某段为空表示沿用 [windows]。
     */
    val dayWindows: String = "",
    /** 节假日（Holiday 表中 isHoliday=true 的日期）使用的区间编码串，空表示沿用默认 */
    val holidayWindows: String = "",
    /** 备注，例如「上午停开检修」 */
    val note: String? = null,
    val enabled: Boolean = true,
    val sortOrder: Int = 0
)

/**
 * 作息区间的编解码工具。
 *
 * 编码格式：`HH:mm-HH:mm;HH:mm-HH:mm`，以 `#` 分隔多个「按星期」的条目。
 * 使用字符串而不是关联表，是为了让 Room 实体保持扁平、便于整体替换。
 */
object TimeWindowCodec {

    private const val DAY_SEPARATOR = "#"
    private const val RANGE_SEPARATOR = ";"
    private const val SPAN_SEPARATOR = "-"

    /** 解析为区间列表。结束时间必须晚于开始时间，否则该段视为无效。 */
    fun decode(encoded: String?): List<FacilityWindow> {
        if (encoded.isNullOrBlank()) return emptyList()
        return encoded.split(RANGE_SEPARATOR)
            .mapNotNull { part ->
                val t = part.trim()
                if (t.isEmpty()) return@mapNotNull null
                val idx = t.indexOf(SPAN_SEPARATOR)
                if (idx <= 0) return@mapNotNull null
                val start = t.substring(0, idx).trim()
                val end = t.substring(idx + 1).trim()
                val window = FacilityWindow(start, end)
                if (!window.isValid()) return@mapNotNull null
                window
            }
    }

    /** 编码区间列表。 */
    fun encode(windows: List<FacilityWindow>): String =
        windows.filter { it.isValid() }
            .joinToString(RANGE_SEPARATOR) { "${it.start}-${it.end}" }

    /** 编码 7 天的覆盖串。 */
    fun encodeDays(days: List<String>): String = days.joinToString(DAY_SEPARATOR)

    /**
     * 解码 7 天的覆盖串，始终返回长度为 7 的列表。
     */
    fun decodeDays(encoded: String?): List<String> {
        if (encoded.isNullOrBlank()) return List(7) { "" }
        val parts = encoded.split(DAY_SEPARATOR)
        return List(7) { index -> parts.getOrElse(index) { "" } }
    }

    fun isValidTime(text: String): Boolean =
        Regex("""^([01]?\d|2[0-4]):([0-5]\d)$""").matches(text.trim())

    /** 把 "HH:mm" 转成当天分钟数；24:00 视为 1440。 */
    fun toMinutes(time: String): Int {
        val parts = time.trim().split(":")
        if (parts.size < 2) return 0
        val h = parts[0].toIntOrNull() ?: 0
        val m = parts[1].toIntOrNull() ?: 0
        return h * 60 + m
    }
}

/** 单个开放区间。 */
data class FacilityWindow(
    val start: String,
    val end: String
) {
    val startMinutes: Int get() = TimeWindowCodec.toMinutes(start)
    val endMinutes: Int get() = TimeWindowCodec.toMinutes(end)

    fun isValid(): Boolean =
        TimeWindowCodec.isValidTime(start) && TimeWindowCodec.isValidTime(end) &&
            endMinutes > startMinutes

    fun label(): String = "$start-$end"

    /** 该分钟是否落在区间内（左闭右开）。 */
    fun contains(minutes: Int): Boolean = minutes >= startMinutes && minutes < endMinutes
}
