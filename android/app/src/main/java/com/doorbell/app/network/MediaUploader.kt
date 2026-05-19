package com.doorbell.app.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class MediaUploader(
    private val serverAddress: String,
    private val serverPort: Int
) {
    private val TAG = "MediaUploader"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String get() = "http://$serverAddress:$serverPort"

    suspend fun uploadSnapshot(eventId: String, file: File): String? = withContext(Dispatchers.IO) {
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("event_id", eventId)
                .addFormDataPart("file", file.name, RequestBody.create(MediaType.get("image/jpeg"), file))
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/upload/snapshot")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val bodyStr = response.body()?.string()
            if (response.isSuccessful) {
                Log.i(TAG, "Snapshot uploaded: event=$eventId -> $bodyStr")
                bodyStr
            } else {
                Log.w(TAG, "Snapshot upload failed: ${response.code} $bodyStr")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Snapshot upload error", e)
            null
        }
    }

    suspend fun uploadVideo(eventId: String, file: File): String? = withContext(Dispatchers.IO) {
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("event_id", eventId)
                .addFormDataPart("file", file.name, RequestBody.create(MediaType.get("video/mp4"), file))
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/upload/video")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val bodyStr = response.body()?.string()
            if (response.isSuccessful) {
                Log.i(TAG, "Video uploaded: event=$eventId -> $bodyStr")
                bodyStr
            } else {
                Log.w(TAG, "Video upload failed: ${response.code} $bodyStr")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Video upload error", e)
            null
        }
    }
}
