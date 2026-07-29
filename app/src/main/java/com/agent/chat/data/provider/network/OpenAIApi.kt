package com.agent.chat.data.provider.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url

interface OpenAIApi {
    @Streaming
    @POST
    suspend fun createChatCompletion(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body body: ChatCompletionRequest,
    ): Response<ResponseBody>
}
