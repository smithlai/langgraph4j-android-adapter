package com.smith.lai.langgraph4j_android_adapter

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool


class DummyTestTools {


    @Tool("translate a string into cat language, returns string")
    fun cat_language(@P("Original string") text: String): String {
        val catted = text.toList().joinToString(" Miao ")
        return "$catted"
    }
}
