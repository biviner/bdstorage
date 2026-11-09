package com.zjh.timetable.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 小组件刷新门面（详细设计 §7.3；概要设计 §6.3）。
 * 触发源：WorkManager 每 15 分钟 | 午夜日切 Alarm | 课程/学期变更回调 | 时间/时区广播。
 * refreshAll 一律执行在 Dispatchers.Default；数据读取由各 widget 在 provideGlance 内
 * 现查 Repository，本类不做任何数据缓存。
 */
object WidgetUpdater {

    /** 更新全部已添加实例（未添加时为空操作）；写后钩子与定时触发均走此入口。 */
    suspend fun refreshAll(context: Context) {
        withContext(Dispatchers.Default) {
            val app = context.applicationContext
            NextClassWidget().updateAll(app)
            TodayWidget().updateAll(app)
        }
    }

    /** app 启动装配时调用一次：注册 15 分钟周期 Worker + 午夜日切 Alarm。 */
    fun ensureScheduled(context: Context) {
        val app = context.applicationContext
        WidgetRefreshWorker.ensureScheduled(app)
        MidnightAlarm.schedule(app)
    }
}
