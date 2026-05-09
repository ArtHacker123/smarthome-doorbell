package com.doorbell.app.detection

import com.doorbell.app.model.DoorbellConfig
import org.junit.Assert.*
import org.junit.Test

class PersonDetectorTest {

    private fun createConfig(
        sensitivity: Float = 0.7f,
        minDuration: Int = 2,
        cooldown: Int = 60
    ) = DoorbellConfig(
        sensitivity = sensitivity,
        minDuration = minDuration,
        cooldown = cooldown
    )

    @Test
    fun `detection state starts as IDLE`() {
        val detector = PersonDetector(createConfig())
        assertEquals(PersonDetector.DetectionState.IDLE, detector.detectionState.value)
    }

    @Test
    fun `config can be updated`() {
        val detector = PersonDetector(createConfig())
        val newConfig = createConfig(sensitivity = 0.9f)
        detector.updateConfig(newConfig)
    }

    @Test
    fun `detector can be reset`() {
        val detector = PersonDetector(createConfig())
        detector.reset()
        assertEquals(PersonDetector.DetectionState.IDLE, detector.detectionState.value)
    }

    @Test
    fun `detector can be closed`() {
        val detector = PersonDetector(createConfig())
        detector.close()
    }
}
