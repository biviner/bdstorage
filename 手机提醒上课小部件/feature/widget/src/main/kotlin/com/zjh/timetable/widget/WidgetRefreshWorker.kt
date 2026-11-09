package com.zjh.timetable.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 小组件周期刷新 Worker（详细设计 §7.3）：每 15 分钟，flex 5 分钟。
 * 刷新粒度刻意保守，保证「下一节」「进行中」在可接受误差内正确且不耗电；不做秒级刷新。
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        WidgetUpdater.refreshAll(applicationContext)
        return Result.success()
    }

    companion object {

        private const val UNIQUE_PERIODIC = "widget_refresh_periodic"

        /** 周期任务注册（KEEP：已注册不重复）；app 启动时调用一次。 */
        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                15, TimeUnit.MINUTES, 5, TimeUnit.MINUTES,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }
    }
}
