package com.zjh.timetable.ui.data

import com.zjh.timetable.data.repository.RecognitionDraft
import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.PeriodTime
import com.zjh.timetable.domain.model.SemesterSetting
import kotlinx.coroutines.flow.Flow

/**
 * UI 车道（B2-2）面向 ViewModels 的仓储接口——方法面与 core/data 的
 * `TimetableRepository` 一一对应（详细设计 §3.3），写路径必经此处（全局约束 §9.2）。
 * B2-1 已交付真实现，由总控在装配处（[ServiceLocator]）换装并触发 ChangeNotifier。
 */
interface UiTimetableRepository {

    // ── 课程 ──────────────────────────────────────────────────────────

    fun observeCourses(): Flow<List<Course>>

    suspend fun course(id: Long): Course?

    /** 新建课程，返回自增 id。 */
    suspend fun addCourse(course: Course): Long

    suspend fun updateCourse(course: Course)

    suspend fun deleteCourse(course: Course)

    /** 批量新建（识别确认页导入，整批单事务，详细设计 §3.3）；返回自增 id 列表。 */
    suspend fun addCourses(courses: List<Course>): List<Long>

    // ── 学期设置 ─────────────────────────────────────────────────────

    fun observeSemester(): Flow<SemesterSetting?>

    suspend fun semester(): SemesterSetting?

    suspend fun saveSemester(setting: SemesterSetting)

    // ── 作息表 ────────────────────────────────────────────────────────

    fun observeTimetable(): Flow<List<PeriodTime>>

    suspend fun timetable(): List<PeriodTime>

    /** 全量替换（编辑保存/恢复默认）。 */
    suspend fun replaceTimetable(periods: List<PeriodTime>)

    // ── 识别草稿（B3 消费；非课表数据，不走 ChangeNotifier） ──────────

    suspend fun saveDraft(coursesJson: String, rawImageUri: String? = null): Long

    suspend fun draft(id: Long): RecognitionDraft?

    suspend fun deleteDraft(id: Long)
}
