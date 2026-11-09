package com.zjh.timetable.ui.common

import androidx.compose.ui.graphics.Color

/**
 * 课程块 8 色调色板（详细设计 §8.4：8 色 + 自动）。
 * 自动分配 = 按课程 id 轮转，保证同 id 颜色稳定。
 */
object CoursePalette {

    val colors: List<Color> = listOf(
        Color(0xFF90CAF9), // 蓝
        Color(0xFFA5D6A7), // 绿
        Color(0xFFFFCC80), // 橙
        Color(0xFFB39DDB), // 紫
        Color(0xFF80CBC4), // 青
        Color(0xFFF48FB1), // 粉
        Color(0xFFFFAB91), // 珊瑚
        Color(0xFFE6EE9C), // 黄绿
    )

    /** 课程块颜色：显式 colorArgb 优先，否则按 id 轮转。 */
    fun colorOf(courseId: Long, colorArgb: Int?): Color =
        colorArgb?.let { Color(it) } ?: colors[(courseId % colors.size).toInt()]
}
