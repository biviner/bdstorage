package com.zjh.timetable.network

import com.zjh.timetable.domain.model.RecognizedCourse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** coursesJson 编解码往返（core/data 只透传存储，编解码契约在本模块）。 */
class RecognitionDraftCodecTest {

    @Test
    fun `encode-decode 往返保真`() {
        val outcome = RecognitionOutcome(
            rows = listOf(
                RecognizedRow(
                    course = RecognizedCourse(
                        name = "高等数学", dayOfWeek = 3, periodStart = 1, periodEnd = 2,
                        startTime = null, endTime = null, weeksRaw = "单周",
                        weeksParsed = listOf(1, 3, 5), location = "教1-101",
                        teacher = "张三", confidence = 0.55,
                    ),
                    lowConfidence = true, periodMappingFailed = true, weeksConflict = false,
                )
            ),
            droppedCount = 2,
        )

        val restored = RecognitionDraftCodec.decode(RecognitionDraftCodec.encode(outcome))

        assertEquals(outcome, restored)
    }
}
