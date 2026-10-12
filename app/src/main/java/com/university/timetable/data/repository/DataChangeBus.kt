package com.university.timetable.data.repository

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * 数据变更的类型。
 *
 * 只用于日志与「刷新哪些东西」的判断，不携带任何用户内容。
 */
enum class DataKind {
    /** 待办新增 / 修改 / 删除 / 完成状态变化 */
    TODO,

    /** 课程或上课时段变化 */
    COURSE,

    /** 作息时间变化 */
    PERIOD,

    /** 节假日 / 调休变化 */
    HOLIDAY,

    /** 日期变更记录（调课 / 停课 / 补课 / 换教室）变化 */
    OVERRIDE,

    /** 整体导入或替换课表 */
    IMPORT,

    /** 云同步写入 */
    SYNC,

    /** 容器设置（学期开始、总周数等）变化 */
    SETTINGS;

    /**
     * 这次变更是否会影响「下一节课 / 上课提醒」。
     *
     * 待办变化不影响课表，不必惊动流体云通知；
     * 其余类型都可能改变「下一节课是哪一节、在哪个教室」，需要刷新提醒。
     */
    val affectsSchedule: Boolean
        get() = this != TODO
}

/**
 * 一次数据变更事件。
 *
 * @param version 单调递增的全局序号。用于排查「较早发起的刷新晚于新变更完成」
 *   这类顺序问题；界面与小组件永远不会因为这个序号而丢弃更新，它只是可观测性。
 */
data class DataChange(
    val kind: DataKind,
    val recordId: Long?,
    val version: Long,
    val source: String
)

/** 数据变更监听器。 */
fun interface DataChangeListener {
    fun onDataChanged(change: DataChange)
}

/**
 * 进程内的数据变更总线。
 *
 * **约定**：仓库的每个写方法在 DAO 调用**返回之后**（也就是事务已经提交之后）
 * 调用 [publish]。这样订阅者看到的永远是「已经落库的数据」，
 * 不会出现「界面刷新了但数据还没写完」的假更新。
 *
 * 页面本身不需要订阅它——页面的数据来自 Room 的 Flow，会自动更新。
 * 它主要用于：桌面小组件刷新、日志排查、以及自动化测试。
 */
object DataChangeBus {

    private const val TAG = "DataChange"

    private val version = AtomicLong(0)
    private val listeners = CopyOnWriteArrayList<DataChangeListener>()

    fun addListener(listener: DataChangeListener) {
        listeners += listener
    }

    fun removeListener(listener: DataChangeListener) {
        listeners -= listener
    }

    /** 当前版本号（最近一次变更的序号）。 */
    fun currentVersion(): Long = version.get()

    /**
     * 发布一次数据变更。
     *
     * 日志只写「类型 / 记录编号 / 版本 / 来源」，不写待办正文等隐私内容。
     */
    fun publish(kind: DataKind, recordId: Long?, source: String): DataChange {
        val change = DataChange(
            kind = kind,
            recordId = recordId,
            version = version.incrementAndGet(),
            source = source
        )
        Log.d(
            TAG,
            "数据已提交 kind=${kind.name} id=${recordId ?: "-"} version=${change.version} source=$source"
        )
        listeners.forEach { listener ->
            runCatching { listener.onDataChanged(change) }
                .onFailure { Log.w(TAG, "变更监听器处理失败 kind=${kind.name}", it) }
        }
        return change
    }

    /** 仅供测试：清空监听器与版本号。 */
    internal fun resetForTest() {
        listeners.clear()
        version.set(0)
    }
}
