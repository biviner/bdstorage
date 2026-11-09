package com.zjh.timetable.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import com.zjh.timetable.ui.theme.PokemonColors
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zjh.timetable.domain.model.WeekPattern
import com.zjh.timetable.ui.common.CoursePalette
import com.zjh.timetable.ui.data.ServiceLocator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// 课程编辑页（详细设计 §8.4）：字段校验 / 周次选择器 / 冲突弹窗 / 删除确认 / 停课日期 / 调色板。

private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 提前量选项展示文案；null=跟随全局，0=准点。 */
private fun leadLabel(minutes: Int?): String = when (minutes) {
    null -> "跟随全局"
    0 -> "准点"
    else -> "提前 $minutes 分钟"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditScreen(
    courseId: Long,
    onBack: () -> Unit,
    viewModel: CourseEditViewModel = viewModel(key = "courseEdit-$courseId") {
        CourseEditViewModel(ServiceLocator.repository, courseId)
    },
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.done) {
        if (state.done) onBack()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "添加课程" else "编辑课程") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        // B6-UI2b：表单装进不透明卡面，浮于海报背景上保证可读
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(3.dp, PokemonColors.Blue),
            color = PokemonColors.CardFace,
            shadowElevation = 6.dp,
        ) {
            EditForm(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 保存冲突弹窗（详细设计 §8.4：「与 {课程名} 时间冲突，仍要保存？」）
        if (state.pendingConflicts.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = viewModel::dismissConflict,
                title = { Text("时间冲突") },
                text = { Text("与 ${state.pendingConflicts.joinToString("、")} 时间冲突，仍要保存？") },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmSaveAnyway) { Text("仍要保存") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissConflict) { Text("取消") }
                },
            )
        }

        // 删除确认弹窗（文案含课程名）
        if (state.showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = viewModel::dismissDelete,
                title = { Text("删除课程") },
                text = { Text("确定删除课程「${state.name}」？") },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmDelete) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDelete) { Text("取消") }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditForm(
    state: CourseEditViewModel.UiState,
    viewModel: CourseEditViewModel,
    modifier: Modifier = Modifier,
) {
    var showWeekPicker by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<EditingTime?>(null) }
    var showPausedDatePicker by remember { mutableStateOf(false) }
    var leadMenuOpen by remember { mutableStateOf(false) }

    val periodIndexes = state.timetable.map { it.periodIndex }.sorted()

    LazyColumn(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 名称（必填，空则保存钮禁用）
        item {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = { Text("课程名称 *") },
                singleLine = true,
                isError = state.name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 星期
        item {
            SectionLabel("星期")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DAY_LABELS.forEachIndexed { index, label ->
                    val day = index + 1
                    FilterChip(
                        selected = state.dayOfWeek == day,
                        onClick = { viewModel.updateDay(day) },
                        label = { Text(label) },
                    )
                }
            }
        }

        // 时间模式（随全局模式默认，可单课切换）
        item {
            SectionLabel("时间模式")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.usePeriod,
                    onClick = { viewModel.updateUsePeriod(true) },
                    label = { Text("按节次") },
                )
                FilterChip(
                    selected = !state.usePeriod,
                    onClick = { viewModel.updateUsePeriod(false) },
                    label = { Text("按时刻") },
                )
            }
        }

        item {
            if (state.usePeriod) {
                PeriodSelector(
                    startPeriod = state.startPeriod,
                    endPeriod = state.endPeriod,
                    options = periodIndexes,
                    onStartChange = { viewModel.updatePeriods(it, state.endPeriod) },
                    onEndChange = { viewModel.updatePeriods(state.startPeriod, it) },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editingTime = EditingTime.START }) {
                        Text("开始 ${state.startTime}")
                    }
                    OutlinedButton(onClick = { editingTime = EditingTime.END }) {
                        Text("结束 ${state.endTime}")
                    }
                }
            }
        }

        // 周次
        item {
            SectionLabel("周次")
            AssistChip(
                onClick = { showWeekPicker = true },
                label = { Text(state.weekSummary) },
            )
        }

        // 教室/教师/备注
        item {
            OutlinedTextField(
                value = state.location,
                onValueChange = viewModel::updateLocation,
                label = { Text("教室") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = state.teacher,
                onValueChange = viewModel::updateTeacher,
                label = { Text("教师") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::updateNote,
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 颜色（8 色 + 自动）
        item {
            SectionLabel("颜色")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorDot(
                    color = null,
                    selected = state.colorArgb == null,
                    label = "自动",
                    onClick = { viewModel.updateColor(null) },
                )
                CoursePalette.colors.forEach { color ->
                    ColorDot(
                        color = color,
                        selected = state.colorArgb == color.toArgb(),
                        onClick = { viewModel.updateColor(color.toArgb()) },
                    )
                }
            }
        }

        // 提醒（开关 + 提前量）
        item {
            SectionLabel("提醒")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = state.reminderEnabled, onCheckedChange = viewModel::updateReminderEnabled)
                Text(if (state.reminderEnabled) "开启" else "关闭")
                Box {
                    OutlinedButton(
                        enabled = state.reminderEnabled,
                        onClick = { leadMenuOpen = true },
                    ) { Text(leadLabel(state.reminderLeadMinutes)) }
                    DropdownMenu(expanded = leadMenuOpen, onDismissRequest = { leadMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(leadLabel(null)) },
                            onClick = {
                                leadMenuOpen = false
                                viewModel.updateReminderLead(null)
                            },
                        )
                        CourseEditViewModel.LEAD_OPTIONS.forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text(leadLabel(minutes)) },
                                onClick = {
                                    leadMenuOpen = false
                                    viewModel.updateReminderLead(minutes)
                                },
                            )
                        }
                    }
                }
            }
        }

        // 停课日期（chips 可删，详细设计 §8.4）
        item {
            SectionLabel("停课日期")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.pausedDates.sorted().forEach { date ->
                    AssistChip(
                        onClick = { viewModel.removePausedDate(date) },
                        label = { Text("$date ✕") },
                    )
                }
                AssistChip(
                    onClick = { showPausedDatePicker = true },
                    label = { Text("+ 添加") },
                )
            }
        }

        // 校验错误
        state.fieldError?.let { error ->
            item {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        // 保存 / 删除
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    enabled = state.canSave,
                    onClick = viewModel::save,
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
                if (!state.isNew) {
                    OutlinedButton(
                        onClick = viewModel::requestDelete,
                        modifier = Modifier.weight(1f),
                    ) { Text("删除") }
                }
            }
        }
    }

    // 周次选择器
    if (showWeekPicker) {
        WeekPickerDialog(
            totalWeeks = state.semester?.totalWeeks ?: 20,
            pattern = state.weekPattern,
            customWeeks = state.customWeeks,
            onPickPattern = viewModel::updateWeekPattern,
            onToggleWeek = viewModel::toggleCustomWeek,
            onDismiss = { showWeekPicker = false },
        )
    }

    // 时刻选择器
    editingTime?.let { which ->
        val current = if (which == EditingTime.START) state.startTime else state.endTime
        TimePickerDialogCompat(
            initial = current,
            onConfirm = { picked ->
                editingTime = null
                if (which == EditingTime.START) {
                    viewModel.updateTimes(picked, state.endTime)
                } else {
                    viewModel.updateTimes(state.startTime, picked)
                }
            },
            onDismiss = { editingTime = null },
        )
    }

    // 停课日期选择器
    if (showPausedDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showPausedDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            viewModel.addPausedDate(
                                Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate(),
                            )
                        }
                        showPausedDatePicker = false
                    },
                ) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showPausedDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private enum class EditingTime { START, END }

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(
    startPeriod: Int,
    endPeriod: Int,
    options: List<Int>,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
) {
    var startOpen by remember { mutableStateOf(false) }
    var endOpen by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(
            expanded = startOpen,
            onExpandedChange = { startOpen = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = "第 $startPeriod 节",
                onValueChange = {},
                readOnly = true,
                label = { Text("开始节次") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = startOpen) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = startOpen, onDismissRequest = { startOpen = false }) {
                options.forEach { index ->
                    DropdownMenuItem(
                        text = { Text("第 $index 节") },
                        onClick = {
                            startOpen = false
                            onStartChange(index)
                        },
                    )
                }
            }
        }
        ExposedDropdownMenuBox(
            expanded = endOpen,
            onExpandedChange = { endOpen = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = "第 $endPeriod 节",
                onValueChange = {},
                readOnly = true,
                label = { Text("结束节次") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endOpen) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = endOpen, onDismissRequest = { endOpen = false }) {
                options.forEach { index ->
                    DropdownMenuItem(
                        text = { Text("第 $index 节") },
                        onClick = {
                            endOpen = false
                            onEndChange(index)
                        },
                    )
                }
            }
        }
    }
}

/** 周次选择器：快捷模式（每周/单周/双周）+ 1..totalWeeks 勾选（自动切 CUSTOM）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekPickerDialog(
    totalWeeks: Int,
    pattern: WeekPattern,
    customWeeks: Set<Int>,
    onPickPattern: (WeekPattern) -> Unit,
    onToggleWeek: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择周次") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        WeekPattern.ALL to "每周",
                        WeekPattern.ODD to "单周",
                        WeekPattern.EVEN to "双周",
                    ).forEach { (patternOption, label) ->
                        FilterChip(
                            selected = pattern == patternOption,
                            onClick = { onPickPattern(patternOption) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (week in 1..totalWeeks) {
                        FilterChip(
                            selected = week in customWeeks,
                            onClick = { onToggleWeek(week) },
                            label = { Text("$week") },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("确定") } },
    )
}

@Composable
private fun ColorDot(
    color: Color?,
    selected: Boolean,
    onClick: () -> Unit,
    label: String? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            )
            .padding(2.dp)
            .background(color ?: MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clickable(onClick = onClick),
    ) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** TimePicker + AlertDialog 组合（material3 无现成 TimePickerDialog）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialogCompat(
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间") },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(LocalTime.of(pickerState.hour, pickerState.minute))
                },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
