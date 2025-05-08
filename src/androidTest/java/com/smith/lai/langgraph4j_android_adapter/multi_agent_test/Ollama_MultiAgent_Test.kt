package com.smith.lai.langgraph4j_android_adapter.multi_agent_test

import com.smith.lai.langgraph4j_android_adapter.BuildConfig
import com.smith.lai.langgraph4j_android_adapter.httpclient.OkHttpClientBuilder
import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.ChatMessageType
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.output.structured.Description
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.service.V
import org.bsc.langgraph4j.StateGraph
import org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async
import org.bsc.langgraph4j.action.EdgeAction
import org.bsc.langgraph4j.action.NodeAction
import org.bsc.langgraph4j.langchain4j.serializer.std.ChatMesssageSerializer
import org.bsc.langgraph4j.langchain4j.serializer.std.ToolExecutionRequestSerializer
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer
import org.bsc.langgraph4j.state.AgentStateFactory

import org.bsc.langgraph4j.StateGraph.END
import org.bsc.langgraph4j.StateGraph.START
import org.bsc.langgraph4j.action.AsyncNodeAction.node_async
import org.bsc.langgraph4j.agentexecutor.AgentExecutor
import org.bsc.langgraph4j.prebuilt.MessagesState
import org.junit.Test
import java.time.Duration

class Ollama_MultiAgent_Test {
    @Test
    fun test1() {
        val httpClientBuilder1 = OkHttpClientBuilder()
        httpClientBuilder1.connectTimeout(Duration.ofSeconds(30))
        val httpClientBuilder2 = OkHttpClientBuilder()
        httpClientBuilder2.connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(120))
            .readTimeout(Duration.ofSeconds(120))
        var model = OllamaChatModel.builder()
//            .baseUrl("BuildConfig.OLLAMA_URL2")
            .baseUrl(BuildConfig.OLLAMA_URL)
            .httpClientBuilder(httpClientBuilder1)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .format("json")
//            .modelName("deepseek-r1:70b")
            .modelName("llama3.1:latest")
            .build();

        var modelWithTool = OllamaChatModel.builder()
            .baseUrl(BuildConfig.OLLAMA_URL)
            .httpClientBuilder(httpClientBuilder2)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .modelName("llama3.1:latest")
            .build();

        var supervisor = SupervisorAgent(model)
        var coder = CoderAgent(modelWithTool)
        var researcher = ResearchAgent(modelWithTool)

        var workflow = StateGraph<AgentState.State>(AgentState.State.SCHEMA, AgentState.Serializers.STD.getSerializer())
            .addNode("supervisor", node_async(supervisor))
            .addNode("coder", node_async(coder))
            .addNode("researcher", node_async(researcher))
            .addEdge(START, "supervisor")
            .addConditionalEdges(
                "supervisor",
                edge_async<AgentState.State>(EdgeAction<AgentState.State> { state: AgentState.State ->
                    state.next()!!
                        .orElseThrow()
                }
                ), java.util.Map.of<String, String>(
                    "FINISH", END,
                    "coder", "coder",
                    "researcher", "researcher"
                ))
            .addEdge("coder", "supervisor")
            .addEdge("researcher", "supervisor")

        val graph = workflow.compile()

        val initialState = mapOf(
            "messages" to listOf(UserMessage.from("where are next winter olympic games ?"))
        )

        graph.stream(initialState).forEach { event ->
            println("$event")
        }
        AgentExecutor.Serializers.STD
    }


}