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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider as DayNightColorProvider
import java.time.LocalDateTime

/**
 * 4×2「今日课程」小组件（详细设计 §7.2）。
 * 头部「M月d日 周X · 第N周」（学期外「假期中」）+ 最多 5 条今日课程
 * （色块 + 名称 + 时间段 + 教室；进行中加粗+徽标，已结束 50% 透明，
 * 第一个未开始的左侧高亮竖条）；无课空态居中文案；点击 → 首页。
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext
        val repo = WidgetGraph.repository(app)
        val now = LocalDateTime.now()
        val settings = repo.semester()
        val courses = if (settings == null) emptyList() else repo.coursesOnDay(now.dayOfWeek.value)
        val state = WidgetStateResolver.today(courses, settings, now)
        provideContent {
            GlanceTheme {
                Content(state)
            }
        }
    }

    @Composable
    private fun Content(state: WidgetStateResolver.TodayState) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionRunCallback<WidgetClickAction>())
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = state.header,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.padding(3.dp))
            if (state.entries.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.emptyText.orEmpty(),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(state.entries.size) { index -> EntryRow(state.entries[index]) }
                }
            }
        }
    }

    @Composable
    private fun EntryRow(entry: WidgetStateResolver.TodayEntry) {
        val nameColor = if (entry.ended) faded() else GlanceTheme.colors.onSurface
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 下一节：左侧高亮竖条（§7.2）
            if (entry.isNext) {
                Box(
                    modifier = GlanceModifier
                        .width(3.dp)
                        .height(14.dp)
                        .background(ColorProvider(Color(entry.colorArgb)))
                        .cornerRadius(1.dp),
                ) { }
                Spacer(modifier = GlanceModifier.padding(2.dp))
            }
            // 课程色块
            Box(
                modifier = GlanceModifier
                    .width(4.dp)
                    .height(12.dp)
                    .background(ColorProvider(Color(entry.colorArgb)))
                    .cornerRadius(1.dp),
            ) { }
            Spacer(modifier = GlanceModifier.padding(3.dp))
            Text(
                text = entry.name,
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = nameColor,
                    fontSize = 12.sp,
                    fontWeight = if (entry.ongoing) FontWeight.Bold else FontWeight.Normal,
                ),
            )
            if (entry.ongoing) {
                Text(
                    text = "进行中",
                    style = TextStyle(
                        color = ColorProvider(Color(entry.colorArgb)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(modifier = GlanceModifier.padding(2.dp))
            }
            Text(
                text = entry.timeText,
                style = TextStyle(color = nameColor, fontSize = 11.sp),
            )
            entry.location?.let {
                Spacer(modifier = GlanceModifier.padding(2.dp))
                Text(
                    text = it,
                    maxLines = 1,
                    style = TextStyle(color = nameColor, fontSize = 11.sp),
                )
            }
        }
    }

    /** 已结束 50% 透明度（§7.2）。 */
    private fun faded() = DayNightColorProvider(
        day = Color(red = 0, green = 0, blue = 0, alpha = 0x80),
        night = Color(red = 0xFF, green = 0xFF, blue = 0xFF, alpha = 0x80),
    )
}

/** 清单声明入口（AndroidManifest 引用）。 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
