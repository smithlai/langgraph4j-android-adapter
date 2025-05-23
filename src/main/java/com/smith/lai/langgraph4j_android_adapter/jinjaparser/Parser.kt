package com.smith.lai.langgraph4j_android_adapter.jinjaparser

import com.smith.lai.langgraph4j_android_adapter.jinjaparser.data.ConditionNode
import com.smith.lai.langgraph4j_android_adapter.jinjaparser.data.ListNode
import com.smith.lai.langgraph4j_android_adapter.jinjaparser.data.Node
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
                val elseChildren = mutableListOf<Node>()
                var hasElse = false
                while (pos < tokens.size && tokens[pos] !is Token.IfEnd) {
                    if (tokens[pos] is Token.Else) {
                        pos++ // Skip Else
                        hasElse = true
                        continue
                    }
                    if (hasElse) {
                        elseChildren.add(parseNode())
                    } else {
                        ifChildren.add(parseNode())
                    }
                }
                if (pos >= tokens.size) throw TemplateException("Unclosed if block")
                pos++ // Skip IfEnd
                Node.If(condition, ifChildren, elseChildren)
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
        when {
            trimmed.startsWith("not ") -> {
                val subExpr = trimmed.substring(4).trim()
                return ConditionNode.UnaryOp("not", parseCondition(subExpr))
            }
            trimmed.contains(" and ") -> {
                val parts = trimmed.split(" and ").map { it.trim() }
                if (parts.size == 2) {
                    return ConditionNode.BinaryOp(parseCondition(parts[0]), "and", parseCondition(parts[1]))
                }
            }
            trimmed.contains(" or ") -> {
                val parts = trimmed.split(" or ").map { it.trim() }
                if (parts.size == 2) {
                    return ConditionNode.BinaryOp(parseCondition(parts[0]), "or", parseCondition(parts[1]))
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
            trimmed.contains(" == ") -> {
                val parts = trimmed.split(" == ").map { it.trim() }
                if (parts.size == 2) {
                    return ConditionNode.BinaryOp(
                        ConditionNode.Literal(parts[0]),
                        "==",
                        ConditionNode.Literal(parts[1])
                    )
                }
            }
            trimmed.contains(" != ") -> {
                val parts = trimmed.split(" != ").map { it.trim() }
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
