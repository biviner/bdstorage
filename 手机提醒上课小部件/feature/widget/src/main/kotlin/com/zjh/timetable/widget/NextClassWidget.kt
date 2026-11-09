package com.zjh.timetable.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider as DayNightColorProvider
import java.time.LocalDateTime

/**
 * 2×2「下一节课」小组件（详细设计 §7.1）。
 * 状态徽标 + 课程名（≤2 行省略）+ 时间行（进行中「结束于 HH:mm」）+ 教室（单行省略）+ 倒计时行；
 * 数据现查 Repository，不缓存；点击有课跳课程详情、无课跳首页。
 */
class NextClassWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext
        val repo = WidgetGraph.repository(app)
        val now = LocalDateTime.now()
        val settings = repo.semester()
        val courses = if (settings == null) emptyList() else repo.coursesOnDay(now.dayOfWeek.value)
        val state = WidgetStateResolver.nextClass(courses, settings, now)
        provideContent {
            GlanceTheme {
                Content(state)
            }
        }
    }

    @Composable
    private fun Content(state: WidgetStateResolver.NextClassState) {
        // 整卡可点：有课 → 课程详情深链；空闲 → 首页（§7.1）
        val parameters = if (state is WidgetStateResolver.NextClassState.Active) {
            WidgetClickAction.parameters(state.courseId)
        } else {
            WidgetClickAction.parameters()
        }
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionRunCallback<WidgetClickAction>(parameters))
                .padding(10.dp),
        ) {
            when (state) {
                is WidgetStateResolver.NextClassState.Idle ->
                    Box(
                        modifier = GlanceModifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.text,
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                        )
                    }

                is WidgetStateResolver.NextClassState.Active -> Column(
                    modifier = GlanceModifier.fillMaxSize(),
                ) {
                    Badge(if (state.ongoing) "进行中" else "未开始")
                    Spacer(modifier = GlanceModifier.padding(2.dp))
                    Text(
                        text = state.name,
                        maxLines = 2,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.padding(1.dp))
                    Text(
                        text = state.timeText,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                    )
                    state.location?.let {
                        Text(
                            text = it,
                            maxLines = 1,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 12.sp,
                            ),
                        )
                    }
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = state.countdown,
                        style = TextStyle(
                            color = DayNightColorProvider(day = Color(0xFF2A75BB), night = Color(0xFFFFCB05)),
                            fontSize = 11.sp,
                        ),
                    )
                }
            }
        }
    }

    @Composable
    private fun Badge(text: String) {
        Box(
            modifier = GlanceModifier
                .background(
                    DayNightColorProvider(
                        day = Color(0x14000000),
                        night = Color(0x14FFFFFF),
                    ),
                )
                .cornerRadius(6.dp)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(
                text = text,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

/** 清单声明入口（AndroidManifest 引用）。 */
class NextClassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextClassWidget()
}
