package com.smith.lai.langgraph4j_android_adapter.jinjaparser

import android.util.Log
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.agent.tool.ToolSpecifications
import dev.langchain4j.model.chat.request.json.JsonArraySchema
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema
import dev.langchain4j.model.chat.request.json.JsonEnumSchema
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema
import dev.langchain4j.model.chat.request.json.JsonNumberSchema
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.model.chat.request.json.JsonSchemaElement
import dev.langchain4j.model.chat.request.json.JsonStringSchema

object Utils {

    /**
     * 使用 LangChain4j 的 ToolSpecifications 将 tools 对象转换为 Map
     */
    fun toolsObjectToMap(toolsObj: Any): List<Map<String, Any>> {

        val toolSpecifications = ToolSpecifications.toolSpecificationsFrom(toolsObj)
        return toolsListToMap(toolSpecifications)

    }
    fun toolsListToMap(toolSpecifications: List<ToolSpecification>): List<Map<String, Any>> {
        return toolSpecifications.map { spec ->
            mapOf(
                "name" to spec.name(),
                "description" to (spec.description() ?: ""),
                "parameters" to convertJsonObjectSchemaToMap(spec.parameters())
            )
        }
    }

    /**
     * 将 JsonObjectSchema 转换为 Map 格式，适用于 Jinja 模板
     */
    private fun convertJsonObjectSchemaToMap(jsonObjectSchema: JsonObjectSchema?): Map<String, Any> {
        if (jsonObjectSchema == null) {
            return mapOf("type" to "object")
        }

        val result = mutableMapOf<String, Any>(
            "type" to "object"
        )

        // 处理 properties
        val properties = jsonObjectSchema.properties()
        if (properties != null && properties.isNotEmpty()) {
            val propertiesMap = mutableMapOf<String, Any>()
            properties.forEach { (name, schema) ->
                propertiesMap[name] = convertJsonSchemaElementToMap(schema)
            }
            result["properties"] = propertiesMap
        }

        // 处理 required
        val required = jsonObjectSchema.required()
        if (required != null && required.isNotEmpty()) {
            result["required"] = required
        }

        // 处理 definitions (如果有的话)
        val definitions = jsonObjectSchema.definitions()
        if (definitions != null && definitions.isNotEmpty()) {
            val definitionsMap = mutableMapOf<String, Any>()
            definitions.forEach { (name, schema) ->
                definitionsMap[name] = convertJsonSchemaElementToMap(schema)
            }
            result["definitions"] = definitionsMap
        }

        return result
    }

    /**
     * 将 JsonSchemaElement 转换为 Map 格式
     */
    private fun convertJsonSchemaElementToMap(element: JsonSchemaElement): Map<String, Any> {
        return when (element) {
            is JsonStringSchema -> {
                val map = mutableMapOf<String, Any>("type" to "string")
                element.description()?.let { map["description"] = it }
                map
            }
            is JsonIntegerSchema -> {
                val map = mutableMapOf<String, Any>("type" to "integer")
                element.description()?.let { map["description"] = it }
                map
            }
            is JsonNumberSchema -> {
                val map = mutableMapOf<String, Any>("type" to "number")
                element.description()?.let { map["description"] = it }
                map
            }
            is JsonBooleanSchema -> {
                val map = mutableMapOf<String, Any>("type" to "boolean")
                element.description()?.let { map["description"] = it }
                map
            }
            is JsonArraySchema -> {
                val map = mutableMapOf<String, Any>("type" to "array")
                element.description()?.let { map["description"] = it }
                element.items()?.let { map["items"] = convertJsonSchemaElementToMap(it) }
                map
            }
            is JsonObjectSchema -> convertJsonObjectSchemaToMap(element)
            is JsonEnumSchema -> {
                val map = mutableMapOf<String, Any>("type" to "string")
                element.description()?.let { map["description"] = it }
                map
            }
            else -> {
                val map = mutableMapOf<String, Any>("type" to "object")
                // 尝试获取 description 如果存在的话
                try {
                    val descriptionMethod = element.javaClass.getMethod("description")
                    val description = descriptionMethod.invoke(element) as? String
                    description?.let { map["description"] = it }
                } catch (e: Exception) {
                    // 忽略反射异常
                }
                map
            }
        }
    }
}

class TemplateException(message: String, cause: Throwable? = null) : Exception(message, cause)

object Logger {
    fun debug(tag: String, message: String) {
//        Log.d(tag, message)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
//        Log.e(tag, message, throwable)
    }
}