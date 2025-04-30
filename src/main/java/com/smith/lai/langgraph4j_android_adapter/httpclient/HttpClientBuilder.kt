package com.smith.lai.langgraph4j_android_adapter.httpclient

import dev.langchain4j.http.client.HttpClient
import dev.langchain4j.http.client.HttpClientBuilder
import java.time.Duration

class OkHttpClientBuilder(private val okHttpClientAdapter: OkHttpClientAdapter) : HttpClientBuilder {

    private var connectTimeout: Duration = Duration.ofSeconds(15)
    private var readTimeout: Duration = Duration.ofSeconds(60)

    override fun connectTimeout(connectTimeout: Duration?): HttpClientBuilder {
        if (connectTimeout != null) {
            this.connectTimeout = connectTimeout
        }
        return this
    }

    override fun readTimeout(readTimeout: Duration?): HttpClientBuilder {
        if (readTimeout != null) {
            this.readTimeout = readTimeout
        }
        return this
    }

    override fun build(): HttpClient {
        return okHttpClientAdapter
    }

    override fun connectTimeout(): Duration {
        return connectTimeout
    }

    override fun readTimeout(): Duration {
        return readTimeout
    }
}
