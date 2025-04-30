package com.smith.lai.langgraph4j_android_adapter.httpclient

import android.util.Log
import dev.langchain4j.http.client.HttpClient
import dev.langchain4j.http.client.HttpClientBuilder
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration
import java.util.concurrent.TimeUnit

class OkHttpClientBuilder : HttpClientBuilder {

    private var connectTimeout: Duration = Duration.ofSeconds(15)
    private var readTimeout: Duration = Duration.ofSeconds(60)
//    private var writeTimeout: Duration = Duration.ofSeconds(15)
//    private var isBuilt: Boolean = false
    private var httpClient: OkHttpClientAdapter? = null
    private val tag: String = this::class.java.simpleName

    /**
     * Sets the connection timeout for the HTTP client.
     * Ignored with a warning if called after build().
     *
     * @param connectTimeout The connection timeout duration (optional, default: 15 seconds).
     * @return This builder instance.
     */
    override fun connectTimeout(connectTimeout: Duration?): HttpClientBuilder {
        if (httpClient != null) {
            Log.w(tag, "Warning: connectTimeout() called after build(). Ignoring new timeout: $connectTimeout")
            return this
        }
        if (connectTimeout != null) {
            this.connectTimeout = connectTimeout
        }
        return this
    }

    /**
     * Sets the read timeout for the HTTP client.
     * Ignored with a warning if called after build().
     *
     * @param readTimeout The read timeout duration (optional, default: 60 seconds).
     * @return This builder instance.
     */
    override fun readTimeout(readTimeout: Duration?): HttpClientBuilder {
        if (httpClient != null) {
            Log.w(tag, "Warning: readTimeout() called after build(). Ignoring new timeout: $readTimeout")
            return this
        }
        if (readTimeout != null) {
            this.readTimeout = readTimeout
        }
        return this
    }

//    /**
//     * Sets the write timeout for the HTTP client.
//     * Ignored with a warning if called after build().
//     *
//     * @param writeTimeout The write timeout duration (optional, default: 15 seconds).
//     * @return This builder instance.
//     */
//    fun writeTimeout(writeTimeout: Duration?): OkHttpClientBuilder {
//        if (isBuilt) {
//            Log.w(tag, "Warning: writeTimeout() called after build(). Ignoring new timeout: $writeTimeout")
//            return this
//        }
//        if (writeTimeout != null) {
//            this.writeTimeout = writeTimeout
//        }
//        return this
//    }

    /**
     * Builds an HttpClient with the configured timeouts.
     * Reuses the existing HttpClient if already built.
     *
     * @return A configured HttpClient (OkHttpClientAdapter).
     */
    override fun build(): HttpClient {
        if (httpClient != null) {
            Log.d(tag, "Returning cached HttpClient")
            return httpClient!!
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(connectTimeout)
            .readTimeout(readTimeout)
            .writeTimeout(readTimeout)
            .build()
        httpClient = OkHttpClientAdapter(okHttpClient)
        Log.d(tag, "Built new HttpClient with connectTimeout=$connectTimeout, readTimeout=$readTimeout")
        return httpClient!!
    }

    override fun connectTimeout(): Duration {
        return connectTimeout
    }

    override fun readTimeout(): Duration {
        return readTimeout
    }
    fun shutdown(){
        if (httpClient != null) {
            httpClient!!.shutdown()
            httpClient = null
        }
    }
    fun checkServerConnectivity(baseUrl: String): Boolean {
        val client = httpClient ?: build()

        val httpRequest = dev.langchain4j.http.client.HttpRequest.builder()
            .method(dev.langchain4j.http.client.HttpMethod.GET)
            .url("$baseUrl")
            .build()
        return try {
            Log.d(tag, "Attempting to connect to ${httpRequest.url()}")
            val response = client.execute(httpRequest)
            Log.d(tag, "Response code: ${response.statusCode()}")
            response.statusCode() in 200..299
        } catch (e: Exception) {
            Log.e(tag, "Connection failed: ${e.message}")
            false
        }
    }
}
