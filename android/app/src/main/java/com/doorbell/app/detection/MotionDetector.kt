package com.doorbell.app.detection

import android.graphics.Bitmap

class MotionDetector(
    var sensitivity: Float = 0.05f,
    var pixelThreshold: Int = 30
) {
    private var previousGray: ByteArray? = null
    private var sampleW = 0
    private var sampleH = 0
    private var reusePixels = IntArray(0)

    fun detect(bitmap: Bitmap): Boolean {
        val step = 4
        val w = bitmap.width
        val h = bitmap.height
        val sw = w / step
        val sh = h / step
        val size = sw * sh

        if (sampleW != sw || sampleH != sh) {
            previousGray = null
            sampleW = sw
            sampleH = sh
            reusePixels = IntArray(w)
        }

        if (size == 0) return false

        val gray = ByteArray(size)
        var idx = 0
        for (y in 0 until h step step) {
            bitmap.getPixels(reusePixels, 0, w, 0, y, w, 1)
            for (x in 0 until w step step) {
                val pixel = reusePixels[x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                gray[idx++] = (0.299f * r + 0.587f * g + 0.114f * b).toInt().toByte()
            }
        }

        val prev = previousGray ?: run {
            previousGray = gray
            return false
        }

        var changed = 0
        for (i in 0 until size) {
            val diff = (gray[i].toInt() and 0xFF) - (prev[i].toInt() and 0xFF)
            if (kotlin.math.abs(diff) > pixelThreshold) {
                changed++
            }
        }

        previousGray = gray
        return changed.toFloat() / size.toFloat() > sensitivity
    }

    fun reset() {
        previousGray = null
    }
}
