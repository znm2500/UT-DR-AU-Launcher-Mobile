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

    @Query("DELETE FROM local_games WHERE id = :id")
    suspend fun deleteGameById(id: String)
}
