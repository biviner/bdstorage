package com.zjh.timetable.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * requestCode 契约（详细设计 §6.1：courseId.hashCode() 与 triggerAt epochMinute 组合 hash）。
 */
class ReminderRequestsTest {

    @Test
    fun `同课同刻 requestCode 稳定`() {
        val a = ReminderRequests.requestCode(7L, 1_700_000_000_000L)
        val b = ReminderRequests.requestCode(7L, 1_700_000_000_000L)
        assertEquals(a, b)
    }

    @Test
    fun `同课不同刻 requestCode 不同（可共存）`() {
        val a = ReminderRequests.requestCode(7L, 1_700_000_000_000L)
        val b = ReminderRequests.requestCode(7L, 1_700_060_000_000L)
        assertNotEquals(a, b)
    }

    @Test
    fun `同刻不同课 requestCode 不同`() {
        val a = ReminderRequests.requestCode(7L, 1_700_000_000_000L)
        val b = ReminderRequests.requestCode(8L, 1_700_000_000_000L)
        assertNotEquals(a, b)
    }

    @Test
    fun `同分钟内秒级差异 requestCode 稳定（epochMinute 粒度）`() {
        val a = ReminderRequests.requestCode(7L, 1_700_000_000_000L)
        val b = ReminderRequests.requestCode(7L, 1_700_000_030_000L)
        assertEquals(a, b)
    }
}
