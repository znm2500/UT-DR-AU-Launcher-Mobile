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
        val remoteGames = getRemoteGames(forceRefresh)
        
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

    private suspend fun getRemoteGames(forceRefresh: Boolean): List<GameModel> {
        val cached = getCachedConfig()
        val timestamp = getCacheTimestamp()
        val isExpired = System.currentTimeMillis() - timestamp > CACHE_DURATION

        if (!forceRefresh && cached != null && !isExpired) {
            return cached.games
        }

        return try {
            val response = api.getConfig(Constants.REPO_CONFIG_URL)
            saveConfig(response)
            response.games
        } catch (e: Exception) {
            cached?.games ?: emptyList()
        }
    }

    suspend fun addLocalGame(game: GameEntity) {
        gameDao.insertGame(game)
    }

    suspend fun removeLocalGame(entity: GameEntity) {
        gameDao.deleteGame(entity)
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
