package com.zjh.timetable.domain.model

import java.time.LocalDateTime

/**
 * 一门已排定的提醒（详细设计 §4.4），由 ReminderPlanner 输出、调度层消费。
 */
data class PlannedReminder(
    val courseId: Long,
    val triggerAt: LocalDateTime,
    val title: String,
    val body: String,
)
