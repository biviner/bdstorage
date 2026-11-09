package com.zjh.timetable.domain.calc

import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.CourseStatus

/**
 * 课程当日状态判定（详细设计 §4.3）：以当日分钟数比较。
 * now == start 与 now == end 均算进行中（含下课铃当分钟），now == end + 1 算已结束。
 */
object CourseStatusResolver {

    fun resolve(startMinute: Int, endMinute: Int, nowMinute: Int): CourseStatus = when {
        nowMinute < startMinute -> CourseStatus.NotStarted
        nowMinute <= endMinute -> CourseStatus.Ongoing
        else -> CourseStatus.Ended
    }

    fun statusOf(course: Course, nowMinuteOfDay: Int): CourseStatus =
        resolve(course.startMinute, course.endMinute, nowMinuteOfDay)
}
