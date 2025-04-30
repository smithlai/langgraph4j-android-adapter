package com.smith.lai.langgraph4j_android_adapter.localclient

import kotlinx.coroutines.flow.Flow

interface InferenceEngine {
    suspend fun load(modelPath: String)
    fun generate(prompt: String, systemPrompt: String? = null): Flow<String>
}
