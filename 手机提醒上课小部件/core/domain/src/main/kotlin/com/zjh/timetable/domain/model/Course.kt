package com.zjh.timetable.domain.model

import java.time.LocalDateTime

/**
 * 课程（详细设计 §3.1 course 表）。起止用当日分钟数（0..1439 / 1..1440），
 * 周一=1、周日=7；不可跨零点。所有字段不可变。
 */
data class Course(
    val id: Long,
    val name: String,
    /** 1=周一 .. 7=周日 */
    val dayOfWeek: Int,
    /** 当日开始分钟数，0..1439 */
    val startMinute: Int,
    /** 当日结束分钟数，1..1440，恒 > startMinute */
    val endMinute: Int,
    val location: String? = null,
    val teacher: String? = null,
    /** 课程块颜色（ARGB）；null = 按调色板自动 */
    val colorArgb: Int? = null,
    val note: String? = null,
    val weekPattern: WeekPattern = WeekPattern.ALL,
    /** 自定义周次串 "1-8,10,12-16"，仅 CUSTOM 时非空；编解码见 domain 层纯函数 */
    val customWeeks: String? = null,
    val reminderEnabled: Boolean = true,
    /** 单课提前提醒分钟数；null = 跟随全局默认 */
    val reminderLeadMinutes: Int? = null,
    /** 停课日期串 "2026-11-20,2026-11-27"，null = 无停课 */
    val pausedDates: String? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
