package com.smith.lai.langgraph4j_android_adapter.jinjaparser

import com.smith.lai.langgraph4j_android_adapter.jinjaparser.data.ConditionNode
import com.smith.lai.langgraph4j_android_adapter.jinjaparser.data.ListNode
import com.smith.lai.langgraph4j_android_adapter.jinjaparser.data.Node
import com.smith.lai.langgraph4j_android_adapter.jinjaparser.data.TemplateException

class Renderer(private val nodes: List<Node>, private val context: MutableMap<String, Any>) {
    companion object {
        const val DEBUG_TAG = "JinjaRenderer"
    }
    private val STRFTIME_REGEX = Regex("""strftime_now\s*\(\s*["']([^"']+)["']\s*\)""")

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
//                    println(value)
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
                val result = when (node.op) {
                    "and" -> {
                        val left = evaluateNode(node.left)
                        val right = evaluateNode(node.right)
                        left && right
                    }
                    "or" -> {
                        val left = evaluateNode(node.left)
                        val right = evaluateNode(node.right)
                        left || right
                    }
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
                    "in" -> {
                        // Handle the "in" operator
                        val leftValue = evaluateExpression((node.left as ConditionNode.Literal).value)
                        val rightValue = evaluateExpression((node.right as ConditionNode.Literal).value)
                        Logger.debug(DEBUG_TAG, "Evaluating 'in' operator: $leftValue in $rightValue")

                        when (rightValue) {
                            is Map<*, *> -> rightValue.containsKey(leftValue)
                            is List<*> -> rightValue.contains(leftValue)
                            is String -> rightValue.contains(leftValue?.toString() ?: "")
                            else -> false
                        }
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
        Logger.debug(DEBUG_TAG, "Evaluating expression: '$expr'")
        return when {
            // 首先检查字符串字面量，避免被其他条件误判
            expr.startsWith("'") && expr.endsWith("'") -> {
                val result = expr.substring(1, expr.length - 1)
                Logger.debug(DEBUG_TAG, "Single quoted string: '$expr' -> '$result'")
                result
            }
            expr.startsWith("\"") && expr.endsWith("\"") -> {
                val result = expr.substring(1, expr.length - 1)
                Logger.debug(DEBUG_TAG, "Double quoted string: '$expr' -> '$result'")
                result
            }
            // 然后检查基本字面量
            expr == "none" -> null
            expr == "true" -> true
            expr == "false" -> false
            // 接下来检查复杂表达式
            expr.contains("[") && expr.contains("]") && expr.contains("'") -> {
                // 处理复杂的索引访问，如 messages[0]['role'] 或 message['content']
                val parts = expr.split("[", "]", "'").filter { it.isNotBlank() && it != "'" }
                Logger.debug(DEBUG_TAG, "Complex expression parts: $parts")

                var value: Any? = context[parts[0]]
                for (i in 1 until parts.size) {
                    val key = parts[i].trim()
                    value = when {
                        key.toIntOrNull() != null -> {
                            // 数字索引
                            val index = key.toInt()
                            when (value) {
                                is List<*> -> if (index < value.size) value[index] else null
                                else -> null
                            }
                        }
                        else -> {
                            // 字符串键
                            when (value) {
                                is Map<*, *> -> value[key]
                                is List<*> -> {
                                    // 如果是列表，尝试获取每个元素的该属性
                                    value.mapNotNull { item ->
                                        when (item) {
                                            is Map<*, *> -> item[key]
                                            else -> null
                                        }
                                    }
                                }
                                else -> null
                            }
                        }
                    }
                }
                value
            }
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
            expr == "length" -> {
                // 处理 | length 过滤器的特殊情况
                null
            }
            expr.contains("|") -> {
                // 处理管道过滤器
                val parts = expr.split("|").map { it.trim() }
                var value = evaluateExpression(parts[0])
                for (filter in parts.drop(1)) {
                    value = applyFilter(value, filter)
                }
                value
            }
            expr.contains(" in ") && !expr.startsWith("'") && !expr.startsWith("\"") -> {
                // 处理 'key' in dict 表达式
                val parts = expr.split(" in ").map { it.trim() }
                if (parts.size == 2) {
                    val key = parts[0].trim('\'', '\"')
                    val dict = evaluateExpression(parts[1])
                    when (dict) {
                        is Map<*, *> -> dict.containsKey(key)
                        is List<*> -> dict.contains(key)
                        else -> false
                    }
                } else false
            }
            STRFTIME_REGEX.matches(expr) -> {
                val matchResult = STRFTIME_REGEX.find(expr)
                val format = matchResult?.groupValues?.get(1) ?: "%d %b %Y"
                Logger.debug(DEBUG_TAG, "strftime_now original format: $format")
                // 使用集中管理的函数
                JinjaFunctions.strftimeNow(format)
            }
            else -> {
                val value = context[expr]
                Logger.debug(DEBUG_TAG, "Context lookup for '$expr': $value")
                if (value == null) Logger.debug(DEBUG_TAG, "Expression $expr not found in context")
                value
            }
        }
    }

    private fun applyFilter(value: Any?, filter: String): Any? {
        Logger.debug(DEBUG_TAG, "Applying filter $filter to $value")
        return JinjaFunctions.applyFilter(value, filter)
    }
}