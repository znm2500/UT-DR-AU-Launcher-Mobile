package com.au.launcher.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.au.launcher.api.GameModel
import com.au.launcher.api.GameRepository
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

    private val _pagedGames = MutableStateFlow<List<GameModel>>(emptyList())
    val pagedGames: StateFlow<List<GameModel>> = _pagedGames

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
        refreshGames()
        observeDownloadStates()
        observePackageChanges()
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
                viewModelScope.launch {
                    _installedPackagesUpdate.value = System.currentTimeMillis()
                }
            }
        }

        getApplication<Application>().registerReceiver(receiver, filter)
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
            rawGames = repository.getGames(force)
            applyFilters()
            _isLoading.value = false
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

        // 1. Category filter & sorting
        var result = when (category) {
            "INSTALLED" -> rawGames.filter { isInstalled(it.id) }
            "HOT" -> rawGames.sortedByDescending { it.hotScore }
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
}
