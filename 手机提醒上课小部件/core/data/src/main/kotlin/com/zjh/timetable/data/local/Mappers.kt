package com.zjh.timetable.data.local

import com.zjh.timetable.data.local.entity.CourseEntity
import com.zjh.timetable.data.local.entity.PeriodTimeEntity
import com.zjh.timetable.data.local.entity.SemesterSettingEntity
import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.PeriodTime
import com.zjh.timetable.domain.model.SemesterSetting
import java.time.LocalDateTime

/** 实体 ↔ domain 模型映射（字段语义一一对应，起止分钟/枚举/时间均不经变换直接搬运）。 */

internal fun CourseEntity.toDomain(): Course = Course(
    id = id,
    name = name,
    dayOfWeek = dayOfWeek,
    startMinute = startMinute,
    endMinute = endMinute,
    location = location,
    teacher = teacher,
    colorArgb = colorArgb,
    note = note,
    weekPattern = weekPattern,
    customWeeks = customWeeks,
    reminderEnabled = reminderEnabled,
    reminderLeadMinutes = reminderLeadMinutes,
    pausedDates = pausedDates,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** [id] 传 0 由 Room 自增；时间戳由 Repository 统一盖戳后传入。 */
internal fun Course.toEntity(
    id: Long = this.id,
    createdAt: LocalDateTime = this.createdAt,
    updatedAt: LocalDateTime = this.updatedAt,
): CourseEntity = CourseEntity(
    id = id,
    name = name,
    dayOfWeek = dayOfWeek,
    startMinute = startMinute,
    endMinute = endMinute,
    location = location,
    teacher = teacher,
    colorArgb = colorArgb,
    note = note,
    weekPattern = weekPattern,
    customWeeks = customWeeks,
    reminderEnabled = reminderEnabled,
    reminderLeadMinutes = reminderLeadMinutes,
    pausedDates = pausedDates,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun SemesterSettingEntity.toDomain(): SemesterSetting = SemesterSetting(
    firstWeekStartDate = firstWeekStartDate,
    totalWeeks = totalWeeks,
    defaultReminderLeadMinutes = defaultReminderLeadMinutes,
    timetableMode = timetableMode,
)

internal fun SemesterSetting.toEntity(): SemesterSettingEntity = SemesterSettingEntity(
    id = 1,
    firstWeekStartDate = firstWeekStartDate,
    totalWeeks = totalWeeks,
    defaultReminderLeadMinutes = defaultReminderLeadMinutes,
    timetableMode = timetableMode,
)

internal fun PeriodTimeEntity.toDomain(): PeriodTime = PeriodTime(
    periodIndex = periodIndex,
    startTime = startTime,
    endTime = endTime,
)

internal fun PeriodTime.toEntity(): PeriodTimeEntity = PeriodTimeEntity(
    periodIndex = periodIndex,
    startTime = startTime,
    endTime = endTime,
)
