package com.university.timetable.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 数据变更总线的测试。
 *
 * 关注两件事：
 * 1. 版本号单调递增（用于排查「旧结果覆盖新结果」）；
 * 2. 监听器抛异常不能影响写入方（小组件刷新失败绝不能影响已经保存成功的数据）。
 */
class DataChangeBusTest {

    @Before
    fun setUp() {
        DataChangeBus.resetForTest()
    }

    @Test
    fun `版本号单调递增`() {
        val v0 = DataChangeBus.currentVersion()
        val a = DataChangeBus.publish(DataKind.TODO, 1L, "新增待办")
        val b = DataChangeBus.publish(DataKind.COURSE, 2L, "修改课程")

        assertTrue("版本号必须递增", a.version > v0)
        assertTrue("版本号必须递增", b.version > a.version)
        assertEquals(b.version, DataChangeBus.currentVersion())
    }

    @Test
    fun `事件携带类型与记录编号`() {
        val received = mutableListOf<DataChange>()
        DataChangeBus.addListener { received += it }

        DataChangeBus.publish(DataKind.TODO, 42L, "删除待办")

        assertEquals(1, received.size)
        assertEquals(DataKind.TODO, received[0].kind)
        assertEquals(42L, received[0].recordId)
        assertEquals("删除待办", received[0].source)
    }

    @Test
    fun `监听器抛异常不影响发布方与其他监听器`() {
        val ok = mutableListOf<DataChange>()
        DataChangeBus.addListener { throw IllegalStateException("模拟小组件刷新失败") }
        DataChangeBus.addListener { ok += it }

        // 不应抛出
        DataChangeBus.publish(DataKind.COURSE, null, "清空全部课程")

        assertEquals("坏监听器不能阻塞好监听器", 1, ok.size)
    }

    @Test
    fun `移除监听器后不再收到事件`() {
        val received = mutableListOf<DataChange>()
        val listener = DataChangeListener { received += it }
        DataChangeBus.addListener(listener)
        DataChangeBus.publish(DataKind.TODO, null, "第一次")
        DataChangeBus.removeListener(listener)
        DataChangeBus.publish(DataKind.TODO, null, "第二次")

        assertEquals(1, received.size)
    }

    /**
     * 「哪些变更需要刷新上课提醒」是需求 ④ 的关键判定。
     *
     * 待办变化不会改变「下一节课是哪一节、在哪个教室」，刷新通知纯属浪费；
     * 其余任何一类都可能改变它 —— 尤其是 OVERRIDE（AI 调课 / 停课 / 换教室），
     * 漏掉它就会出现「AI 把课调走了，通知栏还在按旧课表提醒」。
     */
    @Test
    fun `待办变化不影响课表提醒 其余类型都要刷新`() {
        assertEquals("待办不该触发提醒刷新", false, DataKind.TODO.affectsSchedule)
        listOf(
            DataKind.COURSE,
            DataKind.PERIOD,
            DataKind.HOLIDAY,
            DataKind.OVERRIDE,
            DataKind.IMPORT,
            DataKind.SYNC,
            DataKind.SETTINGS
        ).forEach { kind ->
            assertTrue("$kind 应当触发提醒刷新", kind.affectsSchedule)
        }
    }
}
