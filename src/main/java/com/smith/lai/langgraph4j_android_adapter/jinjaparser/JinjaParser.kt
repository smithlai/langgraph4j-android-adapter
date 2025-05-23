package com.smith.lai.langgraph4j_android_adapter.jinjaparser

class JinjaParser {
    fun parseAndRender(template: String, context: Map<String, Any>): String {
        val lexer = Lexer(template)
        val tokens = lexer.tokenize()
        println("tokens:" + tokens.joinToString(","))
        val parser = Parser(tokens)
        val nodes = parser.parse()
        println("nodes:" + nodes.joinToString(","))
        val mutableContext = context.toMutableMap()
        mutableContext["strftime_now"] = "function"
        val renderer = Renderer(nodes, mutableContext)
        return renderer.render()
    }
}