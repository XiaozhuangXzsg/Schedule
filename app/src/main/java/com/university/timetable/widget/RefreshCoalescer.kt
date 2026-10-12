package com.university.timetable.widget

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 刷新请求合并器。
 *
 * 保证三件事（这三件事正是「小组件显示旧数据 / 旧结果覆盖新结果」的根因所在）：
 *
 * 1. **同一时刻只有一轮在执行**（串行），不会出现两次刷新交错、旧的那次后完成把新数据覆盖回去；
 * 2. **请求可以合并**，短时间内的连续请求（例如批量导入逐条写库）不会触发几十上百轮刷新；
 * 3. **合并时不丢最后一次请求**——这是最容易写错的地方：
 *    单纯用 `if (running) return` 会把「正在刷新期间到达的变更」直接丢掉，
 *    于是界面/卡片永远停在那一次变更之前的状态。
 *
 * 实现是「脏标记 + tryLock + 退出前再确认一次」：已经有人在刷时本次调用直接返回，
 * 由对方循环里的脏标记兜住这次请求；否则反复消费脏标记直到确实没有新请求才退出。
 *
 * 与 Android 无关，便于单元测试。
 */
class RefreshCoalescer(
    private val scope: CoroutineScope,
    private val onError: (Throwable) -> Unit = {},
    private val perform: suspend (String) -> Unit
) {

    private val dirty = AtomicBoolean(false)
    private val mutex = Mutex()
    private val pendingReason = AtomicReference("数据变更")
    private val requestedCount = AtomicLong(0)
    private val performedCount = AtomicLong(0)

    /** 请求一次刷新（非阻塞，可随时从任何线程调用）。 */
    fun request(reason: String) {
        pendingReason.set(reason)
        dirty.set(true)
        requestedCount.incrementAndGet()
        scope.launch { drain() }
    }

    /** 立刻执行一轮并等待完成（用于「用户主动刷新」这种需要确定反馈的场景）。 */
    suspend fun runNow(reason: String) {
        mutex.withLock { runOnce(reason) }
    }

    /** (累计请求次数, 实际执行轮次)。 */
    fun stats(): Pair<Long, Long> = requestedCount.get() to performedCount.get()

    private suspend fun drain() {
        while (true) {
            if (!mutex.tryLock()) return
            try {
                while (dirty.getAndSet(false)) {
                    runOnce(pendingReason.get())
                }
            } finally {
                mutex.unlock()
            }
            // 退出前再确认一次，避免「请求刚好落在循环退出之后」被丢掉
            if (!dirty.get()) return
        }
    }

    private suspend fun runOnce(reason: String) {
        performedCount.incrementAndGet()
        // 单轮失败（例如某张卡片刷新抛异常）不能中断合并循环，
        // 也不能把异常抛进应用级作用域；只上报，让后续请求照常执行。
        try {
            perform(reason)
        } catch (error: Throwable) {
            onError(error)
        }
    }
}
