package com.university.timetable.widget

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * 刷新请求合并的回归测试。
 *
 * 覆盖的正是「小组件显示旧数据」的两个典型写法错误：
 * 一次变更触发一次刷新（批量导入会打爆），以及「正在刷新时到达的请求被直接丢弃」。
 *
 * 这里用 [UnconfinedTestDispatcher]：合并循环内部没有真正的挂起点，
 * 用它会立刻执行到结束，断言结果与真实运行语义一致且完全确定。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RefreshCoalescerTest {

    @Test
    fun `单次请求只执行一轮`() = runTest {
        val performed = AtomicInteger(0)
        val coalescer = RefreshCoalescer(CoroutineScope(UnconfinedTestDispatcher(testScheduler))) {
            performed.incrementAndGet()
        }

        coalescer.request("一次变更")

        assertEquals(1, performed.get())
    }

    @Test
    fun `一轮执行期间的连续请求会被合并成一次`() = runTest {
        val performed = AtomicInteger(0)
        val concurrentlyRunning = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        // 第一轮卡在这里，模拟「真实刷新需要时间」；
        // 这期间到达的请求必须被合并，而不是排队刷 50 次。
        val gate = CompletableDeferred<Unit>()
        var firstRound = true

        val coalescer = RefreshCoalescer(CoroutineScope(UnconfinedTestDispatcher(testScheduler))) {
            val now = concurrentlyRunning.incrementAndGet()
            if (now > maxConcurrent.get()) maxConcurrent.set(now)
            performed.incrementAndGet()
            try {
                if (firstRound) {
                    firstRound = false
                    gate.await()
                }
            } finally {
                concurrentlyRunning.decrementAndGet()
            }
        }

        // 模拟一次批量导入：第一条触发第一轮，其余 49 条落在第一轮执行期间
        coalescer.request("导入第 0 条")
        repeat(49) { coalescer.request("导入第 ${it + 1} 条") }
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("50 次请求应合并成 2 轮（当前一轮 + 最后一轮）", 2, performed.get())
        assertEquals("请求数应当被完整计数", 50L, coalescer.stats().first)
        assertTrue("同一时刻不允许有并发刷新", maxConcurrent.get() <= 1)
    }

    @Test
    fun `执行期间到达的请求不会被丢弃且保留最后一次`() = runTest {
        val performed = AtomicInteger(0)
        val reasons = mutableListOf<String>()
        lateinit var coalescer: RefreshCoalescer

        coalescer = RefreshCoalescer(CoroutineScope(UnconfinedTestDispatcher(testScheduler))) { reason ->
            performed.incrementAndGet()
            reasons += reason
            // 第一轮执行过程中又来了两次请求：必须被合并进来，不能丢
            if (performed.get() == 1) {
                coalescer.request("执行中变更 A")
                coalescer.request("执行中变更 B")
            }
        }

        coalescer.request("初始变更")

        assertEquals("最后一轮必须被执行", 2, performed.get())
        assertEquals("合并时应保留最后一次请求", "执行中变更 B", reasons.last())
    }

    @Test
    fun `runNow 会等待执行完成`() = runTest {
        val performed = AtomicInteger(0)
        val coalescer = RefreshCoalescer(CoroutineScope(UnconfinedTestDispatcher(testScheduler))) {
            performed.incrementAndGet()
        }

        coalescer.runNow("手动刷新")

        assertEquals("runNow 返回时应当已经刷过一轮", 1, performed.get())
    }

    @Test
    fun `单轮失败不会中断后续请求`() = runTest {
        val performed = AtomicInteger(0)
        val errors = AtomicInteger(0)
        val coalescer = RefreshCoalescer(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            onError = { errors.incrementAndGet() }
        ) {
            performed.incrementAndGet()
            if (performed.get() == 1) throw IllegalStateException("第一轮故意失败")
        }

        coalescer.request("第一轮")
        coalescer.request("第二轮")

        assertEquals("失败的一轮不应阻止后续刷新", 2, performed.get())
        assertEquals("失败应被上报", 1, errors.get())
    }
}
