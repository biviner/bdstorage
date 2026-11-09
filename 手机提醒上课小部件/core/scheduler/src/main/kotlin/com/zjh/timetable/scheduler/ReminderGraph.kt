package com.zjh.timetable.scheduler

import android.content.Context
import com.zjh.timetable.data.local.TimetableDatabase
import com.zjh.timetable.data.repository.ChangeNotifier
import com.zjh.timetable.data.repository.TimetableRepository

/**
 * 提醒链路装配桥（详细设计 §2：手工 DI，不引 Hilt/Koin）。
 * 生产由 app 模块 [com.zjh.timetable.ui.data.ServiceLocator] 首建并经 [Companion.register]
 * 注册进程级单例；闹钟/开机广播可能把进程冷启在 Receiver（Activity 未走），此时经
 * [cached] 兜底自建，保证同一进程内仓储与调度器单例（scheduler mutex 全局唯一）。
 */
class ReminderGraph private constructor(
    val repository: TimetableRepository,
    val scheduler: ReminderScheduler,
) {

    companion object {

        @Volatile
        private var cached: ReminderGraph? = null

        /** 进程级单例；app 装配点已注册则复用，否则构建（覆盖 Receiver 冷启兜底路径）。 */
        fun cached(context: Context): ReminderGraph =
            cached ?: synchronized(this) {
                cached ?: build(context).also { cached = it }
            }

        /**
         * app 装配点注册首建 graph（完整写后钩子链，含小组件刷新）。
         * 若 Receiver 兜底先建（钩子链缺小组件环节），以其替换——此后全进程
         * 统一走 app 装配，兜底实例仅在被替换前的在途协程中存活（评审阻塞项2）。
         */
        fun register(graph: ReminderGraph) {
            synchronized(this) { cached = graph }
        }

        /**
         * [repository] 供测试注入 in-memory 库；为 null 时按生产装配构建文件库。
         * 写后钩子顺序（ChangeNotifier 文档约束）：先重排提醒，再入队兜底 Worker；
         * 小组件刷新（WidgetUpdater.refreshAll）由 B4-2 车道在其后追加。
         */
        fun build(context: Context, repository: TimetableRepository? = null): ReminderGraph {
            val app = context.applicationContext
            val repo = repository ?: buildProductionRepository(app)
            return ReminderGraph(repo, ReminderSchedulerImpl(app, repo))
        }

        private fun buildProductionRepository(app: Context): TimetableRepository {
            // 闭包延后读取：Repository 构建时 scheduler 尚未存在，写操作必在其后
            lateinit var scheduler: ReminderScheduler
            return TimetableRepository(
                database = TimetableDatabase.build(app),
                changeNotifier = object : ChangeNotifier {
                    override suspend fun onTimetableChanged() {
                        scheduler.rescheduleAll()
                        ScheduleGuardWorker.enqueueNow(app)
                    }
                },
            ).also { scheduler = ReminderSchedulerImpl(app, it) }
        }
    }
}
