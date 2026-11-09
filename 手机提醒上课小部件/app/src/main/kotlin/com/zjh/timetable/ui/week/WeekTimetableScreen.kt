package com.zjh.timetable.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zjh.timetable.domain.calc.ConflictDetector
import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.PeriodTime
import com.zjh.timetable.domain.model.TimetableMode
import com.zjh.timetable.ui.common.CoursePalette
import com.zjh.timetable.ui.data.ServiceLocator
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// 周课表页（详细设计 §8.3）：网格 + 周切换 + 冲突角标 + 空态/假期态。
// 节次模式按 PeriodTime 渲染行，时刻模式按 1h 刻度；课程块按当日分钟数绝对定位。

/** 1 分钟对应的高度，固定比例尺（0.6dp/分钟：一节大课 100 分钟 = 60dp）。 */
private val DP_PER_MINUTE = 0.6.dp

private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 网格行：节次模式取自作息表；时刻模式合成 8:00-21:00 每小时一行。 */
private data class GridRow(val label: String, val startMinute: Int, val endMinute: Int)

private fun gridRows(mode: TimetableMode, timetable: List<PeriodTime>): List<GridRow> = when (mode) {
    TimetableMode.PERIOD -> timetable.map { row ->
        GridRow(
            label = "第${row.periodIndex}节\n${row.startTime.format(HH_MM)}",
            startMinute = row.startTime.hour * 60 + row.startTime.minute,
            endMinute = row.endTime.hour * 60 + row.endTime.minute,
        )
    }
    // 时刻模式：8:00-22:00 每小时一行（详细设计 §8.3）
    TimetableMode.EXACT_TIME -> (8..21).map { hour ->
        GridRow("$hour:00", hour * 60, hour * 60 + 60)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekTimetableScreen(
    initialWeek: Int,
    onBack: () -> Unit,
    onEditCourse: (Long) -> Unit,
    onAddCourse: () -> Unit,
    viewModel: WeekTimetableViewModel = viewModel(key = "weekTimetable") {
        WeekTimetableViewModel(ServiceLocator.repository, initialWeek)
    },
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    WeekTitle(
                        state = state,
                        onWeekSelected = viewModel::jumpToWeek,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::previousWeek) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一周")
                    }
                    IconButton(onClick = viewModel::nextWeek) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一周")
                    }
                    if (!state.isCurrentWeek && state.inSemester) {
                        AssistChip(
                            onClick = viewModel::backToCurrentWeek,
                            label = { Text("回到本周") },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCourse,
                containerColor = Color(0xFFFFCB05),          // 皮卡黄
                contentColor = Color(0xFF1A237E),            // 深蓝加号，高对比
                elevation = FloatingActionButtonDefaults.elevation(8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加课程")
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }

            state.courses.isEmpty() -> EmptyContent(
                title = "暂无课程",
                hint = "点击右下角按钮手动添加课程",
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                if (!state.inSemester) {
                    Text(
                        "假期中",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(vertical = 8.dp),
                    )
                }
                TimetableGrid(
                    state = state,
                    modifier = Modifier.weight(1f),
                    onCourseClick = onEditCourse,
                )
            }
        }
    }
}

/** 顶栏周次标题：`第 N 周 ▾` 下拉跳转 + 本周角标。 */
@Composable
private fun WeekTitle(
    state: WeekTimetableViewModel.UiState,
    onWeekSelected: (Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { menuOpen = true },
        ) {
            Text(
                text = if (state.inSemester) "第 ${state.week} 周" else "假期中",
                style = MaterialTheme.typography.titleLarge,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = "选择周次")
            if (state.isCurrentWeek) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        "本周",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            for (week in WeekTimetableViewModel.weekOptions(state.semester)) {
                DropdownMenuItem(
                    text = { Text("第 $week 周") },
                    onClick = {
                        menuOpen = false
                        onWeekSelected(week)
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyContent(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimetableGrid(
    state: WeekTimetableViewModel.UiState,
    modifier: Modifier = Modifier,
    onCourseClick: (Long) -> Unit,
) {
    val semester = state.semester ?: return
    val rows = gridRows(semester.timetableMode, state.timetable)
    if (rows.isEmpty()) {
        EmptyContent(
            title = "作息表为空",
            hint = "请到「设置 → 作息时间表」补全节次时间",
            modifier = modifier,
        )
        return
    }

    val conflicts = remember(state.weekCourses) {
        ConflictDetector.conflictsOf(state.weekCourses)
            .flatMap { (a, b) -> listOf(a, b) }
            .toSet()
    }

    val dayStart = rows.minOf { it.startMinute }
    val dayEnd = rows.maxOf { it.endMinute }
    val gridHeight = DP_PER_MINUTE * (dayEnd - dayStart)
    val today = LocalDate.now().dayOfWeek.value
    val highlightToday = state.isCurrentWeek

    Column(modifier.fillMaxWidth()) {
        // 表头：角格 + 周一~周日，今日列头高亮（详细设计 §8.3）
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(48.dp).background(Color.Black.copy(alpha = 0.55f)))
            for (day in 1..7) {
                val isToday = highlightToday && day == today
                Text(
                    "周$day",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isToday) Color(0xFF1A237E) else Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isToday) Color(0xFFFFCB05)
                            else Color.Black.copy(alpha = 0.55f),
                        ),
                )
            }
        }

        Row(Modifier.fillMaxWidth().height(gridHeight)) {
            // 左列时间轴
            Box(Modifier.width(48.dp).height(gridHeight).clipToBounds().background(Color.Black.copy(alpha = 0.35f))) {
                for (row in rows) {
                    Text(
                        row.label,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .width(48.dp)
                            .background(Color.Black.copy(alpha = 0.60f))
                            .offset(y = DP_PER_MINUTE * (row.startMinute - dayStart)),
                    )
                }
            }
            // 周一~周日列
            for (day in 1..7) {
                val isToday = highlightToday && day == today
                Box(
                    Modifier
                        .weight(1f)
                        .height(gridHeight)
                        .clipToBounds()
                        .border(0.5.dp, Color.White.copy(alpha = 0.45f))
                        .background(
                            if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            else Color.Transparent,
                        ),
                ) {
                    for (row in rows) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .offset(y = DP_PER_MINUTE * (row.startMinute - dayStart))
                                .background(Color.White.copy(alpha = 0.40f)),
                        )
                    }
                    for (course in state.weekCourses.filter { it.dayOfWeek == day }) {
                        CourseBlock(
                            course = course,
                            conflict = course.id in conflicts,
                            offsetY = DP_PER_MINUTE * (course.startMinute - dayStart).coerceAtLeast(0),
                            height = DP_PER_MINUTE * (course.endMinute - course.startMinute).coerceAtLeast(20),
                            onClick = { onCourseClick(course.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseBlock(
    course: Course,
    conflict: Boolean,
    offsetY: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    val color = CoursePalette.colorOf(course.id, course.colorArgb)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 1.dp)
            .offset(y = offsetY)
            .height(height)
            // B6-UI：保留课程色，统一卡牌描边圆角
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .border(1.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(2.dp)) {
            Text(
                course.name,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color.Black,
            )
            course.location?.let {
                Text(
                    it,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.Black.copy(alpha = 0.7f),
                )
            }
        }
        if (conflict) {
            Text(
                "⚠",
                fontSize = 10.sp,
                color = Color.Red,
                modifier = Modifier.align(Alignment.TopEnd).padding(1.dp),
            )
        }
    }
}
