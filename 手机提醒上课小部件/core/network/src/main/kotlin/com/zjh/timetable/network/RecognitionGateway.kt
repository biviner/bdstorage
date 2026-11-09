package com.zjh.timetable.network

import com.zjh.timetable.domain.model.PeriodTime

/**
 * 识别网关门面（看板 B3-3）：识别器 + §5.2 校验规则编排，导入/确认页（B3-2）唯一入口。
 * 失败统一为 RecognitionFailed，message 可直接展示。
 */
class RecognitionGateway(private val recognizer: ScheduleRecognizer) {

    suspend fun recognize(
        imageJpeg: ByteArray,
        timetable: List<PeriodTime> = emptyList(),
    ): Result<RecognitionOutcome> =
        recognizer.recognize(imageJpeg).map { RecognitionValidator.validate(it, timetable) }
}
