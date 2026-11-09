package com.zjh.timetable.domain.calc

import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.PeriodTime

/**
 * 节次 → 当日分钟数映射（详细设计 §4.5）。
 * 任一节次在作息表中缺失（如无第 11 节）或区间非法 → null，
 * 调用方提示「节次超出作息表，请到设置页补全作息时间」。
 */
object PeriodMapper {

    /** 返回 (startMinute, endMinute)，均为当日分钟数（hour*60+minute）。 */
    fun periodsToMinutes(periodStart: Int, periodEnd: Int, timetable: List<PeriodTime>): Pair<Int, Int>? {
        if (periodStart < 1 || periodEnd < periodStart) return null
        val byIndex = timetable.associateBy { it.periodIndex }
        val start = byIndex[periodStart] ?: return null
        val end = byIndex[periodEnd] ?: return null
        return start.startTime.hour * 60 + start.startTime.minute to
            end.endTime.hour * 60 + end.endTime.minute
    }
}
