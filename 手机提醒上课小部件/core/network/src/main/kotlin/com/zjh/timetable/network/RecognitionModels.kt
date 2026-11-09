package com.zjh.timetable.network

import com.zjh.timetable.domain.model.RecognizedCourse

/** 识别失败统一异常（详细设计 §5.2 规则 1），message 即确认页可直接展示的文案。 */
class RecognitionFailed(message: String) : Exception(message)

/**
 * 单条保留课程 + 标黄标记（详细设计 §5.2/§5.5）。
 * 标黄不丢弃，保留字段供确认页对照修正。
 */
data class RecognizedRow(
    val course: RecognizedCourse,
    /** confidence < 0.6 或关键字段（name/dayOfWeek/时间）缺失 */
    val lowConfidence: Boolean,
    /** 节次经作息表映射失败，需补作息 */
    val periodMappingFailed: Boolean,
    /** weeksRaw 与 weeksParsed 奇偶冲突，需对照修正 */
    val weeksConflict: Boolean,
) {
    val needsReview: Boolean get() = lowConfidence || periodMappingFailed || weeksConflict
}

/**
 * 识别总结果：保留行 + 丢弃计数。
 * droppedCount 对应完成后提示「N 条疑似课程信息不完整已忽略，可手动添加」。
 */
data class RecognitionOutcome(
    val rows: List<RecognizedRow>,
    val droppedCount: Int,
)
