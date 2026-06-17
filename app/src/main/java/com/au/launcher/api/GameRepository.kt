package com.au.launcher.api

import android.content.Context
import com.au.launcher.db.GameDatabase
import com.au.launcher.db.GameEntity
import com.au.launcher.utils.Constants
import com.au.launcher.utils.PackageUtils
import com.google.gson.Gson

class GameRepository(
    private val context: Context,
    private val api: GameApi = RetrofitClient.gameApi
) {
    private val gson = Gson()
    private val PREFS_NAME = "game_cache"
    private val KEY_CONFIG = "cached_config"
    private val KEY_TIMESTAMP = "cache_timestamp"
    private val CACHE_DURATION = 30 * 60 * 1000 // 30 minutes
    private val gameDao = GameDatabase.getDatabase(context).gameDao()

    suspend fun getGames(forceRefresh: Boolean = false): List<GameModel> {
        val config = getFullConfig(forceRefresh)
        val remoteGames = config.games
        
        // Fetch local games and verify they are still installed
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
                        hotScore = 0,
                        version = "Local",
                        publishTime = "",
                        localCoverUri = entity.coverUri,
                        packageName = entity.packageName,
                        isLocal = true
                    )
                )
            } else {
                // Auto-remove if uninstalled
                gameDao.deleteGame(entity)
            }
        }
        
        return validLocalGames + remoteGames
    }

    suspend fun getFullConfig(forceRefresh: Boolean): ConfigResponse {
        val cached = getCachedConfig()
        val timestamp = getCacheTimestamp()
        val isExpired = System.currentTimeMillis() - timestamp > CACHE_DURATION

        if (!forceRefresh && cached != null && !isExpired) {
            return cached
        }

        return try {
            val response = api.getConfig(Constants.REPO_CONFIG_URL)
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

    suspend fun incrementHotScore(packageName: String) {
        if (!Constants.isChinaRegion) return

        try {
            val owner = "znm2500"
            val repo = "AUL-Mobile-Repo"
            val path = "config.json"
            val branch = "data"
            val token = "ZNzRgfc8kf3PAxezKQ77dkyb"

            // 1. Get current file info (V5 returns base64 content and sha)
            val fileResponse = RetrofitClient.gitCodeApi.getFileV5(owner, repo, path, branch, token)
            
            // 2. Decode content
            val decodedBytes = android.util.Base64.decode(fileResponse.content, android.util.Base64.DEFAULT)
            val jsonString = String(decodedBytes, Charsets.UTF_8)
            val config = gson.fromJson(jsonString, ConfigResponse::class.java)

            // 3. Find game and increment score
            var found = false
            val updatedGames = config.games.map { game ->
                val gamePackage = game.packageName ?: PackageUtils.getPackageNameFromId(game.id)
                if (gamePackage == packageName) {
                    found = true
                    game.copy(hotScore = game.hotScore + 1)
                } else {
                    game
                }
            }

            if (!found) return

            val updatedConfig = config.copy(games = updatedGames)
            val updatedJson = gson.toJson(updatedConfig)
            val encodedContent = android.util.Base64.encodeToString(
                updatedJson.toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )

            // 4. Update file back to GitCode V5
            val response = RetrofitClient.gitCodeApi.updateFileV5(
                owner,
                repo,
                path,
                token,
                UpdateFileRequestV5(
                    content = encodedContent,
                    message = "Increment hot score for $packageName",
                    sha = fileResponse.sha,
                    branch = branch
                )
            )
            
            if (!response.isSuccessful) {
                android.util.Log.e("GameRepository", "Failed to update hot score (V5): ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getCachedConfig(): ConfigResponse? {
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
        prefs.edit()
            .putString(KEY_CONFIG, gson.toJson(config))
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }
}
