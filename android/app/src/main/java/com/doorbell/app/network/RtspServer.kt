package com.doorbell.app.network

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer

class RtspServer(
    private val context: Context,
    private val port: Int = 8554
) {
    private val TAG = "RtspServer"
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var videoEncoder: MediaCodec? = null
    private var outputStream: FileOutputStream? = null
    private var mediaMuxer: MediaMuxer? = null

    private var sessionId: String = ""
    private var videoTrackIndex: Int = -1
    private var isMuxerStarted = false

    private val sdpContent = """
        v=0
        o=- 0 0 IN IP4 127.0.0.1
        s=Session
        c=IN IP4 0.0.0.0
        t=0 0
        m=video 0 RTP/AVP 96
        a=rtpmap:96 H264/90000
        a=fmtp:96 profile-level-id=42E01E;packetization-mode=1
    """.trimIndent()

    fun start() {
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                Log.i(TAG, "RTSP server started on port $port")

                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    scope.launch { handleClient(client) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "RTSP server error", e)
            }
        }
    }

    private suspend fun handleClient(client: Socket) {
        try {
            val reader = client.getInputStream().bufferedReader()
            val writer = client.getOutputStream().bufferedWriter()

            var request = StringBuilder()
            var contentLength = 0
            var method = ""
            var url = ""
            var cseq = 0

            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break

                if (line.startsWith("OPTIONS") || line.startsWith("DESCRIBE") ||
                    line.startsWith("SETUP") || line.startsWith("PLAY") ||
                    line.startsWith("TEARDOWN")) {
                    val parts = line.split(" ")
                    method = parts[0]
                    if (parts.size > 1) url = parts[1]
                }

                if (line.startsWith("CSeq:")) {
                    cseq = line.substringAfter("CSeq:").trim().toIntOrNull() ?: 0
                }

                if (line.startsWith("Content-Length:")) {
                    contentLength = line.substringAfter("Content-Length:").trim().toIntOrNull() ?: 0
                }
            }

            if (contentLength > 0) {
                reader.read(charArrayOf(*CharArray(contentLength)))
            }

            when (method) {
                "OPTIONS" -> sendResponse(writer, cseq, "Public: OPTIONS, DESCRIBE, SETUP, PLAY, TEARDOWN")
                "DESCRIBE" -> sendDescribeResponse(writer, cseq)
                "SETUP" -> {
                    sessionId = "12345678"
                    sendSetupResponse(writer, cseq, sessionId)
                }
                "PLAY" -> sendPlayResponse(writer, cseq, sessionId)
                "TEARDOWN" -> sendTeardownResponse(writer, cseq, sessionId)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Client handling error", e)
        } finally {
            client.close()
        }
    }

    private fun sendResponse(writer: java.io.BufferedWriter, cseq: Int, headers: String) {
        val response = "RTSP/1.0 200 OK\r\nCSeq: $cseq\r\n$headers\r\n\r\n"
        writer.write(response)
        writer.flush()
    }

    private fun sendDescribeResponse(writer: java.io.BufferedWriter, cseq: Int) {
        val response = buildString {
            append("RTSP/1.0 200 OK\r\n")
            append("CSeq: $cseq\r\n")
            append("Content-Type: application/sdp\r\n")
            append("Content-Length: ${sdpContent.length}\r\n")
            append("\r\n")
            append(sdpContent)
        }
        writer.write(response)
        writer.flush()
    }

    private fun sendSetupResponse(writer: java.io.BufferedWriter, cseq: Int, session: String) {
        val response = buildString {
            append("RTSP/1.0 200 OK\r\n")
            append("CSeq: $cseq\r\n")
            append("Session: $session\r\n")
            append("Transport: RTP/AVP;unicast;client_port=5000-5001\r\n")
            append("\r\n")
        }
        writer.write(response)
        writer.flush()
    }

    private fun sendPlayResponse(writer: java.io.BufferedWriter, cseq: Int, session: String) {
        val response = buildString {
            append("RTSP/1.0 200 OK\r\n")
            append("CSeq: $cseq\r\n")
            append("Session: $session\r\n")
            append("RTP-Info: url=rtsp://localhost:$port/live\r\n")
            append("\r\n")
        }
        writer.write(response)
        writer.flush()
    }

    private fun sendTeardownResponse(writer: java.io.BufferedWriter, cseq: Int, session: String) {
        val response = buildString {
            append("RTSP/1.0 200 OK\r\n")
            append("CSeq: $cseq\r\n")
            append("Session: $session\r\n")
            append("\r\n")
        }
        writer.write(response)
        writer.flush()
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        videoEncoder?.stop()
        videoEncoder?.release()
        mediaMuxer?.release()
        scope.cancel()
        Log.i(TAG, "RTSP server stopped")
    }
}
