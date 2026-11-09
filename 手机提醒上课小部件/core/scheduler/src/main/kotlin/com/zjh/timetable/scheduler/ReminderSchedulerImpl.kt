package com.zjh.timetable.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.zjh.timetable.data.repository.TimetableRepository
import com.zjh.timetable.domain.calc.ReminderPlanner
import com.zjh.timetable.domain.model.PlannedReminder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.ZoneId

/**
 * 调度器实现（详细设计 §6.1）。
 * 幂等性：requestCode = (courseId, triggerAt) 组合 hash，重设同码即覆盖；
 * 上一次排出的 requestCode 集合持久化在 SharedPreferences，重排前先按其取消，
 * 课程被删/停课后旧闹钟得以清除（约束 §9.3：任意次连续 rescheduleAll 结果一致）。
 * AlarmReceiver 触发后也会调 [rescheduleAll] 滚动补排后续 7 天，并发由 [mutex] 串行化。
 */
class ReminderSchedulerImpl(
    private val context: Context,
    private val repository: TimetableRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ReminderScheduler {

    private val mutex = Mutex()
    private val app = context.applicationContext

    override suspend fun rescheduleAll() = mutex.withLock {
        val now = java.time.LocalDateTime.now(clock)
        val settings = repository.semester()
        // 学期未设置：无可排课程，仅清空既有闹钟
        val plan = if (settings == null) {
            emptyList()
        } else {
            // v1 文案用课程起止分钟（§4.4），作息表参数留扩展，不额外查询
            ReminderPlanner.plan(repository.observeCourses().first(), settings, now = now)
        }
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return@withLock

        // ── cancelAll：取消上一轮排出的全部闹钟 ──
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty()
        for (code in previous) {
            pendingIntent(code.toInt(), create = false)?.let { alarmManager.cancel(it) }
        }

        // ── 逐条 setExactAndAllowWhileIdle ──
        val current = mutableSetOf<String>()
        for (reminder in plan) {
            val triggerAtMillis = reminder.triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val requestCode = ReminderRequests.requestCode(reminder.courseId, triggerAtMillis)
            scheduleExact(alarmManager, triggerAtMillis, ReminderRequests.pendingIntent(app, requestCode, reminder))
            current += requestCode.toString()
        }
        prefs.edit().putStringSet(KEY_CODES, current).apply()
    }

    /** 精确闹钟权限（API 31+）被收回时降级为非精确，精度损失由 ScheduleGuardWorker 兜底。 */
    private fun scheduleExact(alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun pendingIntent(requestCode: Int, create: Boolean): PendingIntent? =
        PendingIntent.getBroadcast(
            app,
            requestCode,
            Intent(app, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or noCreateFlag(create),
        )

    private fun noCreateFlag(create: Boolean) =
        if (create) 0 else PendingIntent.FLAG_NO_CREATE

    private companion object {
        const val PREFS = "reminder_alarm_codes"
        const val KEY_CODES = "requestCodes"
    }
}

/** requestCode 与 PendingIntent 构造（详细设计 §6.1 requestCode 契约）。 */
object ReminderRequests {

    /**
     * courseId.hashCode() 与 triggerAt epochMinute 组合 hash：
     * 同课同刻稳定（重设覆盖），同课不同刻可共存。
     */
    fun requestCode(courseId: Long, triggerAtMillis: Long): Int =
        31 * courseId.hashCode() + (triggerAtMillis / MILLIS_PER_MINUTE).hashCode()

    fun pendingIntent(context: Context, requestCode: Int, reminder: PlannedReminder): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java)
                .putExtra(AlarmReceiver.EXTRA_COURSE_ID, reminder.courseId)
                .putExtra(AlarmReceiver.EXTRA_TRIGGER_AT_MILLIS, reminder.triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .putExtra(AlarmReceiver.EXTRA_TITLE, reminder.title)
                .putExtra(AlarmReceiver.EXTRA_BODY, reminder.body),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private const val MILLIS_PER_MINUTE = 60_000L
}
