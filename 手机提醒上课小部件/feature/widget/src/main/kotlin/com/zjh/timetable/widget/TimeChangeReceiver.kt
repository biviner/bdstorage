package com.zjh.timetable.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 系统时间/时区变化广播（详细设计 §7.3）：TIME_SET / TIMEZONE_CHANGED（均为
 * 受保护广播，仅系统可发）→ 立即刷新，避免用户改时间/换时区后倒计时与日切状态陈旧。
 */
class TimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetUpdater.refreshAll(app)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
