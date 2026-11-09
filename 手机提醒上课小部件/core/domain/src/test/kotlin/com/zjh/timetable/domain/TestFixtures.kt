package com.zjh.timetable.domain

import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.SemesterSetting
import com.zjh.timetable.domain.model.WeekPattern
import java.time.LocalDate
import java.time.LocalDateTime

/** 单测共享夹具：2026-03-02 为第 1 周周一（真实日历校验过）。 */

internal val FIRST_WEEK_START: LocalDate = LocalDate.of(2026, 3, 2)

internal fun testCourse(
    id: Long = 1L,
    name: String = "高等数学",
    dayOfWeek: Int = 1,
    startMinute: Int = 480, // 08:00
    endMinute: Int = 580, // 09:40
    location: String? = "A101",
    weekPattern: WeekPattern = WeekPattern.ALL,
    customWeeks: String? = null,
    reminderEnabled: Boolean = true,
    reminderLeadMinutes: Int? = null,
    pausedDates: String? = null,
): Course = Course(
    id = id,
    name = name,
    dayOfWeek = dayOfWeek,
    startMinute = startMinute,
    endMinute = endMinute,
    location = location,
    weekPattern = weekPattern,
    customWeeks = customWeeks,
    reminderEnabled = reminderEnabled,
    reminderLeadMinutes = reminderLeadMinutes,
    pausedDates = pausedDates,
    createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
    updatedAt = LocalDateTime.of(2026, 1, 1, 0, 0),
)

internal fun testSettings(
    firstWeekStartDate: LocalDate = FIRST_WEEK_START,
    totalWeeks: Int = 20,
    defaultReminderLeadMinutes: Int = 15,
): SemesterSetting = SemesterSetting(
    firstWeekStartDate = firstWeekStartDate,
    totalWeeks = totalWeeks,
    defaultReminderLeadMinutes = defaultReminderLeadMinutes,
)
