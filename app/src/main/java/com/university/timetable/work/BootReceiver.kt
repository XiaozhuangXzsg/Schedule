package com.university.timetable.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机 / 应用更新后重新注册周期性任务，保证流体云与上课提醒持续生效。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                WorkSchedulers.scheduleAll(context)
                FluidCloudWorker.refreshNow(context)
            }
        }
    }
}
