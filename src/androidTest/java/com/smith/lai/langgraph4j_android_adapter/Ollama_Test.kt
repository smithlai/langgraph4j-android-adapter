package com.smith.lai.langgraph4j_android_adapter

import com.smith.lai.langgraph4j_android_adapter.httpclient.OkHttpClientBuilder
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.model.input.PromptTemplate
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.service.AiServices
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
import dev.langchain4j.service.SystemMessage as SystemMessage_Annotation
import dev.langchain4j.service.UserMessage as UserMessage_Annotation

class Ollama_Test {

    interface ToolExecutorInterface {
        @SystemMessage_Annotation(
            """You are a helpful AI assistant. 
                When given a task, analyze what is being asked and use the available tools.
                Respond with a clear answer. If you need to translate into cat language, use the meow tool.
            """
        )
        fun processRequest(@UserMessage_Annotation request: String): String
    }
    @Test
    fun testAgentExecutor() {
        val test = _testAgentExecutor()
        runBlocking {
            test.collect {
                // do nothing
            }
        }
    }

    private fun _testAgentExecutor(): Flow<String> = flow {
        println("======== AgentExecutor Start ========")


        val httpClientBuilder = OkHttpClientBuilder()
        httpClientBuilder.connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(120))

        try {
            // Initialize OllamaChatModel with configured URL
            val chatLanguageModel = OllamaChatModel.builder()
                .baseUrl(BuildConfig.OLLAMA_URL) // Configured in local.properties
//                .modelName("llama3.2:3b-instruct-q4_K_M") // Specific quantized model
                .modelName("hhao/qwen2.5-coder-tools:latest") // Specific quantized model
//                .modelName("llama3.1:latest") // Specific quantized model
                .httpClientBuilder(httpClientBuilder)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build()

            println("Model initialized successfully")
            val toolExecutor = AiServices.builder(ToolExecutorInterface::class.java)
                .chatModel(chatLanguageModel)
                .build()
            // Build AgentExecutor with DummyTestTools
            val stateGraph = AgentExecutor.builder()
                .chatModel(chatLanguageModel)
                .toolsFromObject(DummyTestTools())
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

            println("\n-------- Test 1: [Cat language test] --------")
            val raw_text = "Translate \"Hello, my master.\" into cat language"
            val prompt_template = PromptTemplate.from(
//                """{{raw_text}}"""
"""<|begin_of_text|><|start_header_id|>user<|end_header_id|>
{{raw_text}} <|eot_id|><|start_header_id|>assistant<|end_header_id|>
""")
            val prompt = prompt_template.apply(mapOf("raw_text" to raw_text))
                .toUserMessage()

            val iterator = graph.streamSnapshots(
                mapOf("messages" to listOf(prompt)),  //UserMessage.from(prompt)
                config
            )

            println("[All Steps]")
            var last_message: ChatMessage? = null
            iterator.forEachIndexed { index, step ->
                println("[$index][${step.toString()}]")
                when (step.node()) {
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
                            if (latestmessage.equals(last_message)) {
                                return@forEachIndexed
                            }
                            if (latestmessage is ToolExecutionResultMessage) {
                                println("   Tool response: ${latestmessage.toolName()}: ${latestmessage.text()}")
                            } else if (latestmessage is AiMessage) {
                                val toolExecutionRequests = latestmessage.toolExecutionRequests()
                                if (toolExecutionRequests.size > 0) {
                                    val request = toolExecutionRequests.joinToString(",", transform = {
                                        "${it.name()} ${it.arguments()}"
                                    })
                                    println("   Tool Execution Requests: $request")
                                }
                            }
                            last_message = latestmessage
                        }
                    }
                }
            }

            println("\n======== Test Complete ========")

            println("\n-------- Testing AiServices Directly --------")
            val directResponse = toolExecutor.processRequest(raw_text)
            println("Direct response: $directResponse")

        } catch (e: Exception) {
            println("Error: ${e.message}")
            throw e // Re-throw to let collector handle
        } finally {
            // Clean up OkHttpClient resources
            httpClientBuilder.shutdown()
        }
    }
}