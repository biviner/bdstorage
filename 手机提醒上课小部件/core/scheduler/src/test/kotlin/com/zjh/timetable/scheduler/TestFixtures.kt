package com.zjh.timetable.scheduler

import com.zjh.timetable.data.repository.ChangeNotifier

/** 测试共用空钩子（调度器行为不经 ChangeNotifier，直接调 rescheduleAll）。 */
internal object NoopNotifier : ChangeNotifier {
    override suspend fun onTimetableChanged() = Unit
}
