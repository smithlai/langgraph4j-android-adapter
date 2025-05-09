package com.smith.lai.langgraph4j_android_adapter

import com.smith.lai.langgraph4j_android_adapter.httpclient.OkHttpClientBuilder
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.openai.OpenAiChatModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.bsc.langgraph4j.CompileConfig
import org.bsc.langgraph4j.RunnableConfig
import org.bsc.langgraph4j.StateGraph
import org.bsc.langgraph4j.agentexecutor.AgentExecutor
import org.bsc.langgraph4j.checkpoint.MemorySaver
import org.junit.Test
import java.time.Duration
import kotlin.jvm.optionals.getOrNull

class OpenAI_Test {

    @Test
    fun testAgentExecutor(){
        val test = _testAgentExecutor()
        runBlocking {
            test.collect{
                //do nothing
            }
        }
    }
    private fun _testAgentExecutor(): Flow<String> = flow {
        val apiKey = BuildConfig.OPENAI_API_KEY

        if (apiKey.isEmpty()) {
            println("Error: API Key is empty")
            return@flow // Exit the flow if no API key
        }

        println("======== AgentExecutor Start ========")

        val httpClientBuilder = OkHttpClientBuilder()
        httpClientBuilder.connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(120))

        try {
            val chatLanguageModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl("https://api.openai.com/v1")
                .httpClientBuilder(httpClientBuilder)
                .modelName("gpt-4.1-nano")
                .temperature(0.0)
                .maxTokens(2000)
                .maxRetries(2)
                .logRequests(true)
                .logResponses(true)
                .build()

            println("Model initialized successfully")

            // Build AgentExecutor with DummyTestTools
            val stateGraph = AgentExecutor.builder()
                .chatModel(chatLanguageModel)
                .toolSpecification(DummyTestTools())
                .build()


            val saver = MemorySaver()
            val compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build()

            val graph = stateGraph.compile(compileConfig)
            println("Graph initialized successfully")

            val config = RunnableConfig.builder()
                .threadId("test1")
                .build()

            println("\n-------- Test 1: 'Cat language test' --------")
            val iterator = graph.streamSnapshots(
                mapOf("messages" to UserMessage.from("Translate \"Hello, my master.\" into cat language")),
                config
            )

            println("[All Steps]")
            var last_message:  ChatMessage? = null
            iterator.forEachIndexed { index, step ->
//                println("[$index]Raw: $step")
                when(step.node()){
                    StateGraph.END -> {
                        println("[${step.node()}]Final Graph output: ${step.state().finalResponse().getOrNull()}")
                    }
                    else -> {
                        val latest_message_opt = step.state().lastMessage()
                        println("[$index][${step.node()}]Current message: $latest_message_opt")
                        val final_message_opt = step.state().finalResponse()
                        if (final_message_opt.isPresent) {
                            val final_message = final_message_opt.get()
                            println("   Final answer: $final_message")
                        } else if (latest_message_opt.isPresent) {
                            val latestmessage = latest_message_opt.get()
                            if (latestmessage.equals(last_message)){
                                return@forEachIndexed
                            }
                            if (latestmessage is ToolExecutionResultMessage) {
                                println("   Tool response: ${latestmessage.toolName()}: ${latestmessage.text()}")
                            } else if (latestmessage is AiMessage) {
                                val toolExecutionRequests = latestmessage.toolExecutionRequests()
                                if (toolExecutionRequests.size > 0) {
                                    val request = toolExecutionRequests.joinToString(",", transform = {
                                                                "${it.name()} ${it.arguments()}"
                                                            }
                                    )
                                    println("   Tool Execution Requests: $request")
                                }
                            }
                            last_message = latestmessage
                        }
                    }
                }
            }

            println("\n======== Test Complete ========")
        } catch (e: Exception) {
            println("Error: ${e.message}")
            throw e // Re-throw to let collector handle
        } finally {
            // Clean up OkHttpClient resources
            httpClientBuilder.shutdown()
        }
    }
}