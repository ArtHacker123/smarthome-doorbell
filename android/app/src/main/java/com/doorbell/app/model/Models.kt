package com.doorbell.app.model

import android.graphics.Bitmap
import com.google.gson.annotations.SerializedName
import java.util.UUID

data class DoorbellConfig(
    val sensitivity: Float = 0.7f,
    val minDuration: Int = 2,
    val cooldown: Int = 60,
    val recordDuration: Int = 3,
    val retentionDays: Int = 30,
    val screenTimeout: Int = 30,
    val motionSensitivity: Float = 0.05f,
    val motionPixelThreshold: Int = 30,
    val zoomRatio: Float = 9.0f,
    val pcServerAddress: String = "",
    val pcServerPort: Int = 8000,
    val rtspPort: Int = 8554
)

data class DeviceInfo(
    @SerializedName("deviceId")
    val deviceId: String = "",
    @SerializedName("deviceType")
    val deviceType: String = "doorbell",
    @SerializedName("deviceName")
    val deviceName: String = "Smart Doorbell",
    @SerializedName("capabilities")
    val capabilities: List<String> = listOf("video_stream", "event_detection"),
    @SerializedName("endpoints")
    val endpoints: DeviceEndpoints = DeviceEndpoints()
)

data class DeviceEndpoints(
    @SerializedName("stream")
    val stream: String = "",
    @SerializedName("web")
    val web: String = ""
)

data class DetectionEvent(
    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),
    @SerializedName("deviceId")
    val deviceId: String = "",
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("type")
    val type: EventType = EventType.PERSON_DETECTED,
    @SerializedName("confidence")
    val confidence: Float = 0f,
    @SerializedName("thumbnails")
    val thumbnails: List<String> = emptyList(),
    @SerializedName("duration")
    val duration: Int = 0
)

enum class EventType {
    @SerializedName("person_detected")
    PERSON_DETECTED,
    @SerializedName("low_battery")
    LOW_BATTERY,
    @SerializedName("storage_low")
    STORAGE_LOW,
    @SerializedName("device_offline")
    DEVICE_OFFLINE
}

data class Recording(
    val id: String,
    val timestamp: Long,
    val duration: Int,
    val filePath: String,
    val thumbnailPaths: List<String>
)

enum class ServiceState {
    SURVEILLANCE,
    RECORDING,
    COOLDOWN,
    SLEEP
}

data class DetectionCommand(
    val eventId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Float,
    val frame: Bitmap? = null
)
