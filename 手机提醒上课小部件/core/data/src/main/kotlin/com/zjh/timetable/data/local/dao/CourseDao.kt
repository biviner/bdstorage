package com.zjh.timetable.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.zjh.timetable.data.local.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

/** 课程 DAO（详细设计 §3.3）。UI 不得直接持有，一律经 TimetableRepository。 */
@Dao
interface CourseDao {

    @Query("SELECT * FROM course ORDER BY day_of_week, start_minute")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM course WHERE day_of_week = :day ORDER BY start_minute")
    suspend fun byDay(day: Int): List<CourseEntity>

    @Query("SELECT * FROM course WHERE id = :id")
    suspend fun byId(id: Long): CourseEntity?

    /** 单条插入（新课程 id 传 0 由自增分配） */
    @Insert
    suspend fun insert(course: CourseEntity): Long

    /** 批量插入；Room 保证单方法单事务——整批失败即整批回滚（概要设计 §10 确认导入契约） */
    @Insert
    suspend fun insertAll(courses: List<CourseEntity>): List<Long>

    @Update
    suspend fun update(course: CourseEntity)

    @Delete
    suspend fun delete(course: CourseEntity)

    @Query("UPDATE course SET paused_dates = :dates WHERE id = :id")
    suspend fun setPaused(id: Long, dates: String?)
}
