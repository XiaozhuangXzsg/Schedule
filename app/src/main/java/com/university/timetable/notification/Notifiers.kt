package com.university.timetable.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.university.timetable.MainActivity
import com.university.timetable.R
import com.university.timetable.TimetableApp

/** 上课提醒通知。 */
object ClassReminderNotifier {

    private const val NOTIFICATION_ID = 2001

    fun notify(context: Context, info: NextClassResolver.NextClassInfo) {
        val intent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_TODAY
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val place = info.block.location?.takeIf { it.isNotBlank() } ?: "待定"
        val notification = NotificationCompat.Builder(context, TimetableApp.CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_class)
            .setContentTitle("还有 ${info.minutesUntil} 分钟上课")
            .setContentText("${info.block.name} · $place")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${info.block.name}\n${info.block.startTime}-${info.block.endTime} · $place"
                )
            )
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 忽略权限问题
        }
    }
}

/** 洗衣机倒计时通知（进行中 + 完成）。 */
object LaundryNotifier {

    private const val PROGRESS_ID = 3001
    private const val FINISHED_ID = 3002
    private const val NAME = "laundry_progress"

    /** 展示带倒计时的进行中通知。 */
    fun notifyProgress(context: Context, id: String, label: String, endAtMillis: Long) {
        val notification = NotificationCompat.Builder(context, TimetableApp.CHANNEL_FLUID_CLOUD)
            .setSmallIcon(R.drawable.ic_stat_laundry)
            .setContentTitle("洗衣机运行中 · $label")
            .setContentText("预计完成时间可点击查看")
            .setContentIntent(openToday(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setWhen(endAtMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(PROGRESS_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancelProgress(context: Context) {
        NotificationManagerCompat.from(context).cancel(PROGRESS_ID)
    }

    /** 洗衣完成提醒。 */
    fun notifyFinished(context: Context, id: String, label: String) {
        cancelProgress(context)
        val notification = NotificationCompat.Builder(context, TimetableApp.CHANNEL_LAUNDRY)
            .setSmallIcon(R.drawable.ic_stat_laundry)
            .setContentTitle("洗衣完成")
            .setContentText("$label 已结束，记得及时取衣～")
            .setContentIntent(openToday(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(FINISHED_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun openToday(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        PROGRESS_ID,
        Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_TODAY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
