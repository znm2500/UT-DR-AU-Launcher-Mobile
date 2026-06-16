package com.au.launcher.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
private val Context.dataStore by preferencesDataStore(
    name = "downloads"
)
data class DownloadState(
    val isDownloading: Boolean = false,
    val isPaused: Boolean = false,
    val progress: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val downloadId: Long? = null,
    val error: String? = null
)

private class PauseException : IOException("Download paused")

class DownloadManager(private val context: Context) {

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates

    private val downloadStatesMap = mutableMapOf<String, DownloadState>()
    private val downloadJobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // 直接创建 DataStore，不需要扩展属性
    private val persistence = DownloadPersistence(context.dataStore)

    init {
        scope.launch {
            restorePendingDownloads()
        }
    }

    fun startDownload(gameId: String, url: String, fileName: String) {
        val existing = downloadStatesMap[gameId]
        when {
            existing?.isPaused == true -> {
                resumeDownload(gameId, url, fileName)
            }
            existing?.isDownloading == true -> {
                pauseDownload(gameId)
            }
            else -> {
                startNewDownload(gameId, url, fileName)
            }
        }
    }

    private fun startNewDownload(gameId: String, url: String, fileName: String) {
        val saveDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val savePath = File(saveDir, fileName).absolutePath

        val initialState = DownloadState(
            isDownloading = true,
            isPaused = false,
            progress = 0,
            downloadedBytes = 0,
            totalBytes = 0
        )
        downloadStatesMap[gameId] = initialState
        _downloadStates.value = downloadStatesMap.toMap()

        val job = scope.launch {
            performDownload(gameId, url, savePath)
        }
        downloadJobs[gameId] = job
    }

    private suspend fun performDownload(gameId: String, url: String, savePath: String) {
        val record = persistence.load(gameId)
        var startByte = record?.downloadedBytes ?: 0L
        val totalBytesFromRecord = record?.totalBytes ?: -1L

        val request = Request.Builder()
            .url(url)
            .addHeader("Range", "bytes=$startByte-")
            .build()

        var isPausedByUser = false

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Server error: ${response.code}")
            }

            val contentLength = response.body?.contentLength() ?: -1L
            val totalBytes = if (contentLength > 0) startByte + contentLength else totalBytesFromRecord

            val file = File(savePath)
            file.parentFile?.mkdirs()
            val raf = java.io.RandomAccessFile(file, "rw")
            raf.seek(startByte)

            response.body?.source()?.use { source ->
                val buffer = Buffer()
                var bytesRead: Long
                while (source.read(buffer, 8192).also { bytesRead = it } != -1L) {
                    if (downloadJobs[gameId]?.isCancelled == true) {
                        isPausedByUser = true
                        break
                    }
                    raf.write(buffer.readByteArray(), 0, bytesRead.toInt())
                    startByte += bytesRead

                    val progress = if (totalBytes > 0) (startByte * 100 / totalBytes).toInt() else 0
                    val currentState = downloadStatesMap[gameId] ?: break
                    val newState = currentState.copy(
                        progress = progress,
                        downloadedBytes = startByte,
                        totalBytes = totalBytes
                    )
                    downloadStatesMap[gameId] = newState
                    _downloadStates.value = downloadStatesMap.toMap()

                    persistence.save(gameId, url, startByte, totalBytes, savePath)
                    buffer.clear()
                }
            }
            raf.close()

            if (isPausedByUser) {
                val pausedState = downloadStatesMap[gameId]?.copy(
                    isDownloading = false,
                    isPaused = true
                ) ?: return
                downloadStatesMap[gameId] = pausedState
                _downloadStates.value = downloadStatesMap.toMap()
            } else {
                android.util.Log.d("DownloadManager", "Download complete: $gameId, savePath: $savePath")
                val completedState = downloadStatesMap[gameId]?.copy(
                    isDownloading = false,
                    isPaused = false,
                    progress = 100,
                    downloadedBytes = totalBytes,
                    totalBytes = totalBytes
                ) ?: return
                downloadStatesMap[gameId] = completedState
                _downloadStates.value = downloadStatesMap.toMap()
                persistence.remove(gameId)
                
                // Use Main dispatcher for UI-related activity start
                withContext(Dispatchers.Main) {
                    installApk(savePath, gameId)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadManager", "Download error: ${e.message}", e)
            if (downloadJobs[gameId]?.isCancelled == true) {
                val pausedState = downloadStatesMap[gameId]?.copy(
                    isDownloading = false,
                    isPaused = true
                ) ?: return
                downloadStatesMap[gameId] = pausedState
                _downloadStates.value = downloadStatesMap.toMap()
            } else {
                val errorState = downloadStatesMap[gameId]?.copy(
                    isDownloading = false,
                    isPaused = false,
                    error = e.message
                ) ?: return
                downloadStatesMap[gameId] = errorState
                _downloadStates.value = downloadStatesMap.toMap()
            }
        } finally {
            downloadJobs.remove(gameId)
        }
    }

