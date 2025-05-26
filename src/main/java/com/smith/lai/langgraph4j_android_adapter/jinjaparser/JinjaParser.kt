package com.smith.lai.langgraph4j_android_adapter.jinjaparser

class JinjaParser {
    private val jinjaFunctions = JinjaFunctions()

    fun parseAndRender(template: String, context: Map<String, Any>): String {
        val lexer = Lexer(template)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val nodes = parser.parse()
        val mutableContext = context.toMutableMap()

        // 使用 JinjaFunctions 自动注册所有函数
        jinjaFunctions.registerFunctionsToContext(mutableContext)

        val renderer = Renderer(nodes, mutableContext)
        return renderer.render()
    }

    /**
     * 获取可用的函数列表
     */
    fun getAvailableFunctions(): Map<String, String> {
        return jinjaFunctions.getAvailableFunctions()
    }
}