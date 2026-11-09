package com.zjh.timetable.domain.model

import java.time.LocalTime

/**
 * 单个节次的作息（详细设计 §3.1 period_time 表，节次 1..15）。
 * 连续节次同作息时在数据层合并为一行。
 */
data class PeriodTime(
    /** 节次序号，1 起 */
    val periodIndex: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
)
