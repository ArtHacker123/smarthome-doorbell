package com.doorbell.app.network

import com.doorbell.app.model.DetectionEvent
import com.doorbell.app.model.DeviceInfo
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

class WebSocketClient(
    private val serverAddress: String,
    private val serverPort: Int
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _events = MutableStateFlow<List<DetectionEvent>>(emptyList())
    val events: StateFlow<List<DetectionEvent>> = _events

    private var reconnectJob: Job? = null
    private var pingJob: Job? = null

    var deviceInfo: DeviceInfo? = null

    enum class ConnectionState {
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        ERROR
    }

    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTING) return

        _connectionState.value = ConnectionState.CONNECTING

        val wsUrl = "ws://$serverAddress:$serverPort/ws"
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.CONNECTED
                deviceInfo?.let { registerDevice(it) }
                startPing()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.ERROR
                scheduleReconnect()
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val message = gson.fromJson(text, ServerMessage::class.java)
            when (message.type) {
                "config_update" -> {
                    // Configuration update from server
                }
                "ping" -> {
                    webSocket?.send(gson.toJson(mapOf("type" to "pong")))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendEvent(event: DetectionEvent) {
        val json = gson.toJson(
            mapOf(
                "type" to "event",
                "data" to event
            )
        )
        webSocket?.send(json)
    }

    fun registerDevice(deviceInfo: DeviceInfo) {
        val json = gson.toJson(
            mapOf(
                "type" to "register",
                "data" to deviceInfo
            )
        )
        webSocket?.send(json)
    }

    private fun startPing() {
        pingJob?.cancel()
        pingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(30000)
                webSocket?.send(gson.toJson(mapOf("type" to "ping")))
            }
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            delay(5000)
            connect()
        }
    }

    fun disconnect() {
        pingJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client closing")
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    data class ServerMessage(
        val type: String,
        val data: Any?
    )
}
