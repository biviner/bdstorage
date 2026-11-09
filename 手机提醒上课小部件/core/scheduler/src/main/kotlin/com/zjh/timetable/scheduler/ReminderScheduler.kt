package com.zjh.timetable.scheduler

/**
 * 提醒调度器契约（详细设计 §6.1）。实现方按 ReminderPlanner 输出的清单设置闹钟，
 * 不理解业务规则（规则全在 domain）。
 */
interface ReminderScheduler {

    /** 幂等全量重排：cancelAll → plan(未来7天) → 逐条 setExactAndAllowWhileIdle。 */
    suspend fun rescheduleAll()
}
