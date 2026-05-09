package com.doorbell.app.detection

import android.graphics.Bitmap
import android.util.Log
import com.doorbell.app.model.DoorbellConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

class PersonDetector(
    private val config: DoorbellConfig
) {
    private val TAG = "PersonDetector"

    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableClassification()
        .enableMultipleObjects()
        .build()

    private val objectDetector: ObjectDetector = ObjectDetection.getClient(options)

    private val _detectionState = MutableStateFlow(DetectionState.IDLE)
    val detectionState: StateFlow<DetectionState> = _detectionState

    private var lastDetectionTime = 0L
    private var detectionStartTime = 0L
    private val isPersonInView = AtomicBoolean(false)

    enum class DetectionState {
        IDLE,
        DETECTING,
        PERSON_DETECTED,
        COOLDOWN
    }

    fun processFrame(bitmap: Bitmap, onPersonDetected: () -> Unit) {
        val currentTime = System.currentTimeMillis()

        if (_detectionState.value == DetectionState.COOLDOWN) {
            if (currentTime - lastDetectionTime > config.cooldown * 1000) {
                _detectionState.value = DetectionState.IDLE
            } else {
                return
            }
        }

        val image = InputImage.fromBitmap(bitmap, 0)

        objectDetector.process(image)
            .addOnSuccessListener { detectedObjects ->
                var personFound = false
                var maxConfidence = 0f

                for (obj in detectedObjects) {
                    for (label in obj.labels) {
                        if (label.text == "Person" && label.confidence >= config.sensitivity) {
                            personFound = true
                            maxConfidence = maxOf(maxConfidence, label.confidence)
                            break
                        }
                    }
                }

                handleDetectionResult(personFound, maxConfidence, currentTime, onPersonDetected)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Object detection failed", e)
            }
    }

    private fun handleDetectionResult(
        personFound: Boolean,
        confidence: Float,
        currentTime: Long,
        onPersonDetected: () -> Unit
    ) {
        when {
            personFound && !isPersonInView.get() -> {
                isPersonInView.set(true)
                detectionStartTime = currentTime
                _detectionState.value = DetectionState.DETECTING
            }

            personFound && isPersonInView.get() -> {
                val duration = (currentTime - detectionStartTime) / 1000f
                if (duration >= config.minDuration && _detectionState.value == DetectionState.DETECTING) {
                    _detectionState.value = DetectionState.PERSON_DETECTED
                    lastDetectionTime = currentTime
                    onPersonDetected()
                    _detectionState.value = DetectionState.COOLDOWN
                }
            }

            !personFound && isPersonInView.get() -> {
                isPersonInView.set(false)
                if (_detectionState.value == DetectionState.DETECTING) {
                    _detectionState.value = DetectionState.IDLE
                }
            }
        }
    }

    fun reset() {
        isPersonInView.set(false)
        detectionStartTime = 0L
        _detectionState.value = DetectionState.IDLE
    }

    fun close() {
        objectDetector.close()
    }

    fun updateConfig(newConfig: DoorbellConfig) {
        // Config changes will be picked up in the next detection cycle
    }
}
