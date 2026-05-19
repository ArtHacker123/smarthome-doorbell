package com.doorbell.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.doorbell.app.model.DoorbellConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "doorbell_settings")

class ConfigRepository(private val context: Context) {

    companion object {
        val SENSITIVITY = floatPreferencesKey("sensitivity")
        val MIN_DURATION = intPreferencesKey("min_duration")
        val COOLDOWN = intPreferencesKey("cooldown")
        val RECORD_DURATION = intPreferencesKey("record_duration")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val SCREEN_TIMEOUT = intPreferencesKey("screen_timeout")
        val MOTION_SENSITIVITY = floatPreferencesKey("motion_sensitivity")
        val MOTION_PIXEL_THRESHOLD = intPreferencesKey("motion_pixel_threshold")
        val ZOOM_RATIO = floatPreferencesKey("zoom_ratio")
        val PC_SERVER_ADDRESS = stringPreferencesKey("pc_server_address")
        val PC_SERVER_PORT = intPreferencesKey("pc_server_port")
        val RTSP_PORT = intPreferencesKey("rtsp_port")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val IS_SERVICE_RUNNING = booleanPreferencesKey("is_service_running")
    }

    val configFlow: Flow<DoorbellConfig> = context.dataStore.data.map { preferences ->
        DoorbellConfig(
            sensitivity = preferences[SENSITIVITY] ?: 0.7f,
            minDuration = preferences[MIN_DURATION] ?: 2,
            cooldown = preferences[COOLDOWN] ?: 60,
            recordDuration = preferences[RECORD_DURATION] ?: 3,
            retentionDays = preferences[RETENTION_DAYS] ?: 30,
            screenTimeout = preferences[SCREEN_TIMEOUT] ?: 30,
            motionSensitivity = preferences[MOTION_SENSITIVITY] ?: 0.05f,
            motionPixelThreshold = preferences[MOTION_PIXEL_THRESHOLD] ?: 30,
            zoomRatio = preferences[ZOOM_RATIO] ?: 9.0f,
            pcServerAddress = preferences[PC_SERVER_ADDRESS] ?: "",
            pcServerPort = preferences[PC_SERVER_PORT] ?: 8000,
            rtspPort = preferences[RTSP_PORT] ?: 8554
        )
    }

    val deviceIdFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEVICE_ID] ?: ""
    }

    suspend fun updateConfig(config: DoorbellConfig) {
        context.dataStore.edit { preferences ->
            preferences[SENSITIVITY] = config.sensitivity
            preferences[MIN_DURATION] = config.minDuration
            preferences[COOLDOWN] = config.cooldown
            preferences[RECORD_DURATION] = config.recordDuration
            preferences[RETENTION_DAYS] = config.retentionDays
            preferences[SCREEN_TIMEOUT] = config.screenTimeout
            preferences[MOTION_SENSITIVITY] = config.motionSensitivity
            preferences[MOTION_PIXEL_THRESHOLD] = config.motionPixelThreshold
            preferences[ZOOM_RATIO] = config.zoomRatio
            preferences[PC_SERVER_ADDRESS] = config.pcServerAddress
            preferences[PC_SERVER_PORT] = config.pcServerPort
            preferences[RTSP_PORT] = config.rtspPort
        }
    }

    suspend fun setDeviceId(deviceId: String) {
        context.dataStore.edit { preferences ->
            preferences[DEVICE_ID] = deviceId
        }
    }

    suspend fun setServiceRunning(running: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_SERVICE_RUNNING] = running
        }
    }
}
