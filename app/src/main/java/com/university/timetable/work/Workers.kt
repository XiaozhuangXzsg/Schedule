package com.university.timetable.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.university.timetable.ServiceLocator
import com.university.timetable.notification.FluidCloudNotification
import com.university.timetable.notification.NextClassResolver
import com.university.timetable.notification.ClassReminderNotifier
import com.university.timetable.notification.LaundryNotifier
import com.university.timetable.widget.WidgetRefresher
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 周期性刷新流体云常驻通知。
 * 常驻通知本身使用倒计时样式，系统会自动走秒，因此 15 分钟刷新一次即可。
 */
class FluidCloudWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = ServiceLocator.preferences(applicationContext).preferences.first()
        if (!prefs.fluidCloudEnabled) {
            FluidCloudNotification.cancel(applicationContext)
            return Result.success()
        }
        val info = NextClassResolver.resolve(applicationContext, prefs)
        FluidCloudNotification.refresh(applicationContext, info)
        return Result.success()
    }

    companion object {
        private const val NAME = "fluid_cloud_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FluidCloudWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** 立即触发一次刷新（例如从 App 内切换设置后）。 */
        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${NAME}_now",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<FluidCloudWorker>().build()
            )
        }
    }
}

/** 周期性检查是否需要发送「上课提醒」。 */
class ClassReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefsRepo = ServiceLocator.preferences(applicationContext)
        val prefs = prefsRepo.preferences.first()
        if (!prefs.reminderEnabled) return Result.success()

        val info = NextClassResolver.resolve(applicationContext, prefs, includeTomorrow = false)
            ?: return Result.success()

        if (!info.isOngoing && info.minutesUntil in 1..prefs.remindBeforeMinutes) {
            // 同一节课只提醒一次（键包含日期，跨天自动失效）
            val key = "${info.date}#${info.block.sessionId}"
            if (prefsRepo.lastRemindedKey() != key) {
                ClassReminderNotifier.notify(applicationContext, info)
                prefsRepo.setLastRemindedKey(key)
            }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "class_reminder"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ClassReminderWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

/** 洗衣机倒计时结束后的完成提醒。 */
class LaundryTimerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        val name = inputData.getString(KEY_NAME) ?: "洗衣机"
        LaundryNotifier.notifyFinished(applicationContext, id, name)
        return Result.success()
    }

    companion object {
        const val KEY_ID = "laundry_id"
        const val KEY_NAME = "laundry_name"
    }
}

/**
 * 周期性刷新桌面卡片。
 *
 * 这是**后台补充**手段：应用在前台时的刷新由 [com.university.timetable.widget.WidgetRefresher]
 * 在数据变更、冷启动、回到前台时主动完成，不依赖本任务。
 * 应用被系统清理后，跨日、跨教学周需要有人把卡片改成新的一天，所以保留这个兜底任务。
 *
 * 说明：ColorOS 等国产 ROM 会限制后台任务，实际不会保证精确准点（30 分钟是系统允许的最小周期），
 * 因此这里只承诺「最终会刷新」，不承诺「一定在那个时刻刷新」。
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        WidgetRefresher.refreshNow(applicationContext, "后台周期刷新")
        return Result.success()
    }

    companion object {
        private const val NAME = "widget_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        // 只读本地数据库，不需要网络：没有网络时也能更新本地课程与待办
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

/** 统一入口：在 Application 启动时注册所有周期任务。 */
object WorkSchedulers {
    fun scheduleAll(context: Context) {
        FluidCloudWorker.schedule(context)
        ClassReminderWorker.schedule(context)
        WidgetRefreshWorker.schedule(context)
    }
}

/** Application 中使用的别名，保持调用点语义清晰。 */
object FluidCloudScheduler {
    fun schedule(context: Context) = FluidCloudWorker.schedule(context)
}

object ClassReminderScheduler {
    fun schedule(context: Context) = ClassReminderWorker.schedule(context)
}
