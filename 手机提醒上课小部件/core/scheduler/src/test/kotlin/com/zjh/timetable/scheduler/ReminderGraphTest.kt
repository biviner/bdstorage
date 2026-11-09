package com.zjh.timetable.scheduler

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zjh.timetable.data.local.SchemaConstraints
import com.zjh.timetable.data.local.TimetableDatabase
import com.zjh.timetable.data.repository.TimetableRepository
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * ReminderGraph 进程级单例装配单测（评审阻塞项2：闹钟触发/开机路径与 app
 * 装配点必须复用同一 graph，scheduler mutex 全局唯一）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ReminderGraphTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `兜底自建后注册以app装配替换且此后复用`() {
        val db = Room.inMemoryDatabaseBuilder(context, TimetableDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(SchemaConstraints.callback)
            .build()
        try {
            // Receiver 冷启兜底：cached 缺省自建并复用
            val fallback = ReminderGraph.cached(context)
            assertSame(fallback, ReminderGraph.cached(context))

            // app 装配点注册：替换兜底实例，此后全进程统一复用
            val app = ReminderGraph.build(context, TimetableRepository(db, NoopNotifier))
            ReminderGraph.register(app)
            assertSame(app, ReminderGraph.cached(context))
        } finally {
            db.close()
        }
    }
}
