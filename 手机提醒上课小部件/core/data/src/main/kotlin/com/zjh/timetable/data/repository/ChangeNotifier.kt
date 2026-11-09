package com.zjh.timetable.data.repository

/**
 * 课表写后钩子（详细设计 §3.4、概要设计 §10）。
 * Repository 每个写方法完成后回调；实现由 app 模块装配时绑定（依赖倒置，
 * core/data 不 import scheduler/widget 模块）。实现方必须依序触发：
 * 1. ReminderScheduler.rescheduleAll()
 * 2. WidgetUpdater.refreshAll()
 */
interface ChangeNotifier {
    suspend fun onTimetableChanged()
}
