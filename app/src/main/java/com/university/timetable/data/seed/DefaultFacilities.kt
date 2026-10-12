package com.university.timetable.data.seed

import com.university.timetable.data.entity.CampusFacility
import com.university.timetable.data.entity.FacilityWindow
import com.university.timetable.data.entity.TimeWindowCodec

/**
 * 校园生活设施的默认数据（取自学校实际作息）。
 *
 * 所有条目都可以在「校园生活」页面里自由增删改：
 * 默认区间、按星期的差异、以及节假日专用区间。
 */
object DefaultFacilities {

    private fun w(vararg pairs: Pair<String, String>): String =
        TimeWindowCodec.encode(pairs.map { FacilityWindow(it.first, it.second) })

    /** 生成 7 天覆盖串：只需给出有差异的星期（1=周一 ... 7=周日）。 */
    private fun days(vararg overrides: Pair<Int, String>): String {
        val list = MutableList(7) { "" }
        overrides.forEach { (day, windows) ->
            if (day in 1..7) list[day - 1] = windows
        }
        return TimeWindowCodec.encodeDays(list)
    }

    val ALL: List<CampusFacility> = listOf(
        CampusFacility(
            name = "公寓开关门",
            type = "DOOR",
            location = "公寓大门",
            windows = w("06:00" to "23:00"),
            note = "6:00 开门，23:00 锁门；晚归请提前联系宿管",
            sortOrder = 1
        ),
        CampusFacility(
            name = "宿舍供电",
            type = "POWER",
            location = "宿舍",
            windows = w("06:00" to "23:15"),
            // 周五、周六延长至 24:00
            dayWindows = days(
                5 to w("06:00" to "24:00"),
                6 to w("06:00" to "24:00")
            ),
            note = "周日-周四 6:00-23:15，周五-周六 6:00-24:00",
            sortOrder = 2
        ),
        CampusFacility(
            name = "浴室开放",
            type = "BATH",
            location = "公共浴室",
            windows = w("11:00" to "14:00", "16:30" to "23:00"),
            // 周一上午停开检修，仅 16:30-23:00
            dayWindows = days(1 to w("16:30" to "23:00")),
            note = "周一上午停开检修；周二至周日 11:00-14:00 & 16:30-23:00",
            sortOrder = 3
        ),
        CampusFacility(
            name = "滚筒洗衣机",
            type = "LAUNDRY",
            location = "1-6 层",
            windows = w("07:30" to "23:00"),
            note = "1-6 层各设滚筒洗衣机",
            sortOrder = 4
        ),
        CampusFacility(
            name = "烘干机",
            type = "DRYER",
            location = "1 层",
            windows = w("07:30" to "23:00"),
            sortOrder = 5
        ),
        CampusFacility(
            name = "换桶机",
            type = "CHANGE",
            location = "2 层、5 层",
            windows = w("07:30" to "12:00", "14:00" to "23:00"),
            note = "7:30-12:00 & 14:00-23:00",
            sortOrder = 6
        ),
        CampusFacility(
            name = "洗鞋机",
            type = "SHOE",
            location = "3 层",
            windows = w("07:30" to "23:00"),
            sortOrder = 7
        ),
        CampusFacility(
            name = "吹风机",
            type = "HAIRDRYER",
            location = "各层公共区",
            windows = w("07:00" to "13:00", "14:00" to "23:45"),
            note = "7:00-13:00 & 14:00-23:45",
            sortOrder = 8
        ),
        CampusFacility(
            name = "饮水机",
            type = "WATER",
            location = "各层",
            windows = w("06:00" to "24:00"),
            sortOrder = 9
        ),
        CampusFacility(
            name = "自习室",
            type = "STUDY",
            location = "公寓自习室",
            windows = w("06:00" to "24:00"),
            sortOrder = 10
        )
    )
}
