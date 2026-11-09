package com.zjh.timetable.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 识别服务配置（详细设计 §8.5/§8.6）。三项齐全视为已配置；apiKey 界面打码、不进日志（§9.6）。
 */
data class RecognitionConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
) {
    val configured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

/**
 * DataStore keys（详细设计 §8.6）。
 */
private val Context.timetableDataStore by preferencesDataStore(name = "timetable_prefs")

class TimetableDataStore(context: Context) {

    private val dataStore = context.timetableDataStore

    /** 图片上传至识别服务的知情确认（一次性记住选择，详细设计 §5.4）。 */
    val uploadConsentAcked: Flow<Boolean> =
        dataStore.data.map { it[KEY_UPLOAD_CONSENT_ACKED] ?: false }

    suspend fun setUploadConsentAcked(acked: Boolean) {
        dataStore.edit { it[KEY_UPLOAD_CONSENT_ACKED] = acked }
    }

    /** 识别服务配置；未配置返回 null。 */
    val recognitionConfig: Flow<RecognitionConfig?> =
        dataStore.data.map { prefs ->
            val baseUrl = prefs[KEY_RECOGNITION_BASE_URL]
            val apiKey = prefs[KEY_RECOGNITION_API_KEY]
            val model = prefs[KEY_RECOGNITION_MODEL]
            if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank() || model.isNullOrBlank()) {
                null
            } else {
                RecognitionConfig(baseUrl, apiKey, model)
            }
        }

    /** 保存识别服务配置；任一字段为空视为清除配置。 */
    suspend fun setRecognitionConfig(config: RecognitionConfig?) {
        dataStore.edit {
            if (config == null || config.baseUrl.isBlank() || config.apiKey.isBlank() || config.model.isBlank()) {
                it.remove(KEY_RECOGNITION_BASE_URL)
                it.remove(KEY_RECOGNITION_API_KEY)
                it.remove(KEY_RECOGNITION_MODEL)
            } else {
                it[KEY_RECOGNITION_BASE_URL] = config.baseUrl.trim()
                it[KEY_RECOGNITION_API_KEY] = config.apiKey.trim()
                it[KEY_RECOGNITION_MODEL] = config.model.trim()
            }
        }
    }

    /** 周课表手动翻页记忆，仅 UI 态（详细设计 §8.6）。 */
    val activeWeekOverride: Flow<Int?> =
        dataStore.data.map { it[KEY_ACTIVE_WEEK_OVERRIDE] }

    /** [week] 传 null 清除记忆。 */
    suspend fun setActiveWeekOverride(week: Int?) {
        dataStore.edit {
            if (week == null) it.remove(KEY_ACTIVE_WEEK_OVERRIDE) else it[KEY_ACTIVE_WEEK_OVERRIDE] = week
        }
    }

    companion object {
        private val KEY_UPLOAD_CONSENT_ACKED = booleanPreferencesKey("uploadConsentAcked")
        private val KEY_ACTIVE_WEEK_OVERRIDE = intPreferencesKey("activeWeekOverride")
        private val KEY_RECOGNITION_BASE_URL = stringPreferencesKey("recognitionBaseUrl")
        private val KEY_RECOGNITION_API_KEY = stringPreferencesKey("recognitionApiKey")
        private val KEY_RECOGNITION_MODEL = stringPreferencesKey("recognitionModel")
    }
}
