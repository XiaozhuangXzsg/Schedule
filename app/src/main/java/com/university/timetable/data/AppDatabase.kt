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
import java.io.File
import java.io.RandomAccessFile

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
        /** 数据库文件名（备份 / 恢复依赖它，改动会破坏兼容性）。 */
        const val DB_FILE_NAME = "university_timetable.db"

        /** 当前 Room schema 版本，写进备份清单用于恢复前校验。 */
        const val SCHEMA_VERSION = 5

        /**
         * v4 → v5：新增「日期变更记录」表。
         *
         * 这里**必须**提供显式迁移：缺迁移时升级会触发破坏性回退。
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
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): AppDatabase {
            backupLegacyDatabaseIfNeeded(context)
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                DB_FILE_NAME
            )
                .addMigrations(MIGRATION_4_5)
                // 只有 v1–v3 这些没有迁移路径的历史版本才允许重建表；
                // v4 → v5 必须走上面的显式迁移，绝不静默清空用户数据。
                .fallbackToDestructiveMigrationFrom(true, 1, 2, 3)
                .build()
        }

        /** 数据恢复前调用：关闭连接并清空单例，之后可再次 get() 重新打开。 */
        fun closeForRestore() {
            synchronized(this) {
                runCatching { instance?.close() }
                instance = null
            }
        }

        /**
         * 覆盖升级后第一次打开数据库时，若检测到 v1–v3 老库，
         * 先把原始文件复制到 files/backup/legacy-vN-*.db 留底，再交给 Room 处理。
         * 任何异常都只影响留底，不影响正常打开。
         */
        private fun backupLegacyDatabaseIfNeeded(context: Context) {
            runCatching {
                val databaseFile = context.getDatabasePath(DB_FILE_NAME)
                if (!databaseFile.exists()) return
                val version = readUserVersion(databaseFile) ?: return
                if (version !in 1..3) return
                val dir = File(context.filesDir, "backup")
                if (!dir.exists()) dir.mkdirs()
                val target = File(dir, "legacy-v" + version + "-" + System.currentTimeMillis() + ".db")
                databaseFile.copyTo(target, overwrite = true)
                val wal = File(databaseFile.path + "-wal")
                if (wal.exists()) {
                    wal.copyTo(File(target.path + "-wal"), overwrite = true)
                }
            }
        }

        /** 读取 SQLite 头部 user_version（偏移 60，4 字节大端）。 */
        private fun readUserVersion(file: File): Int? {
            if (file.length() < 64L) return null
            RandomAccessFile(file, "r").use { access ->
                access.seek(60L)
                val b0 = access.read()
                val b1 = access.read()
                val b2 = access.read()
                val b3 = access.read()
                if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) return null
                return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
            }
        }
    }
}
