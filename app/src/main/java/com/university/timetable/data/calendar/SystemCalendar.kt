package com.university.timetable.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * 系统日历（CalendarContract）读取层。
 *
 * 设计原则：
 *  - 只读不写：应用仅申请 `READ_CALENDAR`，不会修改用户日历。
 *  - 永不崩溃：任何 SecurityException / 异常都退化为空列表，由界面给出空状态。
 *  - 列缺失安全：厂商 ROM 可能裁剪 Instances 视图的列，所有 getColumnIndex 都先判断 -1。
 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val location: String?,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val calendarName: String?,
    val colorArgb: Int?
)

/** 是否已获得读取系统日历的权限。 */
fun hasPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED

/**
 * 查询 [from, to]（含首尾两天）区间内的系统日历事件。
 *
 * 使用 [CalendarContract.Instances]，因此重复事件（RRULE）会自动展开成每一次实例，
 * 且跨天事件会在每一天都返回一条实例。
 *
 * @return 按开始时间升序排列的事件列表；无权限或查询失败时返回 emptyList()。
 */
suspend fun queryEvents(context: Context, from: LocalDate, to: LocalDate): List<CalendarEvent> =
    withContext(Dispatchers.IO) {
        try {
            val zone = ZoneId.systemDefault()
            // Instances 的查询区间是 [begin, end) 半开区间，因此 end 取 to 的次日零点
            val rangeStart = from.atStartOfDay(zone).toInstant().toEpochMilli()
            val rangeEnd = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            if (rangeEnd <= rangeStart) return@withContext emptyList()

            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, rangeStart)
            ContentUris.appendId(builder, rangeEnd)
            val uri = builder.build()

            val projection = arrayOf(
                CalendarContract.Instances._ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                CalendarContract.Instances.EVENT_COLOR
            )

            val events = ArrayList<CalendarEvent>()
            context.contentResolver
                .query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
                ?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(CalendarContract.Instances._ID)
                    val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                    val locationIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                    val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                    val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                    val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                    val calendarIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
                    val colorIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_COLOR)

                    while (cursor.moveToNext()) {
                        // BEGIN 是唯一必需列：缺失说明这条实例不可用，直接跳过
                        if (beginIdx < 0) continue
                        val begin = cursor.getLong(beginIdx)
                        val end = if (endIdx >= 0) cursor.getLong(endIdx) else begin

                        val title = if (titleIdx >= 0) cursor.getString(titleIdx) else null
                        val location = if (locationIdx >= 0) cursor.getString(locationIdx) else null
                        val calendarName = if (calendarIdx >= 0) cursor.getString(calendarIdx) else null
                        val allDay = allDayIdx >= 0 && cursor.getInt(allDayIdx) != 0
                        val color = if (colorIdx >= 0 && !cursor.isNull(colorIdx)) {
                            cursor.getInt(colorIdx)
                        } else {
                            null
                        }

                        events += CalendarEvent(
                            id = if (idIdx >= 0) cursor.getLong(idIdx) else begin,
                            title = title?.takeIf { it.isNotBlank() } ?: "(无标题)",
                            location = location?.takeIf { it.isNotBlank() },
                            startMillis = begin,
                            endMillis = if (end > begin) end else begin,
                            allDay = allDay,
                            calendarName = calendarName?.takeIf { it.isNotBlank() },
                            colorArgb = color
                        )
                    }
                }
            events
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
