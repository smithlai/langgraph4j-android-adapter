package com.smith.lai.langgraph4j_android_adapter.localclient.adaptor

import LLMToolAdapter
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.model.chat.request.json.JsonSchemaElement
import dev.langchain4j.model.chat.request.json.JsonStringSchema
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema
import dev.langchain4j.model.chat.request.json.JsonNumberSchema
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema
import dev.langchain4j.model.chat.request.json.JsonEnumSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.builtins.serializer
import android.util.Log

class Llama3_2_ToolAdapter : LLMToolAdapter() {
    override fun createToolPrompt(toolSpecifications: List<ToolSpecification>): String {
        if (toolSpecifications.isEmpty()) return ""
        val functionDefinitions = toolSchemas(toolSpecifications)
//        return """
//You are given a question and a set of possible functions (tools).
//Based on the question, you may need to make one or more function/tool calls to achieve the purpose.
//
//If you decide to invoke any of the function(s), you MUST put it in the format of [func_name1(params_name1=params_value1, params_name2=params_value2...), func_name2(params)]
//Even if a function has no parameters, you MUST still include the parentheses: [func_name()]
//You SHOULD NOT include any other text in the response.
//
//Here is a list of tools in JSON format that you can invoke.
//
//$functionDefinitions
//
//you can answer it directly only if there's no suitable tool call
//""".trimIndent()
        return """
You are an expert in composing functions. You are given a question and a set of possible functions. 
Based on the question, you will need to make one or more function/tool calls to achieve the purpose. 
If none of the functions can be used, point it out. If the given question lacks the parameters required by the function,also point it out. You should only return the function call in tools call sections.
You SHOULD NOT include any other text in the response.
Here is a list of functions in JSON format that you can invoke.$functionDefinitions

If you decide to invoke any of the function(s), you MUST put it in the format of 
```
[func_name1(params_name1=params_value1, params_name2=params_value2...), func_name2(params)]
```

When you receive the results of a tool call, you should respond with a helpful answer based on those results.
Do NOT call additional tools unless the user asks a new question that requires different information.
Format your answer in a clear, human-readable way.
""".trimIndent()
    }

    private fun toolSchemas(toolSpecifications: List<ToolSpecification>): String {
        val toolsArray = buildJsonArray {
            toolSpecifications.forEach { spec ->
                add(buildJsonObject {
                    put("name", spec.name())
                    put("description", spec.description() ?: "No description provided")
                    putJsonObject("parameters") {
                        val parameters = spec.parameters()
                        if (parameters != null) {
                            put("type", "dict")
                            put("required", buildJsonArray {
                                parameters.required()?.forEach { req ->
                                    add(JsonPrimitive(req))
                                } ?: emptyList<String>()
                            })
                            putJsonObject("properties") {
                                parameters.properties()?.forEach { (name, schemaElement) ->
                                    putJsonObject(name) {
                                        val schemaProps = extractSchemaProperties(schemaElement)
                                        put("type", schemaProps["type"] ?: "string")
                                        put("description", schemaProps["description"] ?: "The $name parameter")
                                    }
                                }
                            }
                        } else {
                            put("type", "dict")
                            put("required", buildJsonArray {})
                            putJsonObject("properties") {}
                        }
                    }
                })
            }
        }
        return json.encodeToString(JsonArray.serializer(), toolsArray)
    }

    private fun extractSchemaProperties(schemaElement: JsonSchemaElement): Map<String, String> {
        return when (schemaElement) {
            is JsonStringSchema -> mapOf(
                "type" to "string",
                "description" to (schemaElement.description() ?: "")
            )
            is JsonIntegerSchema -> mapOf(
                "type" to "integer",
                "description" to (schemaElement.description() ?: "")
            )
            is JsonNumberSchema -> mapOf(
                "type" to "number",
                "description" to (schemaElement.description() ?: "")
            )
            is JsonBooleanSchema -> mapOf(
                "type" to "boolean",
                "description" to (schemaElement.description() ?: "")
            )
            is JsonEnumSchema -> mapOf(
                "type" to "string",
                "description" to (schemaElement.description() ?: "")
            )
            else -> mapOf(
                "type" to "string",
                "description" to ""
            )
        }
    }

    override fun parseToolCalls(response: String): List<ToolExecutionRequest> {
        Log.e("AAAaaaa", "parsing... $response")
        val result = mutableListOf<ToolExecutionRequest>()
        try {
            val trimmedResponse = response.trim()
            if (!trimmedResponse.startsWith("[") || !trimmedResponse.endsWith("]")) {
                return emptyList()
            }

            val content = trimmedResponse.substring(1, trimmedResponse.length - 1)
            val toolCallsRaw = mutableListOf<String>()
            var currentCall = ""
            var parenthesesCount = 0

            for (char in content) {
                when (char) {
                    '(' -> parenthesesCount++
                    ')' -> parenthesesCount--
                    ',' -> {
                        if (parenthesesCount == 0) {
                            toolCallsRaw.add(currentCall.trim())
                            currentCall = ""
                            continue
                        }
                    }
                }
                currentCall += char
            }

            if (currentCall.isNotEmpty()) {
                toolCallsRaw.add(currentCall.trim())
            }

            toolCallsRaw.forEach { toolCallRaw ->
                val functionNameEnd = toolCallRaw.indexOf("(")
                if (functionNameEnd > 0) {
                    val functionName = toolCallRaw.substring(0, functionNameEnd).trim()
                    var paramsString = toolCallRaw.substring(functionNameEnd + 1)
                    if (paramsString.endsWith(")")) {
                        paramsString = paramsString.substring(0, paramsString.length - 1)
                    }

                    val params = mutableMapOf<String, String>()
                    var currentParam = ""
                    var currentKey = ""
                    var inValue = false
                    var quoteCount = 0

                    for (i in paramsString.indices) {
                        val char = paramsString[i]
                        when {
                            char == '=' && !inValue -> {
                                currentKey = currentParam.trim()
                                currentParam = ""
                                inValue = true
                            }
                            char == ',' && quoteCount % 2 == 0 -> {
                                if (currentKey.isNotEmpty()) {
                                    params[currentKey] = currentParam.trim()
                                }
                                currentKey = ""
                                currentParam = ""
                                inValue = false
                            }
                            char == '"' -> quoteCount++
                            else -> currentParam += char
                        }
                    }

                    if (currentKey.isNotEmpty()) {
                        params[currentKey] = currentParam.trim()
                    }

                    val jsonParams = buildString {
                        append("{")
                        params.entries.forEachIndexed { index, (key, value) ->
                            if (index > 0) append(", ")
                            val processedValue = when {
                                value.equals("true", ignoreCase = true) -> "true"
                                value.equals("false", ignoreCase = true) -> "false"
                                value.toIntOrNull() != null -> value
                                value.toDoubleOrNull() != null -> value
                                else -> {
                                    val cleanValue = value.trim().replace("^['\"](.+)['\"]$".toRegex(), "$1")
                                    "\"$cleanValue\""
                                }
                            }
                            append("\"$key\": $processedValue")
                        }
                        append("}")
                    }

                    result.add(
                        ToolExecutionRequest.builder()
                            .name(functionName)
                            .arguments(jsonParams)
                            .build()
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("Llama3_2_ToolAdapter", "Error parsing tool calls: ${e.message}")
        }
        return result
    }
}