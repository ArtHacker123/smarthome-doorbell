package com.doorbell.app.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.doorbell.app.model.Recording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VideoStorageManager(
    private val context: Context,
    private val retentionDays: Int = 30
) {
    private val TAG = "VideoStorageManager"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val videosDir: File
        get() {
            val dir = File(context.filesDir, "videos")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val thumbnailsDir: File
        get() {
            val dir = File(context.filesDir, "thumbnails")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    suspend fun createVideoFile(): File = withContext(Dispatchers.IO) {
        val today = dateFormat.format(Date())
        val dayDir = File(videosDir, today)
        if (!dayDir.exists()) dayDir.mkdirs()

        val timestamp = System.currentTimeMillis()
        val file = File(dayDir, "$timestamp.mp4")
        file.createNewFile()
        file
    }

    suspend fun createThumbnailDir(): File = withContext(Dispatchers.IO) {
        val today = dateFormat.format(Date())
        val dayDir = File(thumbnailsDir, today)
        if (!dayDir.exists()) dayDir.mkdirs()

        val timestamp = System.currentTimeMillis()
        val dir = File(dayDir, timestamp.toString())
        dir.mkdirs()
        dir
    }

    suspend fun cleanupOldFiles() = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)

        cleanupDirectory(videosDir, cutoffTime)
        cleanupDirectory(thumbnailsDir, cutoffTime)

        Log.i(TAG, "Storage cleanup completed")
    }

    private fun cleanupDirectory(dir: File, cutoffTime: Long) {
        if (!dir.exists()) return

        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoffTime) {
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
                Log.i(TAG, "Deleted old file: ${file.absolutePath}")
            }
        }
    }

    suspend fun getAvailableStorage(): Long = withContext(Dispatchers.IO) {
        try {
            val stat = StatFs(context.filesDir.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun isStorageLow(): Boolean {
        val availableMB = getAvailableStorage() / (1024 * 1024)
        return availableMB < 100
    }

    fun getRecordings(): List<Recording> {
        val recordings = mutableListOf<Recording>()

        videosDir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension == "mp4") {
                val dateStr = file.parentFile?.name ?: ""
                val timestamp = file.nameWithoutExtension.toLongOrNull() ?: 0L
                val thumbDir = File(thumbnailsDir, dateStr)

                recordings.add(
                    Recording(
                        id = file.nameWithoutExtension,
                        timestamp = timestamp,
                        duration = 10,
                        filePath = file.absolutePath,
                        thumbnailPaths = thumbDir.listFiles()
                            ?.filter { it.extension == "jpg" }
                            ?.sortedBy { it.name }
                            ?.map { it.absolutePath }
                            ?: emptyList()
                    )
                )
            }
        }

        return recordings.sortedByDescending { it.timestamp }
    }

    suspend fun getVideoFile(path: String): File? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.exists()) file else null
    }

    suspend fun deleteRecording(recording: Recording) = withContext(Dispatchers.IO) {
        File(recording.filePath).delete()
        recording.thumbnailPaths.forEach { File(it).delete() }
    }
}
