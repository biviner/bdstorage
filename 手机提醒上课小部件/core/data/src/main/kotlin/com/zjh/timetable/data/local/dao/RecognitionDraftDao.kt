package com.zjh.timetable.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zjh.timetable.data.local.entity.RecognitionDraftEntity

/** 识别草稿 DAO（B3 消费；写入与删除经 Repository 触发小组件/提醒钩子无必要，不走 ChangeNotifier）。 */
@Dao
interface RecognitionDraftDao {

    @Insert
    suspend fun insert(draft: RecognitionDraftEntity): Long

    @Query("SELECT * FROM recognition_draft WHERE id = :id")
    suspend fun byId(id: Long): RecognitionDraftEntity?

    @Query("DELETE FROM recognition_draft WHERE id = :id")
    suspend fun delete(id: Long)
}
