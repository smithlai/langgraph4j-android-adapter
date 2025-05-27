package com.smith.lai.langgraph4j_android_adapter.localclient.toolschema

import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.model.chat.request.json.JsonArraySchema
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema
import dev.langchain4j.model.chat.request.json.JsonEnumSchema
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema
import dev.langchain4j.model.chat.request.json.JsonNumberSchema
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.model.chat.request.json.JsonSchemaElement
import dev.langchain4j.model.chat.request.json.JsonStringSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject

/**
 * 统一的工具规格转换器，消除重复代码
 */
object ToolSchemaConverter {

    private val prettyJson = Json { prettyPrint = true }
    private val compactJson = Json { prettyPrint = false }

    /**
     * 工具数据类，作为中间转换格式
     */
    data class ToolData(
        val name: String,
        val description: String,
        val parameters: ParameterData
    )

    /**
     * 参数数据类
     */
    data class ParameterData(
        val type: String,
        val required: List<String>,
        val properties: Map<String, PropertyData>
    )

    /**
     * 属性数据类
     */
    data class PropertyData(
        val type: String,
        val description: String
    )

    /**
     * 将 ToolSpecification 列表转换为中间格式
     */
    private fun convertToToolData(toolSpecifications: List<ToolSpecification>): List<ToolData> {
        return toolSpecifications.map { spec ->
            ToolData(
                name = spec.name(),
                description = spec.description() ?: "",
                parameters = convertParameters(spec.parameters())
            )
        }
    }

    /**
     * 转换参数规格
     */
    private fun convertParameters(jsonObjectSchema: JsonObjectSchema?): ParameterData {
        if (jsonObjectSchema == null) {
            return ParameterData(
                type = "object",
                required = emptyList(),
                properties = emptyMap()
            )
        }

        val properties = jsonObjectSchema.properties()?.mapValues { (_, schema) ->
            convertSchemaElement(schema)
        } ?: emptyMap()

        return ParameterData(
            type = "object",
            required = jsonObjectSchema.required() ?: emptyList(),
            properties = properties
        )
    }

    /**
     * 转换 JsonSchemaElement 到 PropertyData
     */
    private fun convertSchemaElement(element: JsonSchemaElement): PropertyData {
        return when (element) {
            is JsonStringSchema -> PropertyData(
                type = "string",
                description = element.description() ?: ""
            )
            is JsonIntegerSchema -> PropertyData(
                type = "integer",
                description = element.description() ?: ""
            )
            is JsonNumberSchema -> PropertyData(
                type = "number",
                description = element.description() ?: ""
            )
            is JsonBooleanSchema -> PropertyData(
                type = "boolean",
                description = element.description() ?: ""
            )
            is JsonEnumSchema -> PropertyData(
                type = "string",
                description = element.description() ?: ""
            )
            is JsonArraySchema -> PropertyData(
                type = "array",
                description = element.description() ?: ""
            )
            is JsonObjectSchema -> PropertyData(
                type = "object",
                description = element.description() ?: ""
            )
            else -> PropertyData(
                type = "string",
                description = ""
            )
        }
    }

    /**
     * 转换为 Map 列表格式 (替代 toolsListToMap)
     */
    fun toMapList(toolSpecifications: List<ToolSpecification>): List<Map<String, Any>> {
        val toolDataList = convertToToolData(toolSpecifications)
        return toolDataList.map { toolData ->
            mapOf(
                "name" to toolData.name,
                "description" to toolData.description,
                "parameters" to mapOf(
                    "type" to toolData.parameters.type,
                    "required" to toolData.parameters.required,
                    "properties" to toolData.parameters.properties.mapValues { (_, prop) ->
                        mapOf(
                            "type" to prop.type,
                            "description" to prop.description
                        )
                    }
                )
            )
        }
    }

    /**
     * 转换为 JSON 字符串格式 (替代 toolSchemas)
     * @param useDict 是否使用 "dict" 类型 (true) 还是 "object" 类型 (false)
     * @param prettyPrint 是否美化输出
     */
    fun toJsonString(
        toolSpecifications: List<ToolSpecification>,
        useDict: Boolean = true,
        prettyPrint: Boolean = false
    ): String {
        val toolDataList = convertToToolData(toolSpecifications)

        val toolsArray = buildJsonArray {
            toolDataList.forEach { toolData ->
                add(buildJsonObject {
                    put("name", JsonPrimitive(toolData.name))
                    put("description", JsonPrimitive(toolData.description))
                    putJsonObject("parameters") {
                        put("type", JsonPrimitive(if (useDict) "dict" else "object"))
                        put("required", buildJsonArray {
                            toolData.parameters.required.forEach { req ->
                                add(JsonPrimitive(req))
                            }
                        })
                        putJsonObject("properties") {
                            toolData.parameters.properties.forEach { (name, prop) ->
                                putJsonObject(name) {
                                    put("type", JsonPrimitive(prop.type))
                                    put("description", JsonPrimitive(prop.description))
                                }
                            }
                        }
                    }
                })
            }
        }

        return if (prettyPrint) {
            prettyJson.encodeToString(JsonArray.Companion.serializer(), toolsArray)
        } else {
            compactJson.encodeToString(JsonArray.Companion.serializer(), toolsArray)
        }
    }
}