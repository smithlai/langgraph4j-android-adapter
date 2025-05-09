package com.smith.lai.langgraph4j_android_adapter.multi_agent_test

import android.util.Log
import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessageType
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel

import dev.langchain4j.service.AiServices
import dev.langchain4j.service.UserMessage as UserMessage_Annotation
import org.bsc.langgraph4j.action.NodeAction

class ResearchAgent(model: ChatModel?) : NodeAction<AgentState.State?> {
    val logTag = ResearchAgent::class.java.simpleName
    companion object {
        const val SYSTEM_PROMPT = """
        Use this to perform a research over internet
        
        """
    }
    internal class Tools {
        val logTag = Tools::class.java.simpleName
        @Tool(SYSTEM_PROMPT)
        fun search(@P("internet query") query: String?): String {
            Log.i(logTag,"search query: '${query}'")
            return """
            the games will be in Italy at Cortina '2026
            
            """.trimIndent()
        }
    }

    interface Service {
        fun search(@UserMessage_Annotation query: String?): String
    }

    val service: Service = AiServices.builder<Service>(
        Service::class.java
    ).chatModel(model)
        .tools(Tools())
        .build()

    @Throws(java.lang.Exception::class)
    override fun apply(state: AgentState.State?): Map<String, Any> {
        val message = state?.lastMessage()?.orElseThrow()!!
        val text: String = when (message.type()) {
            ChatMessageType.USER -> (message as UserMessage).toString()
            ChatMessageType.AI -> (message as AiMessage).text()
            else -> throw java.lang.IllegalStateException("unexpected message type: " + message.type())
        }
        Log.i(logTag,"1. Get $text")
        val result = service.search(text)
        Log.i(logTag,"2. Output $result")
        return java.util.Map.of<String, Any>("messages", AiMessage.from(result))
    }
}
