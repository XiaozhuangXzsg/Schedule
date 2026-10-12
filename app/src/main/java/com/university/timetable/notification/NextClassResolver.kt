package com.university.timetable.notification

import android.content.Context
import com.university.timetable.ServiceLocator
import com.university.timetable.data.prefs.UiPreferences
import com.university.timetable.data.repository.ClassBlock
import com.university.timetable.util.SemesterWeeks
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

/**
 * 「下一节课」的统一解析器：流体云通知、小组件、上课提醒共用同一套逻辑。
 */
object NextClassResolver {

    data class NextClassInfo(
        val block: ClassBlock,
        /** 距离上课还有多少分钟；<= 0 表示正在上课 */
        val minutesUntil: Int,
        val isOngoing: Boolean,
        /** 该课结束时间距现在多少分钟 */
        val minutesLeft: Int,
        val week: Int,
        val date: LocalDate
    )

    /**
     * 解析当前（或下一次）课程。
     * @param includeTomorrow 今天已无课时，是否顺延到明天的第一节课
     */
    suspend fun resolve(
        context: Context,
        prefs: UiPreferences,
        now: java.time.LocalDateTime = java.time.LocalDateTime.now(),
        includeTomorrow: Boolean = true
    ): NextClassInfo? {
        val repo = ServiceLocator.schedule(context)
        val start = LocalDate.parse(prefs.semesterStart)
        val minutes = now.hour * 60 + now.minute

        // 今天
        val today = now.toLocalDate()
        val todayWeek = SemesterWeeks.weekOf(today, start)
        if (todayWeek >= 1 && todayWeek <= prefs.totalWeeks) {
            // 必须用「实际课表」：调课 / 停课 / 补课 / 换教室都要体现在提醒里，
            // 否则用户把课调走之后仍然会收到原时段的提醒、甚至提醒到旧教室。
            val todayBlocks = repo.effectiveBlocksOnce(today, todayWeek)
            todayBlocks.firstOrNull { minutes < it.endMinutes }?.let { b ->
                val ongoing = minutes >= b.startMinutes
                return NextClassInfo(
                    block = b,
                    minutesUntil = b.startMinutes - minutes,
                    isOngoing = ongoing,
                    minutesLeft = (b.endMinutes - minutes).coerceAtLeast(0),
                    week = todayWeek,
                    date = today
                )
            }
        }

        if (!includeTomorrow) return null

        // 顺延查找未来 7 天内的第一节课
        for (offset in 1..7) {
            val date = today.plusDays(offset.toLong())
            val week = SemesterWeeks.weekOf(date, start)
            if (week < 1 || week > prefs.totalWeeks) continue
            val blocks = repo.effectiveBlocksOnce(date, week)
            val first = blocks.minByOrNull { it.startMinutes } ?: continue
            return NextClassInfo(
                block = first,
                minutesUntil = (offset * 24 * 60) + first.startMinutes - minutes,
                isOngoing = false,
                minutesLeft = 0,
                week = week,
                date = date
            )
        }
        return null
    }

    /** 生成流体云 / 通知使用的文案。 */
    fun title(info: NextClassInfo): String =
        if (info.isOngoing) "正在上课 · ${info.block.name}" else "下一节 · ${info.block.name}"

    fun shortText(info: NextClassInfo): String = when {
        info.isOngoing -> "还有 ${info.minutesLeft} 分钟下课"
        info.minutesUntil <= 0 -> "即将开始"
        info.minutesUntil < 60 -> "距离上课还有 ${info.minutesUntil} 分钟"
        else -> "距离上课还有 ${info.minutesUntil / 60} 小时 ${info.minutesUntil % 60} 分钟"
    }

    fun detailText(info: NextClassInfo): String {
        val place = info.block.location?.takeIf { it.isNotBlank() } ?: "待定"
        val day = if (info.date == LocalDate.now()) "今天" else SemesterWeeks.dayLabel(info.block.dayOfWeek)
        return "$day ${info.block.startTime}-${info.block.endTime} · $place"
    }

    /** 距离下课的时间（用于进度条）。 */
    fun progress(info: NextClassInfo): Pair<Int, Int> {
        if (!info.isOngoing) return 0 to 1
        val total = (info.block.endMinutes - info.block.startMinutes).coerceAtLeast(1)
        val done = (total - info.minutesLeft).coerceIn(0, total)
        return done to total
    }

    fun nowTimeLabel(): String {
        val t = LocalTime.now()
        return "%02d:%02d".format(t.hour, t.minute)
    }
}
