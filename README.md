
# LlamaAndroid

LlamaAndroid is an Android app for on-device AI inference using LLama models with LangGraph4j and LangChain4j, supporting both local and cloud-based models (OpenAI, Ollama).

## Prerequisites
- Android Studio (2023.1.1+)
- JDK 17
- Android SDK (API 33+, minSdk 33 for app, 28 for `langgraph4j-android-adapter`)
- Git

## Setup

1. **Clone with Submodules**:
   ```bash
   git clone --recurse-submodules https://github.com/smithlai/LlamaAndroid.git

   ```

2. **Configure `local.properties`**:
   **Modify `local.properties` with API keys for OpenAI and/or Ollama:**
   ```properties
   openai.api.key=your-openai-api-key
   ollama.api.url=http://your-ollama-server:11434
   ```
   Leave empty if not using OpenAI or Ollama.
   Ensure this file is not committed (included in `.gitignore`).

3. **Configure Gradle**:
   **- `settings.gradle.kts`:**
     ```kotlin
     ....
     ....
     include(":langgraph4j-android-adapter")
     ```

   **- `app/build.gradle.kts` (dependencies):**
     ```kotlin
     dependencies {
         implementation("org.bsc.langgraph4j:langgraph4j-core:1.5.8")
         implementation("org.bsc.langgraph4j:langgraph4j-langchain4j:1.5.8")
         implementation("org.bsc.langgraph4j:langgraph4j-agent-executor:1.5.8")
         implementation("dev.langchain4j:langchain4j:1.0.0-beta3")
         implementation("dev.langchain4j:langchain4j-open-ai:1.0.0-beta3")
         implementation("dev.langchain4j:langchain4j-ollama:1.0.0-beta3")

         implementation(project(":langgraph4j-android-adapter"))
         ...
     }
     ```
4. **Configure Permissions**: Add Internet permission to app/src/main/AndroidManifest.xml for OpenAI and Ollama requests:
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Using LangGraph4j in Android

**1. Using OpenAI Model:**
**Run an AgentExecutor with OpenAI's GPT model.**
Example is also available in `OpenAI_Test.kt` and `TestTools1.kt`.

```kotlin
package com.example.llama.example

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
- Save to any Kotlin file (e.g., `OpenAIExample.kt`).
- Call `OpenAIExample.runOpenAIExample("your-api-key")` 
  in your application code, using the API key from `local.properties`.


**2. Using Local LLM:**
**Run an AgentExecutor with a local LLama model:**

```kotlin
package com.example.llama.localclient

import android.llama.cpp.LLamaAndroid
import com.smith.lai.langgraph4j_android_adapter.localclient.InferenceEngine
import kotlinx.coroutines.flow.Flow

class LLamaAndroidInferenceEngine(
    private val llamaAndroid: LLamaAndroid = LLamaAndroid.instance()
) : InferenceEngine {
    override suspend fun load(modelPath: String) {
        try {
            llamaAndroid.load(modelPath)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load LLamaAndroid model at $modelPath", e)
        }
    }

    override fun generate(prompt: String, systemPrompt: String?): Flow<String> {
        val fullPrompt = if (systemPrompt != null) {
            """
$systemPrompt
<|start_header_id|>user<|end_header_id>
$prompt <|eot_id><|start_header_id|>assistant<|end_header_id>
""".trimIndent()
        } else {
            """
<|start_header_id|>user<|end_header_id>
$prompt <|eot_id><|start_header_id|>assistant<|end_header_id>
""".trimIndent()
        }
        return llamaAndroid.send(fullPrompt)
    }
}

```

```kotlin
package com.example.llama.example

import com.example.llama.localclient.LLamaAndroidInferenceEngine
import com.smith.lai.langgraph4j_android_adapter.localclient.LocalChatModel
import dev.langchain4j.data.message.UserMessage
import org.bsc.langgraph4j.CompileConfig
import org.bsc.langgraph4j.RunnableConfig
import org.bsc.langgraph4j.agentexecutor.AgentExecutor
import org.bsc.langgraph4j.checkpoint.MemorySaver
import kotlinx.coroutines.runBlocking

object LocalLLMExample {
    fun runLocalLLMExample(modelPath: String) {
        val chatModel = LocalChatModel(
            inferenceEngine = LLamaAndroidInferenceEngine(),
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
- Save to any Kotlin file (e.g., `LocalLLMExample.kt`).
- Call `LocalLLMExample.runLocalLLMExample("/path/to/model")` in your application code, 
  using the model path from your download logic.

## Troubleshooting


## License
MIT License. See [LICENSE](LICENSE).