package com.zjh.timetable.network

import com.zjh.timetable.domain.calc.PeriodMapper
import com.zjh.timetable.domain.model.PeriodTime
import com.zjh.timetable.domain.model.RecognizedCourse

/**
 * §5.2 校验规则 2-4（规则 1 JSON 解析在 OpenAiVisionRecognizer 内）：
 * 2. confidence < 0.3 或 name 缺失 → 丢弃并计数；confidence < 0.6 或关键字段缺失 → lowConfidence 标黄。
 * 3. periodStart 非空时经 PeriodMapper 映射作息，失败 → 该行标黄提示补作息。
 * 4. weeksRaw 与 weeksParsed 单双周奇偶冲突 → 整行标黄，保留两者供用户对照修正。
 */
object RecognitionValidator {

    fun validate(courses: List<RecognizedCourse>, timetable: List<PeriodTime>): RecognitionOutcome {
        val rows = mutableListOf<RecognizedRow>()
        var droppedCount = 0
        for (course in courses) {
            // 规则 2：丢弃并计数，完成后由确认页提示「N 条疑似课程信息不完整已忽略，可手动添加」
            if (course.name == null || course.confidence < 0.3) {
                droppedCount++
                continue
            }
            val lowConfidence =
                course.confidence < 0.6 || course.dayOfWeek == null || timeMissing(course)
            // 规则 3：节次非空才映射；映射失败标黄，时间以 startTime/endTime 兜底
            val periodStart = course.periodStart
            val periodMappingFailed = periodStart != null &&
                PeriodMapper.periodsToMinutes(
                    periodStart,
                    course.periodEnd ?: periodStart,
                    timetable,
                ) == null
            // 规则 4：原文含「单」而 parsed 含偶数（或反之）→ 冲突
            val weeksConflict = weeksParityConflict(course.weeksRaw, course.weeksParsed)
            rows += RecognizedRow(course, lowConfidence, periodMappingFailed, weeksConflict)
        }
        return RecognitionOutcome(rows.toList(), droppedCount)
    }

    private fun timeMissing(course: RecognizedCourse): Boolean =
        course.periodStart == null && (course.startTime == null || course.endTime == null)

    private fun weeksParityConflict(raw: String?, parsed: List<Int>): Boolean {
        if (raw == null || parsed.isEmpty()) return false
        // 「单双周」同时出现（如两栏合并原文）时不判定
        val expectOdd = raw.contains("单") && !raw.contains("双")
        val expectEven = raw.contains("双") && !raw.contains("单")
        val hasOdd = parsed.any { it % 2 == 1 }
        val hasEven = parsed.any { it % 2 == 0 }
        return (expectOdd && hasEven) || (expectEven && hasOdd)
    }
}
