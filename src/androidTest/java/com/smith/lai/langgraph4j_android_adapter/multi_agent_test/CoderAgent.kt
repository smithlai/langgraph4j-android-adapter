package com.smith.lai.langgraph4j_android_adapter.multi_agent_test

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessageType
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.UserMessage
import org.bsc.langgraph4j.action.NodeAction

class CoderAgent(model: ChatLanguageModel?) : NodeAction<AgentState.State?> {
    companion object {
        const val SYSTEM_PROMPT = """
Use this to execute java code and do math. If you want to see the output of a value,
you should print it out with `System.out.println(...);`. This is visible to the user.
"""
    }
    internal class Tools {
        @Tool(SYSTEM_PROMPT)
        fun search(@P("coder request") request: String?): String {
            println("CoderTool request: '${request}'")
            return """
2
""".trimIndent()
        }
    }

    interface Service {
        fun evaluate(@UserMessage code: String?): String
    }

    val service: Service = AiServices.builder<Service>(
        Service::class.java
    )
        .chatLanguageModel(model)
        .tools(Tools())
        .build()

    override fun apply(state: AgentState.State?): Map<String, Any> {
        val message = state?.lastMessage()?.orElseThrow()!!
        val text: String = when (message.type()) {
            ChatMessageType.USER -> (message as UserMessage).toString()
            ChatMessageType.AI -> (message as AiMessage).text()
            else -> throw java.lang.IllegalStateException("unexpected message type: " + message.type())
        }
        val result = service.evaluate(text)
        return java.util.Map.of<String, Any>("messages", AiMessage.from(result))
    }
}
