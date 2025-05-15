package com.smith.lai.langgraph4j_android_adapter.multi_agent_test

import android.util.Log
import com.smith.lai.langgraph4j_android_adapter.BuildConfig
import com.smith.lai.langgraph4j_android_adapter.httpclient.OkHttpClientBuilder
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.data.message.UserMessage
import org.bsc.langgraph4j.StateGraph
import org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async
import org.bsc.langgraph4j.action.EdgeAction

import org.bsc.langgraph4j.StateGraph.END
import org.bsc.langgraph4j.StateGraph.START
import org.bsc.langgraph4j.action.AsyncNodeAction.node_async
import org.junit.Test
import java.time.Duration

class Ollama_MultiAgent_Test {
    val logTag = Ollama_MultiAgent_Test::class.java.name
    @Test
    fun test1() {
        val httpClientBuilder1 = OkHttpClientBuilder()
        httpClientBuilder1.connectTimeout(Duration.ofSeconds(30))
        val httpClientBuilder2 = OkHttpClientBuilder()
        httpClientBuilder2.connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(120))
            .readTimeout(Duration.ofSeconds(120))

        val model =
//            OpenAiChatModel.builder()
//            .apiKey(BuildConfig.OPENAI_API_KEY)
//            .baseUrl("https://api.openai.com/v1")
//            .httpClientBuilder(httpClientBuilder1)
//            .modelName("gpt-4.1-nano")
//            .temperature(0.0)
//            .maxTokens(2000)
//            .maxRetries(2)
//            .logRequests(true)
//            .logResponses(true)
//            .build()
        OllamaChatModel.builder()
//            .baseUrl("BuildConfig.OLLAMA_URL2")
            .baseUrl(BuildConfig.OLLAMA_URL)
            .httpClientBuilder(httpClientBuilder1)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .format("json")
//            .modelName("deepseek-r1:70b")
            .modelName("llama3.1:latest")
//            .modelName("gemma3")
            .build();

        var modelWithTool =
//            OpenAiChatModel.builder()
//            .apiKey(BuildConfig.OPENAI_API_KEY)
//            .baseUrl("https://api.openai.com/v1")
//            .httpClientBuilder(httpClientBuilder1)
//            .modelName("gpt-4.1-nano")
//            .temperature(0.0)
//            .maxTokens(2000)
//            .maxRetries(2)
//            .logRequests(true)
//            .logResponses(true)
//            .build()
            OllamaChatModel.builder()
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
            Log.e(logTag,"$event")
        }
    }


}