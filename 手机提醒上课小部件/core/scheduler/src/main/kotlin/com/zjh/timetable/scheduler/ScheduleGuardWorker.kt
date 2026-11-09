package com.zjh.timetable.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * 调度兜底 Worker（详细设计 §6.1）：每日 04:00 周期执行 + 每次 Repository 变更时
 * 入队（REPLACE）。兜底场景：精确闹钟权限被回收、闹钟被 ROM 清除。
 */
class ScheduleGuardWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ReminderGraph.cached(applicationContext).scheduler.rescheduleAll()
        return Result.success()
    }

    companion object {

        private const val UNIQUE_DAILY = "schedule_guard_daily"
        private const val UNIQUE_NOW = "schedule_guard_now"
        private val RUN_AT: LocalTime = LocalTime.of(4, 0)

        /** 每日 04:00 周期兜底；app 启动时调用一次（KEEP：已注册不重复）。 */
        fun ensureDaily(context: Context) {
            val now = LocalDateTime.now()
            var nextRun = now.toLocalDate().atTime(RUN_AT)
            if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ScheduleGuardWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, nextRun))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_DAILY, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }

        /** 变更时兜底重排（REPLACE：频繁写入只保留最新一次）。 */
        fun enqueueNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ScheduleGuardWorker>().build(),
            )
        }
    }
}
