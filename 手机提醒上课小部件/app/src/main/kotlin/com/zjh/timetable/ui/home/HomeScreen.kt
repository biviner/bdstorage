package com.zjh.timetable.ui.home

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zjh.timetable.domain.model.Course
import com.zjh.timetable.domain.model.CourseStatus
import com.zjh.timetable.domain.text.WidgetText
import com.zjh.timetable.ui.common.CoursePalette
import com.zjh.timetable.ui.data.ServiceLocator
import com.zjh.timetable.ui.home.HomeViewModel.TodayCourse
import com.zjh.timetable.ui.theme.PokemonColors
import java.time.format.DateTimeFormatter

// 首页（详细设计 §8.2）：下一节大卡片 + 今日时间线 + 空态 + 顶部条件提示条。

private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenWeek: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onEditCourse: (Long) -> Unit,
    onAddCourse: () -> Unit,
    onImport: () -> Unit,
    viewModel: HomeViewModel = viewModel(key = "home") {
        HomeViewModel(ServiceLocator.repository)
    },
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // B4-1 通知权限引导（详细设计 §6.2）：首次进入首页请求；拒绝后顶部提示条可再次发起
    var notifDenied by rememberSaveable { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (!granted) notifDenied = true }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !notifDenied &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // B4-FIX（评审阻塞项3）：精确闹钟未授权检测（§8.2）；用户从系统设置返回时
    // 随 ON_RESUME 复查，横幅即时消失
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    var exactAlarmDenied by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // API 31 以下无精确闹钟权限概念，视为正常
                exactAlarmDenied = Build.VERSION.SDK_INT >= 31 &&
                    alarmManager?.canScheduleExactAlarms() == false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("智能课表") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            // 顶部条件提示条：同一时刻只显示一条
            // （§8.2 优先级：通知权限 > 精确闹钟 > 学期未设置）
            when {
                notifDenied -> Banner(
                    text = "通知权限未开启，上课提醒将无法弹出",
                    actionLabel = "去开启",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )

                exactAlarmDenied -> Banner(
                    text = "精确闹钟权限未开启，提醒可能延迟或丢失",
                    actionLabel = "去开启",
                    onAction = {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:${context.packageName}"))
                        runCatching { context.startActivity(intent) }
                    },
                )

                state.semester == null -> Banner(
                    text = "尚未设置学期，请先到设置页配置学期起点",
                    actionLabel = "去设置",
                    onAction = onOpenSettings,
                )
            }

            when {
                state.courses.isEmpty() -> EmptyImportContent(
                    onImport = onImport,
                    modifier = Modifier.fillMaxSize(),
                )

                else -> NextClassCard(
                    model = state.nextClass,
                    // B6-UI：课程类型徽标取课程色（与周课表课程块同源）
                    badgeColor = (state.nextClass as? WidgetText.Model.NextClass)
                        ?.let { next -> state.todayCourses.find { it.course.id == next.courseId } }
                        ?.let { CoursePalette.colorOf(it.course.id, it.course.colorArgb) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }

            when {
                state.courses.isEmpty() -> Unit // 空态已整屏呈现
                state.todayCourses.isEmpty() -> EmptyTodayContent(
                    onOpenWeek = { onOpenWeek(state.currentWeek ?: 1) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )

                else -> TodayTimeline(
                    courses = state.todayCourses,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onCourseClick = onEditCourse,
                )
            }
        }
    }
}

/**
 * 「下一节课」大卡片（§8.2：文案复用 WidgetText，与 2×2 小组件同源）。
 * B6-UI：宝可梦卡牌视觉——16dp 圆角 + 黄圈蓝线双层描边 + 浅黄卡面；
 * 左上角课程类型徽标色块（取课程色），右侧时间信息。
 */
@Composable
private fun NextClassCard(
    model: WidgetText.Model,
    modifier: Modifier = Modifier,
    badgeColor: Color? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PokemonColors.Yellow)
            .padding(3.dp)
            .clip(RoundedCornerShape(13.dp))
            .border(2.dp, PokemonColors.Blue, RoundedCornerShape(13.dp))
            .background(PokemonColors.CardFace)
            .padding(16.dp),
    ) {
        when (model) {
            is WidgetText.Model.NextClass -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = badgeColor ?: MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            "下一节",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${model.startText} 开始",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    model.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                model.location?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    model.countdown,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            WidgetText.Model.AllEnded -> Text(
                WidgetText.TEXT_ALL_ENDED,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            WidgetText.Model.NoClassToday -> Text(
                WidgetText.TEXT_NO_CLASS_TODAY,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 今日时间线（§7.2 同款布局规则，条数不限）。 */
@Composable
private fun TodayTimeline(
    courses: List<TodayCourse>,
    modifier: Modifier = Modifier,
    onCourseClick: (Long) -> Unit,
) {
    LazyColumn(modifier = modifier) {
        items(courses, key = { it.course.id }) { item ->
            TodayCourseRow(item = item, onClick = { onCourseClick(item.course.id) })
        }
    }
}

@Composable
private fun TodayCourseRow(item: TodayCourse, onClick: () -> Unit) {
    val course = item.course
    val ended = item.status == CourseStatus.Ended
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (ended) 0.5f else 1f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // 下一节左侧高亮竖条（§7.2）
        Box(
            Modifier
                .width(3.dp)
                .height(36.dp)
                .background(
                    if (item.isNext) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(2.dp),
                ),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(12.dp)
                .background(CoursePalette.colorOf(course.id, course.colorArgb), RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    course.name,
                    fontSize = 15.sp,
                    fontWeight = if (item.status == CourseStatus.Ongoing) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.status == CourseStatus.Ongoing) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            "进行中",
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Text(
                buildString {
                    append(formatMinutes(course.startMinute))
                    append("–")
                    append(formatMinutes(course.endMinute))
                    course.location?.let { append(" · ").append(it) }
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 当日分钟数 → "HH:mm"（约定见 domain Course）。 */
private fun formatMinutes(minute: Int): String =
    HH_MM.format(java.time.LocalTime.of(minute / 60, minute % 60))

/** 条件提示条（§8.2：同一时刻只显示一条）。 */
@Composable
private fun Banner(text: String, actionLabel: String, onAction: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** 空态：无任何课程 → 中央「导入课表」（§8.2；B3-2 起指向图片导入页）。 */
@Composable
private fun EmptyImportContent(onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("还没有课程", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onImport) { Text("导入课表") }
    }
}

/** 空态：有课但今日无课 → 「今天没有课」+ 周课表入口（§8.2）。 */
@Composable
private fun EmptyTodayContent(onOpenWeek: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("今天没有课，好好休息", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpenWeek) { Text("查看周课表") }
    }
}
