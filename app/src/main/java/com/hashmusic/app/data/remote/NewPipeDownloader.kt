package com.hashmusic.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class NewPipeDownloader : Downloader() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: NewPipeRequest): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val data = request.dataToSend()

        val requestBody = when {
            data != null -> data.toRequestBody()
            httpMethod == "POST" -> "".toRequestBody()
            else -> null
        }

        val okHttpRequest = Request.Builder()
            .url(url)
            .method(httpMethod, requestBody)

        headers.forEach { (key, values) ->
            values.forEach { value ->
                okHttpRequest.addHeader(key, value)
            }
        }

        try {
            val response = client.newCall(okHttpRequest.build()).execute()
            val responseBody = response.body?.string() ?: ""
            val responseHeaders = response.headers.toMultimap()

            return Response(
                response.code,
                response.message,
                responseHeaders,
                responseBody,
                url
            )
        } catch (e: IOException) {
            throw e
        }
    }
}
