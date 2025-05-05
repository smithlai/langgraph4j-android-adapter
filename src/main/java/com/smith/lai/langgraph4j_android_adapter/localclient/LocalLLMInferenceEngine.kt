package com.smith.lai.langgraph4j_android_adapter.localclient

import LLMToolAdapter
import android.util.Log
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

abstract class LocalLLMInferenceEngine(
    val toolSpecifications: List<ToolSpecification> = emptyList(),
    val toolAdapter: LLMToolAdapter
) : ChatLanguageModel {
    protected val tag: String? = this::class.simpleName
    private var processed_index = -1
    val toolPrompt: String
        get() = toolAdapter.createToolPrompt(toolSpecifications)

    override fun chat(chatRequest: ChatRequest): ChatResponse {
//        val messages = chatRequest.messages()
//        刪除langgraph4j強行添加的prompt
        val messages = chatRequest.messages().filter { message ->
            !(message is SystemMessage && message.text() == "You are a helpful assistant")
        }
        messages.forEachIndexed { index, chatMessage ->
            when (chatMessage) {
                is UserMessage -> {
                    if (processed_index < index) {
                        Log.e(tag, "[$index.]Adding User: ${chatMessage.singleText()}")
                        addUserMessage(chatMessage.singleText())
                        processed_index=index
                    }
                }
                is ToolExecutionResultMessage -> {
                    if (processed_index < index) {
                        Log.e(tag, "[$index.]Adding Tool Response (${chatMessage.toolName()}): ${chatMessage.text()}")
                        addUserMessage("The ${chatMessage.toolName()} tool returns: \"${chatMessage.text()}\", keep answering the question.")
                        processed_index=index
                    }
                }
                is AiMessage -> {
                    if (chatMessage.hasToolExecutionRequests()) {
                        if (processed_index < index){
                            Log.e(tag, "[$index.]Called tools: ${chatMessage.toolExecutionRequests().joinToString { it.name() }}")
                            processed_index=index
                        }

                    } else {
                        if (processed_index < index) {
                            Log.e(tag, "[$index.]Adding AI: ${chatMessage.text()}")
                            addAssistantMessage(chatMessage.text())
                            processed_index=index
                        }
                    }
                }
                is SystemMessage -> {

                    if (processed_index < index) {
                        Log.e(tag, "[$index.]Adding System: ${chatMessage.text()}")
                        addSystemPrompt(chatMessage.text())
                        processed_index=index
                    }
                }
            }
        }

        val responseText = runBlocking {
            val responses = mutableListOf<String>()
            generate("").collect { responses.add(it) }
            responses.joinToString("")
        }
        Log.d(tag, "Model output: $responseText")

        val cleanedResponse = responseText.trim()

        return try {
            val toolExecutionRequests = toolAdapter.parseToolCalls(cleanedResponse)
            if (toolExecutionRequests.isNotEmpty()) {
                ChatResponse.builder()
                    .aiMessage(AiMessage.aiMessage(toolExecutionRequests))
                    .build()
            } else {
                Log.w(tag, "No tool calls detected, returning raw response as content")
                ChatResponse.builder()
                    .aiMessage(AiMessage.aiMessage(cleanedResponse))
                    .build()
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to parse tool call response: '$cleanedResponse', error: $e")
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