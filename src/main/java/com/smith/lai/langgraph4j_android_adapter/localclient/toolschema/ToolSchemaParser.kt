package com.smith.lai.langgraph4j_android_adapter.localclient.toolschema

import android.util.Log
import dev.langchain4j.agent.tool.ToolExecutionRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ToolSchemaParser {


    fun parseToolCalls(response: String): List<ToolExecutionRequest> {
        val result = mutableListOf<ToolExecutionRequest>()
        try {
            val trimmedResponse = response.trim()

            // 嘗試解析 JSON 格式: {"name": "func_name", "parameters": {...}}
            if (trimmedResponse.startsWith("{") && trimmedResponse.endsWith("}")) {
                return parseJsonFormat(trimmedResponse)
            }

            // 嘗試解析數組格式: [func_name(params...)]
            if (trimmedResponse.startsWith("[") && trimmedResponse.endsWith("]")) {
                return parseBracketFormat(trimmedResponse)
            }

            return emptyList()
        } catch (e: Exception) {
            Log.e("Llama3_2_ToolAdapter", "Error parsing tool calls: ${e.message}")
            return emptyList()
        }
    }

    private fun parseJsonFormat(response: String): List<ToolExecutionRequest> {
        return try {
            val jsonElement = Json.parseToJsonElement(response)
            val jsonObject = jsonElement.jsonObject

            val functionName = jsonObject["name"]?.jsonPrimitive?.content ?: return emptyList()
            val parameters = jsonObject["parameters"]?.jsonObject

            val jsonParams = if (parameters != null) {
                Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), parameters)
            } else {
                "{}"
            }

            listOf(
                ToolExecutionRequest.builder()
                    .name(functionName)
                    .arguments(jsonParams)
                    .build()
            )
        } catch (e: Exception) {
            Log.e("Llama3_2_ToolAdapter", "Error parsing JSON format: ${e.message}")
            emptyList()
        }
    }

    private fun parseBracketFormat(response: String): List<ToolExecutionRequest> {
        val result = mutableListOf<ToolExecutionRequest>()

        val content = response.substring(1, response.length - 1)
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

                val params = parseParameters(paramsString)
                val jsonParams = convertParamsToJson(params)

                result.add(
                    ToolExecutionRequest.builder()
                        .name(functionName)
                        .arguments(jsonParams)
                        .build()
                )
            }
        }

        return result
    }

    private fun parseParameters(paramsString: String): Map<String, String> {
        if (paramsString.trim().isEmpty()) return emptyMap()

        val params = mutableMapOf<String, String>()
        var currentParam = ""
        var currentKey = ""
        var inValue = false
        var quoteCount = 0
        var bracketCount = 0
        var braceCount = 0

        for (i in paramsString.indices) {
            val char = paramsString[i]
            when {
                char == '=' && !inValue && bracketCount == 0 && braceCount == 0 && quoteCount % 2 == 0 -> {
                    currentKey = currentParam.trim()
                    currentParam = ""
                    inValue = true
                }
                char == ',' && quoteCount % 2 == 0 && bracketCount == 0 && braceCount == 0 -> {
                    if (currentKey.isNotEmpty()) {
                        params[currentKey] = currentParam.trim()
                    }
                    currentKey = ""
                    currentParam = ""
                    inValue = false
                }
                char == '"' || char == '\'' -> quoteCount++
                char == '[' -> bracketCount++
                char == ']' -> bracketCount--
                char == '{' -> braceCount++
                char == '}' -> braceCount--
                else -> currentParam += char
            }
        }

        if (currentKey.isNotEmpty()) {
            params[currentKey] = currentParam.trim()
        }

        return params
    }

    private fun convertParamsToJson(params: Map<String, String>): String {
        return buildString {
            append("{")
            params.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) append(", ")
                val processedValue = when {
                    value.equals("true", ignoreCase = true) -> "true"
                    value.equals("false", ignoreCase = true) -> "false"
                    value.toIntOrNull() != null -> value
                    value.toDoubleOrNull() != null -> value
                    value.startsWith("[") && value.endsWith("]") -> value // 保持數組格式
                    value.startsWith("{") && value.endsWith("}") -> value // 保持對象格式
                    else -> {
                        val cleanValue = value.trim().replace("^['\"](.+)['\"]$".toRegex(), "$1")
                        "\"$cleanValue\""
                    }
                }
                append("\"$key\": $processedValue")
            }
            append("}")
        }
    }
}