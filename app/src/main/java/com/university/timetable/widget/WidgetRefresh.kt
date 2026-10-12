package com.university.timetable.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicReference

/**
 * 桌面小组件的**唯一刷新入口**。
 *
 * 设计要点（都是为了修掉「小组件显示旧数据」这一类问题）：
 *
 * 1. **不绑定页面生命周期**：自带应用级 [CoroutineScope]，不随 Activity / 页面销毁被取消，
 *    所以「保存后马上回桌面」也不会把还没跑完的刷新掐掉。
 * 2. **请求合并 + 串行**：交给 [RefreshCoalescer]，连续请求只会执行「当前这一轮 + 最后一轮」，
 *    合并时保留最后一次请求；同一时刻只有一轮在跑，避免旧的一次更新晚于新的完成、
 *    把新数据覆盖回旧数据。
 * 3. **无实例直接跳过**：没有任何卡片实例时不做无意义的计算。
 * 4. **失败不抛给调用方**：小组件更新失败只记录日志，绝不影响已经保存成功的业务数据。
 *
 * 日志只记录「变更类型 / 卡片编号 / 结果」这类排查信息，不记录待办正文等隐私内容。
 */
object WidgetRefresher {

    private const val TAG = "WidgetRefresh"

    /** 单张卡片一轮刷新最多尝试几次。 */
    private const val MAX_ATTEMPTS = 3

    /** 重试的基准间隔（毫秒），第 n 次退避 n 倍。 */
    private const val RETRY_DELAY_MS = 120L

    /** 写完 [WidgetRenderCache] 之后留给组合重跑的时间（毫秒）。 */
    private const val STATE_SETTLE_MS = 40L

    /** 与页面无关的应用级作用域：页面退出、返回桌面都不会取消刷新。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val lastAppliedReason = AtomicReference("")

    /**
     * 进程内只需要一个合并器；捕获的 Context 用 applicationContext（进程内唯一），
     * 因此不会泄漏 Activity。
     */
    @Volatile
    private var coalescer: RefreshCoalescer? = null

    private fun coalescerFor(context: Context): RefreshCoalescer {
        coalescer?.let { return it }
        return synchronized(this) {
            coalescer ?: run {
                val app = context.applicationContext
                RefreshCoalescer(
                    scope = scope,
                    onError = { Log.w(TAG, "一轮刷新内部异常，已忽略并等待下次重试", it) }
                ) { reason ->
                    lastAppliedReason.set(reason)
                    refreshAllOnce(app, reason)
                }.also { coalescer = it }
            }
        }
    }

    /** 四个卡片：名称只用于日志。 */
    private fun widgets(): List<Pair<String, GlanceAppWidget>> = listOf(
        "当前课程" to CompactClassesWidget(),
        "待办+课表" to FullScheduleWidget(),
        "今日课表" to TodayClassesWidget(),
        "本周课表" to WeekClassesWidget()
    )

    /**
     * 请求刷新（非阻塞）。可以随时从任何线程调用。
     *
     * 短时间内多次调用会被合并成尽量少的实际刷新轮次，但**最后一次请求一定会被执行**。
     */
    fun request(context: Context, reason: String) {
        val app = context.applicationContext
        Log.d(TAG, "刷新请求 reason=$reason")
        coalescerFor(app).request(reason)
    }

    /**
     * 立刻刷新并等待完成。用于「用户手动点刷新」这类需要给出确定反馈的场景。
     */
    suspend fun refreshNow(context: Context, reason: String) {
        val app = context.applicationContext
        Log.d(TAG, "手动刷新 reason=$reason")
        coalescerFor(app).runNow(reason)
    }

