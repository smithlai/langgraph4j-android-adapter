package com.smith.lai.langgraph4j_android_adapter

import android.util.Log
import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TestTools1 {
    private var lastResult: String? = null

    @Tool("获取指定城市的天气预报信息")
    fun getWeather(@P("城市名称") city: String): String {
        Log.e("aaaaa", "成功呼叫.....getWeather")
        val weathers = mapOf(
            "北京" to "晴天，25°C",
            "上海" to "多云，28°C",
            "广州" to "阵雨，30°C",
            "深圳" to "晴间多云，31°C",
            "台北" to "雷阵雨，27°C",
            "Shanghai" to "多云，28°C"
        )

        val weather = weathers[city] ?: "未知城市，无法获取天气信息"
        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        return "$city 当前天气：$weather (更新时间: $time)"
    }

    @Tool("将文本反转，从后向前排列字符")
    fun reverseText(@P("需要反转的文本") text: String): String {
        val reversed = text.reversed()
        return "原文: $text\n反转后: $reversed"
    }

    @Tool("Performs a test with the given input")
    fun performTest(input: String): String {
        return "Test performed with input: $input"
    }
}
