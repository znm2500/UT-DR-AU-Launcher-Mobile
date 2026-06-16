package com.au.launcher.utils

object Constants {
    // Default (Global)
    const val BASE_URL_GLOBAL = "https://cdn.jsdelivr.net/"
    const val REPO_CONFIG_URL_GLOBAL = "gh/znm2500/AUL-Mobile-Repo@data/config.json"
    const val IMAGE_BASE_URL_GLOBAL = "https://cdn.jsdelivr.net/gh/znm2500/AUL-Mobile-Repo@data/"

    const val DOWNLOAD_URL_GLOBAL =
        "https://github.com/znm2500/AUL-Mobile-Repo/releases/download/"

    // China Mirror (GitCode)
    const val BASE_URL_CN = "https://gitcode.com/znm2500/AUL-Mobile-Repo/raw/data/"
    const val REPO_CONFIG_URL_CN = "config.json"
    const val IMAGE_BASE_URL_CN = "https://gitcode.com/znm2500/AUL-Mobile-Repo/raw/data/"

    const val DOWNLOAD_URL_CN = "https://gitcode.com/znm1145/AUL-Mobile-Repo/releases/download/"
    var isChinaRegion = false

    val BASE_URL: String
        get() = if (isChinaRegion) BASE_URL_CN else BASE_URL_GLOBAL

    val REPO_CONFIG_URL: String
        get() = if (isChinaRegion) REPO_CONFIG_URL_CN else REPO_CONFIG_URL_GLOBAL
    val DOWNLOAD_URL: String
        get() = if (isChinaRegion) DOWNLOAD_URL_CN else DOWNLOAD_URL_GLOBAL
    val IMAGE_BASE_URL: String
        get() = if (isChinaRegion) IMAGE_BASE_URL_CN else IMAGE_BASE_URL_GLOBAL

    const val DATABASE_NAME = "game_database"
    const val DEFAULT_COVER =
        "https://raw.githubusercontent.com/Weaver-Tools/UT-DR-AU-Launcher-Mobile/main/app/src/main/assets/default_cover.webp"
}
