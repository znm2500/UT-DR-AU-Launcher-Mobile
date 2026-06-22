package com.au.launcher.api

import retrofit2.Response
import retrofit2.http.*

interface GameApi {
    @GET
    suspend fun getConfig(@Url url: String): ConfigResponse

    // GitCode V5 API
    @GET("repos/{owner}/{repo}/contents/{path}")
    @Headers("Accept: application/json")
    suspend fun getFileV5(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") ref: String,
        @Query("access_token") token: String?
    ): GitCodeFileResponse

    @PUT("repos/{owner}/{repo}/contents/{path}")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun updateFileV5(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("access_token") token: String,
        @Body body: UpdateFileRequestV5
    ): Response<GitCodeUpdateResponse>
}

data class GitCodeUpdateResponse(
    val content: GitCodeFileResponse?
)

data class GitCodeFileResponse(
    val content: String,
    val sha: String
)

data class UpdateFileRequestV5(
    val content: String,
    val message: String,
    val sha: String,
    val branch: String
)

data class UpdateFileRequest(
    val branch: String,
    val commit_message: String,
    val content: String
)
