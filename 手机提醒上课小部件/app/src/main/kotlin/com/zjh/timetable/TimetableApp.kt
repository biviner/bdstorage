package com.zjh.timetable

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import androidx.navigation.compose.rememberNavController
import com.zjh.timetable.nav.TimetableNavHost
import com.zjh.timetable.ui.theme.PokemonBackground
import com.zjh.timetable.ui.theme.TimetableTheme

// B1-1 壳 + B2-2 导航骨架（详细设计 §8.1）；B6-UI2 全局宝可梦海报背景
@Composable
fun TimetableApp() {
    TimetableTheme {
        PokemonBackground {
            val navController = rememberNavController()
            // B4-FIX（评审阻塞项1）：深链热启转发（§7.1 桌面点小组件 → 课程详情）——
            // MainActivity.onNewIntent 经 ComponentActivity 回调监听器，此处补
            // NavHost 不监听 Intent 的缺口
            val activity = LocalContext.current as? ComponentActivity
            DisposableEffect(activity) {
                val listener = Consumer<Intent> { intent -> navController.handleDeepLink(intent) }
                activity?.addOnNewIntentListener(listener)
                onDispose { activity?.removeOnNewIntentListener(listener) }
            }
            TimetableNavHost(navController = navController)
        }
    }
}
