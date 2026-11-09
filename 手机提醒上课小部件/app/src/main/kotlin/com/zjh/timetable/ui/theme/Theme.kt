package com.zjh.timetable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// B6-UI：宝可梦卡通风主题（Pokemon TCG 风）。
// 主色 宝可梦蓝 #2A75BB / 强调黄 #FFCB05（皮卡黄）/ 辅助红 #EE1C25（精灵球红）；
// 浅色卡面浅黄 #FFF8E1，深色模式用深蓝 #1A237E 变体。不再使用 Android 12 动态取色。

/** 宝可梦 TCG 品牌色（视觉层共用：卡牌描边、徽标、小组件同步色）。 */
object PokemonColors {
    val Blue = Color(0xFF2A75BB)
    val Yellow = Color(0xFFFFCB05)
    val Red = Color(0xFFEE1C25)

    /** 卡牌面（不透明，浮于海报背景上保证可读） */
    val CardFace = Color(0xFFFFFDF5)

    /** 明快宝可梦系课程块色板（白字+深描边） */
    val CoursePalette = listOf(
        Color(0xFF7AC74C), // 草绿
        Color(0xFF3E9DE0), // 水蓝
        Color(0xFFF5822A), // 火橙
        Color(0xFFF7D02C), // 电黄
        Color(0xFF9B5DE0), // 超能紫
        Color(0xFFF4A7C3), // 粉
    )

    /** 按 name hash 稳定取色（无自定义色课程） */
    fun courseColor(name: String): Color = CoursePalette[abs(name.hashCode()) % CoursePalette.size]

    private fun abs(v: Int): Int = if (v == Int.MIN_VALUE) Int.MAX_VALUE else if (v < 0) -v else v
}

private val LightColors = lightColorScheme(
    primary = PokemonColors.Blue,
    onPrimary = Color.White,
    primaryContainer = PokemonColors.Yellow,
    onPrimaryContainer = Color(0xFF1A237E),
    secondary = PokemonColors.Red,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE9B8),
    onSecondaryContainer = Color(0xFF1A237E),
    tertiary = Color(0xFFF9A825),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFFFFE082),
    onTertiaryContainer = Color(0xFF3E2723),
    // 页面背景由海报图承担，scheme.background 仅作兜底
    background = Color(0xFF0D1B2A),
    onBackground = Color.White,
    surface = PokemonColors.CardFace,
    onSurface = Color(0xFF263238),
    surfaceVariant = Color(0xFFFFECB3),
    onSurfaceVariant = Color(0xFF37474F), // 加深提升卡内对比
    outline = PokemonColors.Blue,
    outlineVariant = Color(0xFFE6D9A8),
    error = Color(0xFFC62828),
)

private val DarkColors = darkColorScheme(
    primary = PokemonColors.Yellow,
    onPrimary = Color(0xFF1A237E),
    primaryContainer = PokemonColors.Blue,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFFF5252),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFB71C1C),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFFFD54F),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF5F4B00),
    onTertiaryContainer = Color(0xFFFFE082),
    background = Color(0xFF10173F),
    onBackground = Color(0xFFE8EAF6),
    surface = Color(0xFF1A237E),
    onSurface = Color(0xFFE8EAF6),
    surfaceVariant = Color(0xFF283593),
    onSurfaceVariant = Color(0xFFC5CAE9),
    outline = Color(0xFF7986CB),
    outlineVariant = Color(0xFF3949AB),
    error = Color(0xFFEF5350),
)

/** 卡牌圆角：卡片容器统一 20dp（卡牌感）。 */
private val PokemonShapes = Shapes(
    medium = RoundedCornerShape(20.dp),
)

// B6-UI2：宝可梦世界主题（背景海报在页面层）+ 浅深色跟随系统
@Composable
fun TimetableTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, shapes = PokemonShapes, content = content)
}
