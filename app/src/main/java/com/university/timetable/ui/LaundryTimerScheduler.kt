package com.university.timetable.ui

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.university.timetable.work.LaundryTimerWorker
import java.util.concurrent.TimeUnit

/** 洗衣机倒计时的后台调度。 */
object LaundryTimerScheduler {

    private const val PREFIX = "laundry_"

    fun schedule(context: Context, id: String, label: String, minutes: Int) {
        val data = Data.Builder()
            .putString(LaundryTimerWorker.KEY_ID, id)
            .putString(LaundryTimerWorker.KEY_NAME, label)
            .build()

        val request = OneTimeWorkRequestBuilder<LaundryTimerWorker>()
            .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            PREFIX + id,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(PREFIX + id)
    }
}
