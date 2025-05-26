package com.smith.lai.langgraph4j_android_adapter.jinjaparser

import java.text.SimpleDateFormat
import java.util.*

class JinjaFunctions {
    private val registeredFunctions = mutableMapOf<String, String>()
    private val filterMap = mutableMapOf<String, (Any?, String) -> Any?>()

    init {
        // 注册所有可用的函数
        registerFunction("strftime_now", "Date formatting function")
        registerFunction("tojson", "JSON formatting function")
        registerFunction("trim", "String trimming function")
        registerFunction("length", "Get length of collection or string")

        // 注册所有过滤器
        registerFilter("trim") { value, _ -> trim(value) }
        registerFilter("length") { value, _ -> length(value) }
        registerFilter("tojson") { value, filter ->
            val indentMatch = Regex("""tojson\(indent=(\d+)\)""").find(filter)
            val indent = indentMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            toJson(value, indent)
        }
    }

    /**
     * 注册函数到上下文中
     */
    fun registerFunctionsToContext(context: MutableMap<String, Any>) {
        registeredFunctions.forEach { (name, _) ->
            context[name] = "function"
        }
    }

    /**
     * 添加新函数注册
     */
    private fun registerFunction(name: String, description: String) {
        registeredFunctions[name] = description
    }

    /**
     * 注册过滤器
     */
    private fun registerFilter(name: String, handler: (Any?, String) -> Any?) {
        filterMap[name] = handler
    }

    /**
     * 统一的过滤器应用方法 - 不再硬编码
     */
    fun applyFilter(value: Any?, filter: String): Any? {
        // 提取过滤器名称（去掉参数部分）
        val filterName = when {
            filter.contains("(") -> filter.substringBefore("(")
            else -> filter
        }

        // 查找并应用过滤器
        return filterMap[filterName]?.invoke(value, filter) ?: value
    }

    /**
     * 日期格式转换函数
     */
    fun strftimeNow(format: String): String {
        val androidFormat = convertStrftimeToAndroid(format)
        return try {
            SimpleDateFormat(androidFormat, Locale.getDefault()).format(Date())
        } catch (e: IllegalArgumentException) {
            Logger.error("JinjaFunctions", "Invalid date format: $androidFormat", e)
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        }
    }

    /**
     * JSON 格式化函数
     */
    fun toJson(value: Any?, indent: Int = 0): String {
        if (value == null) return "{}"

        return when (value) {
            is Map<*, *> -> {
                val entries = value.entries.map { (k, v) ->
                    val indentStr = if (indent > 0) " ".repeat(indent) else ""
                    when (v) {
                        is Map<*, *> -> {
                            val nestedJson = toJson(v, indent)
                            val innerJson = nestedJson.removeSurrounding("{", "}").trim()
                            val nestedEntries = (v as Map<*, *>).entries.map { (nk, nv) ->
                                "\"$nk\": \"$nv\""
                            }.joinToString(",\n${indentStr}    ")
                            "\"$k\": {\n${indentStr}    $nestedEntries\n$indentStr}"
                        }
                        is List<*> -> "\"$k\": [${v.joinToString(", ") { "\"$it\"" }}]"
                        else -> "\"$k\": \"$v\""
                    }
                }.joinToString(",\n${if (indent > 0) " ".repeat(indent) else ""}")
                "{\n${if (indent > 0) " ".repeat(indent) else ""}$entries\n}"
            }
            is List<*> -> {
                val indentStr = if (indent > 0) " ".repeat(indent) else ""
                "[\n$indentStr${value.joinToString(",\n$indentStr") { "\"$it\"" }}\n]"
            }
            else -> "\"$value\""
        }
    }

    /**
     * 字符串修剪函数
     */
    fun trim(value: Any?): String? {
        return value?.toString()?.trim()
    }

    /**
     * 获取长度函数
     */
    fun length(value: Any?): Int {
        return when (value) {
            is List<*> -> value.size
            is Map<*, *> -> value.size
            is String -> value.length
            else -> 0
        }
    }

    /**
     * 将 Python strftime 格式转换为 Android SimpleDateFormat 格式
     */
    private fun convertStrftimeToAndroid(format: String): String {
        return format
            .replace("%a", "EEE")      // Short weekday (Mon)
            .replace("%A", "EEEE")     // Full weekday (Monday)
            .replace("%b", "MMM")      // Short month (Jan)
            .replace("%B", "MMMM")     // Full month (January)
            .replace("%c", "EEE MMM dd HH:mm:ss yyyy") // Full date-time
            .replace("%d", "dd")       // Day of month (01-31)
            .replace("%H", "HH")       // Hour 24-hour (00-23)
            .replace("%I", "hh")       // Hour 12-hour (01-12)
            .replace("%j", "DDD")      // Day of year (001-366)
            .replace("%m", "MM")       // Month (01-12)
            .replace("%M", "mm")       // Minute (00-59)
            .replace("%p", "a")        // AM/PM
            .replace("%S", "ss")       // Second (00-59)
            .replace("%U", "ww")       // Week number (00-53, Sunday start)
            .replace("%w", "F")        // Weekday number (0-6, 0=Sunday)
            .replace("%W", "ww")       // Week number (00-53, Monday start)
            .replace("%x", "MM/dd/yy") // Local date format
            .replace("%X", "HH:mm:ss") // Local time format
            .replace("%y", "yy")       // Year last two digits (00-99)
            .replace("%Y", "yyyy")     // Full year (2024)
            .replace("%Z", "zzz")      // Timezone name
            .replace("%%", "%")        // Literal %
    }

    /**
     * 获取所有可用的函数列表
     */
    fun getAvailableFunctions(): Map<String, String> {
        return registeredFunctions.toMap()
    }

    /**
     * 获取所有可用的过滤器列表
     */
    fun getAvailableFilters(): Set<String> {
        return filterMap.keys
    }

    companion object {
        // 提供静态方法以保持向后兼容性
        private val instance = JinjaFunctions()

        fun strftimeNow(format: String): String = instance.strftimeNow(format)
        fun toJson(value: Any?, indent: Int = 0): String = instance.toJson(value, indent)
        fun trim(value: Any?): String? = instance.trim(value)
        fun length(value: Any?): Int = instance.length(value)
        fun applyFilter(value: Any?, filter: String): Any? = instance.applyFilter(value, filter)
    }
}