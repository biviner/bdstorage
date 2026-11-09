package com.zjh.timetable.data.repository

import androidx.room.withTransaction
import com.zjh.timetable.data.local.TimetableDatabase
import com.zjh.timetable.data.local.entity.RecognitionDraftEntity
import com.zjh.timetable.data.local.toDomain
import com.zjh.timetable.data.local.toEntity
import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.PeriodTime
import com.zjh.timetable.domain.model.SemesterSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

/**
 * 课表 Repository 门面（详细设计 §3.3、§3.4；概要设计 §10）。
 * 课程/学期/作息的一切写操作必经此处（全局约束 §9.2），写后依序触发
 * 提醒重排与小组件刷新（经构造注入的 [ChangeNotifier]，见其文档）。
 * UI 只可见本类与 domain 模型，禁止直连 DAO。
 */
class TimetableRepository(
    private val database: TimetableDatabase,
    private val changeNotifier: ChangeNotifier,
) {

    // ── 课程 ──────────────────────────────────────────────────────────

    fun observeCourses(): Flow<List<Course>> =
        database.courseDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun coursesOnDay(day: Int): List<Course> =
        database.courseDao().byDay(day).map { it.toDomain() }

    suspend fun course(id: Long): Course? =
        database.courseDao().byId(id)?.toDomain()

    /** 新建课程（id 由数据库自增，时间戳在此盖戳）；返回自增 id。 */
    suspend fun addCourse(course: Course): Long {
        val now = LocalDateTime.now()
        val id = database.courseDao().insert(course.toEntity(id = 0, createdAt = now, updatedAt = now))
        notifyChange()
        return id
    }

    /**
     * 批量新建（识别确认页导入，概要设计 §10：失败整批回滚——
     * Room @Insert(List) 单方法单事务）；返回自增 id 列表。
     */
    suspend fun addCourses(courses: List<Course>): List<Long> {
        val now = LocalDateTime.now()
        val ids = database.courseDao().insertAll(courses.map { it.toEntity(id = 0, createdAt = now, updatedAt = now) })
        notifyChange()
        return ids
    }

    /** 保存编辑；updatedAt 盖戳，createdAt 保持原值。 */
    suspend fun updateCourse(course: Course) {
        database.courseDao().update(course.toEntity(updatedAt = LocalDateTime.now()))
        notifyChange()
    }

    suspend fun deleteCourse(course: Course) {
        database.courseDao().delete(course.toEntity())
        notifyChange()
    }

    /** 停课日期串 "2026-11-20,2026-11-27"（null=无停课）。 */
    suspend fun setPausedDates(courseId: Long, pausedDates: String?) {
        database.courseDao().setPaused(courseId, pausedDates)
        notifyChange()
    }

    // ── 学期设置 ─────────────────────────────────────────────────────

    fun observeSemester(): Flow<SemesterSetting?> =
        database.semesterSettingDao().observe().map { it?.toDomain() }

    suspend fun semester(): SemesterSetting? =
        database.semesterSettingDao().get()?.toDomain()

    /** 学期设置变更同样触发钩子（详细设计 §3.4）。 */
    suspend fun saveSemester(setting: SemesterSetting) {
        database.semesterSettingDao().upsert(setting.toEntity())
        notifyChange()
    }

    // ── 作息表 ────────────────────────────────────────────────────────

    fun observeTimetable(): Flow<List<PeriodTime>> =
        database.periodTimeDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun timetable(): List<PeriodTime> =
        database.periodTimeDao().getAll().map { it.toDomain() }

    /** 全量替换（编辑保存/恢复默认），事务内先清后写；变更触发钩子。 */
    suspend fun replaceTimetable(periods: List<PeriodTime>) {
        database.withTransaction {
            database.periodTimeDao().clear()
            database.periodTimeDao().upsertAll(periods.map { it.toEntity() })
        }
        notifyChange()
    }

    // ── 识别草稿（B3 消费；草稿非课表数据，不走 ChangeNotifier） ────────

    suspend fun saveDraft(coursesJson: String, rawImageUri: String? = null): Long =
        database.recognitionDraftDao()
            .insert(RecognitionDraftEntity(createdAt = LocalDateTime.now(), rawImageUri = rawImageUri, coursesJson = coursesJson))

    suspend fun draft(id: Long): RecognitionDraft? =
        database.recognitionDraftDao().byId(id)?.toModel()

    suspend fun deleteDraft(id: Long) {
        database.recognitionDraftDao().delete(id)
    }

    private fun RecognitionDraftEntity.toModel() = RecognitionDraft(
        id = id,
        createdAt = createdAt,
        rawImageUri = rawImageUri,
        coursesJson = coursesJson,
    )

    private suspend fun notifyChange() = changeNotifier.onTimetableChanged()
}
