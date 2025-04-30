package com.smith.lai.langgraph4j_android_adapter

import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.openai.OpenAiChatModel
import okhttp3.OkHttpClient
import org.bsc.langgraph4j.CompileConfig
import org.bsc.langgraph4j.RunnableConfig
import org.bsc.langgraph4j.agentexecutor.AgentExecutor
import org.bsc.langgraph4j.checkpoint.MemorySaver
import org.junit.Test
import java.util.concurrent.TimeUnit

class OpenAI_Test {

    @Test
    fun testAgentExecutor() {
        // 从 BuildConfig 获取 API 密钥
        val apiKey = BuildConfig.OPENAI_API_KEY

        if (apiKey.isEmpty()) {
            println("错误: API 密钥未设置")
            return
        }

        println("======== 开始 AgentExecutor 测试 ========")

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val httpClientAdapter =
            com.smith.lai.langgraph4j_android_adapter.httpclient.OkHttpClientAdapter(okHttpClient)
        val httpClientBuilder =
            com.smith.lai.langgraph4j_android_adapter.httpclient.OkHttpClientBuilder(
                httpClientAdapter
            )

        try {
            // Initialize language model with custom HttpClientBuilder
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

            println("模型初始化完成")

            // 创建代理执行器
            val stateGraph = AgentExecutor.builder()
                .chatLanguageModel(chatLanguageModel)
                .objectsWithTools(listOf(TestTools1()))
                .build()

            println("代理执行器创建完成")

            // 创建检查点保存器
            val saver = MemorySaver()

            // 创建编译配置
            val compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build()

            // 编译图
            val graph = stateGraph.compile(compileConfig)
            println("图已编译")

            // 创建运行配置
            val config = RunnableConfig.builder()
                .threadId("test1")
                .build()

            // 执行测试1: 获取上海的天气
            println("\n-------- 执行测试1: 'Get the weather for Shanghai' --------")
            var iterator = graph.streamSnapshots(
                mapOf("messages" to UserMessage.from("Get the weather for Shanghai")),
                config
            )

            println("[执行步骤]")
            for (step in iterator) {
                // 获取状态映射并转换为Map<String, Any>
                val stateMap: Map<String, Any> = step.state.data()

                // 简化输出，只显示关键信息
                if (stateMap.containsKey("agent_response")) {
                    println("AI最终回应: ${stateMap["agent_response"]}")
                } else {
                    val stepStr = step.toString()
                    if (stepStr.contains("ToolExecutionResultMessage")) {
                        println("工具执行结果: ${stepStr.substringAfter("text = ").substringBefore("}")}")
                    } else if (stepStr.contains("toolExecutionRequests")) {
                        val toolName = stepStr.substringAfter("name = \"").substringBefore("\",")
                        println("AI调用工具: $toolName")
                    }
                }
            }

            // 测试2: 反转文本
            val config2 = RunnableConfig.builder()
                .threadId("test2")
                .build()

            println("\n-------- 执行测试2: 'Reverse the text: Hello World' --------")
            iterator = graph.streamSnapshots(
                mapOf("messages" to UserMessage.from("Reverse the text: Hello World")),
                config2
            )

            println("[执行步骤]")
            for (step in iterator) {
                // 获取状态映射并转换为Map<String, Any>
                val stateMap: Map<String, Any> = step.state.data()

                // 简化输出，只显示关键信息
                if (stateMap.containsKey("agent_response")) {
                    println("AI最终回应: ${stateMap["agent_response"]}")
                } else {
                    val stepStr = step.toString()
                    if (stepStr.contains("ToolExecutionResultMessage")) {
                        println("工具执行结果: ${stepStr.substringAfter("text = ").substringBefore("}")}")
                    } else if (stepStr.contains("toolExecutionRequests")) {
                        val toolName = stepStr.substringAfter("name = \"").substringBefore("\",")
                        println("AI调用工具: $toolName")
                    }
                }
            }

            println("\n======== 测试完成 ========")

        } catch (e: Exception) {
            println("测试出错: ${e.message}")
            e.printStackTrace()
        } finally {
            // Clean up OkHttpClient resources
            httpClientAdapter.shutdown()
        }
    }
}
