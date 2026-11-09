package com.zjh.timetable.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * 识别草稿（详细设计 §3.1 recognition_draft 表；概要设计 §7.1）。
 * 确认导入后即可删除；coursesJson 由 core/network 负责编解码。
 */
@Entity(tableName = "recognition_draft")
data class RecognitionDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime,
    @ColumnInfo(name = "raw_image_uri") val rawImageUri: String? = null,
    @ColumnInfo(name = "courses_json") val coursesJson: String,
)
