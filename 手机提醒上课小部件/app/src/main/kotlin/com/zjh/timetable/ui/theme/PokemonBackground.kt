package com.zjh.timetable.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.zjh.timetable.R

/**
 * B6-UI2：宝可梦海报背景 + 渐变遮罩 + 内容浮层。
 * 所有主页面统一用此容器：背景图 Crop 铺满，遮罩保证内容可读。
 * 深色模式遮罩加深。
 */
@Composable
fun PokemonBackground(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.pokemon_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // 垂直渐变遮罩：上深(顶栏区) - 中浅 - 下深(导航区)，深色模式整体加深
        val a = if (darkTheme) 0.70f else 0.55f
        val b = if (darkTheme) 0.45f else 0.30f
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = a),
                            Color.Black.copy(alpha = b),
                            Color.Black.copy(alpha = a),
                        )
                    )
                )
        )
        content()
    }
}
