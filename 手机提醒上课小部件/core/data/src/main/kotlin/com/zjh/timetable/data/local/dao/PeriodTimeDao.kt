package com.zjh.timetable.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zjh.timetable.data.local.entity.PeriodTimeEntity
import kotlinx.coroutines.flow.Flow

/** 作息表 DAO（详细设计 §3.3：批量 upsert）。 */
@Dao
interface PeriodTimeDao {

    @Query("SELECT * FROM period_time ORDER BY period_index")
    fun observeAll(): Flow<List<PeriodTimeEntity>>

    @Query("SELECT * FROM period_time ORDER BY period_index")
    suspend fun getAll(): List<PeriodTimeEntity>

    @Upsert
    suspend fun upsertAll(rows: List<PeriodTimeEntity>)

    /** 全量替换（作息编辑页保存 / 恢复默认）时先清空再写入 */
    @Query("DELETE FROM period_time")
    suspend fun clear()
}
