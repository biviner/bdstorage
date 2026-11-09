package com.zjh.timetable.data.local

import com.zjh.timetable.domain.model.PeriodTime
import java.time.LocalTime

/**
 * 预置默认作息（详细设计 §3.1：首次启动 seed，由 app 模块装配时写入）。
 * 连续节次合并为一行：行序号 = 首节。
 */
object DefaultTimetable {

    val seed: List<PeriodTime> = listOf(
        PeriodTime(1, LocalTime.of(8, 0), LocalTime.of(9, 40)), // 1-2 节
        PeriodTime(3, LocalTime.of(10, 0), LocalTime.of(11, 40)), // 3-4 节
        PeriodTime(5, LocalTime.of(14, 0), LocalTime.of(15, 40)), // 5-6 节
        PeriodTime(7, LocalTime.of(16, 0), LocalTime.of(17, 40)), // 7-8 节
        PeriodTime(9, LocalTime.of(19, 0), LocalTime.of(20, 40)), // 9-10 节
    )
}
