package com.au.launcher.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_games")
data class GameEntity(
    @PrimaryKey val id: String,
    val name: String,
    val author: String,
    val engine: String,
    val packageName: String,
    val coverUri: String?,
    val isLocal: Boolean = true
)
