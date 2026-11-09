package com.zjh.timetable.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalTime

/**
 * 单行作息（详细设计 §3.1 period_time 表）。节次 1..15；连续节次同作息时合并为一行
 * （行以首节序号为主键，如 1=08:00-09:40 表示 1-2 节）。预置默认值见 [DefaultTimetable]。
 */
@Entity(tableName = "period_time")
data class PeriodTimeEntity(
    @PrimaryKey @ColumnInfo(name = "period_index") val periodIndex: Int,
    /** HH:mm（经 TypeConverter 以 TEXT 存储） */
    @ColumnInfo(name = "start_time") val startTime: LocalTime,
    @ColumnInfo(name = "end_time") val endTime: LocalTime,
)
