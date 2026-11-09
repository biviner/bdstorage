package com.zjh.timetable.widget

import android.content.Context
import com.zjh.timetable.data.repository.TimetableRepository
import com.zjh.timetable.data.local.TimetableDatabase
import com.zjh.timetable.data.repository.ChangeNotifier

/**
 * 小组件侧装配（手工 DI，与 core/scheduler 的 ReminderGraph 同款进程级单例模式）。
 * 小组件是数据库只读投影（AGENTS.md §3），不承担写后钩子，ChangeNotifier 为空实现；
 * 课程数据一律现查（provideGlance 内），不缓存。
 */
object WidgetGraph {

    @Volatile
    private var repo: TimetableRepository? = null

    fun repository(context: Context): TimetableRepository =
        repo ?: synchronized(this) {
            repo ?: build(context.applicationContext).also { repo = it }
        }

    private fun build(app: Context): TimetableRepository =
        TimetableRepository(
            database = TimetableDatabase.build(app),
            changeNotifier = object : ChangeNotifier {
                override suspend fun onTimetableChanged() { /* 只读投影，无写后钩子 */ }
            },
        )
}
