package com.smith.lai.langgraph4j_android_adapter.localclient

import android.util.Log
import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.internal.Json as LangChainJson
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json as KotlinxJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

abstract class LocalLLMInferenceEngine(
    val toolSpecifications: List<ToolSpecification> = emptyList()
) : ChatLanguageModel {
    protected val tag: String? = this::class.simpleName
    val toolPrompt: String by lazy {
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


    override fun chat(chatRequest: ChatRequest): ChatResponse {
        val messages = chatRequest.messages();
        //todo: agentexecutor/Agent.java added a redundent "You are a helpful assistant"

        messages.forEachIndexed { index, chatMessage ->
            when(chatMessage){
                is UserMessage -> {
                    Log.e(tag, "Adding ${chatMessage.name()}: " + chatMessage.singleText())
                    addUserMessage(chatMessage.singleText())
                }
                is AiMessage -> {
                    Log.e(tag, "Adding AI: " + chatMessage.text())
                    addAssistantMessage(chatMessage.text())
                }
                is SystemMessage -> {
                    Log.e(tag, "Adding System: " + chatMessage.text())
                    addSystemPrompt(chatMessage.text())
                }
            }
        }

        val responseText = runBlocking {
            val responses = mutableListOf<String>()
            //todo: Add a fun start_generate with empty prompt
            generate("").collect { responses.add(it) }
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


    abstract fun addUserMessage(message: String)
    abstract fun addSystemPrompt(prompt: String)
    abstract fun addAssistantMessage(message: String)
    abstract fun generate(prompt: String): Flow<String>
}
