package com.university.timetable.data.entity

/**
 * 内置默认作息：**兰州大学** 本科生课表作息（14 节）。
 *
 * 与教务系统 `showTimetable.do` 导出的课表节次一一对应：
 * 上午第 1-4 节、中午第 5-6 节、下午第 7-10 节、晚上第 11-14 节。
 *
 * 从教务导入时，解析器会用页面表头里的真实时间覆盖这里的设置，
 * 因此学校调整作息后只需重新导入一次即可。
 */
object DefaultPeriodTimes {

    val ALL: List<PeriodTime> = listOf(
        // 上午
        PeriodTime(1, "08:30", "09:15", 1),
        PeriodTime(2, "09:25", "10:10", 1),
        PeriodTime(3, "10:30", "11:15", 1),
        PeriodTime(4, "11:25", "12:10", 1),
        // 中午
        PeriodTime(5, "12:20", "13:15", 2),
        PeriodTime(6, "13:25", "14:20", 2),
        // 下午
        PeriodTime(7, "14:30", "15:15", 2),
        PeriodTime(8, "15:25", "16:10", 2),
        PeriodTime(9, "16:30", "17:15", 2),
        PeriodTime(10, "17:25", "18:10", 2),
        // 晚上
        PeriodTime(11, "19:00", "19:45", 3),
        PeriodTime(12, "19:55", "20:40", 3),
        PeriodTime(13, "20:50", "21:35", 3),
        PeriodTime(14, "21:45", "22:30", 3)
    )
}
