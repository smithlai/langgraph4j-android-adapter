package com.smith.lai.langgraph4j_android_adapter.localclient

import android.util.Log
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.internal.Json as LangChainJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json as KotlinxJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LocalChatModel(
    private val inferenceEngine: InferenceEngine,
    private val modelPath: String,
    private val toolSpecifications: List<ToolSpecification> = emptyList()
) : ChatLanguageModel {

    private var isLoaded = false
    private val tag: String? = this::class.simpleName
    private var isFirstSend = true

    private val toolPrompt: String by lazy {
        if (toolSpecifications.isEmpty()) return@lazy ""
        val toolsJson = toolSpecifications.map { spec ->
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to spec.name(),
                    "description" to spec.description(),
                    "parameters" to spec.parameters()
                )
            )
        }
        val toolsJsonString = LangChainJson.toJson(toolsJson)
        """
You are a helpful assistant with the following tools:

$toolsJsonString

Use tool_calls as much as possible instead of fabricating answers.
To use the tool, output with the following JSON format, don't output anything else:
```json
{"tool_calls": [
{
    "type": "function",
    "function":
    {
        "name": "<toolName>",
        "arguments": "{\"arg0\":<value0>, \"arg1\":<value1>}"
    }
}
]}
```
Example:
```json
{"tool_calls": [
{
    "type": "function",
    "function": {"name": "getWeather", "arguments": "{\"arg0\": \"Shanghai\"}"}
}]
}
```
""".trimIndent()
    }

    init {
        runBlocking {
            try {
                withContext(Dispatchers.IO) {
                    inferenceEngine.load(modelPath)
                    isLoaded = true
                    Log.i(tag, "Model loaded successfully: $modelPath")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to load model at $modelPath", e)
                throw IllegalStateException("Failed to load model at $modelPath", e)
            }
        }
    }

    fun generate(message: String): Flow<String> {
        if (!isLoaded) {
            throw IllegalStateException("Model not loaded")
        }
        val systemPrompt = if (isFirstSend && toolPrompt.isNotEmpty()) toolPrompt else null
        isFirstSend = false
        Log.d(tag, "Generating with message: $message, systemPrompt: $systemPrompt")
        return inferenceEngine.generate(message, systemPrompt)
            .flowOn(Dispatchers.IO)
            .catch { e ->
                Log.e(tag, "Failed to generate response", e)
                emit("Error: ${e.message}")
            }
    }

    override fun chat(chatRequest: ChatRequest): ChatResponse {
        if (!isLoaded) {
            throw IllegalStateException("Model not loaded")
        }
        val userMessage = chatRequest.messages()
            .filterIsInstance<UserMessage>()
            .lastOrNull()?.singleText()
            ?: throw IllegalArgumentException("No user message found in request")

        val responseText = runBlocking {
            val responses = mutableListOf<String>()
            generate(userMessage).collect { responses.add(it) }
            responses.joinToString("")
        }
        Log.d(tag, "Model output: $responseText")

        val cleanedResponse = responseText.trim()

        return try {
            val jsonResponse = KotlinxJson.parseToJsonElement(cleanedResponse).jsonObject
            val toolCalls = jsonResponse["tool_calls"]?.jsonArray ?: emptyList()
            if (toolCalls.isEmpty()) {
                Log.w(tag, "Warning: tool_calls array is empty")
            }
            val toolExecutionRequests = toolCalls.map { call ->
                val callObj = call.jsonObject
                val toolName: String
                val arguments: String
                when (val functionElement = callObj["function"]) {
                    is kotlinx.serialization.json.JsonObject -> {
                        toolName = functionElement["name"]?.jsonPrimitive?.content ?: ""
                        val argsElement = functionElement["arguments"]
                        arguments = when (argsElement) {
                            is kotlinx.serialization.json.JsonObject -> argsElement.toString()
                            is kotlinx.serialization.json.JsonPrimitive -> argsElement.content
                            is kotlinx.serialization.json.JsonArray -> argsElement.toString()
                            else -> "{}"
                        }
                    }
                    is kotlinx.serialization.json.JsonPrimitive -> {
                        toolName = functionElement.content
                        val argsElement = callObj["arguments"]
                        arguments = when (argsElement) {
                            is kotlinx.serialization.json.JsonObject -> argsElement.toString()
                            is kotlinx.serialization.json.JsonPrimitive -> argsElement.content
                            is kotlinx.serialization.json.JsonArray -> argsElement.toString()
                            else -> "{}"
                        }
                    }
                    else -> {
                        Log.w(tag, "Invalid function field in tool call: $callObj")
                        toolName = ""
                        arguments = "{}"
                    }
                }
                Log.d(tag, "Parsed ToolExecutionRequest: name=$toolName, args=$arguments")
                ToolExecutionRequest.builder()
                    .name(toolName)
                    .arguments(arguments)
                    .build()
            }.filter { it.name().isNotEmpty() }
            ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage(toolExecutionRequests))
                .build()
        } catch (e: Exception) {
            Log.w(tag, "Failed to parse JSON response: '$cleanedResponse', error: ${e.message}")
            Log.w(tag, "No tool call inferred, returning raw response as content")
            ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage(cleanedResponse))
                .build()
        }
    }
}
