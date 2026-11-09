package com.zjh.timetable.ui.confirm

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import com.zjh.timetable.ui.theme.PokemonColors
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zjh.timetable.domain.model.WeekPattern
import com.zjh.timetable.ui.data.ServiceLocator
import java.time.LocalTime

// 导入确认页（详细设计 §5.5）：行内编辑/周次选择器/标黄/冲突 ⚠/二次确认/全部丢弃不写库。

private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmScreen(
    draftId: Long,
    onBack: () -> Unit,
    onImported: (currentWeek: Int) -> Unit,
    viewModel: ConfirmViewModel = viewModel(key = "confirm-$draftId") {
        ConfirmViewModel(ServiceLocator.repository, draftId)
    },
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.discarded) {
        if (state.discarded) onBack()
    }

    LaunchedEffect(state.importedCount) {
        state.importedCount?.let { count ->
            Toast.makeText(context, "已导入 $count 门课程", Toast.LENGTH_SHORT).show()
            onImported(state.currentWeek ?: 1)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("确认导入") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        // B6-UI2b：内容装进不透明卡面，浮于海报背景上保证可读
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(3.dp, PokemonColors.Blue),
            color = PokemonColors.CardFace,
            shadowElevation = 6.dp,
        ) {
        Column(Modifier.fillMaxSize()) {
            // 顶部汇总（§5.5）+ 丢弃计数提示
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "识别到 ${state.rows.size} 门课，${state.needsReviewCount} 门需检查（标黄）",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (state.droppedCount > 0) {
                    Text(
                        "${state.droppedCount} 条疑似课程信息不完整已忽略，可手动添加",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(state.rows) { index, row ->
                    ConfirmRowCard(
                        index = index,
                        row = row,
                        totalWeeks = state.totalWeeks,
                        viewModel = viewModel,
                    )
                }
            }

            // 底部操作：全部丢弃 / 确认导入（二次确认）
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::requestDiscard,
                    modifier = Modifier.weight(1f),
                ) { Text("全部丢弃") }
                Button(
                    enabled = state.canImport,
                    onClick = viewModel::requestImport,
                    modifier = Modifier.weight(1f),
                ) { Text("确认导入") }
            }
        }

        // 导入二次确认：列出将写入的条数
        if (state.showImportConfirm) {
            AlertDialog(
                onDismissRequest = viewModel::dismissImportConfirm,
                title = { Text("确认导入") },
                text = { Text("将写入 ${state.rows.size} 门课程到课表，确认？") },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmImport) { Text("确认导入") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissImportConfirm) { Text("取消") }
                },
            )
        }

        // 全部丢弃二次确认
        if (state.showDiscardConfirm) {
            AlertDialog(
                onDismissRequest = viewModel::dismissDiscardConfirm,
                title = { Text("全部丢弃") },
                text = { Text("将删除本次识别结果，不写入任何课程，确认？") },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmDiscard) { Text("丢弃") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDiscardConfirm) { Text("取消") }
                },
            )
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConfirmRowCard(
    index: Int,
    row: ConfirmViewModel.RowState,
    totalWeeks: Int,
    viewModel: ConfirmViewModel,
) {
    var showWeekPicker by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<EditingTime?>(null) }
    var showConflictDetail by remember { mutableStateOf(false) }
    val periodIndexes = remember { (1..15).toList() }

    Card(
        // B6-UI：卡牌描边感（圆角 16dp 来自主题 Shapes.medium）
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            // §5.5 标黄：低置信度/节次映射失败/周次冲突
            containerColor = if (row.needsReview) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 行头：需检查标记 + 冲突 ⚠（点击展开定位结果）
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.needsReview) {
                    Text(
                        "需检查",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (row.conflictWith.isNotEmpty()) {
                    Text(
                        "⚠",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickable { showConflictDetail = !showConflictDetail },
                    )
                }
            }
            if (showConflictDetail && row.conflictWith.isNotEmpty()) {
                Text(
                    "与 ${row.conflictWith.joinToString("、")} 时间冲突",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedTextField(
                value = row.name,
                onValueChange = { viewModel.updateName(index, it) },
                label = { Text("课程名称 *") },
                singleLine = true,
                isError = row.name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )

            // 星期
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DAY_LABELS.forEachIndexed { dayIndex, label ->
                    FilterChip(
                        selected = row.dayOfWeek == dayIndex + 1,
                        onClick = { viewModel.updateDay(index, dayIndex + 1) },
                        label = { Text(label) },
                    )
                }
            }

            // 时间模式（§5.5：跟随全局，可单行切换）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = row.usePeriod,
                    onClick = { viewModel.updateUsePeriod(index, true) },
                    label = { Text("按节次") },
                )
                FilterChip(
                    selected = !row.usePeriod,
                    onClick = { viewModel.updateUsePeriod(index, false) },
                    label = { Text("按时刻") },
                )
            }
            if (row.usePeriod) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PeriodDropdown(
                        label = "开始节次",
                        period = row.startPeriod,
                        options = periodIndexes,
                        onChange = { viewModel.updatePeriods(index, it, row.endPeriod) },
                        modifier = Modifier.weight(1f),
                    )
                    PeriodDropdown(
                        label = "结束节次",
                        period = row.endPeriod,
                        options = periodIndexes,
                        onChange = { viewModel.updatePeriods(index, row.startPeriod, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editingTime = EditingTime.START }) {
                        Text("开始 ${row.startTime}")
                    }
                    OutlinedButton(onClick = { editingTime = EditingTime.END }) {
                        Text("结束 ${row.endTime}")
                    }
                }
            }

            // 周次选择器入口
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { showWeekPicker = true },
                    label = { Text("周次：${row.weekSummary}") },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "原文：${row.weeksRaw.orEmpty().ifEmpty { "无" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = row.location,
                    onValueChange = { viewModel.updateLocation(index, it) },
                    label = { Text("教室") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.teacher,
                    onValueChange = { viewModel.updateTeacher(index, it) },
                    label = { Text("教师") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            row.timeError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showWeekPicker) {
        ConfirmWeekPickerDialog(
            totalWeeks = totalWeeks,
            pattern = row.weekPattern,
            customWeeks = row.customWeeks,
            onPickPattern = { viewModel.updateWeekPattern(index, it) },
            onToggleWeek = { viewModel.toggleCustomWeek(index, it) },
            onDismiss = { showWeekPicker = false },
        )
    }

    editingTime?.let { which ->
        val current = if (which == EditingTime.START) row.startTime else row.endTime
        TimePickerDialogCompat(
            initial = current,
            onConfirm = { picked ->
                editingTime = null
                if (which == EditingTime.START) {
                    viewModel.updateTimes(index, picked, row.endTime)
                } else {
                    viewModel.updateTimes(index, row.startTime, picked)
                }
            },
            onDismiss = { editingTime = null },
        )
    }
}

private enum class EditingTime { START, END }

/** 周次选择器（§5.5）：All/Odd/Even 快捷钮 + 1..totalWeeks 网格点选（写入 customWeeks）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfirmWeekPickerDialog(
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodDropdown(
    label: String,
    period: Int,
    options: List<Int>,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { open = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = "第 $period 节",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { index ->
                DropdownMenuItem(
                    text = { Text("第 $index 节") },
                    onClick = {
                        open = false
                        onChange(index)
                    },
                )
            }
        }
    }
}

/** TimePicker + AlertDialog 组合（与 CourseEditScreen 同款，material3 无现成 TimePickerDialog）。 */
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
            TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
