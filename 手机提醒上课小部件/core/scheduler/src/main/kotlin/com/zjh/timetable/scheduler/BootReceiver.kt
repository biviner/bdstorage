package com.zjh.timetable.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机重建全部闹钟（详细设计 §6.1：BOOT_COMPLETED → rescheduleAll）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val graph = ReminderGraph.cached(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                onReceiveAsync(graph)
            } finally {
                pendingResult.finish()
            }
        }
    }

    suspend fun onReceiveAsync(graph: ReminderGraph) {
        graph.scheduler.rescheduleAll()
    }
}
