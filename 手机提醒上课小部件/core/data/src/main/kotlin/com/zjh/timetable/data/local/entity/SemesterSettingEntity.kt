package com.zjh.timetable.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zjh.timetable.domain.model.TimetableMode
import java.time.LocalDate

/**
 * 学期设置（详细设计 §3.1 semester_setting 表）。全应用单行，id 恒为 1
 * （schema 层 CHECK 约束兜底，经 SQLite 触发器实现，见
 * [com.zjh.timetable.data.local.SchemaConstraints]）。
 */
@Entity(tableName = "semester_setting")
data class SemesterSettingEntity(
    @PrimaryKey val id: Long = 1,
    /** 第 1 周周一，ISO yyyy-MM-dd（经 TypeConverter 以 TEXT 存储） */
    @ColumnInfo(name = "first_week_start_date") val firstWeekStartDate: LocalDate,
    @ColumnInfo(name = "total_weeks", defaultValue = "20") val totalWeeks: Int = 20,
    @ColumnInfo(name = "default_reminder_lead_minutes", defaultValue = "15")
    val defaultReminderLeadMinutes: Int = 15,
    @ColumnInfo(name = "timetable_mode", defaultValue = "PERIOD")
    val timetableMode: TimetableMode = TimetableMode.PERIOD,
)
