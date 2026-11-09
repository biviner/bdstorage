package com.zjh.timetable.domain.model

/**
 * 周次规则（详细设计 §4.2）。周一=1、周日=7；week 为教学周序号（1 起）。
 */
enum class WeekPattern {
    ALL,
    ODD,
    EVEN,
    CUSTOM;

    /** 该课程是否在第 [week] 周上课；仅 CUSTOM 时使用 [custom] 周次集合。 */
    fun matches(week: Int, custom: Set<Int>): Boolean = when (this) {
        ALL -> true
        ODD -> week % 2 == 1
        EVEN -> week % 2 == 0
        CUSTOM -> week in custom
    }
}
