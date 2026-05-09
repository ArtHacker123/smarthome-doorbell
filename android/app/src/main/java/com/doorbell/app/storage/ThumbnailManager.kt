package com.doorbell.app.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ThumbnailManager(private val context: Context) {
    private val TAG = "ThumbnailManager"

    suspend fun saveThumbnail(bitmap: Bitmap, outputDir: File, index: Int): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(outputDir, "thumb_$index.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save thumbnail", e)
            null
        }
    }

    suspend fun loadThumbnail(path: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load thumbnail", e)
            null
        }
    }

    fun deleteThumbnail(path: String) {
        try {
            File(path).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete thumbnail", e)
        }
    }
}
