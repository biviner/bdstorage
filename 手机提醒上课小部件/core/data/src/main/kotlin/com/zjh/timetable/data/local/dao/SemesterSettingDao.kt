package com.zjh.timetable.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zjh.timetable.data.local.entity.SemesterSettingEntity
import kotlinx.coroutines.flow.Flow

/** 学期设置 DAO（详细设计 §3.3：单行 upsert）。 */
@Dao
interface SemesterSettingDao {

    @Query("SELECT * FROM semester_setting WHERE id = 1")
    fun observe(): Flow<SemesterSettingEntity?>

    @Query("SELECT * FROM semester_setting WHERE id = 1")
    suspend fun get(): SemesterSettingEntity?

    @Upsert
    suspend fun upsert(setting: SemesterSettingEntity)
}
