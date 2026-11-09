package com.zjh.timetable.domain.calc

import com.zjh.timetable.domain.model.Course

/**
 * 时间冲突检测（详细设计 §4.6）：同 day_of_week 且区间重叠
 * （a.start < b.end && b.start < a.end），相接不算冲突。
 * 确认页与周课表页调用，UI 层高亮但不阻止保存。
 */
object ConflictDetector {

    /** 返回冲突课程 id 对（小 id 在前），按 (前, 后) 字典序，确定性输出。 */
    fun conflictsOf(courses: List<Course>): List<Pair<Long, Long>> {
        val sorted = courses.sortedBy { it.id }
        val result = mutableListOf<Pair<Long, Long>>()
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                val a = sorted[i]
                val b = sorted[j]
                if (a.dayOfWeek == b.dayOfWeek &&
                    a.startMinute < b.endMinute &&
                    b.startMinute < a.endMinute
                ) {
                    result += a.id to b.id
                }
            }
        }
        return result
    }
}
