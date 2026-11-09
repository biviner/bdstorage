package com.zjh.timetable.ui.settings

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import com.zjh.timetable.ui.theme.PokemonColors
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zjh.timetable.domain.model.TimetableMode
import com.zjh.timetable.ui.data.ServiceLocator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// 设置页（详细设计 §8.5）：学期 / 作息 / 提醒；识别服务为 B3 留空位；关于显示版本。

private fun leadLabel(minutes: Int): String = if (minutes == 0) "准点" else "提前 $minutes 分钟"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(key = "settings") {
        SettingsViewModel(ServiceLocator.repository, ServiceLocator.dataStore)
    },
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.1.0"
    }

    LaunchedEffect(state.timetableSavedTick) {
        if (state.timetableSavedTick > 0) snackbar.showSnackbar("作息时间表已保存")
    }

    LaunchedEffect(state.recSavedTick) {
        if (state.recSavedTick > 0) snackbar.showSnackbar("识别服务配置已保存")
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
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
            SettingsContent(
                state = state,
                viewModel = viewModel,
                versionName = versionName,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    state: SettingsViewModel.UiState,
    viewModel: SettingsViewModel,
    versionName: String,
    modifier: Modifier = Modifier,
) {
    var showStartDatePicker by remember { mutableStateOf(false) }
    var leadMenuOpen by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<EditingRowTime?>(null) }
    var showClearLeadMenu by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── 学期 ──
        item {
            SectionHeader("学期")
            if (showStartDatePicker) {
                val pickerState = rememberDatePickerState()
                DatePickerDialog(
                    onDismissRequest = { showStartDatePicker = false },
                    confirmButton = {
                        TextButton(
                            enabled = pickerState.selectedDateMillis != null,
                            onClick = {
                                pickerState.selectedDateMillis?.let { millis ->
                                    viewModel.setFirstWeekDate(
                                        Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate(),
                                    )
                                }
                                showStartDatePicker = false
                            },
                        ) { Text("确定") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStartDatePicker = false }) { Text("取消") }
                    },
                ) {
                    DatePicker(state = pickerState)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { showStartDatePicker = true },
                    label = { Text("第 1 周周一：${state.semester?.firstWeekStartDate ?: "-"}") },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("总周数")
                    OutlinedButton(onClick = viewModel::decrementWeeks) { Text("−") }
                    Text("${state.semester?.totalWeeks ?: "-"}", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = viewModel::incrementWeeks) { Text("+") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("课表模式")
                    FilterChip(
                        selected = state.semester?.timetableMode == TimetableMode.PERIOD,
                        onClick = { viewModel.setTimetableMode(TimetableMode.PERIOD) },
                        label = { Text("节次") },
                    )
                    FilterChip(
                        selected = state.semester?.timetableMode == TimetableMode.EXACT_TIME,
                        onClick = { viewModel.setTimetableMode(TimetableMode.EXACT_TIME) },
                        label = { Text("时刻") },
                    )
                }
            }
        }

        item { HorizontalDivider() }

        // ── 作息时间表 ──
        item {
            SectionHeader("作息时间表")
        }
        items(state.rows.size) { index ->
            val row = state.rows[index]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("第 ${row.periodIndex} 节", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { editingTime = EditingRowTime(index, true) }) {
                    Text(row.startTime.toString())
                }
                Text("~")
                OutlinedButton(onClick = { editingTime = EditingRowTime(index, false) }) {
                    Text(row.endTime.toString())
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { viewModel.removeRow(index) }) {
                    Icon(Icons.Default.Close, contentDescription = "删除该节次")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::addRow) { Text("+ 添加节次") }
                    OutlinedButton(onClick = viewModel::restoreDefaultTimetable) { Text("恢复默认") }
                }
                state.rowsError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (state.rowsDirty) {
                    Button(onClick = viewModel::saveTimetable) { Text("保存作息表") }
                }
            }
        }

        item { HorizontalDivider() }

        // ── 提醒 ──
        item {
            SectionHeader("提醒")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("默认提前量")
                Box {
                    OutlinedButton(onClick = { showClearLeadMenu = true }) {
                        Text(leadLabel(state.semester?.defaultReminderLeadMinutes ?: 15))
                    }
                    DropdownMenu(expanded = showClearLeadMenu, onDismissRequest = { showClearLeadMenu = false }) {
                        SettingsViewModel.LEAD_OPTIONS.forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text(leadLabel(minutes)) },
                                onClick = {
                                    showClearLeadMenu = false
                                    viewModel.setDefaultLead(minutes)
                                },
                            )
                        }
                    }
                }
            }
        }

        item { HorizontalDivider() }

        // ── 提醒可靠性（B4-1，详细设计 §6.2） ──
        item {
            val context = LocalContext.current
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            // API 31 以下无精确闹钟权限概念，视为正常
            val exactOk = Build.VERSION.SDK_INT < 31 ||
                alarmManager?.canScheduleExactAlarms() == true
            SectionHeader("提醒可靠性")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!exactOk) {
                    // 黄色提示条 + 跳系统设置（§6.2）
                    Surface(color = Color(0xFFFFF3CD), shape = MaterialTheme.shapes.small) {
                        Text(
                            "精确闹钟权限未开启，提醒可能延迟或丢失",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF7A5C00),
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                        )
                    }
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:${context.packageName}"))
                            runCatching { context.startActivity(intent) }
                        },
                    ) { Text("去开启精确闹钟") }
                } else {
                    Text(
                        "精确闹钟权限正常",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "国产 ROM 请将本应用加入电池优化白名单并允许后台运行，否则提醒可能被系统拦截。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    },
                ) { Text("电池优化设置") }
            }
        }

        item { HorizontalDivider() }

        // ── 识别服务（B3，详细设计 §8.5） ──
        item {
            SectionHeader("识别服务")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.recBaseUrl,
                    onValueChange = viewModel::updateRecBaseUrl,
                    label = { Text("服务地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.recApiKey,
                    onValueChange = viewModel::updateRecApiKey,
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.recModel,
                        onValueChange = viewModel::updateRecModel,
                        label = { Text("模型名") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        enabled = !state.recModelsFetching &&
                            state.recBaseUrl.isNotBlank() && state.recApiKey.isNotBlank(),
                        onClick = viewModel::fetchModels,
                    ) {
                        if (state.recModelsFetching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text("⟳ 获取模型列表")
                    }
                }
                state.recModelsError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    enabled = state.recDirty && state.recBaseUrl.isNotBlank() &&
                        state.recApiKey.isNotBlank() && state.recModel.isNotBlank(),
                    onClick = viewModel::saveRecognitionConfig,
                ) { Text("保存识别配置") }
                Text(
                    "图片仅在上传识图时发送至上述服务；配置完整后首页「导入课表」入口可用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { HorizontalDivider() }

        // ── 关于 ──
        item {
            SectionHeader("关于")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("版本：$versionName", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "图片上传隐私声明：课程表截图仅在上传识图时发送至你配置的识别服务，本应用不收集其他数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // 模型选择弹窗（B5-FIX2：拉取成功后单选回填）
    state.recModels?.let { models ->
        AlertDialog(
            onDismissRequest = viewModel::dismissModels,
            title = { Text("选择模型") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(models.size) { index ->
                        TextButton(onClick = { viewModel.selectModel(models[index]) }) {
                            Text(models[index])
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::dismissModels) { Text("取消") }
            },
        )
    }

    // 节次时刻编辑弹窗
    editingTime?.let { target ->
        val row = state.rows.getOrNull(target.rowIndex) ?: return@let
        val current = if (target.isStart) row.startTime else row.endTime
        val pickerState = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { editingTime = null },
            title = { Text(if (target.isStart) "选择开始时间" else "选择结束时间") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    editingTime = null
                    val picked = LocalTime.of(pickerState.hour, pickerState.minute)
                    if (target.isStart) {
                        viewModel.updateRowTime(target.rowIndex, picked, row.endTime)
                    } else {
                        viewModel.updateRowTime(target.rowIndex, row.startTime, picked)
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editingTime = null }) { Text("取消") }
            },
        )
    }
}

private data class EditingRowTime(val rowIndex: Int, val isStart: Boolean)

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}