    /**
     * 一轮刷新：对四个卡片分别查实例 → 有实例才 [GlanceAppWidget.updateAll]。
     *
     * 注意：**不要再对 `getGlanceIds` 返回的 id 逐个 `update(context, id)`**。
     * 两条路径会重复刷新同一个实例，实测会让启动器拿到尚未绑定 widgetId 的 RemoteViews，
     * 触发常驻的「无法添加微件。」错误视图（详见 README 的卡片排错一节）。
     *
     * 失败会重试 [MAX_ATTEMPTS] 次：卡片刷新失败大多是瞬时问题（数据库正忙、启动器拒绝绑定），
     * 只在日志里记一笔就等下一次用户操作，等于「这一次修改永远丢了」——
     * 用户看到的就是「改了但卡片没变」。重试间隔很短，不会拖慢前台。
     */
    private suspend fun refreshAllOnce(context: Context, reason: String) {
        val round = stats().second
        val manager = GlanceAppWidgetManager(context)
        var touched = 0
        var failed = 0

        // 先查一遍实例：既能跳过桌面上没有的卡片类型，也能据此决定要不要读「本周」这类较重的数据。
        val pending = widgets().map { (label, widget) ->
            val ids = runCatching { manager.getGlanceIds(widget.javaClass) }
                .onFailure { Log.w(TAG, "取 $label 卡片实例失败", it) }
                .getOrDefault(emptyList())
            Pending(label, widget, ids)
        }

        publishLatestData(context, pending)

        for ((label, widget, ids) in pending) {
            if (ids.isEmpty()) {
                Log.d(TAG, "第 $round 轮：$label 桌面上没有卡片实例，跳过")
                continue
            }

            Log.d(
                TAG,
                "第 $round 轮：$label 实例 id=${ids.mapNotNull { (it as? AppWidgetId)?.appWidgetId }}"
            )

            touched += ids.size
            var lastError: Throwable? = null
            var ok = false
            // 成功就停：这里原来是 `return@repeat`，那在 Kotlin 里等于 continue，
            // 会把 updateAll 白白再做满 3 次，反而让启动器拿到多份相互覆盖的 RemoteViews。
            for (attempt in 0 until MAX_ATTEMPTS) {
                val result = runCatching { widget.updateAll(context) }
                if (result.isSuccess) {
                    ok = true
                    break
                }
                lastError = result.exceptionOrNull()
                Log.w(TAG, "第 $round 轮：$label 第 ${attempt + 1} 次刷新失败，准备重试", lastError)
                kotlinx.coroutines.delay(RETRY_DELAY_MS * (attempt + 1))
            }

            if (!ok) {
                failed++
                // 卡片刷新失败不能影响已经保存成功的数据，只记录并等下一次重试。
                Log.w(TAG, "第 $round 轮：$label 重试 $MAX_ATTEMPTS 次仍失败（reason=$reason）", lastError)
                continue
            }

            // LazyColumn 在 AppWidget 里落地成 RemoteViews 的集合视图，
            // 只调 updateAll 有些启动器会继续显示旧的子项列表。
            // 这里补一次「集合数据已变化」通知，成本极低，能免掉「要重新添加卡片才刷新」的现象。
            notifyCollections(context, widget.javaClass, ids)
            Log.d(TAG, "第 $round 轮：$label 已提交 ${ids.size} 个实例的更新（reason=$reason）")
        }

        if (touched == 0) {
            Log.d(TAG, "第 $round 轮：桌面上没有任何卡片实例，本轮无需刷新（reason=$reason）")
        } else {
            Log.d(
                TAG,
                "第 $round 轮完成：共 $touched 个实例、$failed 个卡片类型失败（reason=$reason）；" +
                    "注意这里只代表「更新请求已提交」，桌面是否已重绘需另行观察"
            )
        }
    }

    /**
     * 把最新数据写进组合可见的 [WidgetRenderCache]。
     *
     * **这是「改了设置卡片不跟着变」的正解**：Glance 的 `provideGlance` 每个会话只跑一次，
     * 之后 `updateAll` 只重新渲染已有组合；数据若被 `provideContent` 捕获成常量就会一直用旧的，
     * 用户必须先杀掉应用重开（会话重建）才看得到新值。
     *
     * 只有桌面上确实存在对应卡片时才读数据，避免白跑一次数据库。
     */
    private suspend fun publishLatestData(context: Context, pending: List<Pending>) {
        val needToday = pending.any { it.ids.isNotEmpty() && it.widget !is WeekClassesWidget }
        val needWeek = pending.any { it.ids.isNotEmpty() && it.widget is WeekClassesWidget }

        if (needToday) {
            runCatching { WidgetDataLoader.loadToday(context) }
                .onFailure { Log.w(TAG, "刷新前预读今日数据失败", it) }
                .getOrNull()
                ?.let { WidgetRenderCache.today.value = it }
        }
        if (needWeek) {
            runCatching { WidgetDataLoader.loadWeek(context) }
                .onFailure { Log.w(TAG, "刷新前预读本周数据失败", it) }
                .getOrNull()
                ?.let { WidgetRenderCache.week.value = it }
        }

        // 状态刚写完就调用 updateAll，组合可能还没重新跑。
        // 让出一次主线程，保证这一轮推给启动器的就是新组合。
        if (needToday || needWeek) kotlinx.coroutines.delay(STATE_SETTLE_MS)
    }

    /**
     * 通知启动器集合视图的子项已变化。
     *
     * 只在卡片里确实用了 LazyColumn（即「待办+课表」这一族）时才需要，
     * 但这里对所有实例都调用一次：失败只记日志，不会影响已经提交的更新。
     */
    private fun notifyCollections(
        context: Context,
        widgetClass: Class<out GlanceAppWidget>,
        ids: List<androidx.glance.GlanceId>
    ) {
        runCatching {
            val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
            ids.forEach { glanceId ->
                val appWidgetId = runCatching { manager.getAppWidgetId(glanceId) }.getOrNull() ?: return@forEach
                runCatching {
                    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, android.R.id.list)
                }
            }
        }.onFailure { Log.w(TAG, "通知集合视图更新失败（不影响主刷新）", it) }
    }

    /** 供排查使用：累计请求数与实际执行轮次。 */
    fun stats(): Pair<Long, Long> = coalescer?.stats() ?: (0L to 0L)

    /** 最近一次实际执行的刷新原因（排查用）。 */
    fun lastReason(): String = lastAppliedReason.get()

    /** 一轮刷新里，某张卡片及其桌面实例。 */
    private data class Pending(
        val label: String,
        val widget: GlanceAppWidget,
        val ids: List<androidx.glance.GlanceId>
    )
}

/**
 * 「用户手动刷新」入口：卡片上的刷新图标点一下就走这里。
 *
 * 放在卡片上而不是只在应用内，是因为「卡片显示旧数据」时用户的第一反应就是在卡片上点刷新；
 * 这里直接重新读取数据库再渲染，不做任何业务写入。
 */
class RefreshWidgetsAction : androidx.glance.appwidget.action.ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: androidx.glance.GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        WidgetRefresher.refreshNow(context, "用户点击卡片刷新")
    }
}
