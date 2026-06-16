package com.au.launcher.api

import retrofit2.http.GET
import retrofit2.http.Url

interface GameApi {
    @GET
    suspend fun getConfig(@Url url: String): ConfigResponse
}
