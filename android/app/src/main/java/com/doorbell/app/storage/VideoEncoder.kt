package com.doorbell.app.storage

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File

class VideoEncoder {
    private val TAG = "VideoEncoder"

    fun encodeFrames(frames: List<Bitmap>, outputFile: File, targetFps: Int = 10) {
        if (frames.size < 2) {
            Log.w(TAG, "Not enough frames to encode: ${frames.size}")
            return
        }

        val width = frames[0].width
        val height = frames[0].height

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        format.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 4)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, targetFps)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = codec.createInputSurface()
            codec.start()

            val frameIntervalMs = 1000L / targetFps

            for (frame in frames) {
                val frameStart = System.currentTimeMillis()

                val canvas = inputSurface.lockCanvas(null)
                canvas.drawBitmap(frame, 0f, 0f, null)
                inputSurface.unlockCanvasAndPost(canvas)

                drain@ while (true) {
                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 5000L)
                    when {
                        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break@drain
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                val newFormat = codec.getOutputFormat()
                                muxer = MediaMuxer(
                                    outputFile.absolutePath,
                                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                                )
                                trackIndex = muxer!!.addTrack(newFormat)
                                muxer!!.start()
                                muxerStarted = true
                            }
                        }
                        outIndex >= 0 -> {
                            val outputBuffer = codec.getOutputBuffer(outIndex)
                            if (muxerStarted && trackIndex >= 0 && outputBuffer != null) {
                                muxer?.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                        }
                    }
                }

                val elapsed = System.currentTimeMillis() - frameStart
                val sleepMs = frameIntervalMs - elapsed
                if (sleepMs > 0) {
                    Thread.sleep(sleepMs)
                }
            }

            codec.signalEndOfInputStream()

            drain@ while (true) {
                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break@drain
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { }
                    outIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outIndex)
                        if (muxerStarted && trackIndex >= 0 && outputBuffer != null) {
                            muxer?.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break@drain
                        }
                    }
                }
            }

            Log.i(TAG, "Encoded ${frames.size} frames -> ${outputFile.absolutePath} (${outputFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Encoding failed", e)
            outputFile.delete()
        } finally {
            try { codec.stop() } catch (_: Exception) { }
            try { codec.release() } catch (_: Exception) { }
            try { muxer?.stop() } catch (_: Exception) { }
            try { muxer?.release() } catch (_: Exception) { }
        }
    }
}
