package com.zjh.timetable.ui.import

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import com.zjh.timetable.ui.theme.PokemonColors
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zjh.timetable.ui.data.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 图片导入页（详细设计 §5.4 状态机、概要设计 §6.1）：GetContent 选图 → 上传声明 → 识别 → 确认页。
// 选图用 ACTION_GET_CONTENT 而非 PickVisualMedia：后者依赖系统 photo picker，部分国产 ROM 上回调链路存在兼容性崩溃。

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onRecognized: (Long) -> Unit,
    viewModel: ImportViewModel = viewModel(key = "import") {
        ImportViewModel(
            repository = ServiceLocator.repository,
            settings = DataStoreImportSettings(ServiceLocator.dataStore),
            prompt = ServiceLocator.recognitionPrompt,
        )
    },
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        try {
            if (uri != null) {
                viewModel.onImagePicked { loadJpegBytes(context.applicationContext, uri) }
            }
        } catch (e: Exception) {
            // 选图回调任何异常都转成失败提示，绝不裸抛（真机兼容性防御）
            Log.e("ImportDebug", "选图回调异常 uri=$uri", e)
            viewModel.onPickFailed()
        }
    }

    LaunchedEffect(state.doneDraftId) {
        state.doneDraftId?.let { draftId ->
            onRecognized(draftId)
            viewModel.onNavigated()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("导入课表") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        // B6-UI2b：内容装进不透明卡面，浮于海报背景上保证可读
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(3.dp, PokemonColors.Blue),
            color = PokemonColors.CardFace,
            shadowElevation = 6.dp,
        ) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.busy -> ImportProgress(state.busyLabel, Modifier.fillMaxSize())

                else -> ImportIdleContent(
                    state = state,
                    onPick = { picker.launch("image/*") },
                    onRetry = viewModel::retry,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 上传声明确认（§5.4：Uploading 前弹，一次性记住选择）
        if (state.showConsentDialog) {
            AlertDialog(
                onDismissRequest = viewModel::consentDismissed,
                title = { Text("上传声明") },
                text = {
                    Text("课程表截图将上传至你配置的识别服务（${state.baseUrl}）用于解析，确认继续？")
                },
                confirmButton = {
                    TextButton(onClick = viewModel::consentConfirmed) { Text("确认并继续") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::consentDismissed) { Text("取消") }
                },
            )
        }
    }
}

@Composable
private fun ImportIdleContent(
    state: ImportViewModel.UiState,
    onPick: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!state.configured) {
            // §5.4：未配置 → 入口置灰 + 引导去设置页
            Text(
                "请先在设置页配置识别服务",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenSettings) { Text("去设置") }
            return@Column
        }

        Text(
            "选择一张课程表截图，识别后可逐条确认再导入",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))

        state.failedMessage?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(16.dp))
        }

        Button(onClick = onPick) {
            Text(if (state.failedMessage == null) "选择图片" else "重新选图")
        }
        if (state.failedMessage != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRetry) { Text("重试识别") }
        }
    }
}

@Composable
private fun ImportProgress(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// ── 图片读取与压缩（概要设计 §6.1：压缩至 ≤2048px JPEG） ──────────────

private const val MAX_DIM = 2048

/** [loadImage] 注入实现：ContentResolver 解码 → 降采样 → JPEG 压缩；失败抛异常由 ViewModel 兜底。 */
suspend fun loadJpegBytes(context: Context, uri: Uri): ByteArray = try {
    withContext(Dispatchers.IO) {
    val resolver = context.contentResolver

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法解码图片" }

    val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (maxSide / (sample * 2) >= MAX_DIM) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: throw IllegalStateException("无法解码图片")

    val scaled = if (maxOf(bitmap.width, bitmap.height) > MAX_DIM) {
        val scale = MAX_DIM.toFloat() / maxOf(bitmap.width, bitmap.height)
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        bitmap
    }

    val bytes = java.io.ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }
    scaled.recycle()
    if (scaled !== bitmap) bitmap.recycle()
    bytes
    }
} catch (e: Exception) {
    // 带堆栈落 logcat，方便用户抓日志定位真机问题
    Log.e("ImportDebug", "loadJpegBytes 失败 uri=$uri", e)
    throw e
}
