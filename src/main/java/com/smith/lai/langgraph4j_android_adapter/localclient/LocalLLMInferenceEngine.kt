package com.smith.lai.langgraph4j_android_adapter.localclient

import android.util.Log
import com.google.gson.GsonBuilder
import com.smith.lai.langgraph4j_android_adapter.jinjaparser.JinjaParser
import com.smith.lai.langgraph4j_android_adapter.jinjaparser.Utils
import com.smith.lai.langgraph4j_android_adapter.localclient.toolschema.ToolSchemaConverter
import com.smith.lai.langgraph4j_android_adapter.localclient.toolschema.ToolSchemaParser
import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

abstract class LocalLLMInferenceEngine(
    var toolSpecifications: List<ToolSpecification> = emptyList(),
    var mTemplate:String? = null,

) : ChatModel {
    protected val tag: String? = this::class.simpleName
    private var processed_index = -1

    private val parser = JinjaParser()
//    val toolPrompt: String
//        get() = toolAdapter.createToolPrompt(toolSpecifications)

    fun setTemplate(template: String){
        mTemplate = template
        Log.d(tag, "Template: $mTemplate")
    }

    fun setToolSpecitications(_toolSpecifications: List<ToolSpecification>) {
        toolSpecifications = _toolSpecifications
    }
    fun reset(){
        processed_index = 0
    }

    open fun toolPrompt(): String{
        // Define ChatMessage list
//        val chatMessages: List<ChatMessage> = listOf(
//            SystemMessage("You are a helpful AI assistant.\n"),
//            UserMessage("What's the weather like today?")
//        )

        // Convert ChatMessage to List<Map<String, String>>
//        val messages: List<Map<String, String>> = chatMessages.map { message ->
//            mapOf(
//                "role" to message.type().toString().lowercase(), // e.g., "system", "user"
//                "content" to message.toString() // Use text() for content
//            )
//        }
        val tools = ToolSchemaConverter.toMapList(toolSpecifications)
//        val gson = GsonBuilder().setPrettyPrinting().create()

        val context = mapOf(
            "bos_token" to "",  // 虽然定义了，但不会输出
            "custom_tools" to tools,
            "tools" to tools,
            "tools_in_user_message" to false,
//            "messages" to messages
        )

        val result = parser.parseAndRender(mTemplate!!, context)

        //去除頭尾
        val regex = Regex("""<\|end_header_id\|>\s*(.*?)\s*<\|eot_id\|>""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(result)
        val content = match?.groupValues?.get(1)?:result

        return content
    }



    override fun chat(chatRequest: ChatRequest): ChatResponse {

        val messages = chatRequest.messages().filter { message ->
            !(message is SystemMessage && message.text() == "You are a helpful assistant")
        }
        var trigger:UserMessage? = null
        messages.forEachIndexed { index, chatMessage ->
            when (chatMessage) {
                is UserMessage -> {
                    if (processed_index < index) {
                        if (chatMessage == messages.last()){
                            trigger=chatMessage;
                        }else{
                        Log.e(tag, "[$index.]Adding User: ${chatMessage.singleText()}")
                        addUserMessage(chatMessage.singleText())
                            }
                        processed_index=index
                    }
                }
                is ToolExecutionResultMessage -> {
                    if (processed_index < index) {
                        Log.i(tag, (chatMessage as ToolExecutionResultMessage).toString())
                        Log.e(tag, "[$index.]Adding Tool Response (${chatMessage.toolName()}): ${chatMessage.text()}")

                        if (chatMessage.text().isNotEmpty()) {
                            trigger =
                                UserMessage.from("The ${chatMessage.toolName()} tool returns: \"${chatMessage.text()}\"")
                            addUserMessage(trigger!!.singleText())
                        }
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
            if (trigger != null) {
                generate(trigger!!.singleText().toString()).collect { responses.add(it) }
            }
            else{
                generate("").collect { responses.add(it) }
            }
            responses.joinToString("")
        }
        Log.d(tag, "Model output: $responseText")

        val cleanedResponse = responseText.trim()

        return try {
            val toolExecutionRequests = ToolSchemaParser().parseToolCalls(cleanedResponse)
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