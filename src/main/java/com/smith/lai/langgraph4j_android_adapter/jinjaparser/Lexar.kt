package com.smith.lai.langgraph4j_android_adapter.jinjaparser

import com.smith.lai.langgraph4j_android_adapter.jinjaparser.data.Token

class Lexer(private val input: String) {
    private var pos = 0

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (pos < input.length) {
            while (pos < input.length && input[pos].isWhitespace()) pos++
            if (pos >= input.length) break
            when {
                input.startsWith("{#", pos) -> parseComment() // 处理注释
                input.startsWith("{{-", pos) || input.startsWith("{{", pos) -> {
                    val token = parseVariableOrText()
                    // 过滤掉 bos_token 变量
                    if (token !is Token.Variable || token.name != "bos_token") {
                        tokens.add(token)
                    }
                }
                input.startsWith("{%-", pos) || input.startsWith("{%", pos) -> tokens.add(parseControlBlock())
                else -> tokens.add(parseText())
            }
        }
        return tokens
    }

    private fun parseComment() {
        // 跳过注释块 {# ... #}
        val start = pos
        pos += 2 // Skip {#
        while (pos < input.length - 1 && !input.startsWith("#}", pos)) {
            pos++
        }
        if (pos < input.length - 1) {
            pos += 2 // Skip #}
        }
        Logger.debug("Lexer", "Skipped comment: ${input.substring(start, pos)}")
    }

    private fun parseVariableOrText(): Token {
        val trimLeft = input.startsWith("{{-", pos)
        pos += if (trimLeft) 3 else 2 // Skip {{- or {{
        val start = pos
        while (pos < input.length && !input.startsWith("}}", pos) && !input.startsWith("-}}", pos)) pos++
        val trimRight = input.startsWith("-}}", pos)
        val content = input.substring(start, pos).trim()
        pos += if (trimRight) 3 else 2 // Skip -}} or }}
        if (content.startsWith("\"") && content.endsWith("\"") && !content.contains("+")) {
            return Token.Text(content.trim('\"'), trimLeft, trimRight)
        } else {
            return Token.Variable(content, trimLeft, trimRight)
        }
    }

    private fun parseControlBlock(): Token {
        val trimLeft = input.startsWith("{%-", pos)
        pos += if (trimLeft) 3 else 2 // Skip {%- or {%
        val endIndex = input.indexOf("%}", pos)
        val endTrimIndex = input.indexOf("-%}", pos)
        val trimRight = endTrimIndex != -1 && (endTrimIndex < endIndex || endIndex == -1)
        val actualEndIndex = if (trimRight && endTrimIndex != -1) endTrimIndex else endIndex
        if (actualEndIndex == -1) throw TemplateException("Unclosed control block at position $pos")
        val content = input.substring(pos, actualEndIndex).trim()
        pos = actualEndIndex + (if (trimRight) 3 else 2) // Skip -%} or %}
        return when {
            content.startsWith("if ") -> Token.IfStart(content.substring(3).trim())
            content == "endif" -> Token.IfEnd
            content.startsWith("elif ") -> Token.ElseIf(content.substring(5).trim())
            content == "else" -> Token.Else
            content.startsWith("for ") -> {
                val parts = content.substring(4).trim().split(" in ")
                if (parts.size == 2) Token.ForStart(parts[0].trim(), parts[1].trim())
                else throw TemplateException("Invalid for syntax: $content")
            }
            content == "endfor" -> Token.ForEnd
            content.startsWith("set ") -> {
                val parts = content.substring(4).trim().split(" = ", limit = 2)
                if (parts.size == 2) Token.Set(parts[0].trim(), parts[1].trim())
                else throw TemplateException("Invalid set syntax: $content")
            }
            content.startsWith("raise_exception(") -> {
                val message = content.substring(16, content.length - 1).trim('\"')
                Token.RaiseException(message)
            }
            else -> throw TemplateException("Unknown control block: $content")
        }
    }

    private fun parseText(): Token {
        val start = pos
        while (pos < input.length && !input.startsWith("{{", pos) && !input.startsWith("{%", pos) && !input.startsWith("{#", pos)) pos++
        val text = input.substring(start, pos)
        return Token.Text(text)
    }
}