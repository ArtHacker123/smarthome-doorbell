package com.doorbell.app.model

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ModelsTest {

    @Test
    fun `DoorbellConfig has correct defaults`() {
        val config = DoorbellConfig()
        
        assertEquals(0.7f, config.sensitivity, 0.01f)
        assertEquals(2, config.minDuration)
        assertEquals(60, config.cooldown)
        assertEquals(10, config.recordDuration)
        assertEquals(30, config.retentionDays)
        assertEquals(30, config.screenTimeout)
        assertEquals(8080, config.pcServerPort)
        assertEquals(8554, config.rtspPort)
    }

    @Test
    fun `DoorbellConfig can be customized`() {
        val config = DoorbellConfig(
            sensitivity = 0.5f,
            minDuration = 5,
            cooldown = 120,
            recordDuration = 15,
            retentionDays = 7
        )
        
        assertEquals(0.5f, config.sensitivity, 0.01f)
        assertEquals(5, config.minDuration)
        assertEquals(120, config.cooldown)
        assertEquals(15, config.recordDuration)
        assertEquals(7, config.retentionDays)
    }

    @Test
    fun `DetectionEvent has unique ID by default`() {
        val event1 = DetectionEvent()
        val event2 = DetectionEvent()
        
        assertNotNull(event1.id)
        assertNotNull(event2.id)
        assertNotEquals(event1.id, event2.id)
    }

    @Test
    fun `DetectionEvent has correct timestamp`() {
        val before = System.currentTimeMillis()
        val event = DetectionEvent()
        val after = System.currentTimeMillis()
        
        assertTrue(event.timestamp >= before)
        assertTrue(event.timestamp <= after)
    }

    @Test
    fun `DetectionEvent defaults to person detected type`() {
        val event = DetectionEvent()
        assertEquals(EventType.PERSON_DETECTED, event.type)
    }

    @Test
    fun `DetectionEvent can have all event types`() {
        val personEvent = DetectionEvent(type = EventType.PERSON_DETECTED)
        val batteryEvent = DetectionEvent(type = EventType.LOW_BATTERY)
        val storageEvent = DetectionEvent(type = EventType.STORAGE_LOW)
        val offlineEvent = DetectionEvent(type = EventType.DEVICE_OFFLINE)
        
        assertEquals(EventType.PERSON_DETECTED, personEvent.type)
        assertEquals(EventType.LOW_BATTERY, batteryEvent.type)
        assertEquals(EventType.STORAGE_LOW, storageEvent.type)
        assertEquals(EventType.DEVICE_OFFLINE, offlineEvent.type)
    }

    @Test
    fun `DeviceInfo has correct defaults`() {
        val device = DeviceInfo()
        
        assertEquals("doorbell", device.deviceType)
        assertEquals("Smart Doorbell", device.deviceName)
        assertTrue(device.capabilities.contains("video_stream"))
        assertTrue(device.capabilities.contains("event_detection"))
    }

    @Test
    fun `Recording has correct structure`() {
        val recording = Recording(
            id = "test-id",
            timestamp = 1234567890L,
            duration = 10,
            filePath = "/path/to/video.mp4",
            thumbnailPaths = listOf("/path/to/thumb1.jpg", "/path/to/thumb2.jpg")
        )
        
        assertEquals("test-id", recording.id)
        assertEquals(1234567890L, recording.timestamp)
        assertEquals(10, recording.duration)
        assertEquals("/path/to/video.mp4", recording.filePath)
        assertEquals(2, recording.thumbnailPaths.size)
    }

    @Test
    fun `ServiceState enum has all states`() {
        assertEquals(4, ServiceState.values().size)
        assertNotNull(ServiceState.SURVEILLANCE)
        assertNotNull(ServiceState.RECORDING)
        assertNotNull(ServiceState.COOLDOWN)
        assertNotNull(ServiceState.SLEEP)
    }
}
