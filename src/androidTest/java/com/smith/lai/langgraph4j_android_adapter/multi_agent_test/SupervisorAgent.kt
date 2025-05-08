package com.smith.lai.langgraph4j_android_adapter.multi_agent_test
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ChatMessageType
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.output.structured.Description
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.SystemMessage as SystemMessage_Annotation
import dev.langchain4j.service.UserMessage as UserMessage_Annotation
import android.util.Log
import dev.langchain4j.service.V
import org.bsc.langgraph4j.action.NodeAction

class SupervisorAgent(model: ChatLanguageModel?) : NodeAction<AgentState.State?> {
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
            Given the following user request, respond with the worker to act next.
            Each worker will perform a task and respond with their results and status.
            When finished, respond with FINISH.
        """
    }

    interface Service {
        @SystemMessage_Annotation(SYSTEM_PROMPT)
        fun evaluate(@V("members") members: String?, @UserMessage_Annotation userMessage: String?): Router
    }

    val service: Service
    val members: Array<String> = arrayOf("researcher", "coder")

    init {
        service = AiServices.create(Service::class.java, model)
    }

    @Throws(Exception::class)
    override fun apply(state: AgentState.State?): Map<String, Any> {
        val message = state?.lastMessage()?.orElseThrow()!!
        Log.i("AAAA","1 "+ message.toString())
        val text: String = when (message.type()) {
            ChatMessageType.USER -> (message as UserMessage).toString()
            ChatMessageType.AI -> (message as AiMessage).text()
            else -> throw IllegalStateException("unexpected message type: " + message.type())
        }
        Log.i("AAAA","2 "+ text.toString())
        val m = members.joinToString(",") //String.join(",", *members)

        val result = service.evaluate(m, text)
        Log.i("AAAA","3 "+ result.toString())
        return java.util.Map.of<String, Any>("next", result.next)
    }
}
