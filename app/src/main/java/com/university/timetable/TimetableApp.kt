package com.university.timetable

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.university.timetable.data.AppDatabase
import com.university.timetable.data.prefs.PreferencesRepository
import com.university.timetable.data.repository.DataChangeBus
import com.university.timetable.data.repository.ScheduleRepository
import com.university.timetable.notification.FluidCloudRefresher
import com.university.timetable.widget.WidgetRefresher
import com.university.timetable.widget.WidgetVisiblePrefs
import com.university.timetable.work.ClassReminderScheduler
import com.university.timetable.work.FluidCloudScheduler
import com.university.timetable.work.WidgetRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 极简依赖容器。避免引入 Hilt 以保持构建轻量、可读。
 */
object ServiceLocator {

    @Volatile
    private var repo: ScheduleRepository? = null

    @Volatile
    private var prefs: PreferencesRepository? = null

    fun schedule(context: Context): ScheduleRepository =
        repo ?: synchronized(this) {
            repo ?: ScheduleRepository(context.applicationContext).also { repo = it }
        }

    fun preferences(context: Context): PreferencesRepository =
        prefs ?: synchronized(this) {
            prefs ?: PreferencesRepository(context.applicationContext).also { prefs = it }
        }

    fun database(context: Context): AppDatabase = AppDatabase.get(context)
}

class TimetableApp : Application() {

    /**
     * 已 start（可见）的 Activity 数量。
     *
     * 用它来判断「应用整体是否在前台」：
     * - 0 → 1 是真正的「从后台回来」，推一次卡片；
     * - 1 → 0 是真正的「退到后台」，也推一次卡片。
     *
     * 应用内部页面之间切换时计数在 1 和 2 之间变化，两个分支都不会命中，
     * 避免每次页面切换都重复刷新卡片。
     */
    private var startedActivities = 0

    /** 应用级作用域：跟随进程存活，用来长期观察偏好变化。 */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        FluidCloudScheduler.schedule(this)
        ClassReminderScheduler.schedule(this)
        // 后台补充：应用被清理后也能跨日把卡片改成新的一天（受系统限制，不保证准点）
        WidgetRefreshWorker.schedule(this)

        // 统一的数据变更出口：仓库每次写入成功（事务已提交）都会走到这里。
        // 这里只做「请求刷新卡片 / 流体云」，失败也不会影响已经保存成功的数据。
        //
        // 挂在应用级监听器上（而不是各页面里各自调用）的原因：内置 AI 直接调仓库改库，
        // 绕开了 MainViewModel，只有这里才能保证「AI 改完之后提醒也跟着变」。
        DataChangeBus.addListener { change ->
            WidgetRefresher.request(this, "数据变更:${change.kind.name}")
            if (change.kind.affectsSchedule) {
                FluidCloudRefresher.request(this, "数据变更:${change.kind.name}")
            }
            Log.d(TAG, "收到变更 v${change.version} ${change.kind.name}，已请求刷新卡片")
        }

        // 冷启动：先让卡片用本地数据刷新一次（Room 首次访问会自动打开数据库，无需额外等待）。
        // 写偏好 / 云同步完成之后如果有变化，会再通过 DataChangeBus 触发一次。
        WidgetRefresher.request(this, "冷启动")

        watchWidgetPreferences()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                if (startedActivities == 1) {
                    // 回到前台：重新核对日期 / 时区 / 教学周，并用本地数据刷新卡片
                    WidgetRefresher.request(activity.applicationContext, "回到前台")
                }
            }

            override fun onActivityStopped(activity: Activity) {
                if (startedActivities > 0) startedActivities--
                if (startedActivities == 0 && !activity.isChangingConfigurations) {
                    // 退到后台：把这次会话里做过的改动（改透明度、改课表、改待办…）再推一次，
                    // 保证桌面卡片在用户回到桌面时就是最新的。
                    //
                    // 用应用级的 request（非阻塞）而不是 refreshNow：这里是主线程，
                    // 而且进程退到后台后仍会存活一小段时间，足以把这次更新发出去。
                    WidgetRefresher.request(activity.applicationContext, "退到后台")
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /**
     * 盯住「小组件显示内容真正依赖的那几个偏好」，一变就推一次卡片。
     *
     * 为什么要这样做，而不是靠每个 setter 自己记得调 `refreshWidgets()`：
     * 漏掉一处，用户看到的就是「设置改了、卡片不动」，而且这种漏很难在测试里发现。
     * 这里把卡片读的字段集中成 [WidgetVisiblePrefs]，**新增一个字段就自动获得刷新**。
     *
     * 注意只比对卡片真正读的字段：把整个 UiPreferences 拿来比会在改昵称、
     * 改提醒时间这类与卡片无关的操作上也触发刷新，白白唤醒启动器。
     */
    private fun watchWidgetPreferences() {
        applicationScope.launch {
            ServiceLocator.preferences(this@TimetableApp).preferences
                .map { WidgetVisiblePrefs.from(it) }
                .distinctUntilChanged()
                .drop(1) // 第一次是当前值，冷启动那一轮已经推过了
                .collect { changed ->
                    Log.d(TAG, "卡片可见的偏好发生变化，请求刷新：$changed")
                    WidgetRefresher.request(this@TimetableApp, "偏好变更")
                }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val cloud = NotificationChannel(
            CHANNEL_FLUID_CLOUD,
            "流体云课表",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "常驻显示下一节课信息，可被 ColorOS 流体云收纳"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        val reminder = NotificationChannel(
            CHANNEL_REMINDER,
            "上课提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "上课前提醒"
            enableVibration(true)
        }

        val laundry = NotificationChannel(
            CHANNEL_LAUNDRY,
            "洗衣机倒计时",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "洗衣完成提醒"
            enableVibration(true)
        }

        manager.createNotificationChannels(listOf(cloud, reminder, laundry))
    }

    companion object {
        private const val TAG = "TimetableApp"

        const val CHANNEL_FLUID_CLOUD = "fluid_cloud"
        const val CHANNEL_REMINDER = "class_reminder"
        const val CHANNEL_LAUNDRY = "laundry_timer"
    }
}
