package com.zjh.timetable.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zjh.timetable.domain.calc.WeekCalculator
import com.zjh.timetable.domain.model.PlannedReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 上课提醒闹钟触发（详细设计 §6.1）。
 * 载荷 courseId+triggerAt → 重查该课防陈旧（已删除/已停课静默跳过）→ 发通知 →
 * 立即 rescheduleAll() 滚动补排后续 7 天。判定用域函数（pausedDatesOf），
 * Receiver 内不写业务规则（卡面禁止项）。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val graph = ReminderGraph.cached(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                onReceiveAsync(context, graph, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** 独立成 suspend 函数便于单测（详细设计 §10：receiver 陈旧闹钟跳过）。 */
    suspend fun onReceiveAsync(context: Context, graph: ReminderGraph, intent: Intent) {
        val courseId = intent.getLongExtra(EXTRA_COURSE_ID, -1L)
        val triggerAtMillis = intent.getLongExtra(EXTRA_TRIGGER_AT_MILLIS, 0L)
        if (courseId <= 0L) return
        val course = graph.repository.course(courseId) ?: return // 课程已删除：陈旧闹钟静默跳过
        if (!course.reminderEnabled) return // 排程后用户关闭了该课提醒
        val triggerAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(triggerAtMillis), ZoneId.systemDefault())
        if (triggerAt.toLocalDate() in WeekCalculator.pausedDatesOf(course)) return // 已停课
        NotificationHelper.notify(
            context,
            PlannedReminder(
                courseId = courseId,
                triggerAt = triggerAt,
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                body = intent.getStringExtra(EXTRA_BODY).orEmpty(),
            ),
        )
        graph.scheduler.rescheduleAll()
    }

    companion object {
        const val EXTRA_COURSE_ID = "courseId"
        const val EXTRA_TRIGGER_AT_MILLIS = "triggerAtMillis"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
    }
}
