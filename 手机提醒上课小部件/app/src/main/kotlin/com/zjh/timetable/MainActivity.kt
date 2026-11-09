package com.zjh.timetable

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zjh.timetable.ui.data.ServiceLocator
import com.zjh.timetable.ui.theme.TimetableTheme

// B1-1：单 Activity 壳（详细设计 §8.1，导航宿主在 B2 填充路由）
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // B2-3：装配真仓储（Room + Repository），须在首帧组合前完成
        ServiceLocator.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            TimetableTheme {
                TimetableApp()
            }
        }
    }

    // B4-FIX（评审阻塞项1）：singleTask 应用存活时系统只回调 onNewIntent，
    // NavHost 不会自行读取新 Intent——setIntent 保存后经监听器转发深链
    // （监听器注册见 TimetableApp，super 已分发 OnNewIntentProvider 消费者）
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
