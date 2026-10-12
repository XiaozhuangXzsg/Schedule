package com.university.timetable.notification

import android.content.Context
import android.util.Log
import com.university.timetable.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 流体云常驻通知与「下一节课」的**统一刷新入口**。
 *
 * 为什么不能只在 [com.university.timetable.ui.MainViewModel] 里刷新：
 * 内置 AI 是直接调用 [com.university.timetable.data.repository.ScheduleRepository] 改库的，
 * 完全绕开 ViewModel。结果是「AI 把课调走了 / 换了教室，通知栏还在按旧课表提醒」。
 * 所以刷新必须挂在应用级的 [com.university.timetable.data.repository.DataChangeBus] 监听器上。
 *
 * 与 `WidgetRefresher` 一样：应用级作用域（不随页面销毁被取消）、请求合并、串行、失败只记日志。
 * 它读取的是 [NextClassResolver]，而后者走的是**实际课表**，
 * 因此调课 / 停课 / 补课 / 换教室都会立刻反映到通知里。
 */
object FluidCloudRefresher {

    private const val TAG = "FluidCloud"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var running = false

    @Volatile
    private var pending = false

    /** 请求刷新。同一时刻只有一轮在跑，期间到达的请求会被合并进来（保留最后一次）。 */
    fun request(context: Context, reason: String) {
        val app = context.applicationContext
        synchronized(this) {
            if (running) {
                pending = true
                return
            }
            running = true
        }
        scope.launch {
            try {
                do {
                    synchronized(this@FluidCloudRefresher) { pending = false }
                    runCatching { refreshNow(app, reason) }
                        .onFailure { Log.w(TAG, "刷新失败（reason=$reason）：${it.message}") }
                } while (synchronized(this@FluidCloudRefresher) { pending })
            } finally {
                synchronized(this@FluidCloudRefresher) { running = false }
            }
        }
    }

    /** 立即刷新一次（挂起版本，供已经在协程里的调用方使用）。 */
    suspend fun refreshNow(context: Context, reason: String) {
        val prefs = ServiceLocator.preferences(context).preferences.first()
        if (!prefs.fluidCloudEnabled) {
            FluidCloudNotification.cancel(context)
            return
        }
        val info = NextClassResolver.resolve(context, prefs)
        FluidCloudNotification.refresh(context, info)
        Log.d(
            TAG,
            "已刷新（reason=$reason）：" +
                (info?.let { "${NextClassResolver.title(it)} · ${NextClassResolver.detailText(it)}" } ?: "无下一节课")
        )
    }
}
