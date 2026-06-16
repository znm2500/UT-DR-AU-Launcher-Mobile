package com.au.launcher.api

import com.au.launcher.utils.Constants
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private fun createRetrofit(baseUrl: String) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    var gameApi: GameApi = createRetrofit(Constants.BASE_URL).create(GameApi::class.java)
        private set

    fun refreshApi() {
        gameApi = createRetrofit(Constants.BASE_URL).create(GameApi::class.java)
    }

    val webhookApi: WebhookApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://qyapi.weixin.qq.com/cgi-bin/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WebhookApi::class.java)
    }
}
