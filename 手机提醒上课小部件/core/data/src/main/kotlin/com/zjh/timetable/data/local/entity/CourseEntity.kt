package com.zjh.timetable.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zjh.timetable.domain.model.WeekPattern
import java.time.LocalDateTime

/**
 * 课程（详细设计 §3.1 course 表）。周一=1、周日=7；起止为当日分钟数；不跨零点。
 * 合法区间由 schema 层 CHECK 约束兜底（Room 无 @Check 注解，经 SQLite 触发器实现，
 * 见 [com.zjh.timetable.data.local.SchemaConstraints]；反例：day_of_week=0 插入被拒绝），
 * 非法输入仍应在 ViewModel 层先行拦截（详细设计 §3.1 校验注解）。
 */
@Entity(
    tableName = "course",
    indices = [Index(value = ["day_of_week"], name = "idx_course_day")],
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    @ColumnInfo(name = "start_minute") val startMinute: Int,
    @ColumnInfo(name = "end_minute") val endMinute: Int,
    @ColumnInfo(name = "location") val location: String? = null,
    @ColumnInfo(name = "teacher") val teacher: String? = null,
    @ColumnInfo(name = "color_argb") val colorArgb: Int? = null,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "week_pattern", defaultValue = "ALL")
    val weekPattern: WeekPattern = WeekPattern.ALL,
    /** "1-8,10,12-16"，仅 CUSTOM 时非空；编解码见 domain 层 CustomWeeks */
    @ColumnInfo(name = "custom_weeks") val customWeeks: String? = null,
    @ColumnInfo(name = "reminder_enabled", defaultValue = "1") val reminderEnabled: Boolean = true,
    /** NULL=跟随全局默认 */
    @ColumnInfo(name = "reminder_lead_minutes") val reminderLeadMinutes: Int? = null,
    /** "2026-11-20,2026-11-27"，NULL=无停课 */
    @ColumnInfo(name = "paused_dates") val pausedDates: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime,
    @ColumnInfo(name = "updated_at") val updatedAt: LocalDateTime,
)
