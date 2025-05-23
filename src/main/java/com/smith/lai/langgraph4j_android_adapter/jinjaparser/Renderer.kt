package com.smith.lai.langgraph4j_android_adapter.jinjaparser

import com.smith.lai.langgraph4j_android_adapter.jinjaparser.data.ConditionNode
import com.smith.lai.langgraph4j_android_adapter.jinjaparser.data.ListNode
import com.smith.lai.langgraph4j_android_adapter.jinjaparser.data.Node
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Renderer(private val nodes: List<Node>, private val context: MutableMap<String, Any>) {
    companion object {
        const val DEBUG_TAG = "JinjaRenderer"
    }
    private val STRFTIME_REGEX = Regex("""strftime_now\s*\(\s*["']([^"']+)["']\s*\)""")

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

    init {
        Logger.debug(DEBUG_TAG, "Initial context: $context")
    }

    fun render(): String {
        val result = StringBuilder()
        for (node in nodes) {
            val output = renderNode(node)
            if (!output.isNullOrEmpty()) {
                result.append(output)
            }
        }
        return result.toString()
    }

    private fun renderNode(node: Node): String? {
        return when (node) {
            is Node.Text -> {
                var value = node.value
                var hasNewline = false
                if (value.contains("\\n")) {
                    hasNewline = true
                }
                value = value.replace("\\n", "\n")
                if (hasNewline) {
                    println(value)
                }
                // Only apply trim if the value contains non-newline characters
                if (node.trimLeft && value.any { it != '\n' }) {
                    value = value.trimStart()
                }
                if (node.trimRight && value.any { it != '\n' }) {
                    value = value.trimEnd()
                }
                Logger.debug(DEBUG_TAG, "Text node value: $value")
                value
            }
            is Node.Variable -> {
                val parts = node.name.split("|").map { it.trim() }
                val exprParts = parts[0].split("+").map { it.trim() }
                var value: Any? = if (exprParts.size > 1) {
                    exprParts.joinToString("") { evaluateExpression(it)?.toString() ?: "" }
                } else {
                    evaluateExpression(parts[0])
                }
                Logger.debug(DEBUG_TAG, "Variable ${parts[0]} evaluated to: $value")
                for (filter in parts.drop(1)) {
                    value = applyFilter(value, filter)
                    Logger.debug(DEBUG_TAG, "Applied filter $filter, value: $value")
                }
                var result = value?.toString() ?: ""
                if (parts[0].contains("Today Date")) {
                    result = result.replace("\\n", "\n")
                }
                if (node.trimLeft) result = result.trimStart()
                if (node.trimRight) result = result.trimEnd()
                Logger.debug(DEBUG_TAG, "Variable result after processing: $result")
                result
            }
            is Node.If -> {
                val conditionValue = evaluateCondition(node.condition)
                Logger.debug(DEBUG_TAG, "If condition node evaluated to: $conditionValue")
                if (conditionValue) {
                    node.children.joinToString("") { renderNode(it) ?: "" }
                } else {
                    node.elseChildren.joinToString("") { renderNode(it) ?: "" }
                }
            }
            is Node.For -> {
                val list = evaluateListNode(node.list)
                Logger.debug(DEBUG_TAG, "For loop list evaluated to: $list")
                list.map { item ->
                    val newContext = context.toMutableMap().apply { put(node.item, item) }
                    Renderer(node.children, newContext).render()
                }.joinToString("\n\n")
            }
            is Node.Set -> {
                val value = evaluateExpression(node.value) ?: ""
                Logger.debug(DEBUG_TAG, "Set ${node.variable} to: $value")
                context[node.variable] = value
                ""
            }
            is Node.RaiseException -> {
                Logger.debug(DEBUG_TAG, "Raising exception: ${node.message}")
                throw TemplateException(node.message)
            }
        }
    }

    private fun evaluateCondition(node: ConditionNode): Boolean {
        Logger.debug(DEBUG_TAG, "Evaluating condition node: $node")
        return evaluateNode(node)
    }

    private fun evaluateNode(node: ConditionNode): Boolean {
        return when (node) {
            is ConditionNode.Literal -> {
                val value = evaluateExpression(node.value)
                val result = when (value) {
                    is Boolean -> value
                    is String -> value.isNotEmpty() && value.lowercase() != "false"
                    is Number -> value.toDouble() != 0.0
                    null -> false
                    else -> true
                }
                Logger.debug(DEBUG_TAG, "Literal '${node.value}' evaluated to: $result")
                result
            }
            is ConditionNode.UnaryOp -> {
                if (node.op != "not") throw TemplateException("Unsupported unary operator: ${node.op}")
                val result = !evaluateNode(node.child)
                Logger.debug(DEBUG_TAG, "Unary 'not' on ${node.child} evaluated to: $result")
                result
            }
            is ConditionNode.BinaryOp -> {
                val left = evaluateNode(node.left)
                val right = evaluateNode(node.right)
                val result = when (node.op) {
                    "and" -> left && right
                    "or" -> left || right
                    "==" -> {
                        val leftValue = evaluateExpression((node.left as ConditionNode.Literal).value)
                        val rightValue = evaluateExpression((node.right as ConditionNode.Literal).value)
                        leftValue == rightValue
                    }
                    "!=" -> {
                        val leftValue = evaluateExpression((node.left as ConditionNode.Literal).value)
                        val rightValue = evaluateExpression((node.right as ConditionNode.Literal).value)
                        leftValue != rightValue
                    }
                    else -> throw TemplateException("Unsupported binary operator: ${node.op}")
                }
                Logger.debug(DEBUG_TAG, "Binary '${node.op}' on ${node.left} and ${node.right} evaluated to: $result")
                result
            }
            is ConditionNode.IsDefined -> {
                val result = context.containsKey(node.variable)
                Logger.debug(DEBUG_TAG, "${node.variable} is defined: $result")
                result
            }
            is ConditionNode.IsNotNone -> {
                val value = evaluateExpression(node.variable)
                val result = value != null
                Logger.debug(DEBUG_TAG, "${node.variable} is not none: $result")
                result
            }
        }
    }

    private fun evaluateListNode(node: ListNode): List<Any> {
        return when (node) {
            is ListNode.Variable -> {
                val value = evaluateExpression(node.name) as? List<Any> ?: emptyList()
                Logger.debug(DEBUG_TAG, "List variable '${node.name}' evaluated to: $value")
                value
            }
            is ListNode.Slice -> {
                val list = evaluateListNode(node.list)
                val start = node.start ?: 0
                val end = node.end ?: list.size
                val result = list.subList(start.coerceAtLeast(0), end.coerceAtMost(list.size))
                Logger.debug(DEBUG_TAG, "Slice on ${node.list} from $start to $end evaluated to: $result")
                result
            }
        }
    }

    private fun evaluateExpression(expr: String): Any? {
        return when {
            expr.contains("[") && expr.contains("]") -> {
                val parts = expr.split("[", "]").filter { it.isNotBlank() }
                Logger.debug(DEBUG_TAG, "Expression parts: $parts")
                var value = context[parts[0]] as? List<Map<String, Any>>
                if (value != null && parts[1].toIntOrNull() != null) {
                    value = listOf(value[parts[1].toInt()])
                }
                if (parts.size > 2 && parts[2].startsWith("'") && parts[2].endsWith("'")) {
                    value?.firstOrNull()?.get(parts[2].trim('\''))
                } else if (parts[1].endsWith(":")) {
                    val startIndex = parts[1].removeSuffix(":").toIntOrNull() ?: 0
                    (context[parts[0]] as? List<Any>)?.subList(startIndex, (context[parts[0]] as List<Any>).size)
                } else {
                    value
                }
            }
            STRFTIME_REGEX.matches(expr) -> {
                val matchResult = STRFTIME_REGEX.find(expr)
                val format = matchResult?.groupValues?.get(1) ?: "%d %b %Y"
                Logger.debug(DEBUG_TAG, "strftime_now original format: $format")
                val androidFormat = convertStrftimeToAndroid(format)
                Logger.debug(DEBUG_TAG, "strftime_now android format: $androidFormat")
                try {
                    val result = SimpleDateFormat(androidFormat, Locale.getDefault()).format(Date())
                    Logger.debug(DEBUG_TAG, "strftime_now result: $result")
                    result
                } catch (e: IllegalArgumentException) {
                    Logger.error(DEBUG_TAG, "Invalid date format: $androidFormat", e)
                    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
                }
            }
            expr == "none" -> null
            expr == "true" -> true
            expr == "false" -> false
            expr.startsWith("'") && expr.endsWith("'") -> expr.trim('\'')
            expr.startsWith("\"") && expr.endsWith("\"") -> expr.trim('\"')
            else -> {
                val value = context[expr]
                if (value == null) Logger.debug(DEBUG_TAG, "Expression $expr not found in context")
                value
            }
        }
    }

    private fun applyFilter(value: Any?, filter: String): Any? {
        Logger.debug(DEBUG_TAG, "Applying filter $filter to $value")
        return when (filter) {
            "trim" -> value?.toString()?.trim()
            "tojson(indent=4)" -> {
                if (value == null) return "{}"
                when (value) {
                    is Map<*, *> -> {
                        val entries = value.entries.map { (k, v) ->
                            when (v) {
                                is Map<*, *> -> {
                                    val nestedJson = applyFilter(v, filter) as String
                                    val innerJson = nestedJson.removeSurrounding("{", "}").trim()
                                    val nestedEntries = v.entries.map { (nk, nv) ->
                                        "\"$nk\": \"$nv\""
                                    }.joinToString(",\n        ")
                                    "\"$k\": {\n        $nestedEntries\n    }"
                                }
                                is List<*> -> "\"$k\": [${v.joinToString(", ") { "\"$it\"" }}]"
                                else -> "\"$k\": \"$v\""
                            }
                        }.joinToString(",\n    ")
                        "{\n    $entries\n}"
                    }
                    is List<*> -> "[\n    ${value.joinToString(",\n    ") { "\"$it\"" }}\n]"
                    else -> "\"$value\""
                }
            }
            else -> value
        }
    }
}