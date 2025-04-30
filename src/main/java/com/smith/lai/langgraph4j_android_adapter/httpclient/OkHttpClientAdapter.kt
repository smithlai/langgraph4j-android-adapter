package com.smith.lai.langgraph4j_android_adapter.httpclient

import android.util.Log
import dev.langchain4j.http.client.HttpClient
import dev.langchain4j.http.client.HttpMethod
import dev.langchain4j.http.client.HttpRequest
import dev.langchain4j.http.client.sse.ServerSentEventListener
import dev.langchain4j.http.client.sse.ServerSentEventParser
import dev.langchain4j.http.client.SuccessfulHttpResponse
import dev.langchain4j.exception.HttpException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InputStream

class OkHttpClientAdapter(private val okHttpClient: OkHttpClient) : HttpClient {
    private val tag: String? = this::class.qualifiedName
    override fun execute(request: HttpRequest): SuccessfulHttpResponse {
        val okHttpRequest = buildOkHttpRequest(request)

        try {
            val response = okHttpClient.newCall(okHttpRequest).execute()
            return handleSynchronousResponse(response)
        } catch (e: IOException) {
            throw RuntimeException("Failed to execute HTTP request", e)
        }
    }

    override fun execute(request: HttpRequest, parser: ServerSentEventParser, listener: ServerSentEventListener) {
        val okHttpRequest = buildOkHttpRequest(request)
        okHttpClient.newCall(okHttpRequest).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                listener.onError(e)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No error details"
                    listener.onError(HttpException(response.code, errorBody))
                    response.close()
                    return
                }

                val inputStream: InputStream? = response.body?.byteStream()
                if (inputStream == null) {
                    listener.onError(IOException("Response body is null"))
                    response.close()
                    return
                }

                try {
                    parser.parse(inputStream, listener)
                    listener.onClose()
                } catch (e: IOException) {
                    listener.onError(e)
                } finally {
                    inputStream.close()
                    response.close()
                }
            }
        })
    }

    private fun buildOkHttpRequest(request: HttpRequest): Request {
        val okHttpRequestBuilder = Request.Builder()
            .url(request.url())

        // Set method and body
        when (request.method()) {
            HttpMethod.GET -> okHttpRequestBuilder.get()
            HttpMethod.POST -> {
                val body = request.body() ?: ""
                val mediaType = "application/json; charset=utf-8".toMediaType()
                okHttpRequestBuilder.post(body.toRequestBody(mediaType))
            }
            HttpMethod.DELETE -> okHttpRequestBuilder.delete()
            else -> throw UnsupportedOperationException("Unsupported HTTP method: ${request.method()}")
        }

        // Add headers
        request.headers().forEach { (name, values) ->
            values.forEach { value ->
                okHttpRequestBuilder.addHeader(name, value)
            }
        }

        return okHttpRequestBuilder.build()
    }

    private fun handleSynchronousResponse(response: Response): SuccessfulHttpResponse {
        try {
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error details"
                Log.e(tag, "fatal error: " + errorBody)

                throw HttpException(response.code, errorBody)
            }

            val statusCode = response.code
            val headers = response.headers.toMultimap()
            val body = response.body?.string() ?: ""
            return SuccessfulHttpResponse.builder()
                .statusCode(statusCode)
                .headers(headers)
                .body(body)
                .build()
        } finally {
            response.close()
        }
    }

    fun shutdown() {
        // Shutdown the dispatcher's executor service
        okHttpClient.dispatcher.executorService.shutdown()
        // Clear the connection pool
        okHttpClient.connectionPool.evictAll()
        // Close the cache if it exists
        okHttpClient.cache?.close()
    }
}
