package com.zjh.timetable.network

import com.zjh.timetable.domain.model.RecognizedCourse

/**
 * 课表识别器契约（详细设计 §5.1）。
 * 实现只负责出网 + 把响应解析清洗成 RecognizedCourse 列表；
 * §5.2 校验规则（丢弃/标黄/计数）由 [RecognitionGateway] 编排 [RecognitionValidator] 完成。
 */
interface ScheduleRecognizer {
    suspend fun recognize(imageJpeg: ByteArray): Result<List<RecognizedCourse>>
}
