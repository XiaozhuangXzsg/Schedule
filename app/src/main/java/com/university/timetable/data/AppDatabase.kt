package com.university.timetable.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.university.timetable.data.dao.AiChatDao
import com.university.timetable.data.dao.CampusFacilityDao
import com.university.timetable.data.dao.CourseDao
import com.university.timetable.data.dao.DormServiceDao
import com.university.timetable.data.dao.HolidayDao
import com.university.timetable.data.dao.PeriodTimeDao
import com.university.timetable.data.dao.ScheduleOverrideDao
import com.university.timetable.data.dao.TodoDao
import com.university.timetable.data.entity.CampusFacility
import com.university.timetable.data.entity.ChatMessage
import com.university.timetable.data.entity.ChatSession
import com.university.timetable.data.entity.Course
import com.university.timetable.data.entity.CourseSession
import com.university.timetable.data.entity.DormService
import com.university.timetable.data.entity.Holiday
import com.university.timetable.data.entity.PeriodTime
import com.university.timetable.data.entity.ScheduleOverride
import com.university.timetable.data.entity.TodoItem

@Database(
    entities = [
        Course::class,
        CourseSession::class,
        PeriodTime::class,
        Holiday::class,
        DormService::class,
        CampusFacility::class,
        TodoItem::class,
        ChatSession::class,
        ChatMessage::class,
        ScheduleOverride::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun periodTimeDao(): PeriodTimeDao
    abstract fun holidayDao(): HolidayDao
    abstract fun dormServiceDao(): DormServiceDao
    abstract fun campusFacilityDao(): CampusFacilityDao
    abstract fun todoDao(): TodoDao
    abstract fun aiChatDao(): AiChatDao
    abstract fun scheduleOverrideDao(): ScheduleOverrideDao

    companion object {
        private const val DB_NAME = "university_timetable.db"

        /**
         * v4 → v5：新增「日期变更记录」表。
         *
         * 这里**必须**提供显式迁移：本库注册了 `fallbackToDestructiveMigration`，
         * 缺迁移时升级会直接清空用户的课表、待办与聊天记录。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedule_overrides` (" +
                        "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "`date` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`sourceSessionId` INTEGER, " +
                        "`courseId` INTEGER, " +
                        "`name` TEXT, " +
                        "`teacher` TEXT, " +
                        "`location` TEXT, " +
                        "`color` INTEGER, " +
                        "`startPeriod` INTEGER, " +
                        "`endPeriod` INTEGER, " +
                        "`note` TEXT, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_overrides_date` ON `schedule_overrides` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_overrides_type` ON `schedule_overrides` (`type`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_schedule_overrides_sourceSessionId` " +
                        "ON `schedule_overrides` (`sourceSessionId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_schedule_overrides_date_type_sourceSessionId` " +
                        "ON `schedule_overrides` (`date`, `type`, `sourceSessionId`)"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { instance = it }
        }
    }
}
