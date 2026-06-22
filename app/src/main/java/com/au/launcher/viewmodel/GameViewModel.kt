package com.au.launcher.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.au.launcher.api.ConfigResponse
import com.au.launcher.api.GameModel
import com.au.launcher.api.GameRepository
import com.au.launcher.api.LocalizedString
import com.au.launcher.utils.DownloadManager
import com.au.launcher.utils.DownloadState
import com.au.launcher.utils.PackageUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameRepository(application)
    private var rawGames: List<GameModel> = emptyList()
    private var filteredGames: List<GameModel> = emptyList()
    private val downloadManager = DownloadManager(application)
    private val prefs = application.getSharedPreferences("dialog_prefs", Context.MODE_PRIVATE)

    private val _pagedGames = MutableStateFlow<List<GameModel>>(emptyList())
    val pagedGames: StateFlow<List<GameModel>> = _pagedGames

    // New state for dialogs
    private val _announcementToShow = MutableStateFlow<LocalizedString?>(null)
    val announcementToShow: StateFlow<LocalizedString?> = _announcementToShow

    private val _updateToShow = MutableStateFlow<ConfigResponse?>(null)
    val updateToShow: StateFlow<ConfigResponse?> = _updateToShow

    // Add a flow to track installed packages changes
    private val _installedPackagesUpdate = MutableStateFlow(System.currentTimeMillis())
    val installedPackagesUpdate: StateFlow<Long> = _installedPackagesUpdate

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isMoreLoading = MutableStateFlow(false)
    val isMoreLoading: StateFlow<Boolean> = _isMoreLoading

    private val _currentCategory = MutableStateFlow("ALL")
    val currentCategory: StateFlow<String> = _currentCategory

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates

    private val PAGE_SIZE = 8

    init {
        android.util.Log.d("GameViewModel", "GameViewModel initialized with version ${com.au.launcher.BuildConfig.VERSION_NAME}")
        refreshGames()
        observeDownloadStates()
        observePackageChanges()
    }

    private fun isNewerVersion(newVersion: String, currentVersion: String): Boolean {
        return try {
            val newParts = newVersion.split(".").map { it.toInt() }
            val currentParts = currentVersion.split(".").map { it.toInt() }
            val length = maxOf(newParts.size, currentParts.size)
            for (i in 0 until length) {
                val v1 = if (i < newParts.size) newParts[i] else 0
                val v2 = if (i < currentParts.size) currentParts[i] else 0
                if (v1 > v2) return true
                if (v1 < v2) return false
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun checkForDialogs(config: ConfigResponse) {
        val lastAnnEn = prefs.getString("last_ann_en", "")
        val lastAnnZh = prefs.getString("last_ann_zh", "")
        val lastVersion = prefs.getString("last_version", "")
        val currentVersion = com.au.launcher.BuildConfig.VERSION_NAME

        if (config.announcement.en != lastAnnEn || config.announcement.zh != lastAnnZh) {
            _announcementToShow.value = config.announcement
        }

        if (isNewerVersion(config.newestVersion, currentVersion) && config.newestVersion != lastVersion) {
            _updateToShow.value = config
        }
    }

    fun dismissAnnouncement() {
        _announcementToShow.value?.let {
            prefs.edit()
                .putString("last_ann_en", it.en)
                .putString("last_ann_zh", it.zh)
                .apply()
        }
        _announcementToShow.value = null
    }

    fun dismissUpdate() {
        _updateToShow.value?.let {
            prefs.edit()
                .putString("last_version", it.newestVersion)
                .apply()
        }
        _updateToShow.value = null
    }

    fun neverShowUpdateAgain(version: String) {
        prefs.edit()
            .putString("last_version", version)
            .apply()
        _updateToShow.value = null
    }

    private fun observePackageChanges() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                val packageName = intent?.data?.schemeSpecificPart

                if (action == Intent.ACTION_PACKAGE_ADDED && packageName != null) {
                    val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    if (!isReplacing) {
                        viewModelScope.launch {
                            repository.incrementHotScore(packageName)
                            // Force refresh from network after incrementing
                            refreshGames(force = true)
                        }
                    }
                    
                    // Cleanup APK if enabled
                    val settingsPrefs = getApplication<Application>().getSharedPreferences("settings", Context.MODE_PRIVATE)
                    val deleteAfterInstall = settingsPrefs.getBoolean("delete_after_install", true)
                    if (deleteAfterInstall) {
                        val cleanupPrefs = getApplication<Application>().getSharedPreferences("install_cleanup", Context.MODE_PRIVATE)
                        val apkPath = cleanupPrefs.getString(packageName, null)
                        if (apkPath != null) {
                            val file = java.io.File(apkPath)
                            if (file.exists()) {
                                val deleted = file.delete()
                                android.util.Log.d("GameViewModel", "Deleted APK after install: $apkPath, success: $deleted")
                            }
                            cleanupPrefs.edit().remove(packageName).apply()
                        }
                    }
                }

                viewModelScope.launch {
                    _installedPackagesUpdate.value = System.currentTimeMillis()
                }
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            getApplication<Application>().registerReceiver(receiver, filter)
        }
    }

    private fun observeDownloadStates() {
        viewModelScope.launch {
            downloadManager.downloadStates.collect { states ->
                _downloadStates.value = states
            }
        }
    }

    fun handleGameAction(gameId: String, game: GameModel) {
        android.util.Log.d("GameViewModel", "handleGameAction called for: $gameId, installed: ${isInstalled(gameId)}")
        if (isInstalled(gameId)) {
            launchApp(gameId, game.packageName)
        } else {
            toggleDownload(gameId, game)
        }
    }

    private fun toggleDownload(gameId: String, game: GameModel) {
        val currentState = downloadManager.getDownloadState(gameId)
        android.util.Log.d("GameViewModel", "toggleDownload: $gameId, currentState: $currentState")

        when {
            currentState?.isDownloading == true -> {
                android.util.Log.d("GameViewModel", "Pausing download for: $gameId")
                downloadManager.pauseDownload(gameId)
            }
            currentState?.isPaused == true -> {
                val downloadUrl = "${com.au.launcher.utils.Constants.DOWNLOAD_URL}v${game.version}/${gameId}.apk"
                android.util.Log.d("GameViewModel", "Resuming download: $downloadUrl")
                downloadManager.startDownload(gameId, downloadUrl, "$gameId.apk")
            }
            else -> {
                val downloadUrl = "${com.au.launcher.utils.Constants.DOWNLOAD_URL}v${game.version}/${gameId}.apk"
                android.util.Log.d("GameViewModel", "Starting new download: $downloadUrl")
                downloadManager.startDownload(gameId, downloadUrl, "$gameId.apk")
            }
        }
    }

    private fun launchApp(gameId: String, packageName: String?) {
        val pkg = packageName ?: PackageUtils.getPackageNameFromId(gameId)
        PackageUtils.launchApp(getApplication(), pkg)
    }

    fun refreshGames(force: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Use Flow for "Cache First, then Network" strategy
            repository.getGamesFlow(force).collect { games ->
                android.util.Log.d("GameViewModel", "Fetched ${games.size} games from repository")
                val config = repository.getCachedConfig()
                android.util.Log.d("GameViewModel", "Cached Config: $config")
                
                rawGames = games
                applyFilters()
                // Initial check for dialogs when first data arrives
                if (config != null) checkForDialogs(config)

                _isLoading.value = false
            }
        }
    }

    fun setCategory(category: String) {
        if (_currentCategory.value == category) return
        _currentCategory.value = category
        applyFilters()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    private fun applyFilters() {
        val category = _currentCategory.value
        val query = _searchQuery.value.trim().lowercase()
        android.util.Log.d("GameViewModel", "Applying filters for category: $category, query: $query")

        // 1. Category filter & sorting
        var result = when (category) {
            "INSTALLED" -> rawGames.filter { isInstalled(it.id) }
            "HOT" -> {
                val sorted = rawGames.sortedByDescending { it.hotScore }
                // Log the top 5 games to verify sorting
                android.util.Log.d("GameViewModel", "--- HOT Category Sorting Verification ---")
                sorted.take(5).forEachIndexed { index, game ->
                    android.util.Log.d("GameViewModel", "#${index + 1}: ${game.name.zh} (Score: ${game.hotScore}, ID: ${game.id})")
                }
                sorted
            }
            "NEW" -> rawGames.sortedByDescending { it.publishTime }
            else -> rawGames
        }

        // 2. Search filter
        if (query.isNotEmpty()) {
            result = result.filter { game ->
                game.name.zh.lowercase().contains(query) ||
                game.name.en.lowercase().contains(query) ||
                game.author.zh.lowercase().contains(query) ||
                game.author.en.lowercase().contains(query) ||
                game.engine.lowercase().contains(query)
            }
        }

        filteredGames = result
        _pagedGames.value = filteredGames.take(PAGE_SIZE)
    }

    fun loadNextPage() {
        if (_isMoreLoading.value || _pagedGames.value.size >= filteredGames.size) return

        viewModelScope.launch {
            _isMoreLoading.value = true
            delay(500)
            val currentSize = _pagedGames.value.size
            val nextBatch = filteredGames.drop(currentSize).take(PAGE_SIZE)
            _pagedGames.value = _pagedGames.value + nextBatch
            _isMoreLoading.value = false
        }
    }

    fun isInstalled(id: String): Boolean {
        if (id.startsWith("local_")) return true
        val packageName = PackageUtils.getPackageNameFromId(id)
        return PackageUtils.isPackageInstalled(getApplication(), packageName)
    }

    fun hasMore(): Boolean {
        return _pagedGames.value.size < filteredGames.size
    }

    fun removeLocalGame(id: String) {
        viewModelScope.launch {
            repository.removeLocalGameById(id)
            refreshGames()
        }
    }
}
