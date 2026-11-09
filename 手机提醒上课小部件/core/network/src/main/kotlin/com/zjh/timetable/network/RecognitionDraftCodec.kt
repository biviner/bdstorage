package com.zjh.timetable.network

import com.zjh.timetable.domain.model.RecognizedCourse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * recognition_draft.coursesJson 编解码（core/data/repository/RecognitionDraft.kt 注明的契约：
 * data 层只透传存储，编解码由本模块负责）。保留标黄标记与丢弃计数，草稿回读后确认页可原样复现。
 */
object RecognitionDraftCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(outcome: RecognitionOutcome): String =
        json.encodeToString(
            DraftDto.serializer(),
            DraftDto(
                droppedCount = outcome.droppedCount,
                rows = outcome.rows.map { row ->
                    val c = row.course
                    DraftCourseDto(
                        name = c.name,
                        dayOfWeek = c.dayOfWeek,
                        periodStart = c.periodStart,
                        periodEnd = c.periodEnd,
                        startTime = c.startTime,
                        endTime = c.endTime,
                        weeksRaw = c.weeksRaw,
                        weeksParsed = c.weeksParsed,
                        location = c.location,
                        teacher = c.teacher,
                        confidence = c.confidence,
                        lowConfidence = row.lowConfidence,
                        periodMappingFailed = row.periodMappingFailed,
                        weeksConflict = row.weeksConflict,
                    )
                },
            ),
        )

    fun decode(coursesJson: String): RecognitionOutcome {
        val dto = json.decodeFromString(DraftDto.serializer(), coursesJson)
        return RecognitionOutcome(
            rows = dto.rows.map { r ->
                RecognizedRow(
                    course = RecognizedCourse(
                        name = r.name,
                        dayOfWeek = r.dayOfWeek,
                        periodStart = r.periodStart,
                        periodEnd = r.periodEnd,
                        startTime = r.startTime,
                        endTime = r.endTime,
                        weeksRaw = r.weeksRaw,
                        weeksParsed = r.weeksParsed,
                        location = r.location,
                        teacher = r.teacher,
                        confidence = r.confidence,
                    ),
                    lowConfidence = r.lowConfidence,
                    periodMappingFailed = r.periodMappingFailed,
                    weeksConflict = r.weeksConflict,
                )
            },
            droppedCount = dto.droppedCount,
        )
    }
}

@Serializable
internal data class DraftDto(val droppedCount: Int, val rows: List<DraftCourseDto>)

@Serializable
internal data class DraftCourseDto(
    val name: String?,
    val dayOfWeek: Int?,
    val periodStart: Int?,
    val periodEnd: Int?,
    val startTime: String?,
    val endTime: String?,
    val weeksRaw: String?,
    val weeksParsed: List<Int>,
    val location: String?,
    val teacher: String?,
    val confidence: Double,
    val lowConfidence: Boolean = false,
    val periodMappingFailed: Boolean = false,
    val weeksConflict: Boolean = false,
)
