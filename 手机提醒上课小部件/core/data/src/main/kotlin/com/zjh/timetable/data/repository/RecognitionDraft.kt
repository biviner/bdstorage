package com.zjh.timetable.data.repository

import java.time.LocalDateTime

/**
 * 识别草稿（详细设计 §3.1 recognition_draft 表；概要设计 §7.1）。
 * coursesJson 的编解码由 core/network 负责，data 层只透传存储。
 */
data class RecognitionDraft(
    val id: Long,
    val createdAt: LocalDateTime,
    val rawImageUri: String?,
    val coursesJson: String,
)
