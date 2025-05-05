import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.agent.tool.ToolExecutionRequest
import kotlinx.serialization.json.Json

abstract class LLMToolAdapter {
    protected val json = Json {
        prettyPrint = true
        prettyPrintIndent = "    "
    }

    abstract fun createToolPrompt(toolSpecifications: List<ToolSpecification>): String
    abstract fun parseToolCalls(response: String): List<ToolExecutionRequest>
}