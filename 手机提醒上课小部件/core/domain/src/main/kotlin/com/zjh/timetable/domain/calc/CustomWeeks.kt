package com.zjh.timetable.domain.calc

/**
 * 自定义周次编解码（详细设计 §3.2）。
 * 编码：升序去重，连续 ≥2 合并为区间，如 "1-8,10,12-16"。
 * 解码：容忍空段、逆序段、重复；非法 token 丢弃并附 warning 列表。
 * 超界周次（> totalWeeks）保留，由 WeekCalculator / 上层过滤。
 */
object CustomWeeks {

    /** [weeks] → "1-8,10,12-16"；空集合 → 空串。 */
    fun encode(weeks: Collection<Int>): String {
        if (weeks.isEmpty()) return ""
        val sorted = weeks.toSortedSet()
        val parts = mutableListOf<String>()
        var start = sorted.first()
        var prev = start
        for (week in sorted.drop(1)) {
            if (week == prev + 1) {
                prev = week
                continue
            }
            parts += renderRun(start, prev)
            start = week
            prev = week
        }
        parts += renderRun(start, prev)
        return parts.joinToString(",")
    }

    /**
     * "1-8,10,12-16" → (周次集合, warning 列表)。
     * null/空串 → 空集合；空段静默跳过；非法 token（非数字、如 "1-2-3"）丢弃并记 warning；
     * 逆序段（"16-1"）归一化为 1..16；重复周次由集合去重。
     */
    fun decode(raw: String?): Pair<Set<Int>, List<String>> {
        if (raw.isNullOrBlank()) return emptySet<Int>() to emptyList<String>()
        val weeks = sortedSetOf<Int>()
        val warnings = mutableListOf<String>()
        for (token in raw.split(',')) {
            val piece = token.trim()
            if (piece.isEmpty()) continue // 空段容忍
            val bounds = piece.split('-', limit = 2)
            if (bounds.size == 2) {
                val a = bounds[0].trim().toIntOrNull()
                val b = bounds[1].trim().toIntOrNull()
                if (a == null || b == null) {
                    warnings += piece
                    continue
                }
                for (week in minOf(a, b)..maxOf(a, b)) weeks.add(week) // 逆序归一化
            } else {
                val single = piece.toIntOrNull()
                if (single == null) warnings += piece else weeks.add(single)
            }
        }
        return weeks.toSortedSet() to warnings
    }

    private fun renderRun(start: Int, end: Int): String =
        if (start == end) "$start" else "$start-$end"
}
