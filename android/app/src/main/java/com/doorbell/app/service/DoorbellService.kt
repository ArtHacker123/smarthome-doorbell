package com.doorbell.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.doorbell.app.DoorbellApplication
import com.doorbell.app.data.ConfigRepository
import com.doorbell.app.detection.PersonDetector
import com.doorbell.app.detection.MotionDetector
import com.doorbell.app.model.*
import com.doorbell.app.network.RtspServer
import com.doorbell.app.network.WebSocketClient
import com.doorbell.app.storage.ThumbnailManager
import com.doorbell.app.storage.VideoStorageManager
import com.doorbell.app.ui.MainActivity
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DoorbellService : Service() {
    private val TAG = "DoorbellService"

    private lateinit var configRepository: ConfigRepository
    private lateinit var config: DoorbellConfig

    private var personDetector: PersonDetector? = null
    private var motionDetector: MotionDetector? = null
    private var videoStorage: VideoStorageManager? = null
    private var thumbnailManager: ThumbnailManager? = null
    private var webSocketClient: WebSocketClient? = null
    private var rtspServer: RtspServer? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _serviceState = MutableStateFlow(ServiceState.SLEEP)
    val serviceState: StateFlow<ServiceState> = _serviceState

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _events = MutableStateFlow<List<DetectionEvent>>(emptyList())
    val events: StateFlow<List<DetectionEvent>> = _events

    private val _connectionState = MutableStateFlow(WebSocketClient.ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WebSocketClient.ConnectionState> = _connectionState

    private var lastDetectionTime = 0L
    private var recordingJob: Job? = null
    private var cooldownJob: Job? = null
    private var cleanupJob: Job? = null
    private var batteryCheckJob: Job? = null
    private var screenWakeLock: PowerManager.WakeLock? = null
    private var screenOffJob: Job? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysisUseCase: ImageAnalysis? = null
    private var cameraExecutor: ExecutorService? = null
    private var lastFrameTime = 0L

    private val cameraLifecycleOwner = object : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        fun start() { registry.currentState = Lifecycle.State.STARTED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
        override val lifecycle: Lifecycle get() = registry
    }

    private var deviceId: String = ""
    private var currentVideoFile: java.io.File? = null
    private var currentThumbDir: java.io.File? = null
    private var thumbnailIndex = 0

    private val isServiceRunning = AtomicBoolean(false)

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.doorbell.app.START"
        const val ACTION_STOP = "com.doorbell.app.STOP"

        var currentInstance: DoorbellService? = null

        private val _connectionStateState = MutableStateFlow(WebSocketClient.ConnectionState.DISCONNECTED)
        val connectionStateState: StateFlow<WebSocketClient.ConnectionState> = _connectionStateState.asStateFlow()

        private val _serviceStateState = MutableStateFlow(ServiceState.SLEEP)
        val serviceStateState: StateFlow<ServiceState> = _serviceStateState.asStateFlow()

        private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
        val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, DoorbellService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DoorbellService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        configRepository = ConfigRepository(this)
        videoStorage = VideoStorageManager(this)
        thumbnailManager = ThumbnailManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForegroundService()
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        if (isServiceRunning.get()) return

        acquireWakeLock()
        startForeground(NOTIFICATION_ID, createNotification())

        isServiceRunning.set(true)

        serviceScope.launch {
            connectionState.collect { _connectionStateState.value = it }
        }

        serviceScope.launch {
            serviceState.collect { _serviceStateState.value = it }
        }

        serviceScope.launch {
            initializeServices()
        }

        scheduleCleanup()
        scheduleBatteryCheck()
    }

    private suspend fun initializeServices() {
        config = configRepository.configFlow.first()

        deviceId = configRepository.deviceIdFlow.first()
        if (deviceId.isEmpty()) {
            deviceId = UUID.randomUUID().toString()
            configRepository.setDeviceId(deviceId)
        }

        personDetector = PersonDetector(config)
        motionDetector = MotionDetector(config.motionSensitivity, config.motionPixelThreshold)

        if (config.pcServerAddress.isNotEmpty()) {
            webSocketClient = WebSocketClient(config.pcServerAddress, config.pcServerPort)
            webSocketClient?.connectionState?.onEach { _connectionState.value = it }?.launchIn(serviceScope)
            webSocketClient?.connect()
        }

        rtspServer = RtspServer(this, config.rtspPort)
        rtspServer?.start()

        initializeCamera()

        _serviceState.value = ServiceState.SURVEILLANCE
        currentInstance = this@DoorbellService
        Log.i(TAG, "Doorbell service started")
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, DoorbellService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, DoorbellApplication.CHANNEL_ID)
            .setContentTitle("Smart Doorbell")
            .setContentText("Monitoring active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun initializeCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        cameraLifecycleOwner.start()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                imageAnalysisUseCase = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor!!) { imageProxy ->
                            Log.d(TAG, "[FRAME] received, state=${_serviceState.value}")
                            val now = System.currentTimeMillis()
                            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                            val screenOn = pm.isInteractive()
                            val interval = when {
                                _serviceState.value == ServiceState.RECORDING -> 33L
                                screenOn -> 33L
                                else -> 333L
                            }
                            if (now - lastFrameTime < interval) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            lastFrameTime = now

                            try {
                                val bitmap = imageProxyToBitmap(imageProxy)
                                Log.d(TAG, "[BITMAP] conversion ok, size=${bitmap?.width}x${bitmap?.height}")
                                imageProxy.close()
                                bitmap?.let { processFrame(it) }
                            } catch (e: Exception) {
                                Log.e(TAG, "[BITMAP] conversion failed: ${e.message}")
                                imageProxy.close()
                            }
                        }
                    }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    cameraLifecycleOwner,
                    cameraSelector,
                    imageAnalysisUseCase
                )

            } catch (e: Exception) {
                Log.e(TAG, "Camera init failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val img = imageProxy.image ?: return null
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val planes = img.planes
        val width = img.width
        val height = img.height

        val yBuffer = planes[0].buffer
        val yRowStride = planes[0].rowStride

        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        val uPixelStride = planes[1].pixelStride
        val vPixelStride = planes[2].pixelStride
        val uRowStride = planes[1].rowStride
        val vRowStride = planes[2].rowStride

        val ySize = width * height
        val uvHeight = height / 2
        val nv21 = ByteArray(ySize + width * uvHeight)

        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            yBuffer.get(nv21, row * width, width)
        }

        var uvPos = ySize
        for (row in 0 until uvHeight) {
            for (col in 0 until width / 2) {
                nv21[uvPos++] = vBuffer.get(row * vRowStride + col * vPixelStride)
                nv21[uvPos++] = uBuffer.get(row * uRowStride + col * uPixelStride)
            }
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 80, out)
        val jpegData = out.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size) ?: return null

        return if (rotationDegrees != 0) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    private fun processFrame(bitmap: Bitmap) {
        _previewBitmap.value = bitmap
        Log.d(TAG, "[PREVIEW] updated preview bitmap")

        val motion = motionDetector?.detect(bitmap) == true
        Log.d(TAG, "[MOTION] detect=$motion")
        if (motion) {
            Log.d(TAG, "[SCREEN] calling acquireScreenWakeLock")
            acquireScreenWakeLock()
        }

        if (_serviceState.value == ServiceState.COOLDOWN) return

        personDetector?.processFrame(bitmap) {
            onPersonDetected()
        }
    }

    private fun onPersonDetected() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime < config.cooldown * 1000) {
            return
        }
        lastDetectionTime = currentTime

        acquireScreenWakeLock()
        _serviceState.value = ServiceState.RECORDING

        serviceScope.launch {
            startRecording()
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireScreenWakeLock() {
        Log.d(TAG, "[SCREEN] acquiring wake lock")
        screenOffJob?.cancel()
        screenOffJob = serviceScope.launch {
            delay(30 * 1000L)
            releaseScreenWakeLock()
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (screenWakeLock == null) {
            screenWakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "SmartDoorbell::Screen"
            )
        }
        screenWakeLock?.acquire(30 * 1000L)
        Log.d(TAG, "[SCREEN] wake lock acquired, hasLock=${screenWakeLock != null}")
    }

    @Suppress("DEPRECATION")
    private fun releaseScreenWakeLock() {
        Log.d(TAG, "[SCREEN] releasing wake lock")
        screenWakeLock?.release()
        screenWakeLock = null
        screenOffJob = null
    }

    private suspend fun startRecording() {
        _isRecording.value = true

        val videoFile = videoStorage?.createVideoFile()
        val thumbDir = videoStorage?.createThumbnailDir()

        currentVideoFile = videoFile
        currentThumbDir = thumbDir
        thumbnailIndex = 0

        val eventId = UUID.randomUUID().toString()
        val thumbnailPaths = mutableListOf<String>()

        recordingJob = serviceScope.launch {
            delay(config.recordDuration * 1000L)
            stopRecording(eventId, thumbnailPaths)
        }

        for (i in 1..3) {
            delay(500)
        }

        _serviceState.value = ServiceState.SURVEILLANCE
    }

    private suspend fun stopRecording(eventId: String, thumbnailPaths: List<String>) {
        _isRecording.value = false
        _serviceState.value = ServiceState.SURVEILLANCE

        val event = DetectionEvent(
            id = eventId,
            deviceId = deviceId,
            timestamp = System.currentTimeMillis(),
            type = EventType.PERSON_DETECTED,
            confidence = 0.95f,
            thumbnails = thumbnailPaths,
            duration = config.recordDuration
        )

        _events.value = _events.value + event
        webSocketClient?.sendEvent(event)

        startCooldown()
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = serviceScope.launch {
            delay(config.cooldown * 1000L)
            _serviceState.value = ServiceState.SURVEILLANCE
        }
        _serviceState.value = ServiceState.COOLDOWN
    }

    private fun scheduleCleanup() {
        cleanupJob?.cancel()
        cleanupJob = serviceScope.launch {
            while (isActive) {
                delay(60 * 60 * 1000L)
                videoStorage?.cleanupOldFiles()
            }
        }
    }

    private fun scheduleBatteryCheck() {
        batteryCheckJob?.cancel()
        batteryCheckJob = serviceScope.launch {
            while (isActive) {
                delay(5 * 60 * 1000L)
                checkBatteryLevel()
            }
        }
    }

    private fun checkBatteryLevel() {
        val batteryIntent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = level * 100 / scale.toFloat()

        if (batteryPct < 20) {
            val event = DetectionEvent(
                id = UUID.randomUUID().toString(),
                deviceId = deviceId,
                timestamp = System.currentTimeMillis(),
                type = EventType.LOW_BATTERY,
                confidence = batteryPct / 100f
            )
            _events.value = _events.value + event
            webSocketClient?.sendEvent(event)
        }
    }

    suspend fun checkStorageLevel() {
        if (videoStorage?.isStorageLow() == true) {
            val event = DetectionEvent(
                id = UUID.randomUUID().toString(),
                deviceId = deviceId,
                timestamp = System.currentTimeMillis(),
                type = EventType.STORAGE_LOW
            )
            _events.value = _events.value + event
            webSocketClient?.sendEvent(event)
        }
    }

    fun updateConfig(newConfig: DoorbellConfig) {
        config = newConfig
        personDetector?.updateConfig(newConfig)
        motionDetector?.let {
            it.sensitivity = newConfig.motionSensitivity
            it.pixelThreshold = newConfig.motionPixelThreshold
            it.reset()
        }
    }

    fun requestIgnoreBatteryOptimization(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(packageName)
        }
        return true
    }

    fun requestIgnoreBatteryOptimizationStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SmartDoorbell::WakeLock"
        ).apply {
            acquire(10 * 60 * 60 * 1000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isServiceRunning.set(false)
        serviceScope.cancel()

        cameraLifecycleOwner.destroy()
        cameraProvider?.unbindAll()
        cameraProvider = null
        cameraExecutor?.shutdown()
        cameraExecutor = null

        wakeLock?.release()
        releaseScreenWakeLock()
        personDetector?.close()
        motionDetector?.reset()
        webSocketClient?.disconnect()
        rtspServer?.stop()

        Log.i(TAG, "Doorbell service stopped")
        currentInstance = null
        super.onDestroy()
    }
}
