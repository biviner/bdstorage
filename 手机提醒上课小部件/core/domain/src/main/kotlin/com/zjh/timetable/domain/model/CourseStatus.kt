package com.zjh.timetable.domain.model

/**
 * 课程当日状态（详细设计 §4.3）：以当日分钟数比较，
 * now == start 与 now == end 均算进行中，now == end + 1 算已结束。
 */
enum class CourseStatus {
    NotStarted,
    Ongoing,
    Ended,
}
