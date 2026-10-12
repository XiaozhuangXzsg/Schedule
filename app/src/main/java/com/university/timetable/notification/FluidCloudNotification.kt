package com.university.timetable.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.university.timetable.MainActivity
import com.university.timetable.R
import com.university.timetable.TimetableApp
import com.university.timetable.data.prefs.UiPreferences

/**
 * ColorOS 16「流体云」平替实现。
 *
 * ColorOS 的流体云会自动收纳带有自定义布局的高优先级**常驻通知**，
 * 因此这里使用 `setOngoing(true)` + `PRIORITY_HIGH` + 进度条 / 倒计时，
 * 模拟外卖类应用的进度效果：
 *   正在上课 → 显示课程结束进度条
 *   即将上课 → 显示「距离下一节[课程]还有 X 分钟」的实时倒计时
 *
 * 点击通知会拉起 App 并直接跳转到「今日课表」。
 */
object FluidCloudNotification {

    const val NOTIFICATION_ID = 1001

    /**
     * 纯函数式构建通知，不做任何挂起查询，可安全在主线程 / RemoteViews 场景调用。
     */
    fun build(context: Context, info: NextClassResolver.NextClassInfo): Notification {
        val contentIntent = contentPendingIntent(context)
        val (done, total) = NextClassResolver.progress(info)

        val builder = NotificationCompat.Builder(context, TimetableApp.CHANNEL_FLUID_CLOUD)
            .setSmallIcon(R.drawable.ic_stat_class)
            .setContentTitle(NextClassResolver.title(info))
            .setContentText(NextClassResolver.shortText(info))
            .setSubText(NextClassResolver.detailText(info))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${NextClassResolver.shortText(info)}\n${NextClassResolver.detailText(info)}")
            )
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)

        if (info.isOngoing && total > 0) {
            // 正在上课：进度条模拟「课程进行中」
            builder.setProgress(total, done, false)
            builder.setShowWhen(false)
        } else if (info.minutesUntil in 1..120) {
            // 即将上课：倒计时，系统会自己走秒
            builder.setWhen(System.currentTimeMillis() + info.minutesUntil * 60_000L)
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(true)
            builder.setShowWhen(true)
        } else {
            builder.setShowWhen(false)
        }

        return builder.build()
    }

    /** 立即刷新流体云通知。 */
    fun refresh(context: Context, info: NextClassResolver.NextClassInfo?) {
        if (info == null) {
            cancel(context)
            return
        }
        if (!notificationsEnabled(context)) return
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, build(context, info))
        } catch (_: SecurityException) {
            // 通知权限被拒绝，静默跳过
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun contentPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_TODAY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
