package com.au.launcher.api

import com.google.gson.annotations.SerializedName

data class ConfigResponse(
    @SerializedName("newest_version") val newestVersion: String,
    val announcement: LocalizedString,
    @SerializedName("update_log") val updateLog: LocalizedString,
    val games: List<GameModel>
)

data class LocalizedString(
    val en: String,
    val zh: String
) {
    fun get(lang: String): String {
        return if (lang.startsWith("zh")) {
            zh.ifEmpty { en }
        } else {
            en.ifEmpty { zh }
        }
    }
}

data class GameModel(
    val id: String,
    val name: LocalizedString,
    val author: LocalizedString,
    val engine: String,
    @SerializedName(value = "hot_score", alternate = ["hotscore", "hotScore"]) val hotScore: Int,
    val version: String,
    @SerializedName("publish_time") val publishTime: String,
    val uploadTarget: String? = null,
    val localized: Boolean = false,
    // Local import fields
    val isLocal: Boolean = false,
    val localCoverUri: String? = null,
    val packageName: String? = null
)