    fun pauseDownload(gameId: String) {
        downloadJobs[gameId]?.cancel()
    }

    private fun resumeDownload(gameId: String, url: String, fileName: String) {
        val existing = downloadStatesMap[gameId] ?: return
        if (!existing.isPaused) return

        val saveDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val savePath = File(saveDir, fileName).absolutePath

        val resumedState = existing.copy(isDownloading = true, isPaused = false)
        downloadStatesMap[gameId] = resumedState
        _downloadStates.value = downloadStatesMap.toMap()

        val job = scope.launch {
            performDownload(gameId, url, savePath)
        }
        downloadJobs[gameId] = job
    }

    private suspend fun restorePendingDownloads() {
        val records = persistence.getAll()
        for (record in records) {
            val state = DownloadState(
                isDownloading = false,
                isPaused = true,
                progress = if (record.totalBytes > 0) (record.downloadedBytes * 100 / record.totalBytes).toInt() else 0,
                downloadedBytes = record.downloadedBytes,
                totalBytes = record.totalBytes,
                downloadId = null
            )
            downloadStatesMap[record.gameId] = state
        }
        _downloadStates.value = downloadStatesMap.toMap()
    }

    private fun installApk(filePath: String, gameId: String) {
        val apkFile = File(filePath)
        if (!apkFile.exists()) {
            android.util.Log.e("DownloadManager", "APK file does not exist: $filePath")
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            android.util.Log.d("DownloadManager", "Started install activity for: $gameId")
        } catch (e: Exception) {
            android.util.Log.e("DownloadManager", "Failed to start install activity", e)
        }

        downloadStatesMap.remove(gameId)
        _downloadStates.value = downloadStatesMap.toMap()
    }

    fun getDownloadState(gameId: String): DownloadState? = downloadStatesMap[gameId]

    fun cleanup() {
        scope.cancel()
    }

    private inner class DownloadPersistence(private val dataStore: DataStore<Preferences>) {
        suspend fun save(gameId: String, url: String, downloadedBytes: Long, totalBytes: Long, savePath: String) {
            val key = stringPreferencesKey(gameId)
            val value = "$url|$downloadedBytes|$totalBytes|$savePath"
            dataStore.edit { prefs ->
                prefs[key] = value
            }
        }

        suspend fun load(gameId: String): DownloadRecord? {
            val key = stringPreferencesKey(gameId)
            val prefs = dataStore.data.first()
            val value = prefs[key] ?: return null
            val parts = value.split("|")
            if (parts.size == 4) {
                return DownloadRecord(
                    gameId = gameId,
                    url = parts[0],
                    downloadedBytes = parts[1].toLong(),
                    totalBytes = parts[2].toLong(),
                    savePath = parts[3]
                )
            }
            return null
        }

        suspend fun remove(gameId: String) {
            val key = stringPreferencesKey(gameId)
            dataStore.edit { prefs ->
                prefs.remove(key)
            }
        }

        suspend fun getAll(): List<DownloadRecord> {
            val prefs = dataStore.data.first()
            return prefs.asMap().mapNotNull { (key, value) ->
                val gameId = key.name
                val str = value as? String ?: return@mapNotNull null
                val parts = str.split("|")
                if (parts.size == 4) {
                    DownloadRecord(
                        gameId = gameId,
                        url = parts[0],
                        downloadedBytes = parts[1].toLong(),
                        totalBytes = parts[2].toLong(),
                        savePath = parts[3]
                    )
                } else null
            }
        }
    }

    private data class DownloadRecord(
        val gameId: String,
        val url: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val savePath: String
    )
}