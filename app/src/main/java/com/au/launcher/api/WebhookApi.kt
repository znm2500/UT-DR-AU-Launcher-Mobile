package com.au.launcher.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface WebhookApi {
    @POST("webhook/send")
    suspend fun sendMessage(
        @Query("key") key: String,
        @Body message: WebhookMessage
    ): Response<Unit>
}

data class WebhookMessage(
    val msgtype: String,
    val markdown: MarkdownContent? = null,
    val image: ImageContent? = null
)

data class MarkdownContent(
    val content: String
)

data class ImageContent(
    val base64: String,
    val md5: String
)
