package com.smith.lai.langgraph4j_android_adapter.multi_agent_test
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ChatMessageType
import dev.langchain4j.model.output.structured.Description
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.SystemMessage as SystemMessage_Annotation
import dev.langchain4j.service.UserMessage as UserMessage_Annotation
import android.util.Log
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.service.V
import org.bsc.langgraph4j.action.NodeAction
class SupervisorAgent(model: ChatModel?) : NodeAction<AgentState.State?> {
    val logTag = SupervisorAgent::class.java.simpleName
    class Router {
        @Description("Worker to route to next. If no workers needed, route to FINISH.")
        var next: String? = null

        override fun toString(): String {
            return String.format("Router[next: %s]", next)
        }
    }

    companion object {
        const val SYSTEM_PROMPT = """
            You are a supervisor tasked with managing a conversation between the following workers: {{members}}.
            Given the following user request and the conversation history, respond with the worker to act next.
            Each worker will perform a task and respond with their results and status.
            If the latest response fully answers the original query, respond with FINISH.
            The original query is: {{originalQuery}}.
            Current conversation history: {{history}}.
        """
    }

    interface Service {
        @SystemMessage_Annotation(SYSTEM_PROMPT)
        fun evaluate(
            @V("members") members: String?,
            @V("originalQuery") originalQuery: String?,
            @V("history") history: String?,
            @UserMessage_Annotation userMessage: String?
        ): Router
    }

    val service: Service
    val members: Array<String> = arrayOf("researcher", "coder")

    init {
        service = AiServices.create(Service::class.java, model)
    }

    @Throws(Exception::class)
    override fun apply(state: AgentState.State?): Map<String, String?> {

        val messages = state?.messages() ?: emptyList()
        Log.i(logTag, messages.toString())
        val message = state?.lastMessage()?.orElseThrow()!!
        val text: String = when (message.type()) {
            ChatMessageType.USER -> (message as UserMessage).singleText()
            ChatMessageType.AI -> (message as AiMessage).text()
            else -> throw IllegalStateException("unexpected message type: ${message.type()}")
        }

        // 獲取原始問題
        val originalQuery = messages.firstOrNull { it?.type() == ChatMessageType.USER }?.let { (it as UserMessage).singleText() } ?: ""

        // 構建對話歷史
        val history = messages.joinToString("\n") { msg ->
            when (msg?.type()) {
                ChatMessageType.USER -> "User: ${(msg as UserMessage).singleText()}"
                ChatMessageType.AI -> "Assistant: ${(msg as AiMessage).text()}"
                else -> ""
            }
        }

        val m = members.joinToString(",")
        Log.i(logTag, "0. message size: ${messages.size}")
        Log.i(logTag, "1. Got: $text")
        val result = service.evaluate(m, originalQuery, history, text)
        Log.i(logTag, "2. Next $result")
        return mapOf<String, String?>("next" to result.next)
    }
}