package com.zjh.timetable.domain.model

/**
 * 识别服务返回的单条课程（详细设计 §5.1）。
 * 字段可能缺失（模型看不清填 null，禁止编造）；时间字段为 "HH:mm"。
 */
data class RecognizedCourse(
    val name: String?,
    /** 1=周一 .. 7=周日 */
    val dayOfWeek: Int?,
    val periodStart: Int?,
    val periodEnd: Int?,
    /** 节次缺失时模型直接给时刻，"HH:mm" */
    val startTime: String?,
    val endTime: String?,
    /** 周次原文（如 "1-8,10,12-16" 或 "单周"），供用户对照修正 */
    val weeksRaw: String?,
    /** 解析后的周次集合 */
    val weeksParsed: List<Int>,
    val location: String?,
    val teacher: String?,
    /** 0..1，低于阈值由调用方标黄或丢弃 */
    val confidence: Double,
)
