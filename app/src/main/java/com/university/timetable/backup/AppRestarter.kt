package com.university.timetable.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import com.university.timetable.R

/**
 * 数据恢复后需要重启进程，让 Room / DataStore 重新读取被覆盖的文件。
 *
 * 为什么不能用「闹钟 / 服务自动拉起自己」：
 * - 主进程一死，ColorOS 的 OplusHansManager 会在同一瞬间把这个 uid 的闹钟全部清掉
 *   （真机日志 `onUidGone(), <uid> com.university.timetable alarm removed`）；
 * - 换到独立进程里仍然不行：`AlarmManager` 的 PendingIntent 由系统发出，
 *   但 targetSdk 37 下仍然被判定为后台启动
 *   （真机日志 `Background activity launch blocked! … realCallerStartMode: MODE_BACKGROUND_ACTIVITY_START_DENIED`）。
 *   这是 Android 14+ 的后台启动限制，任何「自己把自己拉到前台」的做法都会被拦。
 *
 * 所以这里只能：退出前发一条通知，用户点一下回到应用
 * （通知的 PendingIntent 由系统界面发出，不受后台启动限制）。
 *
 * 注意 ColorOS 会拦截这条通知（真机日志
 * `intercepted: 0|com.university.timetable|9113|null|10455,new:!priority`），
 * 即使渠道拉到 IMPORTANCE_HIGH 也一样，所以界面文案不再承诺「点通知回去」，
 * 只告诉用户重新打开应用；这条通知在其它 ROM 上仍然是有用的。
 */
object AppRestarter {

    private const val TAG = "AppRestarter"

    fun restartLater(context: Context) {
        postNotification(context)
    }

    fun killNow() {
        Process.killProcess(Process.myPid())
    }

    private fun postNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "数据恢复",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "导入数据恢复完成后的提示"
                    // ColorOS 会把低优先级通知折叠进「不重要」，高优先级才能顶到通知栏最上面。
                    enableVibration(false)
                    setShowBadge(true)
                }
            )
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
            ?: return
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val contentIntent = PendingIntent.getActivity(context, REQUEST_CODE, launch, flags)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_class)
            .setContentTitle("数据已恢复")
            .setContentText("点击重新打开娅娅课表")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onFailure { Log.w(TAG, "发送恢复通知失败：" + (it.message ?: "")) }
            .onSuccess { Log.i(TAG, "已发送「数据已恢复」通知，等待用户点回应用") }
    }

    const val CHANNEL_ID = "restore_ready_v2"
    const val NOTIFICATION_ID = 9113
    private const val REQUEST_CODE = 9113
}