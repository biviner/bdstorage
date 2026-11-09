package com.zjh.timetable.domain.model

import java.time.LocalDate

/** 课表展示模式：节次模式 / 时刻模式（详细设计 §3.1 semester_setting.timetable_mode）。 */
enum class TimetableMode {
    PERIOD,
    EXACT_TIME,
}

/**
 * 学期设置（详细设计 §3.1）。全应用单行（Room 层 id 恒为 1），domain 不携带 id。
 */
data class SemesterSetting(
    /** 第 1 周周一日期，ISO yyyy-MM-dd */
    val firstWeekStartDate: LocalDate,
    /** 总周数，默认 20 */
    val totalWeeks: Int = 20,
    /** 全局默认提前提醒分钟数；≤0 表示准点 */
    val defaultReminderLeadMinutes: Int = 15,
    val timetableMode: TimetableMode = TimetableMode.PERIOD,
)
