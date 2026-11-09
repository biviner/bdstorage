package com.zjh.timetable.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback

/**
 * 小组件点击跳转（详细设计 §7.1/§7.2）：
 * 2×2 有课 → 课程详情（courseEdit 深链）；2×2 无课 / 4×2 → 首页（启动 Intent）。
 * 用显式类名 + 深链 URI，避免 widget 模块反向依赖 app 模块。
 */
class WidgetClickAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val courseId = parameters[KEY_COURSE_ID]
        val intent = if (courseId != null && courseId > 0L) {
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("timetable://courseEdit?courseId=$courseId"),
            ).setClassName(context.packageName, "com.zjh.timetable.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?: return
        }
        context.startActivity(intent)
    }

    companion object {
        val KEY_COURSE_ID = ActionParameters.Key<Long>("courseId")

        /** 无课/4×2 场景：不带 courseId，落首页。 */
        fun parameters() = actionParametersOf()

        fun parameters(courseId: Long) = actionParametersOf(KEY_COURSE_ID to courseId)
    }
}
