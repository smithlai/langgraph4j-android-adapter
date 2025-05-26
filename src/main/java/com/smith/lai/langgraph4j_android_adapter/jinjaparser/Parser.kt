package com.smith.lai.langgraph4j_android_adapter.jinjaparser

import com.smith.lai.langgraph4j_android_adapter.jinjaparser.data.ConditionNode
import com.smith.lai.langgraph4j_android_adapter.jinjaparser.data.ListNode
import com.smith.lai.langgraph4j_android_adapter.jinjaparser.data.Node
import com.smith.lai.langgraph4j_android_adapter.jinjaparser.data.TemplateException
import com.smith.lai.langgraph4j_android_adapter.jinjaparser.data.Token

class Parser(private val tokens: List<Token>) {
    private var pos = 0

    fun parse(): List<Node> {
        val nodes = mutableListOf<Node>()
        while (pos < tokens.size) {
            nodes.add(parseNode())
        }
        return nodes
    }

    private fun parseNode(): Node {
        val token = tokens[pos++]
        return when (token) {
            is Token.Text -> Node.Text(token.value, token.trimLeft, token.trimRight)
            is Token.Variable -> Node.Variable(token.name, token.trimLeft, token.trimRight)
            is Token.IfStart -> {
                val condition = parseCondition(token.condition)
                val ifChildren = mutableListOf<Node>()
                val elseIfChildren = mutableListOf<Pair<ConditionNode, List<Node>>>()
                val elseChildren = mutableListOf<Node>()
                var currentChildren = ifChildren

                while (pos < tokens.size && tokens[pos] !is Token.IfEnd) {
                    when (val currentToken = tokens[pos]) {
                        is Token.ElseIf -> {
                            pos++ // Skip ElseIf
                            val elseIfCondition = parseCondition(currentToken.condition)
                            val elseIfNodes = mutableListOf<Node>()
                            elseIfChildren.add(Pair(elseIfCondition, elseIfNodes))
                            currentChildren = elseIfNodes
                        }
                        is Token.Else -> {
                            pos++ // Skip Else
                            currentChildren = elseChildren
                        }
                        else -> {
                            currentChildren.add(parseNode())
                        }
                    }
                }
                if (pos >= tokens.size) throw TemplateException("Unclosed if block")
                pos++ // Skip IfEnd

                // 简化处理：将 elseif 当作嵌套的 if-else 处理
                var resultNode = Node.If(condition, ifChildren, elseChildren)
                for ((elseIfCondition, elseIfNodes) in elseIfChildren.reversed()) {
                    resultNode = Node.If(condition, ifChildren, listOf(Node.If(elseIfCondition, elseIfNodes, listOf(resultNode))))
                }
                resultNode
            }
            is Token.ForStart -> {
                val listNode = parseListExpression(token.list)
                val children = mutableListOf<Node>()
                while (pos < tokens.size && tokens[pos] !is Token.ForEnd) {
                    children.add(parseNode())
                }
                if (pos >= tokens.size) throw TemplateException("Unclosed for block")
                pos++ // Skip ForEnd
                Node.For(token.item, listNode, children)
            }
            is Token.Set -> Node.Set(token.variable, token.value)
            is Token.RaiseException -> Node.RaiseException(token.message)
            else -> throw TemplateException("Unexpected token: $token")
        }
    }

    private fun parseCondition(expr: String): ConditionNode {
        val trimmed = expr.trim()

        // 处理括号包围的表达式
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            return parseCondition(trimmed.substring(1, trimmed.length - 1))
        }

        when {
            trimmed.startsWith("not ") -> {
                val subExpr = trimmed.substring(4).trim()
                return ConditionNode.UnaryOp("not", parseCondition(subExpr))
            }
            // 处理复杂的 or 条件（优先级低）
            trimmed.contains(" or ") -> {
                val parts = splitByOperator(trimmed, " or ")
                if (parts.size >= 2) {
                    var result = parseCondition(parts[0])
                    for (i in 1 until parts.size) {
                        result = ConditionNode.BinaryOp(result, "or", parseCondition(parts[i]))
                    }
                    return result
                }
            }
            // 处理复杂的 and 条件（优先级高）
            trimmed.contains(" and ") -> {
                val parts = splitByOperator(trimmed, " and ")
                if (parts.size >= 2) {
                    var result = parseCondition(parts[0])
                    for (i in 1 until parts.size) {
                        result = ConditionNode.BinaryOp(result, "and", parseCondition(parts[i]))
                    }
                    return result
                }
            }
            trimmed.endsWith(" is defined") -> {
                val varName = trimmed.removeSuffix(" is defined").trim()
                return ConditionNode.IsDefined(varName)
            }
            trimmed.endsWith(" is not none") -> {
                val varName = trimmed.removeSuffix(" is not none").trim()
                return ConditionNode.IsNotNone(varName)
            }
            trimmed.contains(" in ") -> {
                val parts = splitByOperator(trimmed, " in ")
                if (parts.size == 2) {
                    return ConditionNode.BinaryOp(
                        ConditionNode.Literal(parts[0].trim('\'', '\"')),
                        "in",
                        ConditionNode.Literal(parts[1])
                    )
                }
            }
            trimmed.contains(" == ") -> {
                val parts = splitByOperator(trimmed, " == ")
                if (parts.size == 2) {
                    return ConditionNode.BinaryOp(
                        ConditionNode.Literal(parts[0]),
                        "==",
                        ConditionNode.Literal(parts[1])
                    )
                }
            }
            trimmed.contains(" != ") -> {
                val parts = splitByOperator(trimmed, " != ")
                if (parts.size == 2) {
                    return ConditionNode.BinaryOp(
                        ConditionNode.Literal(parts[0]),
                        "!=",
                        ConditionNode.Literal(parts[1])
                    )
                }
            }
            else -> return ConditionNode.Literal(trimmed)
        }
        throw TemplateException("Invalid condition: $trimmed")
    }

    /**
     * 按操作符分割字符串，考虑括号嵌套
     */
    private fun splitByOperator(expr: String, operator: String): List<String> {
        val parts = mutableListOf<String>()
        var currentPart = StringBuilder()
        var parenLevel = 0
        var i = 0

        while (i < expr.length) {
            when {
                expr[i] == '(' -> {
                    parenLevel++
                    currentPart.append(expr[i])
                }
                expr[i] == ')' -> {
                    parenLevel--
                    currentPart.append(expr[i])
                }
                parenLevel == 0 && expr.substring(i).startsWith(operator) -> {
                    // 找到操作符且不在括号内
                    parts.add(currentPart.toString().trim())
                    currentPart = StringBuilder()
                    i += operator.length - 1 // -1 因为循环会 i++
                }
                else -> {
                    currentPart.append(expr[i])
                }
            }
            i++
        }

        if (currentPart.isNotEmpty()) {
            parts.add(currentPart.toString().trim())
        }

        return parts
    }

    private fun parseListExpression(expr: String): ListNode {
        val trimmed = expr.trim()
        when {
            trimmed.contains("[") && trimmed.contains("]") -> {
                val parts = trimmed.split("[", "]").filter { it.isNotBlank() }
                if (parts.size == 2 && parts[1].contains(":")) {
                    val range = parts[1].split(":").map { it.trim().toIntOrNull() }
                    val start = range.getOrNull(0)
                    val end = range.getOrNull(1)
                    return ListNode.Slice(parseListExpression(parts[0]), start, end)
                }
                return ListNode.Variable(parts[0])
            }
            else -> return ListNode.Variable(trimmed)
        }
    }
}