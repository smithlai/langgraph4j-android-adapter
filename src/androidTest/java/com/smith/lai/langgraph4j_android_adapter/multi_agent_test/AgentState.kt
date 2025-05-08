package com.smith.lai.langgraph4j_android_adapter.multi_agent_test

import dev.langchain4j.data.message.ChatMessage
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer
import org.bsc.langgraph4j.prebuilt.MessagesState
import org.bsc.langgraph4j.serializer.StateSerializer
import org.bsc.langgraph4j.state.AgentStateFactory
import java.util.Optional


object AgentState {

    class State(initData: Map<String, Any>?) : MessagesState<ChatMessage?>(initData) {
        fun next(): Optional<String>? {
            return value<String>("next")
        }

        companion object {
            val SCHEMA: MutableMap<String, org.bsc.langgraph4j.state.Channel<*>>? = MessagesState.SCHEMA
        }
    }


    enum class Serializers(private val serializer: StateSerializer<State>) {
        STD(
            LC4jStateSerializer(
                AgentStateFactory<State> { initData: Map<String, Any>? ->
                    initData?.let { State(it) } ?: throw IllegalArgumentException("initData cannot be null")
                }
            )
        );

        fun getSerializer(): StateSerializer<State> {
            return serializer
        }
    }
}