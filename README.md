# LangGraph4j Android Adapter Integration Guide

This guide demonstrates how to integrate the `langgraph4j-android-adapter` module into an Android application to enable on-device AI inference using [LangGraph4j](https://github.com/bsorrentino/langgraph4j) and LangChain4j, supporting both local and cloud-based models (e.g., OpenAI, Ollama, or local LLMs).

## Overview

The `langgraph4j-android-adapter` module provides a bridge between [LangGraph4j](https://github.com/bsorrentino/langgraph4j)/LangChain4j and Android, enabling developers to run AI inference tasks efficiently. It includes utilities for HTTP clients (e.g., OkHttp) and local inference engines compatible with Android's environment.

Here's also an [example](https://github.com/smithlai/SmolChat_Langgraph4j_Example/blob/main/app/src/main/java/io/shubham0204/smollmandroid/llm/SmolLMManager.kt) about leveraging langchain4j on SomlChat

## Prerequisites

- **Android Studio**: Version 2023.1.1 or higher
- **JDK**: Version 17
- **Android SDK**: API 33+ (minSdk 33 for the app, 28 for `langgraph4j-android-adapter`)
- **Git**: For cloning repositories and submodules
- **Gradle**: Compatible with Kotlin DSL

## Setup Instructions

Follow these steps to integrate `langgraph4j-android-adapter` into your Android project.

### 1. Clone the Repository with Submodules

Clone your project repository and include the `langgraph4j-android-adapter` submodule.

```bash
git clone --recurse-submodules <your-project-repo-url>
cd <your-project-directory>
```

Alternatively, if the repository is already cloned, initialize and update submodules:

```bash
git submodule update --init --recursive
```

The `.gitmodules` file should include the `langgraph4j-android-adapter` submodule, as shown below:

```ini
[submodule "langgraph4j-android-adapter"]
    path = langgraph4j-android-adapter
    url = https://github.com/smithlai/langgraph4j-android-adapter.git
```

### 2. Configure Gradle Settings

Add the `langgraph4j-android-adapter` module to your project's `settings.gradle.kts`:

```kotlin
include(":langgraph4j-android-adapter")
```

Update the app's `build.gradle.kts` to include the necessary dependencies and configurations:

```kotlin
android {
    compileSdk = 33
    defaultConfig {
        applicationId = "com.example.yourapp"
        minSdk = 33
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    .....
    ....
    // LangGraph4j Android Adapter
    implementation(project(":langgraph4j-android-adapter"))
}
```

### 3. Configure Permissions

Add the Internet permission to `app/src/main/AndroidManifest.xml` to enable network requests for cloud-based models (e.g., OpenAI, Ollama):

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### 4. Configure API Keys

Create or modify the `local.properties` file in the project root to include API keys for cloud-based models (if used):

```properties
openai.api.key=your-openai-api-key
ollama.api.url=http://your-ollama-server:11434
```

Ensure `local.properties` is listed in `.gitignore` to prevent committing sensitive information.

## Usage Examples

Below are examples demonstrating how to use the `langgraph4j-android-adapter` for cloud-based (OpenAI) and local LLM inference, including how to process the `AgentExecutor` output to obtain the final answer.

### Example 1: Using OpenAI Model

This example shows how to run an `AgentExecutor` with OpenAI's GPT model.

You can also directly run the Unittest in  
`androidTest/java/com/smith/lai/langgraph4j_android_adapter/OpenAI_Test.kt`
__OR__
`androidTest/java/com/smith/lai/langgraph4j_android_adapter/Ollama_Test.kt`
```kotlin
package com.example.yourapp

import com.smith.lai.langgraph4j_android_adapter.httpclient.OkHttpClientBuilder
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.openai.OpenAiChatModel
import org.bsc.langgraph4j.CompileConfig
import org.bsc.langgraph4j.RunnableConfig
import org.bsc.langgraph4j.agentexecutor.AgentExecutor
import org.bsc.langgraph4j.checkpoint.MemorySaver
import java.time.Duration

object OpenAIExample {
    fun runOpenAIExample(apiKey: String) {
        val httpClientBuilder = OkHttpClientBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(120))

        val chatModel = OpenAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl("https://api.openai.com/v1")
            .httpClientBuilder(httpClientBuilder)
            .modelName("gpt-4.1-nano")
            .temperature(0.0)
            .build()

        val stateGraph = AgentExecutor.builder()
            .chatLanguageModel(chatModel)
            .build()

        val graph = stateGraph.compile(CompileConfig.builder().checkpointSaver(MemorySaver()).build())
        val input = mapOf("messages" to listOf(UserMessage.from("What is the capital of France?")))
        val config = RunnableConfig.builder().threadId("test").build()

        graph.streamSnapshots(input, config).forEach { step ->
            val messages = step.state.data()["messages"] as? List<*>
            messages?.forEach { msg ->
                if (msg is dev.langchain4j.data.message.AiMessage) println("AI: ${msg.text()}")
            }
        }
    }
}
```

**Usage**:
- Save the code in a Kotlin file (e.g., `OpenAIExample.kt`).
- Call `OpenAIExample.runOpenAIExample("your-api-key")` with the API key from `local.properties`.

### Example 2: Using Local LLM

This example demonstrates running an `AgentExecutor` with a local LLM, with or without tools (e.g., a "cat language" translator), using a custom `LLMInferenceEngine` for on-device inference.

#### Implementing LLMInferenceEngine

The `LLMInferenceEngine` is a custom implementation of `LocalLLMInferenceEngine` from the `langgraph4j-android-adapter` module, designed to integrate a local LLM library for on-device inference on Android. It handles loading models, managing chat messages, and generating responses.

```kotlin
package com.example.yourapp.localclient

import com.smith.lai.langgraph4j_android_adapter.localclient.LocalLLMInferenceEngine
import dev.langchain4j.agent.tool.ToolSpecification
import your.llm.library.LLM // Replace with your LLM library
import kotlinx.coroutines.flow.Flow
import com.smith.lai.langgraph4j_android_adapter.localclient.adaptor.Llama3_2_ToolAdapter

class LLMInferenceEngine(
    private val llm: LLM,
    toolSpecifications: List<ToolSpecification> = emptyList(),
    toolAdapter: LLMToolAdapter = Llama3_2_ToolAdapter()
) : LocalLLMInferenceEngine(toolSpecifications, toolAdapter) {

    override fun addUserMessage(message: String) {
        llm.addUserMessage(message)
    }

    override fun addSystemPrompt(systemPrompt: String) {
        llm.addSystemPrompt(systemPrompt)
    }

    override fun addAssistantMessage(message: String) {
        llm.addAssistantMessage(message)
    }

    override fun generate(prompt: String): Flow<String> {
        return llm.getResponse(prompt)
    }
}
```

**Key Features**:
- **Inheritance**: Extends `LocalLLMInferenceEngine` to leverage the adapter's tool integration and chat functionality.
- **LLM Integration**: Wraps the `LLM` instance to handle model loading and response generation.
- **Tool Support**: Accepts `ToolSpecification` and `LLMToolAdapter` for integrating tools.
- **Message Management**: Implements methods to add user, system, and assistant messages to the chat context.
- **Response Generation**: Uses `llm.getResponse` to generate responses as a `Flow<String>`.

#### Local LLM Inference (With or Without Tools)

This code runs an `AgentExecutor` with a local LLM, optionally including tools like a "cat language" translator.

```kotlin
package com.example.yourapp

import com.example.yourapp.localclient.LLMInferenceEngine
import com.smith.lai.langgraph4j_android_adapter.localclient.LocalChatModel
import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.UserMessage
import your.llm.library.LLM // Replace with your LLM library
import org.bsc.langgraph4j.CompileConfig
import org.bsc.langgraph4j.RunnableConfig
import org.bsc.langgraph4j.agentexecutor.AgentExecutor
import org.bsc.langgraph4j.checkpoint.MemorySaver
import kotlinx.coroutines.runBlocking

class CatLanguageTool {
    @Tool("Translate a string into cat language, returns string")
    fun catLanguage(@P("Original string") text: String): String {
        return text.toList().joinToString(" Miao ")
    }
}

object LocalLLMExample {
    fun runLocalLLMExample(modelPath: String, useTools: Boolean = false) {
        val llm = LLM() // Initialize your LLM instance
        val tools = if (useTools) CatLanguageTool() else null
        val toolSpecifications = if (useTools && tools != null) {
            dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationsFrom(tools)
        } else {
            emptyList()
        }
        val inferenceEngine = LLMInferenceEngine(llm, toolSpecifications)
        val chatModel = LocalChatModel(
            inferenceEngine = inferenceEngine,
            modelPath = modelPath
        )

        runBlocking {
            try {
                chatModel.inferenceEngine.load(modelPath)
                println("Model loaded: $modelPath")
            } catch (e: Exception) {
                println("Failed to load model: ${e.message}")
                return@runBlocking
            }
        }

        val stateGraphBuilder = AgentExecutor.builder().chatLanguageModel(chatModel)
        if (useTools && tools != null) {
            stateGraphBuilder.toolSpecification(tools)
        }
        val stateGraph = stateGraphBuilder.build()

        val graph = stateGraph.compile(CompileConfig.builder().checkpointSaver(MemorySaver()).build())
        val input = mapOf("messages" to listOf(UserMessage.from(
            if (useTools) "Translate 'Hello World' into cat language" else "What is the capital of France?"
        )))
        val config = RunnableConfig.builder().threadId("test").build()

        var finalAnswer = ""
        graph.streamSnapshots(input, config).forEach { step ->
            when (step.node()) {
                org.bsc.langgraph4j.StateGraph.END -> {
                    val response = step.state().finalResponse().orElse(null)
                    if (response != null) {
                        finalAnswer = response
                        println("Final Answer: $finalAnswer")
                    }
                }
                else -> {
                    val latestMessage = step.state().lastMessage().orElse(null)
                    if (latestMessage is dev.langchain4j.data.message.ToolExecutionResultMessage) {
                        println("Tool Result: ${latestMessage.toolName()}: ${latestMessage.text()}")
                    }
                }
            }
        }
    }
}
```

**Usage**:
- Save the code in a Kotlin file (e.g., `LocalLLMExample.kt`).
- For inference without tools, call `LocalLLMExample.runLocalLLMExample("/path/to/model")`.
- For inference with tools, call `LocalLLMExample.runLocalLLMExample("/path/to/model", useTools = true)`.

**Obtaining the Final Answer**:
- The code processes `streamSnapshots` to handle both intermediate tool execution results (if `useTools` is true) and the final answer.
- For intermediate steps, it checks for `ToolExecutionResultMessage` to display tool outputs (e.g., cat language translation).
- At the `StateGraph.END` node, the final response is extracted using `step.state().finalResponse().orElse(null)` and stored in `finalAnswer`.

## Troubleshooting

**HTTP Communication Issues**:

Cleartext Traffic Blocked: 
Android blocks HTTP (cleartext) traffic by default for apps targeting API 28+. 
If you encounter `java.net.UnknownServiceException: CLEARTEXT communication to <IP> not permitted`, configure(or create) the network security policy in 

`app/src/main/res/xml/network_security_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">ollama.local</domain>
        <!-- Add other specific IPs as needed -->
    </domain-config>
</network-security-config>
```
Modify `AndroidManifest.xml`
```xml
    ....
    <uses-permission android:name="android.permission.INTERNET"/>

    <application
        ...
        android:networkSecurityConfig="@xml/network_security_config"
        >
        .....
        .....
 
    </application>

</manifest>
```

- **Submodule Not Found**: Ensure `git submodule update --init --recursive` is run after cloning.
- **Dependency Conflicts**: Verify that all LangGraph4j and LangChain4j dependencies use compatible versions (e.g., 1.5.12 for LangGraph4j, 1.0.0-beta5 for LangChain4j).
- **Model Loading Failure**: Check the model path and ensure the model file is accessible and compatible with your LLM library.
- **Network Errors**: Confirm that the Internet permission is added and API keys/URLs in `local.properties` are correct.
- **Tool Execution Issues**: Ensure `ToolSpecification` and `LLMToolAdapter` are correctly configured in `LLMInferenceEngine`.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.