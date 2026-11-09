package com.zjh.timetable.data.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * DataStore keys 测试（详细设计 §8.6：uploadConsentAcked / activeWeekOverride）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TimetableDataStoreTest {

    @Test
    fun keys_defaults_setAndClear() = runTest {
        val store = TimetableDataStore(ApplicationProvider.getApplicationContext())

        // 默认值
        assertFalse(store.uploadConsentAcked.first())
        assertNull(store.activeWeekOverride.first())

        // 写入与回读
        store.setUploadConsentAcked(true)
        assertTrue(store.uploadConsentAcked.first())

        store.setActiveWeekOverride(7)
        assertEquals(7, store.activeWeekOverride.first())

        // null 清除
        store.setActiveWeekOverride(null)
        assertNull(store.activeWeekOverride.first())
    }
}
