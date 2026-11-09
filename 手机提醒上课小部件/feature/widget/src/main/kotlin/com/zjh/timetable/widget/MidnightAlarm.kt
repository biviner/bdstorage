package com.zjh.timetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * 午夜日切 Alarm（详细设计 §7.3）：AlarmManager.setExact（type RTC），
 * 触发后刷新两枚小组件并滚动排定次日零点。app 启动与每次触发均重设（幂等）。
 */
object MidnightAlarm {

    private const val REQUEST_CODE = 2001

    /** 排定次日 00:00 的日切闹钟；精确闹钟权限被回收时降级为非精确（避免 SecurityException）。 */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val triggerAt = LocalDate.now().plusDays(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pendingIntent = PendingIntent.getBroadcast(
            app, REQUEST_CODE, Intent(app, MidnightAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExact(AlarmManager.RTC, triggerAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC, triggerAt, pendingIntent)
        }
    }
}

/** 午夜闹钟触发：刷新小组件（跨日 → 日期/周次/课程全变）→ 滚动排定次日。 */
class MidnightAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetUpdater.refreshAll(app)
                MidnightAlarm.schedule(app)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
