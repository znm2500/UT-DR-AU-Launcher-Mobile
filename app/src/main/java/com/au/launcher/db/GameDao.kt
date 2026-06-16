package com.au.launcher.db

import androidx.room.*

@Dao
interface GameDao {
    @Query("SELECT * FROM local_games")
    suspend fun getAllLocalGames(): List<GameEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: GameEntity)

    @Delete
    suspend fun deleteGame(game: GameEntity)
}
