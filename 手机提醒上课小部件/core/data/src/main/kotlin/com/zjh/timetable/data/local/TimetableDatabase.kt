package com.zjh.timetable.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.zjh.timetable.data.local.dao.CourseDao
import com.zjh.timetable.data.local.dao.PeriodTimeDao
import com.zjh.timetable.data.local.dao.RecognitionDraftDao
import com.zjh.timetable.data.local.dao.SemesterSettingDao
import com.zjh.timetable.data.local.entity.CourseEntity
import com.zjh.timetable.data.local.entity.PeriodTimeEntity
import com.zjh.timetable.data.local.entity.RecognitionDraftEntity
import com.zjh.timetable.data.local.entity.SemesterSettingEntity

/**
 * 课表数据库 schema v1（详细设计 §3；概要设计 §7.3）。
 * schema JSON 导出至 core/data/schemas/ 并入 git；后续字段变更走 Migration（全局约束 §9.8）。
 */
@Database(
    entities = [
        SemesterSettingEntity::class,
        PeriodTimeEntity::class,
        CourseEntity::class,
        RecognitionDraftEntity::class,
    ],
    version = TimetableDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TimetableDatabase : RoomDatabase() {

    abstract fun semesterSettingDao(): SemesterSettingDao

    abstract fun periodTimeDao(): PeriodTimeDao

    abstract fun courseDao(): CourseDao

    abstract fun recognitionDraftDao(): RecognitionDraftDao

    companion object {
        const val SCHEMA_VERSION = 1
        const val DATABASE_NAME = "timetable.db"

        /** 生产装配入口：app 模块经此创建数据库，确保 schema 约束回调不被遗漏。 */
        fun build(context: Context): TimetableDatabase =
            Room.databaseBuilder(context, TimetableDatabase::class.java, DATABASE_NAME)
                .addCallback(SchemaConstraints.callback)
                .build()
    }
}
