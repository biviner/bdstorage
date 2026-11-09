package com.zjh.timetable.widget

import androidx.compose.ui.graphics.Color

/**
 * 小组件课程色（详细设计 §7.2）：8 色调色板与 app 的 CoursePalette 同源，
 * 缺省取法按本卡规格用 name hash（非 id 轮转），保证同名课跨添加颜色一致。
 */
object WidgetCoursePalette {

    private val colors: List<Int> = listOf(
        0xFF90CAF9.toInt(), // 蓝
        0xFFA5D6A7.toInt(), // 绿
        0xFFFFCC80.toInt(), // 橙
        0xFFB39DDB.toInt(), // 紫
        0xFF80CBC4.toInt(), // 青
        0xFFF48FB1.toInt(), // 粉
        0xFFFFAB91.toInt(), // 珊瑚
        0xFFE6EE9C.toInt(), // 黄绿
    )

    fun colorFor(name: String): Color = Color(argbFor(name))

    fun argbFor(name: String): Int = colors[indexFor(name)]

    fun indexFor(name: String): Int =
        ((name.hashCode() % colors.size) + colors.size) % colors.size
}
