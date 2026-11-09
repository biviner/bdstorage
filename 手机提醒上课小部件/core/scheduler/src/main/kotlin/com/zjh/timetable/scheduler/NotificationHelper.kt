package com.zjh.timetable.scheduler

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.zjh.timetable.domain.model.PlannedReminder

/**
 * 通知渠道与发送（详细设计 §6.3）。渠道 class_reminder（IMPORTANCE_HIGH），
 * 在本对象首次使用前幂等创建，覆盖主流程与 Receiver 冷启两条进程启动路径。
 */
object NotificationHelper {

    const val CHANNEL_ID = "class_reminder"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "上课提醒", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    /** 发送上课提醒；通知权限未授予（API 33+）时静默跳过。 */
    fun notify(context: Context, reminder: PlannedReminder) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(reminder.courseId.toInt(), buildNotification(context, reminder))
    }

    private fun buildNotification(context: Context, reminder: PlannedReminder): Notification =
        Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon(context))
            .setContentTitle(reminder.title)
            .setContentText(reminder.body)
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .build()

    private fun smallIcon(context: Context): Int =
        context.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.sym_def_app_icon

    /** contentIntent → 首页（app 启动 Intent，§6.3：不做课程深链）。 */
    private fun contentIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
