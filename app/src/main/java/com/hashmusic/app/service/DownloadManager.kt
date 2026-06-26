package com.hashmusic.app.service

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class DownloadManager(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    fun downloadSong(
        url: String,
        videoId: String,
        title: String,
        onProgress: (Int) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    "url" to url,
                    "videoId" to videoId,
                    "title" to title
                )
            )
            .build()
        
        WorkManager.getInstance(context).enqueue(workRequest)
        
        WorkManager.getInstance(context).getWorkInfoByIdLiveData(workRequest.id)
            .observeForever { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            val path = workInfo.outputData.getString("path") ?: ""
                            onComplete(path)
                        }
                        WorkInfo.State.FAILED -> {
                            onError("Download failed")
                        }
                        else -> {
                            val progress = workInfo.progress.getInt("progress", 0)
                            onProgress(progress)
                        }
                    }
                }
            }
    }
}

class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val url = inputData.getString("url") ?: return@withContext Result.failure()
                val videoId = inputData.getString("videoId") ?: return@withContext Result.failure()
                val title = inputData.getString("title") ?: "Unknown"
                
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
                
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) return@withContext Result.failure()
                
                val dir = File(applicationContext.filesDir, "downloads")
                if (!dir.exists()) dir.mkdirs()
                
                val file = File(dir, "${videoId}.m4a")
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        val totalSize = response.body?.contentLength() ?: 0
                        var downloadedSize = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedSize += bytesRead
                            if (totalSize > 0) {
                                val progress = ((downloadedSize * 100) / totalSize).toInt()
                                setProgress(workDataOf("progress" to progress))
                            }
                        }
                    }
                }
                
                Result.success(workDataOf("path" to file.absolutePath))
            } catch (e: Exception) {
                Log.e("DownloadWorker", "Download failed", e)
                Result.failure()
            }
        }
    }
}