package com.au.launcher.api

import android.content.Context
import com.au.launcher.db.GameDatabase
import com.au.launcher.db.GameEntity
import com.au.launcher.utils.Constants
import com.au.launcher.utils.PackageUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class GameRepository(
    private val context: Context
) {
    private val api: GameApi get() = RetrofitClient.gameApi
    private val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
    private val PREFS_NAME = "game_cache"
    private val KEY_CONFIG = "cached_config"
    private val KEY_TIMESTAMP = "cache_timestamp"
    private val CACHE_DURATION = 30 * 60 * 1000 // 30 minutes
    private val gameDao = GameDatabase.getDatabase(context).gameDao()

    fun getGamesFlow(forceRefresh: Boolean = false): Flow<List<GameModel>> = flow {
        // 1. Emits cached data immediately if it exists
        val cachedConfig = getCachedConfig()
        if (cachedConfig != null && !forceRefresh) {
            android.util.Log.d("GameRepository", "Emitting cached config")
            emit(combineWithLocalGames(cachedConfig))
        }

        // 2. Fetch from network
        try {
            val remoteConfig = if (Constants.isChinaRegion) {
                fetchConfigViaGitCode()
            } else {
                val relativeUrl = Constants.REPO_CONFIG_URL
                // Add timestamp to bypass CDN cache
                val fullUrl = if (relativeUrl.contains("?")) {
                    "$relativeUrl&t=${System.currentTimeMillis()}"
                } else {
                    "$relativeUrl?t=${System.currentTimeMillis()}"
                }
                android.util.Log.d("GameRepository", "Fetching remote config from Global URL: ${Constants.BASE_URL}$fullUrl")
                api.getConfig(fullUrl)
            }

            android.util.Log.d("GameRepository", "RAW REMOTE CONFIG: $remoteConfig")
            saveConfig(remoteConfig)
            emit(combineWithLocalGames(remoteConfig))
        } catch (e: Exception) {
            android.util.Log.e("GameRepository", "Network fetch failed", e)
            if (cachedConfig == null) {
                emit(combineWithLocalGames(ConfigResponse("", LocalizedString("", ""), LocalizedString("", ""), emptyList())))
            }
        }
    }

    private suspend fun fetchConfigViaGitCode(): ConfigResponse {
        val owner = "znm1145"
        val repo = "AUL-Mobile-Repo"
        val path = "config.json"
        val branch = "data"
        val token = com.au.launcher.BuildConfig.GITCODE_TOKEN

        android.util.Log.d("GameRepository", "Fetching config via GitCode API: $owner/$repo/$path")
        val fileResponse = RetrofitClient.gitCodeApi.getFileV5(owner, repo, path, branch, token.takeIf { it.isNotEmpty() })
        
        val contentStr = fileResponse.content.replace("\n", "")
        val decodedBytes = android.util.Base64.decode(contentStr, android.util.Base64.DEFAULT)
        val jsonString = String(decodedBytes, Charsets.UTF_8)
        
        return gson.fromJson(jsonString, ConfigResponse::class.java)
    }

    private suspend fun combineWithLocalGames(config: ConfigResponse): List<GameModel> {
        val remoteGames = config.games
        android.util.Log.d("GameRepository", "Combining games. Remote games: ${remoteGames.size}")
        remoteGames.forEach { 
            android.util.Log.v("GameRepository", "Remote game: ${it.id}, score: ${it.hotScore}")
        }

        val allLocalEntities = gameDao.getAllLocalGames()
        val validLocalGames = mutableListOf<GameModel>()
        
        allLocalEntities.forEach { entity ->
            if (PackageUtils.isPackageInstalled(context, entity.packageName)) {
                validLocalGames.add(
                    GameModel(
                        id = entity.id,
                        name = LocalizedString(entity.name, entity.name),
                        author = LocalizedString(entity.author, entity.author),
                        engine = entity.engine,
                        hotScore = entity.hotScore,
                        version = "Local",
                        publishTime = "",
                        localCoverUri = entity.coverUri,
                        packageName = entity.packageName,
                        isLocal = true
                    )
                )
            } else {
                gameDao.deleteGame(entity)
            }
        }
        return validLocalGames + remoteGames
    }

    fun getFullConfigFlow(forceRefresh: Boolean): Flow<ConfigResponse> = flow {
        val cached = getCachedConfig()
        if (cached != null) emit(cached)

        val timestamp = getCacheTimestamp()
        val isExpired = System.currentTimeMillis() - timestamp > CACHE_DURATION

        if (forceRefresh || isExpired || cached == null) {
            try {
                val response = if (Constants.isChinaRegion) fetchConfigViaGitCode() else api.getConfig(Constants.REPO_CONFIG_URL)
                saveConfig(response)
                emit(response)
            } catch (e: Exception) {
                if (cached == null) {
                    emit(ConfigResponse("", LocalizedString("", ""), LocalizedString("", ""), emptyList()))
                }
            }
        }
    }

    suspend fun getGames(forceRefresh: Boolean = false): List<GameModel> {
        val config = getFullConfig(forceRefresh)
        return combineWithLocalGames(config)
    }

    suspend fun getFullConfig(forceRefresh: Boolean): ConfigResponse {
        val cached = getCachedConfig()
        val timestamp = getCacheTimestamp()
        val isExpired = System.currentTimeMillis() - timestamp > CACHE_DURATION

        if (!forceRefresh && cached != null && !isExpired) {
            return cached
        }

        return try {
            val response = if (Constants.isChinaRegion) fetchConfigViaGitCode() else api.getConfig(Constants.REPO_CONFIG_URL)
            saveConfig(response)
            response
        } catch (e: Exception) {
            cached ?: ConfigResponse("", LocalizedString("", ""), LocalizedString("", ""), emptyList())
        }
    }

    private suspend fun getRemoteGames(forceRefresh: Boolean): List<GameModel> {
        return getFullConfig(forceRefresh).games
    }

    suspend fun addLocalGame(game: GameEntity) {
        gameDao.insertGame(game)
    }

    suspend fun removeLocalGame(entity: GameEntity) {
        gameDao.deleteGame(entity)
    }

    suspend fun removeLocalGameById(id: String) {
        gameDao.deleteGameById(id)
    }

    suspend fun incrementHotScore(packageName: String) {
        android.util.Log.d("GameRepository", "incrementHotScore called for $packageName, isChinaRegion: ${Constants.isChinaRegion}")
        if (!Constants.isChinaRegion) return

        try {
            val owner = "znm1145"
            val repo = "AUL-Mobile-Repo"
            val path = "config.json"
            val branch = "data"
            val token = com.au.launcher.BuildConfig.GITCODE_TOKEN

            // 1. Get current file info (V5 returns base64 content and sha)
            val fileResponse = RetrofitClient.gitCodeApi.getFileV5(owner, repo, path, branch, token.takeIf { it.isNotEmpty() })
            
            // 2. Decode content
            val contentStr = fileResponse.content.replace("\n", "")
            val decodedBytes = android.util.Base64.decode(contentStr, android.util.Base64.DEFAULT)
            val jsonString = String(decodedBytes, Charsets.UTF_8)
            val config = gson.fromJson(jsonString, ConfigResponse::class.java)

            // 3. Find game and increment score
            var found = false
            val updatedGames = config.games.map { game ->
                // The trigger provides the system packageName. 
                // We compare it against the game's packageName OR its id (which is often related).
                val gamePackage = game.packageName ?: PackageUtils.getPackageNameFromId(game.id)
                android.util.Log.v("GameRepository", "Checking game: ${game.id}, gamePackage: $gamePackage against target: $packageName")
                if (gamePackage == packageName || game.id == packageName) {
                    found = true
                    val newScore = game.hotScore + 1
                    android.util.Log.d("GameRepository", "Found game to increment: ${game.id}, old score: ${game.hotScore}, new score: $newScore")
                    game.copy(hotScore = newScore)
                } else {
                    game
                }
            }

            if (!found) {
                android.util.Log.w("GameRepository", "Game not found in config: $packageName")
                return
            }

            // If we found the game but don't have a token, we can't update
            if (token.isEmpty()) {
                android.util.Log.e("GameRepository", "Cannot update hot score: GitCode token is empty")
                return
            }

            val updatedConfig = config.copy(games = updatedGames)
            // Use a plain Gson instance without pretty printing
            val compactGson = com.google.gson.Gson()
            val updatedJson = compactGson.toJson(updatedConfig)
            // Match Rust's formatting: trailing newline
            val contentWithNewline = updatedJson + "\n"
            val encodedContent = android.util.Base64.encodeToString(
                contentWithNewline.toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )

            android.util.Log.d("GameRepository", "Updating file with SHA: ${fileResponse.sha}")

            // 4. Update file back to GitCode V5
            val response = RetrofitClient.gitCodeApi.updateFileV5(
                owner,
                repo,
                path,
                token,
                UpdateFileRequestV5(
                    content = encodedContent,
                    message = "chore: bump hot_score for $packageName",
                    sha = fileResponse.sha,
                    branch = branch
                )
            )
            
            if (response.isSuccessful) {
                android.util.Log.d("GameRepository", "Successfully incremented hot score for $packageName")
                // Clear cache timestamp to force next refresh to be from network
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putLong(KEY_TIMESTAMP, 0L).apply()
            } else {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("GameRepository", "Failed to update hot score (V5): ${response.code()} $errorBody")
            }
        } catch (e: Exception) {
            android.util.Log.e("GameRepository", "Error in incrementHotScore", e)
        }
    }

    fun getCachedConfig(): ConfigResponse? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CONFIG, null)
        return if (json != null) {
            try {
                gson.fromJson(json, ConfigResponse::class.java)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    private fun getCacheTimestamp(): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_TIMESTAMP, 0L)
    }

    private fun saveConfig(config: ConfigResponse) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(config)
        android.util.Log.d("GameRepository", "Saving config to cache: $json")
        prefs.edit()
            .putString(KEY_CONFIG, json)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }
}
