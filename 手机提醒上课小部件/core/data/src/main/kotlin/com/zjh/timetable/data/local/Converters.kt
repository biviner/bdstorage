package com.zjh.timetable.data.local

import androidx.room.TypeConverter
import com.zjh.timetable.domain.model.TimetableMode
import com.zjh.timetable.domain.model.WeekPattern
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Room TypeConverter（详细设计 §1.1：全部时间用 java.time，出入库处转换）。
 * 日期存 ISO yyyy-MM-dd、时刻存 HH:mm、时间戳存 ISO_LOCAL_DATE_TIME，枚举存 name。
 */
class Converters {

    @TypeConverter
    fun localDateTimeToText(value: LocalDateTime?): String? =
        value?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    @TypeConverter
    fun textToLocalDateTime(value: String?): LocalDateTime? =
        value?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }

    @TypeConverter
    fun localDateToText(value: LocalDate?): String? =
        value?.format(DateTimeFormatter.ISO_LOCAL_DATE)

    @TypeConverter
    fun textToLocalDate(value: String?): LocalDate? =
        value?.let { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }

    @TypeConverter
    fun localTimeToText(value: LocalTime?): String? =
        value?.format(HH_MM)

    @TypeConverter
    fun textToLocalTime(value: String?): LocalTime? =
        value?.let { LocalTime.parse(it, HH_MM) }

    @TypeConverter
    fun weekPatternToText(value: WeekPattern?): String? = value?.name

    @TypeConverter
    fun textToWeekPattern(value: String?): WeekPattern? =
        value?.let { WeekPattern.valueOf(it) }

    @TypeConverter
    fun timetableModeToText(value: TimetableMode?): String? = value?.name

    @TypeConverter
    fun textToTimetableMode(value: String?): TimetableMode? =
        value?.let { TimetableMode.valueOf(it) }

    companion object {
        private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
