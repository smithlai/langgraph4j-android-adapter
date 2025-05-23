package com.smith.lai.langgraph4j_android_adapter

import com.smith.lai.langgraph4j_android_adapter.jinjaparser.JinjaParser
import com.smith.lai.langgraph4j_android_adapter.jinjaparser.Logger
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.util.*

// Unit tests for JinjaParser
class JinjaParserTest {
    private val parser = JinjaParser()
    companion object {
        const val DEBUG_TAG = "EnhancedLangGraph"
    }

    @get:Rule
    val testName = TestName()

    @Before
    fun setup() {
        Logger.debug(DEBUG_TAG, "========================================")
        Logger.debug(DEBUG_TAG, "STARTING TEST: ${testName.methodName}")
        Logger.debug(DEBUG_TAG, "========================================")
    }

    @Test
    fun testComplexTemplateWithToolsAndSystemMessage() {
        val template = """
            {{- bos_token }}
            {%- if custom_tools is defined %}
                {%- set tools = custom_tools %}
            {%- endif %}
            {%- if not tools_in_user_message is defined %}
                {%- set tools_in_user_message = true %}
            {%- endif %}
            {%- if not date_string is defined %}
                {%- if strftime_now is defined %}
                    {%- set date_string = strftime_now("%d %b %Y") %}
                {%- else %}
                    {%- set date_string = "26 Jul 2024" %}
                {%- endif %}
            {%- endif %}
            {%- if not tools is defined %}
                {%- set tools = none %}
            {%- endif %}
            {%- if messages[0]['role'] == 'system' %}
                {%- set system_message = messages[0]['content']|trim %}
                {%- set messages = messages[1:] %}
            {%- else %}
                {%- set system_message = "" %}
            {%- endif %}
            {{- "<|start_header_id|>system<|end_header_id|>\n\n" }}
            {%- if tools is not none %}
                {{- "Environment: ipython\n" }}
            {%- endif %}
            {{- "Cutting Knowledge Date: December 2023\n" }}
            {{- "Today Date: " + date_string + "\n\n" }}
            {%- if tools is not none and not tools_in_user_message %}
                {{- "You have access to the following functions. To call a function, please respond with JSON for a function call." }}
                {{- 'Respond in the format {"name": function name, "parameters": dictionary of argument name and its value}.' }}
                {{- "Do not use variables.\n\n" }}
                {%- for t in tools %}
                    {{- t | tojson(indent=4) }}
                    {{- "\n\n" }}
                {%- endfor %}
            {%- endif %}
            {{- system_message }}
            {{- "<|eot_id|>" }}
        """.trimIndent()

        val context = mapOf(
            "bos_token" to "<|BEGIN_OF_STREAM|>",
            "custom_tools" to listOf(
                mapOf("name" to "get_weather", "parameters" to mapOf("city" to "string")),
                mapOf("name" to "send_email", "parameters" to mapOf("to" to "string", "subject" to "string"))
            ),
            "tools_in_user_message" to false,
            "messages" to listOf(
                mapOf("role" to "system", "content" to "You are a helpful AI assistant.\n"),
                mapOf("role" to "user", "content" to "What's the weather like today?")
            ),
//            "date_string" to "26 Jul 2024" // Added to fix date issue
        )
        val result = parser.parseAndRender(template, context)
        val expected = """
            <|BEGIN_OF_STREAM|><|start_header_id|>system<|end_header_id>

            Environment: ipython
            Cutting Knowledge Date: December 2023
            Today Date: 26 Jul 2024

            You have access to the following functions. To call a function, please respond with JSON for a function call.Respond in the format {"name": function name, "parameters": dictionary of argument name and its value}.Do not use variables.

            {
                "name": "get_weather",
                "parameters": {
                    "city": "string"
                }
            }


            {
                "name": "send_email",
                "parameters": {
                    "to": "string",
                    "subject": "string"
                }
            }


            You are a helpful AI assistant.<|eot_id>
        """.trimIndent()
        println("expected: [$expected]")
        println("result: [$result]")
        assertEquals(expected, result)
    }
}