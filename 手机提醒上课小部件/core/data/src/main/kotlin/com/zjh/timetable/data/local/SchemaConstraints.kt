package com.zjh.timetable.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * schema 层 CHECK 约束兜底（详细设计 §3.1 DDL 语义）。
 * Room 不提供 @Check 注解，此处以 SQLite 触发器实现同等约束：
 * course.day_of_week/start_minute/end_minute、semester_setting.id/timetable_mode
 * 的非法插入与更新被 RAISE(ABORT) 拒绝，DAO 层抛 SQLiteConstraintException。
 * 触发器幂等创建（IF NOT EXISTS），每次打开数据库时确保存在。
 */
object SchemaConstraints {

    val callback: RoomDatabase.Callback = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            TRIGGERS.forEach(db::execSQL)
        }
    }

    private val TRIGGERS = listOf(
        """
        CREATE TRIGGER IF NOT EXISTS trg_course_insert_check
        BEFORE INSERT ON course
        WHEN NEW.day_of_week NOT BETWEEN 1 AND 7
            OR NEW.start_minute NOT BETWEEN 0 AND 1439
            OR NEW.end_minute NOT BETWEEN 1 AND 1440
        BEGIN
            SELECT RAISE(ABORT, 'course check constraint violated');
        END
        """,
        """
        CREATE TRIGGER IF NOT EXISTS trg_course_update_check
        BEFORE UPDATE ON course
        WHEN NEW.day_of_week NOT BETWEEN 1 AND 7
            OR NEW.start_minute NOT BETWEEN 0 AND 1439
            OR NEW.end_minute NOT BETWEEN 1 AND 1440
        BEGIN
            SELECT RAISE(ABORT, 'course check constraint violated');
        END
        """,
        """
        CREATE TRIGGER IF NOT EXISTS trg_semester_insert_check
        BEFORE INSERT ON semester_setting
        WHEN NEW.id <> 1 OR NEW.timetable_mode NOT IN ('PERIOD', 'EXACT_TIME')
        BEGIN
            SELECT RAISE(ABORT, 'semester_setting check constraint violated');
        END
        """,
        """
        CREATE TRIGGER IF NOT EXISTS trg_semester_update_check
        BEFORE UPDATE ON semester_setting
        WHEN NEW.id <> 1 OR NEW.timetable_mode NOT IN ('PERIOD', 'EXACT_TIME')
        BEGIN
            SELECT RAISE(ABORT, 'semester_setting check constraint violated');
        END
        """,
    )
}
