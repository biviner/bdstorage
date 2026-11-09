package com.zjh.timetable.ui.data

import android.content.Context
import com.zjh.timetable.data.local.DefaultTimetable
import com.zjh.timetable.data.local.TimetableDatabase
import com.zjh.timetable.data.repository.ChangeNotifier
import com.zjh.timetable.data.repository.RecognitionDraft
import com.zjh.timetable.data.repository.TimetableRepository
import com.zjh.timetable.data.settings.TimetableDataStore
import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.PeriodTime
import com.zjh.timetable.domain.model.SemesterSetting
import com.zjh.timetable.scheduler.ReminderGraph
import com.zjh.timetable.scheduler.ReminderScheduler
import com.zjh.timetable.scheduler.ReminderSchedulerImpl
import com.zjh.timetable.scheduler.ScheduleGuardWorker
import com.zjh.timetable.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow

/**
 * 装配点（详细设计 §2：手工 DI，不引 Hilt/Koin）。B2-3 换装 core/data 真实现：
 * Room 库 + [TimetableRepository]，首次启动预填默认作息（详细设计 §3.1）。
 * MainActivity.onCreate 必须先调用 [init]。
 */
object ServiceLocator {

    @Volatile
    private var repo: UiTimetableRepository? = null

    @Volatile
    private var store: TimetableDataStore? = null

    /** 识别 prompt 全文（core/network assets，构建期合并进 app），懒加载一次。 */
    @Volatile
    private var prompt: String? = null

    val repository: UiTimetableRepository
        get() = checkNotNull(repo) { "ServiceLocator.init(context) 必须在首次访问前调用" }

    val dataStore: TimetableDataStore
        get() = checkNotNull(store) { "ServiceLocator.init(context) 必须在首次访问前调用" }

    val recognitionPrompt: String
        get() = checkNotNull(prompt) { "ServiceLocator.init(context) 必须在首次访问前调用" }

    fun init(context: Context) {
        if (repo != null) return
        synchronized(this) {
            if (repo != null) return
            val app = context.applicationContext
            // B4-2：写后钩子在 app 装配点绑定（AGENTS.md §3 依赖倒置）——
            // 依序：提醒重排（B4-1）→ 兜底 Worker（B4-1）→ 小组件即时刷新（B4-2）
            val graph = buildGraph(app)
            // B4-FIX（评审阻塞项2）：注册进程级单例——闹钟/开机路径经
            // ReminderGraph.cached 复用同一 graph，scheduler mutex 全局唯一
            ReminderGraph.register(graph)
            val repository = graph.repository
            // 首次启动写入默认作息；同步执行消除与设置页首读的竞态（轻量单查，毫秒级）
            runBlocking(Dispatchers.IO) {
                if (repository.timetable().isEmpty()) {
                    repository.replaceTimetable(DefaultTimetable.seed)
                }
            }
            repo = RoomTimetableRepository(repository)
            store = TimetableDataStore(app)
            // B4-1：每日 04:00 调度兜底（精确闹钟权限被回收/闹钟被 ROM 清除场景）
            ScheduleGuardWorker.ensureDaily(app)
            // B4-2：小组件 15 分钟周期刷新 + 午夜日切 Alarm
            WidgetUpdater.ensureScheduled(app)
            // B3-2：识别 prompt 从合并 assets 读取（OpenAiVisionRecognizer 契约）
            prompt = runCatching {
                app.assets.open("prompt.md").bufferedReader().use { it.readText() }
            }.getOrDefault("")
        }
    }

    /**
     * B4-2：在 app 装配点构建带完整写后钩子的 Repository（详细设计 §3.4：
     * ChangeNotifier 实现由 app 模块装配时绑定；ReminderGraph.build 支持注入，
     * 供 Receiver 冷启兜底链路保持同一装配语义）。B4-FIX 起构建后经
     * [ReminderGraph.register] 注册为进程级单例。
     */
    private fun buildGraph(app: Context): ReminderGraph {
        lateinit var scheduler: ReminderScheduler
        val repository = TimetableRepository(
            database = TimetableDatabase.build(app),
            changeNotifier = object : ChangeNotifier {
                override suspend fun onTimetableChanged() {
                    scheduler.rescheduleAll()
                    ScheduleGuardWorker.enqueueNow(app)
                    WidgetUpdater.refreshAll(app)
                }
            },
        ).also { scheduler = ReminderSchedulerImpl(app, it) }
        return ReminderGraph.build(app, repository)
    }
}

/** core/data 真实现 → UI 车道接口适配（方法一一对应，纯委托）。 */
private class RoomTimetableRepository(
    private val impl: TimetableRepository,
) : UiTimetableRepository {

    override fun observeCourses(): Flow<List<Course>> = impl.observeCourses()

    override suspend fun course(id: Long): Course? = impl.course(id)

    override suspend fun addCourse(course: Course): Long = impl.addCourse(course)

    override suspend fun addCourses(courses: List<Course>): List<Long> = impl.addCourses(courses)

    override suspend fun updateCourse(course: Course) = impl.updateCourse(course)

    override suspend fun deleteCourse(course: Course) = impl.deleteCourse(course)

    override fun observeSemester(): Flow<SemesterSetting?> = impl.observeSemester()

    override suspend fun semester(): SemesterSetting? = impl.semester()

    override suspend fun saveSemester(setting: SemesterSetting) = impl.saveSemester(setting)

    override fun observeTimetable(): Flow<List<PeriodTime>> = impl.observeTimetable()

    override suspend fun timetable(): List<PeriodTime> = impl.timetable()

    override suspend fun replaceTimetable(periods: List<PeriodTime>) = impl.replaceTimetable(periods)

    override suspend fun saveDraft(coursesJson: String, rawImageUri: String?): Long =
        impl.saveDraft(coursesJson, rawImageUri)

    override suspend fun draft(id: Long): RecognitionDraft? = impl.draft(id)

    override suspend fun deleteDraft(id: Long) = impl.deleteDraft(id)
}
