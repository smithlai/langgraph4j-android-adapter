package com.smith.lai.langgraph4j_android_adapter

import com.smith.lai.langgraph4j_android_adapter.jinjaparser.JinjaParser
import com.smith.lai.langgraph4j_android_adapter.jinjaparser.Logger
import com.smith.lai.langgraph4j_android_adapter.jinjaparser.Utils
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import com.google.gson.GsonBuilder

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

{#- This block extracts the system message, so we can slot it into the right place. #}
{%- if messages[0]['role'] == 'system' %}
    {%- set system_message = messages[0]['content']|trim %}
    {%- set messages = messages[1:] %}
{%- else %}
    {%- set system_message = "" %}
{%- endif %}

{#- System message #}
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

{#- Custom tools are passed in a user message with some extra guidance #}
{%- if tools_in_user_message and not tools is none %}
    {#- Extract the first user message so we can plug it in here #}
    {%- if messages | length != 0 %}
        {%- set first_user_message = messages[0]['content']|trim %}
        {%- set messages = messages[1:] %}
    {%- else %}
        {{- raise_exception("Cannot put tools in the first user message when there's no first user message!") }}
{%- endif %}
    {{- '<|start_header_id|>user<|end_header_id|>\n\n' -}}
    {{- "Given the following functions, please respond with a JSON for a function call " }}
    {{- "with its proper arguments that best answers the given prompt.\n\n" }}
    {{- 'Respond in the format {"name": function name, "parameters": dictionary of argument name and its value}.' }}
    {{- "Do not use variables.\n\n" }}
    {%- for t in tools %}
        {{- t | tojson(indent=4) }}
        {{- "\n\n" }}
    {%- endfor %}
    {{- first_user_message + "<|eot_id|>"}}
{%- endif %}

{%- for message in messages %}
    {%- if not (message.role == 'ipython' or message.role == 'tool' or 'tool_calls' in message) %}
        {{- '<|start_header_id|>' + message['role'] + '<|end_header_id|>\n\n'+ message['content'] | trim + '<|eot_id|>' }}
    {%- elif 'tool_calls' in message %}
        {%- if not message.tool_calls|length == 1 %}
            {{- raise_exception("This model only supports single tool-calls at once!") }}
        {%- endif %}
        {%- set tool_call = message.tool_calls[0].function %}
        {{- '<|start_header_id|>assistant<|end_header_id|>\n\n' -}}
        {{- '{"name": "' + tool_call.name + '", ' }}
        {{- '"parameters": ' }}
        {{- tool_call.arguments | tojson }}
        {{- "}" }}
        {{- "<|eot_id|>" }}
    {%- elif message.role == "tool" or message.role == "ipython" %}
        {{- "<|start_header_id|>ipython<|end_header_id|>\n\n" }}
        {%- if message.content is mapping or message.content is iterable %}
            {{- message.content | tojson }}
        {%- else %}
            {{- message.content }}
        {%- endif %}
        {{- "<|eot_id|>" }}
    {%- endif %}
{%- endfor %}
{%- if add_generation_prompt %}
    {{- '<|start_header_id|>assistant<|end_header_id|>\n\n' }}
{%- endif %}
        """.trimIndent()

        // Define ChatMessage list
        val chatMessages: List<ChatMessage> = listOf(
//            SystemMessage("You are a helpful AI assistant.\n"),
            UserMessage("What's the weather like today?")
        )

        // Convert ChatMessage to List<Map<String, String>>
        val messages: List<Map<String, String>> = chatMessages.map { message ->
            mapOf(
                "role" to message.type().toString().lowercase(), // e.g., "system", "user"
                "content" to message.toString() // Use text() for content
            )
        }

        // Use DummyTools and generate tools JSON
        val dummyTools = DummyTestTools()
        val tools = Utils.toolsObjectToMap(dummyTools)
        val gson = GsonBuilder().setPrettyPrinting().create()
        println("tools---\n${gson.toJson(tools)}\n------------")

        val context = mapOf(
            "bos_token" to "",  // 虽然定义了，但不会输出
            "custom_tools" to tools,
            "tools_in_user_message" to false,
            "messages" to messages
        )
        println("context---\n${gson.toJson(context)}\n------------")

        val result = parser.parseAndRender(template, context)
        println("result: [$result]")

        // 验证结果不包含 bos_token
        assert(!result.contains("<|BEGIN_OF_STREAM|>")) {
            "Result should not contain bos_token value"
        }
        println("✅ Test passed: bos_token is not rendered in output")
    }

//    @Test
    fun testFunctionRegistration() {
        // 测试函数注册功能
        val availableFunctions = parser.getAvailableFunctions()
        println("Available functions: $availableFunctions")

        assert(availableFunctions.containsKey("strftime_now")) { "strftime_now should be registered" }
        assert(availableFunctions.containsKey("tojson")) { "tojson should be registered" }
        assert(availableFunctions.containsKey("trim")) { "trim should be registered" }

        println("✅ Test passed: All functions are properly registered")
    }


//    @Test
    fun testFunctionCentralization() {
        val template = """
            Date: {{ strftime_now("%Y-%m-%d") }}
            Trimmed: {{ "  hello world  " | trim }}
            JSON: {{ {"key": "value"} | tojson(indent=2) }}
        """.trimIndent()

        val context = mapOf<String, Any>()
        val result = parser.parseAndRender(template, context)
        println("Function test result: [$result]")

        // 验证函数都能正常工作
        assert(result.contains("Date:")) { "strftime_now function should work" }
        assert(result.contains("Trimmed: hello world")) { "trim filter should work" }
        assert(result.contains("JSON:")) { "tojson filter should work" }
        println("✅ Test passed: All functions work correctly")
    }
}